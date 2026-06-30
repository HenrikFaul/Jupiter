package com.jupiter.filemanager.feature.settings

import com.jupiter.filemanager.data.preferences.SettingsDataStore
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
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showHidden: Boolean = false,
    val dualPaneEnabled: Boolean = false,
    val aiEnabled: Boolean = false,
    val aiApiKey: String = "",
    // Personalization (additive). Defaults preserve the current look.
    val accentColorArgb: Long = 0L,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    // Privacy (additive). Analytics is opt-in and off by default.
    val analyticsOptIn: Boolean = false,
)
