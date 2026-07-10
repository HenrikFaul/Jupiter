package com.jupiter.filemanager.feature.cleanup

import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.MediaQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cache of the last completed duplicate analysis so re-opening the Duplicates screen renders the
 * previous result INSTANTLY instead of showing a blank scanning screen for many seconds.
 *
 * Two tiers:
 *  - an in-memory copy (survives navigation within the process — the `@HiltViewModel` is recreated
 *    on each open, so a VM field would not);
 *  - a small on-disk snapshot ([snapshotFile]) so the result also survives a **full process kill** —
 *    the user closing the app and re-opening it later still sees the list immediately.
 *
 * The cached result is a starting point, not the final word: the ViewModel still runs a **silent**
 * background rescan on open and swaps in fresh results when it finishes. Only the group list is
 * persisted (enough to render + act on); media-quality labels are re-probed in the background.
 */
@Singleton
class DuplicateScanCache @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val snapshotFile: File get() = File(context.filesDir, SNAPSHOT_NAME)

    @Volatile
    private var cachedGroups: List<DuplicateGroup>? = null

    @Volatile
    private var cachedQualities: Map<String, MediaQuality> = emptyMap()

    @Volatile
    private var diskLoaded = false

    /** Probed media-quality labels from the last scan this process (may be partial, not persisted). */
    val qualities: Map<String, MediaQuality> get() = cachedQualities

    /**
     * The last completed scan's groups: the in-memory copy if present, otherwise the on-disk
     * snapshot (read once, on the IO dispatcher). Returns null only when nothing has ever been
     * scanned/persisted, so the caller falls back to a full scan.
     */
    suspend fun load(): List<DuplicateGroup>? {
        cachedGroups?.let { return it }
        if (diskLoaded) return cachedGroups
        val fromDisk = withContext(ioDispatcher) { readSnapshot() }
        diskLoaded = true
        if (fromDisk != null) cachedGroups = fromDisk
        return cachedGroups
    }

    /** Records a completed scan's groups (an empty list is valid — "scanned, no duplicates"). */
    fun saveGroups(groups: List<DuplicateGroup>) {
        cachedGroups = groups
        diskLoaded = true
        ioScope.launch { writeSnapshot(groups) }
    }

    /** Records the latest probed qualities (in-memory only; re-probed on a cold start). */
    fun saveQualities(qualities: Map<String, MediaQuality>) {
        cachedQualities = qualities
    }

    // --- Disk snapshot (tab-separated, one line per file) ------------------------------------

    private fun writeSnapshot(groups: List<DuplicateGroup>) {
        runCatching {
            val sb = StringBuilder()
            for (group in groups) {
                for (f in group.files) {
                    // A control char in a path/name would corrupt the line — skip that file (rare).
                    if (group.hash.hasControl() || f.path.hasControl() || f.name.hasControl()) continue
                    sb.append(group.hash).append('\t')
                        .append(f.path).append('\t')
                        .append(f.name).append('\t')
                        .append(f.sizeBytes).append('\t')
                        .append(f.lastModified).append('\t')
                        .append(f.type.name).append('\t')
                        .append(f.extension).append('\n')
                }
            }
            val tmp = File(context.filesDir, "$SNAPSHOT_NAME.tmp")
            tmp.writeText(sb.toString())
            // Atomic-ish replace so a crash mid-write never leaves a half-written snapshot.
            if (!tmp.renameTo(snapshotFile)) {
                snapshotFile.writeText(sb.toString())
                tmp.delete()
            }
        }
    }

    private fun readSnapshot(): List<DuplicateGroup>? {
        return runCatching {
            val file = snapshotFile
            if (!file.isFile) return null
            val byHash = LinkedHashMap<String, MutableList<FileItem>>()
            file.forEachLine { line ->
                val p = line.split('\t')
                if (p.size < 7) return@forEachLine
                val (hash, path, name) = Triple(p[0], p[1], p[2])
                val size = p[3].toLongOrNull() ?: return@forEachLine
                val mtime = p[4].toLongOrNull() ?: return@forEachLine
                val type = runCatching { FileType.valueOf(p[5]) }.getOrDefault(FileType.OTHER)
                byHash.getOrPut(hash) { mutableListOf() }.add(
                    FileItem(
                        path = path,
                        name = name,
                        isDirectory = false,
                        sizeBytes = size,
                        lastModified = mtime,
                        type = type,
                        extension = p[6],
                    ),
                )
            }
            // Only real duplicate groups (a snapshot of a since-cleaned group could be stale; the
            // silent rescan corrects it, and existence is re-verified when a delete is attempted).
            byHash.entries
                .filter { it.value.size > 1 }
                .map { DuplicateGroup(hash = it.key, files = it.value.toList()) }
        }.getOrNull()
    }

    private fun String.hasControl(): Boolean = any { it == '\t' || it == '\n' || it == '\r' }

    private companion object {
        const val SNAPSHOT_NAME = "duplicate_scan_snapshot.tsv"
    }
}
