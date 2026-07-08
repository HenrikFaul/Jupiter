package com.jupiter.filemanager.feature.home

import com.jupiter.filemanager.domain.model.Bookmark
import com.jupiter.filemanager.domain.model.CategoryUsage
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.StorageVolumeInfo

/**
 * A single Quick Access shortcut tile shown near the top of the Home dashboard.
 *
 * Each shortcut maps a well-known device folder (Downloads, Documents, Images,
 * etc.) to a concrete filesystem [path] the user can browse, alongside a
 * human-readable [label] and an aggregated [itemCount] / [sizeBytes] derived
 * from the real storage overview where available.
 *
 * @property id stable identity used as a list key.
 * @property label folder name shown on the tile.
 * @property path absolute filesystem path opened when the tile is tapped.
 * @property itemCount number of files counted toward this shortcut, or null when unknown.
 * @property sizeBytes total bytes attributed to this shortcut, or null when unknown.
 */
data class QuickAccessShortcut(
    val id: String,
    val label: String,
    val path: String,
    val itemCount: Int? = null,
    val sizeBytes: Long? = null,
)

/**
 * UI state for the redesigned NEXUS-style Home dashboard.
 *
 * @property volumes the mounted storage volumes (internal storage, SD cards, etc.).
 * @property primaryVolume the primary internal volume, surfaced in the storage overview card.
 * @property categories per-category storage usage from the storage overview.
 * @property quickAccess Quick Access folder shortcuts (Downloads / Documents / Images …).
 * @property recents recently visited locations, resolved to [FileItem]s.
 * @property bookmarks user-saved bookmarks.
 * @property isLoading whether a refresh is currently in progress.
 * @property error a human-readable error message, or null when there is none.
 */
data class HomeUiState(
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val primaryVolume: StorageVolumeInfo? = null,
    val categories: List<CategoryUsage> = emptyList(),
    val quickAccess: List<QuickAccessShortcut> = emptyList(),
    val recents: List<FileItem> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
