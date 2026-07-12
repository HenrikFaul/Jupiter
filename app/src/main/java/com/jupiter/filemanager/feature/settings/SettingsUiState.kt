package com.jupiter.filemanager.feature.settings

import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.model.ThemeMode

/**
 * Immutable UI state for the settings screen.
 *
 * Mirrors the user-facing preferences persisted by [SettingsDataStore]. All
 * fields default to the values that preserve the app's current behavior so the
 * initial (pre-load) render matches the persisted defaults.
 *
 * The original fields ([themeMode], [showHidden], [dualPaneEnabled],
 * [aiEnabled], [aiApiKey]) are preserved unchanged; the personalization and
 * privacy fields are additive.
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val showHidden: Boolean = false,
    val dualPaneEnabled: Boolean = false,
    val aiEnabled: Boolean = false,
    val aiApiKey: String = "",
    // Personalization (additive). Defaults preserve the current look.
    val accentColorArgb: Long = 0L,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = false,
    // Privacy (additive). Analytics is opt-in and off by default.
    val analyticsOptIn: Boolean = false,
    // Indexing (additive). The persistent file index speeds up search and is on
    // by default; [indexedCount] and [indexing] reflect the current index state.
    val indexingEnabled: Boolean = true,
    val indexedCount: Int = 0,
    val indexing: Boolean = false,
    // Live progress of an in-flight index build, for a real percentage instead of a
    // spinner. [indexProgressTotal] is 0 until the worker publishes its first estimate.
    val indexProgressCurrent: Int = 0,
    val indexProgressTotal: Int = 0,
    // Recycle Bin: days after which trashed items are auto-deleted (0 = OFF, the default).
    val trashAutoDeleteDays: Int = 0,
    // File-behaviour preferences are additive and deliberately follow every pre-v0.51 field so
    // positional callers compiled against the established SettingsUiState ordering keep working.
    val defaultSortOption: SortOption = SortOption(),
    val groupFilesByType: Boolean = false,
    val confirmBeforeTrash: Boolean = true,
    // Vault security defaults remain fail-closed until explicitly configured by the user.
    val vaultBiometricLock: Boolean = true,
    val vaultAutoLockMinutes: Int = 5,
    val vaultPinConfigured: Boolean = false,
    // Empty means follow the system locale / use Android's per-app language setting.
    val appLanguageTag: String = "",
) {
    /** Percentage [0,100] of the current index build, or null when unknown/not running. */
    val indexProgressPercent: Int?
        get() = if (indexing && indexProgressTotal > 0) {
            ((indexProgressCurrent.toLong() * 100L) / indexProgressTotal).toInt().coerceIn(0, 100)
        } else {
            null
        }
}
