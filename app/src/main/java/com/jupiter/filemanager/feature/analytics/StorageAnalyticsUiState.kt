package com.jupiter.filemanager.feature.analytics

import com.jupiter.filemanager.domain.model.StorageOverview

/**
 * UI state for the Storage Analytics screen.
 *
 * The screen presents a real, category-by-category breakdown of how the primary
 * storage volume is used, derived from
 * [com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository.observeStorageOverview].
 *
 * @param isLoading true until the FIRST (possibly partial) overview emission arrives;
 *   gates the full-screen loading view only while no content is shown yet.
 * @param isScanning true while the incremental walk is still in progress AFTER the
 *   first emission; drives a lightweight "updating…" chip without blocking content.
 * @param overview the analyzed storage overview, or null when not yet loaded or on error.
 * @param error a human-readable error message when analysis failed, otherwise null.
 * @param permissionRequired true when broad storage access is missing, so the screen
 *   should render an actionable CTA instead of spinning on an empty scan. Defaults to
 *   false to preserve existing behavior.
 */
data class StorageAnalyticsUiState(
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val overview: StorageOverview? = null,
    val error: String? = null,
    val permissionRequired: Boolean = false,
)
