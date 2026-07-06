package com.jupiter.filemanager.feature.permission

import androidx.lifecycle.ViewModel
import com.jupiter.filemanager.data.index.GrantSurveyPolicy
import com.jupiter.filemanager.data.index.IndexStatus
import com.jupiter.filemanager.data.index.IndexingScheduler
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.data.permission.StorageAccessState
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.di.ApplicationScope
import com.jupiter.filemanager.domain.repository.IndexStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the storage-permission screen.
 *
 * @property accessState the current storage-access level resolved by the
 *   [StorageAccessManager].
 * @property hasAccess convenience flag that is true when the app has full
 *   file-system access (i.e. [accessState] == [StorageAccessState.FULL_ACCESS]).
 */
data class PermissionUiState(
    val accessState: StorageAccessState = StorageAccessState.NONE,
    val hasAccess: Boolean = false,
)

/**
 * Drives the permission screen: exposes the current [StorageAccessState], lets the UI
 * re-evaluate it (e.g. after returning from the system settings screen), and — critically —
 * kicks off the full-storage survey the moment access is granted.
 *
 * The first-run survey is scheduled at process start, which runs BEFORE the user grants All
 * Files Access, so it no-ops (the worker cannot see the shared volume). Without a re-trigger
 * here the user's grant would only take effect on the next cold start ("I gave access but it
 * still doesn't scan"). [refresh] therefore consults [GrantSurveyPolicy] whenever access is
 * present and asks [IndexingScheduler] to run the survey when appropriate.
 */
@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val storageAccessManager: StorageAccessManager,
    private val settings: SettingsDataStore,
    private val indexStateRepository: IndexStateRepository,
    private val indexingScheduler: IndexingScheduler,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    /**
     * Re-reads the current storage-access state and publishes it. Intended to be invoked when
     * the screen (re)gains focus or after the user returns from the system "All files access"
     * settings page. When access is (now) present, it also starts the full-storage survey so
     * granting access immediately begins scanning instead of waiting for the next cold start.
     */
    fun refresh() {
        val state = buildState()
        _uiState.update { state }
        if (state.hasAccess) {
            startSurveyIfNeeded()
        }
    }

    /**
     * Starts a full-storage survey when [GrantSurveyPolicy] allows it: access is held, indexing
     * is enabled, and the index is neither already RUNNING nor COMPLETE. Uses
     * [IndexingScheduler.rebuildNow] (REPLACE) so it cancels the earlier no-op survey (scheduled
     * before access existed) and starts a fresh pass that can actually see the volume.
     *
     * Runs on the process-lifetime [appScope], NOT `viewModelScope`: granting access navigates
     * away from the permission screen (`popUpTo(..., inclusive = true)`), which clears this
     * ViewModel and would cancel a `viewModelScope` job mid-flight — before its DataStore/Room
     * reads finish and the enqueue lands. On [appScope] the survey is scheduled regardless. Any
     * failure is swallowed — this is an optimization, never critical path.
     */
    private fun startSurveyIfNeeded() {
        appScope.launch {
            runCatching {
                val indexingEnabled = settings.indexingEnabled.first()
                val status = indexStateRepository.current()?.status ?: IndexStatus.EMPTY
                if (GrantSurveyPolicy.shouldStartSurvey(
                        hasFullAccess = true,
                        indexingEnabled = indexingEnabled,
                        status = status,
                    )
                ) {
                    indexingScheduler.rebuildNow()
                }
            }
        }
    }

    private fun buildState(): PermissionUiState {
        val state = storageAccessManager.currentState()
        return PermissionUiState(
            accessState = state,
            hasAccess = state == StorageAccessState.FULL_ACCESS,
        )
    }
}
