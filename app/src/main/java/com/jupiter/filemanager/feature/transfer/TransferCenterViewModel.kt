package com.jupiter.filemanager.feature.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.domain.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Transfer Center screen.
 *
 * Streams the real [TransferTask][com.jupiter.filemanager.domain.model.TransferTask]
 * list from the [TransferRepository]. There is no live transfer backend wired yet,
 * so the repository starts empty and this ViewModel surfaces honest empty states
 * rather than fabricating progress. The UI splits the single source list into an
 * "active" and a "history" view via [TransferCenterUiState].
 */
@HiltViewModel
class TransferCenterViewModel @Inject constructor(
    private val transferRepository: TransferRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransferCenterUiState())
    val uiState: StateFlow<TransferCenterUiState> = _uiState.asStateFlow()

    init {
        observeTransfers()
    }

    /** Switches the visible tab between active transfers and history. */
    fun selectTab(tab: TransferCenterTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    /** Removes all terminal (completed / failed) transfers from history. */
    fun clearCompleted() {
        viewModelScope.launch {
            transferRepository.clearCompleted()
        }
    }

    // ---- internals -------------------------------------------------------

    private fun observeTransfers() {
        viewModelScope.launch {
            transferRepository.observeTransfers().collect { tasks ->
                _uiState.value = _uiState.value.copy(
                    transfers = tasks,
                    isLoading = false,
                )
            }
        }
    }
}
