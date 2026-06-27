package com.jupiter.filemanager.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Produces a real, categorized overview of the primary storage volume for the
 * Storage Analytics screen. All heavy filesystem work happens inside the
 * repository (off the main thread); this ViewModel only orchestrates state.
 */
@HiltViewModel
class StorageAnalyticsViewModel @Inject constructor(
    private val analyticsRepository: StorageAnalyticsRepository,
    @Suppress("unused") private val fileRepository: FileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageAnalyticsUiState())
    val uiState: StateFlow<StorageAnalyticsUiState> = _uiState.asStateFlow()

    init {
        analyze()
    }

    /**
     * Recomputes the storage overview. Safe to call repeatedly (e.g. from a
     * pull-to-refresh / retry action); guards against concurrent reloads.
     */
    fun analyze() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = analyticsRepository.storageOverview()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        overview = result.data,
                        error = null,
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.error.displayMessage,
                    )
                }
            }
        }
    }
}
