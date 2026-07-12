package com.jupiter.filemanager.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.repository.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Recycle Bin (Trash) screen.
 *
 * Streams the current [com.jupiter.filemanager.domain.model.TrashItem] list from
 * the [TrashRepository] (already ordered most-recently deleted first) and exposes
 * the three recovery/cleanup actions:
 *
 *  - [restore] moves an item back to its original location (never overwriting an
 *    existing file — the repository restores alongside as `"name (restored)"`).
 *  - [restoreAll] restores a snapshot of the current bin serially, preserving the
 *    repository's no-overwrite rule for every item.
 *  - [deletePermanently] removes a single item from the bin for good.
 *  - [emptyAll] permanently clears the entire bin.
 *
 * All actions are delegated to the repository, run off the main thread there, and
 * surface any failure as a one-shot [TrashUiState.errorMessage].
 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashRepository: TrashRepository,
    private val settings: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        observeTrash()
    }

    /** Restores the trashed item with the given [id] back to its original location. */
    fun restore(id: String) {
        runAction { trashRepository.restore(id) }
    }

    /**
     * Restores every item that was in the bin when the action was requested.
     *
     * This deliberately runs serially: each restore can create parent folders or choose a
     * non-conflicting "(restored)" name, and serial execution prevents two entries with the same
     * original name from racing to choose the same target. A failure leaves that item's payload in
     * the Recycle Bin; successful restores are never rolled back or overwritten.
     */
    fun restoreAll() {
        if (_uiState.value.busy) return
        val ids = _uiState.value.items.map { it.id }
        if (ids.isEmpty()) return

        _uiState.value = _uiState.value.copy(busy = true, errorMessage = null)
        viewModelScope.launch {
            var failed = 0
            for (id in ids) {
                when (trashRepository.restore(id)) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> failed++
                }
            }
            _uiState.value = _uiState.value.copy(
                busy = false,
                errorMessage = if (failed == 0) null else {
                    "$failed item" + if (failed == 1) " could not be restored." else "s could not be restored."
                },
            )
        }
    }

    /** Permanently deletes the trashed item with the given [id]. */
    fun deletePermanently(id: String) {
        runAction { trashRepository.deletePermanently(id) }
    }

    /** Permanently empties the entire Recycle Bin. */
    fun emptyAll() {
        runAction { trashRepository.emptyAll() }
    }

    /** Clears a previously surfaced one-shot error message. */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ---- internals -------------------------------------------------------

    private fun runAction(block: suspend () -> AppResult<Unit>) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true, errorMessage = null)
        viewModelScope.launch {
            val message = when (val result = block()) {
                is AppResult.Success -> null
                is AppResult.Failure -> result.error.displayMessage
            }
            _uiState.value = _uiState.value.copy(busy = false, errorMessage = message)
        }
    }

    private fun observeTrash() {
        viewModelScope.launch {
            combine(
                trashRepository.observeTrash(),
                settings.trashAutoDeleteDays,
            ) { items, autoDeleteDays -> items to autoDeleteDays }
                .collect { (items, autoDeleteDays) ->
                    _uiState.value = _uiState.value.copy(
                        items = items,
                        isLoading = false,
                        autoDeleteDays = autoDeleteDays,
                    )
                }
        }
    }
}
