package com.jupiter.filemanager.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable UI state for the settings screen.
 *
 * Mirrors the user-facing preferences persisted by [SettingsDataStore].
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showHidden: Boolean = false,
    val dualPaneEnabled: Boolean = false,
    val aiEnabled: Boolean = false,
    val aiApiKey: String = "",
)

/**
 * Exposes persisted user preferences as a single [StateFlow] and forwards
 * mutations to [SettingsDataStore].
 *
 * Reads are composed from the individual preference flows so the UI always
 * reflects the latest persisted values; writes are dispatched on
 * [viewModelScope] and persisted asynchronously by the DataStore.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.themeMode,
        settings.showHidden,
        settings.dualPaneEnabled,
        settings.aiEnabled,
        settings.aiApiKey,
    ) { themeMode, showHidden, dualPaneEnabled, aiEnabled, aiApiKey ->
        SettingsUiState(
            themeMode = themeMode,
            showHidden = showHidden,
            dualPaneEnabled = dualPaneEnabled,
            aiEnabled = aiEnabled,
            aiApiKey = aiApiKey,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settings.setThemeMode(mode)
        }
    }

    fun setShowHidden(value: Boolean) {
        viewModelScope.launch {
            settings.setShowHidden(value)
        }
    }

    fun setDualPane(value: Boolean) {
        viewModelScope.launch {
            settings.setDualPaneEnabled(value)
        }
    }

    fun setAiEnabled(value: Boolean) {
        viewModelScope.launch {
            settings.setAiEnabled(value)
        }
    }

    /**
     * Persists the Claude API key used to unlock AI-assisted features. The key
     * is stored on-device by [SettingsDataStore]; trimming avoids accidental
     * whitespace pasted alongside the token.
     */
    fun setAiApiKey(value: String) {
        viewModelScope.launch {
            settings.setAiApiKey(value.trim())
        }
    }
}
