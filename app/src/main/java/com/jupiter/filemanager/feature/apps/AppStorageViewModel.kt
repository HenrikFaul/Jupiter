package com.jupiter.filemanager.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.apps.AppStorageSource
import com.jupiter.filemanager.domain.model.AppStorageOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the App-storage screen.
 *
 * @property isLoading whether the per-app scan is still running (partial results may already show).
 * @property overview the aggregated per-app storage, or null before the first load. During a scan
 *   this holds the partial, growing result.
 * @property permissionRequired true when Usage-access has not been granted.
 */
data class AppStorageUiState(
    val isLoading: Boolean = false,
    val overview: AppStorageOverview? = null,
    val permissionRequired: Boolean = false,
)

/**
 * Drives the App-storage screen: queries [AppStorageSource] for per-app usage and exposes a
 * permission-required state so the screen can prompt for Usage-access. Re-queried on resume
 * (e.g. after the user grants access in system settings).
 */
@HiltViewModel
class AppStorageViewModel @Inject constructor(
    private val source: AppStorageSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppStorageUiState())
    val uiState: StateFlow<AppStorageUiState> = _uiState.asStateFlow()

    /** The in-flight scan collection, cancelled and replaced when a new load starts. */
    private var scanJob: Job? = null

    init {
        load()
    }

    /**
     * Called when the screen resumes. If Usage-access is still missing (the user likely just
     * returned from granting it in Settings), POLL for the grant for a short while before loading —
     * the system's AppOps state can lag the return by a moment, which is why the screen used to stay
     * on the "Grant Usage access" prompt until the user left and re-entered. Once access appears (or
     * the poll times out) it loads, so the scan starts on its own with no re-entry. When access is
     * already held (e.g. returning from the uninstall dialog) it just reloads immediately.
     */
    fun onResume() {
        if (source.hasUsageAccess()) {
            load()
            return
        }
        viewModelScope.launch {
            repeat(ACCESS_POLL_ATTEMPTS) {
                if (source.hasUsageAccess()) {
                    load()
                    return@launch
                }
                delay(ACCESS_POLL_INTERVAL_MS)
            }
            // Still not granted after the grace window — refresh anyway (shows the prompt again).
            load()
        }
    }

    /**
     * (Re)loads per-app storage. Safe to call repeatedly (init, retry, on-resume).
     *
     * When access is already held, a "Scanning…" state is shown SYNCHRONOUSLY (before the scan even
     * starts) so the user never stares at "Grant Usage access" for the ~10 s the per-app walk takes;
     * results then stream in. When access is NOT held, the grant prompt is shown immediately (no
     * misleading "Scanning…" flash) — `hasUsageAccess()` is the same authoritative check the stream
     * makes, so we can commit to it now instead of waiting a frame for the stream's first emission.
     */
    fun load() {
        val granted = source.hasUsageAccess()
        _uiState.update {
            it.copy(
                isLoading = granted,        // only claim "scanning" when we will actually scan
                permissionRequired = !granted,
            )
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            source.queryStream().collect { overview ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = overview.scanning,
                        overview = resolveOverview(current.overview, overview),
                        permissionRequired = overview.permissionRequired,
                    )
                }
            }
        }
    }

    /** True when the grant is currently held (used to re-query only when it changed). */
    fun hasUsageAccess(): Boolean = source.hasUsageAccess()

    private companion object {
        /** Poll ~3 s total to absorb the AppOps grant-propagation lag after returning from Settings. */
        const val ACCESS_POLL_ATTEMPTS = 12
        const val ACCESS_POLL_INTERVAL_MS = 250L
    }
}

/**
 * Decides which overview to show as a streaming scan emits [incoming] over an existing [current].
 *
 * A FIRST-EVER scan (no apps yet) adopts every partial so the list visibly fills. But a RELOAD of an
 * already-populated screen (Refresh button / screen resume) must NOT downsize the full list to the
 * first small partial batch and then regrow it — that made the list and its headline total visibly
 * collapse from N apps to 5 and climb back on every refresh. So while a reload is still scanning we
 * keep the existing full list on screen (with the scanning banner) and swap only once the fresh scan
 * is COMPLETE (`!incoming.scanning`) — or immediately if the incoming frame is the authoritative
 * permission-required frame.
 *
 * Top-level + internal so it is unit-testable in pure JVM without constructing the ViewModel.
 */
internal fun resolveOverview(
    current: AppStorageOverview?,
    incoming: AppStorageOverview,
): AppStorageOverview {
    val reloadingPopulated = current?.apps?.isNotEmpty() == true
    val adoptIncoming = !reloadingPopulated || !incoming.scanning || incoming.permissionRequired
    return if (adoptIncoming) incoming else current!!
}
