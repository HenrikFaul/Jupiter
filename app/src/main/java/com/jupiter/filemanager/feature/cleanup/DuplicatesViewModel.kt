package com.jupiter.filemanager.feature.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.media.MediaQualityProbe
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.MediaQuality
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import com.jupiter.filemanager.domain.repository.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Drives the Duplicates screen: scans the primary storage root for duplicate
 * files (real data via [StorageAnalyticsRepository.findDuplicates]), lets the
 * user select redundant copies to remove, and deletes them on a background
 * dispatcher.
 *
 * It additionally probes each discovered file's intrinsic media quality via
 * [MediaQualityProbe] (off the main thread) so the UI can show a per-file
 * quality label.
 */
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val analyticsRepository: StorageAnalyticsRepository,
    private val mediaQualityProbe: MediaQualityProbe,
    private val storageAccessManager: StorageAccessManager,
    private val trashRepository: TrashRepository,
    private val scanCache: DuplicateScanCache,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    /**
     * Bounds concurrent media-quality probes so deferred [probeGroup] calls never
     * flood the IO dispatcher while (or after) the scan is running.
     */
    private val probeGate = Semaphore(permits = 2)

    /** Tracks deferred probe jobs so a rescan can cancel stale probing. */
    private val probeJobs = mutableListOf<Job>()

    /**
     * Paths the user deleted this session. A scan (especially the silent background refresh that
     * runs on open) can still be in flight when a delete happens and, on completion, would set
     * `groups` back to what it collected BEFORE the delete — resurrecting the just-removed groups.
     * Every place that publishes groups filters these out, so a deleted file can never reappear.
     * Cleared on a user-initiated fresh rescan (the index no longer contains the deleted files).
     */
    private val deletedPaths = mutableSetOf<String>()

    /**
     * Publishes [groups] to the UI with the just-deleted paths removed and any group that is no
     * longer a duplicate (fewer than two copies) dropped. Single funnel so scan-collect,
     * scan-completion, and delete all stay consistent and a scan can't clobber a delete.
     */
    private fun publishGroups(groups: List<DuplicateGroup>, isScanning: Boolean? = null) {
        val cleaned = if (deletedPaths.isEmpty()) {
            groups
        } else {
            groups
                .map { g -> g.copy(files = g.files.filter { it.path !in deletedPaths }) }
                .filter { it.files.size > 1 }
        }
        _uiState.value = _uiState.value.copy(
            groups = cleaned,
            isScanning = isScanning ?: _uiState.value.isScanning,
        )
    }

    init {
        // Load the last analysis (in-memory, or the on-disk snapshot that survives a process kill)
        // and render it IMMEDIATELY, then quietly refresh in the background — the fix for the
        // multi-second blank-screen wait on every re-open. Only when nothing was ever cached do we
        // show a full scan. The disk read is off the main thread, so the launch resolves in ms.
        // Show the loading state synchronously so the "no duplicates" empty view never flashes
        // during the brief cache read.
        _uiState.value = _uiState.value.copy(isScanning = true)
        viewModelScope.launch {
            val cached = runCatching { scanCache.load() }.getOrNull()
            if (cached != null) {
                _uiState.value = _uiState.value.copy(
                    groups = cached,
                    qualities = scanCache.qualities,
                    isScanning = false,
                    permissionRequired = false,
                )
                scan(silent = true)
            } else {
                scan()
            }
        }
    }

    /**
     * Starts (or restarts) a duplicate scan over the primary storage root.
     *
     * If the app lacks All-Files-Access, the walk is skipped entirely and
     * [DuplicatesUiState.permissionRequired] is set so the screen can render an
     * actionable CTA instead of an indefinite spinner.
     *
     * When [silent] is true the currently-shown groups/selection are KEPT while the rescan runs
     * (used for the background refresh on open), and the fresh results are swapped in only when the
     * scan completes — so re-opening never flashes a blank screen. A normal (non-silent) scan clears
     * the list and shows the scanning state as before.
     */
    fun scan(silent: Boolean = false) {
        if (!storageAccessManager.hasAllFilesAccess()) {
            cancelProbes()
            scanCache.saveGroups(emptyList())
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                permissionRequired = true,
                groups = emptyList(),
                selectedPaths = emptySet(),
                qualities = emptyMap(),
                errorMessage = null,
                infoMessage = null,
            )
            return
        }

        cancelProbes()
        // A user-initiated fresh scan trusts the index (which no longer contains files deleted this
        // session — moveToTrash removed their rows), so the session delete-filter can be reset.
        if (!silent) deletedPaths.clear()
        // Set the scanning state synchronously (non-silent) so the screen never momentarily
        // renders the "no duplicates" empty state before the flow's onStart fires.
        if (!silent) {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                permissionRequired = false,
                groups = emptyList(),
                selectedPaths = emptySet(),
                qualities = emptyMap(),
                errorMessage = null,
                infoMessage = null,
            )
        }
        viewModelScope.launch {
            val root = android.os.Environment.getExternalStorageDirectory()?.absolutePath
                ?: "/storage/emulated/0"
            val collected = mutableListOf<DuplicateGroup>()
            analyticsRepository.findDuplicates(root)
                .onStart {
                    _uiState.value = _uiState.value.copy(
                        isScanning = true,
                        permissionRequired = false,
                        errorMessage = null,
                        infoMessage = null,
                    )
                }
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        errorMessage = throwable.message ?: "Failed to scan for duplicates",
                    )
                }
                .onCompletion { cause ->
                    if (cause == null) {
                        // publishGroups drops anything deleted while this scan was in flight, so a
                        // scan finishing AFTER a delete cannot resurrect the removed groups. A silent
                        // refresh keeps the old list visible during the scan; swap in the fresh
                        // result now. A visible scan has been streaming groups already.
                        publishGroups(collected.toList(), isScanning = false)
                        scanCache.saveGroups(_uiState.value.groups)
                        // Probe quality only after the scan finishes, so probing never
                        // competes with the (CPU/IO-heavy) hashing walk on the IO pool.
                        probeGroups(_uiState.value.groups)
                    }
                }
                .collect { group ->
                    if (group.files.size > 1) {
                        collected.add(group)
                        // A visible scan renders content as soon as `groups` is non-empty; a silent
                        // refresh keeps the cached list on screen until it completes (no flicker).
                        if (!silent) {
                            publishGroups(collected.toList())
                        }
                    }
                }
        }
    }

    /**
     * Probes media quality for every discovered [groups] off the scan critical
     * path, one group per coroutine, with concurrency bounded by [probeGate].
     * Merges results into UI state as they resolve. Never throws; the probe itself
     * guards each file and falls back to a safe empty quality.
     */
    private fun probeGroups(groups: List<DuplicateGroup>) {
        for (group in groups) {
            val paths = group.files.map { it.path }
            if (paths.isEmpty()) continue
            val job = viewModelScope.launch {
                probeGate.withPermit {
                    val probed: Map<String, MediaQuality> = mediaQualityProbe.probeAll(paths)
                    if (probed.isNotEmpty()) {
                        val merged = _uiState.value.qualities + probed
                        _uiState.value = _uiState.value.copy(qualities = merged)
                        scanCache.saveQualities(merged)
                    }
                }
            }
            probeJobs.add(job)
        }
    }

    /** Cancels any in-flight deferred probe jobs (e.g. before a rescan). */
    private fun cancelProbes() {
        probeJobs.forEach { it.cancel() }
        probeJobs.clear()
    }

    /** Toggles selection of a single file path within a duplicate group. */
    fun toggleSelection(path: String) {
        val current = _uiState.value.selectedPaths
        val updated = if (path in current) current - path else current + path
        _uiState.value = _uiState.value.copy(selectedPaths = updated)
    }

    /**
     * Selects every duplicate copy except the first (kept) file in each group,
     * which is the recommended way to reclaim the most space safely.
     */
    fun selectAllExtras() {
        val extras = _uiState.value.groups
            .asSequence()
            .flatMap { it.files.drop(1).asSequence() }
            .map { it.path }
            .toSet()
        _uiState.value = _uiState.value.copy(selectedPaths = extras)
    }

    /**
     * One-tap "keep the best copy in each group, select the rest" — the capability
     * absorbed from the former Smart Merge screen.
     *
     * For every group, the BEST file is the highest probed [MediaQuality.score]
     * (ties broken by larger on-disk size, then most-recent modification); every
     * OTHER file in the group is added to [DuplicatesUiState.selectedPaths] so the
     * user can review the selection and delete with the existing flow.
     */
    fun selectDuplicatesKeepingBest() {
        val qualities = _uiState.value.qualities
        // Best copy is greatest: highest quality score, then largest size, then
        // most recently modified. Everything else in the group is selected.
        val comparator = compareBy<com.jupiter.filemanager.domain.model.FileItem> {
            qualities[it.path]?.score ?: 0L
        }
            .thenBy { it.sizeBytes }
            .thenBy { it.lastModified }

        val toSelect = _uiState.value.groups
            .asSequence()
            .flatMap { group ->
                val best = group.files.maxWithOrNull(comparator)
                group.files.asSequence()
                    .filter { best == null || it.path != best.path }
                    .map { it.path }
            }
            .toSet()
        _uiState.value = _uiState.value.copy(selectedPaths = toSelect)
    }

    /** Clears the current selection. */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPaths = emptySet())
    }

    /** Sets the minimum-size filter that narrows the visible duplicate groups. */
    fun setSizeFilter(filter: SizeFilter) {
        _uiState.value = _uiState.value.copy(sizeFilter = filter)
    }

    /** Dismisses any transient error or info message. */
    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    /** Surfaces a transient informational message (e.g. "Path copied"). */
    fun notify(message: String) {
        _uiState.value = _uiState.value.copy(infoMessage = message)
    }

    /**
     * Moves the currently selected files into the Recycle Bin on a background
     * dispatcher, then refreshes group state by removing the trashed files and
     * dropping any group that no longer contains duplicates.
     *
     * A file only counts as "deleted" when it was actually moved to trash; if a
     * move fails the source is PRESERVED (never hard-deleted) and it is reported
     * as a failure.
     */
    fun deleteSelected() {
        val state = _uiState.value
        if (state.selectedPaths.isEmpty() || state.isDeleting) return

        // Resolve the selected paths back to their FileItems so they can be routed
        // through the trash-aware delete path.
        val itemsByPath = state.groups.asSequence()
            .flatMap { it.files.asSequence() }
            .associateBy { it.path }
        val targets = state.selectedPaths.mapNotNull { itemsByPath[it] }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, errorMessage = null, infoMessage = null)

            val (deleted, failed) = withContext(Dispatchers.IO) {
                val movedPaths = mutableSetOf<String>()
                var failures = 0
                for (item in targets) {
                    if (trashRepository.moveToTrash(item)) {
                        movedPaths.add(item.path)
                    } else {
                        failures++
                    }
                }
                movedPaths to failures
            }

            // Remember the deleted paths for the whole session so an in-flight scan finishing later
            // (e.g. the silent refresh that runs on open — note "Still scanning…") can't re-add the
            // groups the user just removed. Every group publish filters these out.
            deletedPaths.addAll(deleted)
            val cleanedGroups = _uiState.value.groups
                .map { group -> group.copy(files = group.files.filter { it.path !in deletedPaths }) }
                .filter { it.files.size > 1 }
            // Keep the cache consistent with what the user now sees, so a re-open shows the
            // post-deletion list instantly rather than the pre-deletion one.
            scanCache.saveGroups(cleanedGroups)
            scanCache.saveQualities(_uiState.value.qualities - deleted)

            val message = when {
                deleted.isEmpty() -> "Could not delete the selected files"
                failed > 0 -> "Deleted ${deleted.size} file" +
                    (if (deleted.size == 1) "" else "s") + ", $failed failed"
                else -> "Deleted ${deleted.size} file" + if (deleted.size == 1) "" else "s"
            }

            _uiState.value = _uiState.value.copy(
                isDeleting = false,
                groups = cleanedGroups,
                selectedPaths = _uiState.value.selectedPaths - deleted,
                qualities = _uiState.value.qualities - deleted,
                errorMessage = if (deleted.isEmpty()) message else null,
                infoMessage = if (deleted.isEmpty()) null else message,
            )
        }
    }
}
