package com.jupiter.filemanager.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Sync Conflicts screen.
 *
 * Streams the real [com.jupiter.filemanager.domain.model.SyncConflict] list from
 * the [SyncRepository]. There is no live sync backend (cloud / NAS protocol I/O,
 * conflict detection) wired up yet, so the repository starts empty and this
 * ViewModel surfaces an honest empty state rather than fabricating conflicts.
 *
 * Resolving a conflict delegates to [SyncRepository.resolve], keeping either the
 * local or the remote copy.
 */
@HiltViewModel
class SyncConflictsViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncConflictsUiState())
    val uiState: StateFlow<SyncConflictsUiState> = _uiState.asStateFlow()

    init {
        observeConflicts()
    }

    /** Resolves [conflictId] by keeping the local copy. */
    fun keepLocal(conflictId: String) {
        resolve(conflictId, keepLocal = true)
    }

    /** Resolves [conflictId] by keeping the remote copy. */
    fun keepRemote(conflictId: String) {
        resolve(conflictId, keepLocal = false)
    }

    /** Clears a previously surfaced one-shot error message. */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ---- internals -------------------------------------------------------

    private fun resolve(conflictId: String, keepLocal: Boolean) {
        if (_uiState.value.resolvingId != null) return
        _uiState.value = _uiState.value.copy(resolvingId = conflictId, errorMessage = null)
        viewModelScope.launch {
            when (val result = syncRepository.resolve(conflictId, keepLocal)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(resolvingId = null)
                }

                is AppResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        resolvingId = null,
                        errorMessage = result.error.displayMessage,
                    )
                }
            }
        }
    }

    private fun observeConflicts() {
        viewModelScope.launch {
            syncRepository.observeConflicts().collect { conflicts ->
                _uiState.value = _uiState.value.copy(
                    conflicts = conflicts,
                    isLoading = false,
                )
            }
        }
    }
}
