package com.jupiter.filemanager.feature.permission

import androidx.lifecycle.ViewModel
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.data.permission.StorageAccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
 * Drives the permission screen: exposes the current [StorageAccessState] and lets
 * the UI re-evaluate it (e.g. after returning from the system settings screen).
 *
 * All permission detection is delegated to [StorageAccessManager]; no file IO is
 * performed here, so [refresh] is safe to call on the main thread.
 */
@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val storageAccessManager: StorageAccessManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    /**
     * Re-reads the current storage-access state and publishes it. Intended to be
     * invoked when the screen (re)gains focus or after the user returns from the
     * system "All files access" settings page.
     */
    fun refresh() {
        _uiState.update { buildState() }
    }

    private fun buildState(): PermissionUiState {
        val state = storageAccessManager.currentState()
        return PermissionUiState(
            accessState = state,
            hasAccess = state == StorageAccessState.FULL_ACCESS,
        )
    }
}
