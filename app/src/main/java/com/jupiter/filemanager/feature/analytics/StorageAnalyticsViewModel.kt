package com.jupiter.filemanager.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.IndexStateRepository
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Produces a real, categorized overview of the primary storage volume for the
 * Storage Analytics screen. All heavy filesystem work happens inside the
 * repository (off the main thread); this ViewModel only orchestrates state.
 *
 * The overview is collected INCREMENTALLY via
 * [StorageAnalyticsRepository.observeStorageOverview] so the screen renders a
 * meaningful breakdown on the first partial emission (clearing the full-screen
 * spinner) and refines it as the walk progresses, rather than blocking behind a
 * full-volume walk that emits nothing for minutes.
 */
@HiltViewModel
class StorageAnalyticsViewModel @Inject constructor(
    private val analyticsRepository: StorageAnalyticsRepository,
    private val storageAccessManager: StorageAccessManager,
    private val indexRepository: FileIndexRepository,
    private val indexStateRepository: IndexStateRepository,
    @Suppress("unused") private val fileRepository: FileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageAnalyticsUiState())
    val uiState: StateFlow<StorageAnalyticsUiState> = _uiState.asStateFlow()

    init {
        analyze()
    }

    /**
     * Recomputes the storage overview by collecting the incremental flow. Safe to
     * call repeatedly (e.g. from a refresh / retry action, or on resume after the
     * user grants permission).
     */
    fun analyze() {
        // Gate the scan on broad storage access: without it the walk silently
        // returns nothing, so surface an actionable CTA instead of spinning.
        if (!storageAccessManager.hasAllFilesAccess()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isScanning = false,
                    permissionRequired = true,
                    error = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                isScanning = true,
                permissionRequired = false,
                error = null,
            )
        }

        viewModelScope.launch {
            // Reflect whether this pass will be served instantly from the index (usable:
            // COMPLETE, or rescan over a prior complete generation) or a live walk, so the
            // screen can show the same "indexed" affordance as Cleanup.
            val usingIndex = runCatching { indexStateRepository.isUsable() }
                .getOrDefault(false)
            val indexedCount = runCatching { indexRepository.stats().first().indexedCount }
                .getOrDefault(0)
            _uiState.update { it.copy(fromIndex = usingIndex, indexedCount = indexedCount) }

            analyticsRepository.observeStorageOverview()
                .onStart {
                    _uiState.update { it.copy(isScanning = true, error = null) }
                }
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isScanning = false,
                            // Keep any partial overview already shown; only surface
                            // the error when there is nothing to display.
                            error = if (it.overview == null) {
                                throwable.message ?: "Unable to analyze storage."
                            } else {
                                it.error
                            },
                        )
                    }
                }
                .onCompletion {
                    _uiState.update { it.copy(isLoading = false, isScanning = false) }
                }
                .collect { overview ->
                    // First (partial) emission clears the full-screen spinner;
                    // every subsequent emission refines the breakdown in place.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            overview = overview,
                            error = null,
                        )
                    }
                }
        }
    }
}
