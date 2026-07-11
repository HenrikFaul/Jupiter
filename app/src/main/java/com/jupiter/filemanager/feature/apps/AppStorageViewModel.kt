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
     * When access is already held, the grant prompt is dropped SYNCHRONOUSLY (before the scan even
     * starts) and a "Scanning…" state is shown, so the user never stares at "Grant Usage access"
     * for the ~10 s the per-app walk takes. Results then stream in: the first apps appear almost
     * immediately and the list grows until the scan completes.
     */
    fun load() {
        val granted = source.hasUsageAccess()
        _uiState.update {
            it.copy(
                isLoading = true,
                // Leave the grant prompt at once when we already know access is granted; otherwise
                // keep whatever the last scan reported (the stream will set it authoritatively).
                permissionRequired = if (granted) false else it.permissionRequired,
            )
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            source.queryStream().collect { overview ->
                _uiState.update { current ->
                    // On a REFRESH (we already have apps), don't blank the list back to the scan
                    // view for the initial empty "scanning" frame — keep the old apps on screen with
                    // the scanning indicator until fresh results arrive. A first-ever scan (no apps
                    // yet) still shows the dedicated "Scanning…" view.
                    val keepPrevious = overview.scanning && overview.apps.isEmpty() &&
                        current.overview?.apps?.isNotEmpty() == true
                    current.copy(
                        isLoading = overview.scanning,
                        overview = if (keepPrevious) current.overview else overview,
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
