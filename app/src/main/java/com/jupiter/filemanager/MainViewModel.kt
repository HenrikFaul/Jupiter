package com.jupiter.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Process-wide view model owning state that must outlive individual feature
 * screens — currently the resolved [ThemeMode] used to drive the app theme.
 *
 * The theme mode is sourced from [SettingsDataStore] and exposed as a hot
 * [StateFlow] so the root composable can collect it with lifecycle awareness
 * without re-reading DataStore on every recomposition.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsDataStore,
) : ViewModel() {

    /**
     * Current theme preference. Starts from [ThemeMode.SYSTEM] until the first
     * value is read from persistence, and stays active while there are
     * subscribers (plus a short grace period across configuration changes).
     */
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ThemeMode.SYSTEM,
        )
}
