package com.jupiter.filemanager.feature.analytics

import com.jupiter.filemanager.domain.model.StorageOverview

/**
 * UI state for the Storage Analytics screen.
 *
 * The screen presents a real, category-by-category breakdown of how the primary
 * storage volume is used, derived from [StorageAnalyticsRepository.storageOverview].
 *
 * @param isLoading true while the overview is being computed off the main thread.
 * @param overview the analyzed storage overview, or null when not yet loaded or on error.
 * @param error a human-readable error message when analysis failed, otherwise null.
 */
data class StorageAnalyticsUiState(
    val isLoading: Boolean = true,
    val overview: StorageOverview? = null,
    val error: String? = null,
)
