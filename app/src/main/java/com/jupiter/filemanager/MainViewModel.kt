package com.jupiter.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.preferences.AppStateDataStore
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Process-wide view model owning state that must outlive individual feature
 * screens — the resolved [ThemeMode] plus the personalization preferences
 * (accent color, AMOLED-black, dynamic color) that together drive the app theme,
 * and the one-shot "What's New" gate.
 *
 * Each preference is sourced from [SettingsDataStore] and exposed as a hot
 * [StateFlow] so the root composable can collect it with lifecycle awareness
 * without re-reading DataStore on every recomposition. Initial values mirror the
 * established defaults so the very first frame matches the current look exactly.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val appState: AppStateDataStore,
) : ViewModel() {

    /**
     * Current theme preference. Starts from branded [ThemeMode.DARK] until the first
     * value is read from persistence, and stays active while there are
     * subscribers (plus a short grace period across configuration changes).
     */
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ThemeMode.DARK,
        )

    /**
     * Optional ARGB accent override. `0L` (default) means "no override" so the
     * dynamic/brand default is used, preserving the current look.
     */
    val accentColorArgb: StateFlow<Long> = settings.accentColorArgb
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = 0L,
        )

    /** Whether AMOLED-black backgrounds are forced in dark mode; defaults to false. */
    val amoledBlack: StateFlow<Boolean> = settings.amoledBlack
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false,
        )

    /** Whether wallpaper-based dynamic color is used on Android 12+; defaults to false. */
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false,
        )

    /**
     * Resolves whether the "What's New" sheet should be shown once for the current
     * build, marking it seen as a side effect so it never reappears for this version.
     *
     * Non-blocking: reads [AppStateDataStore.lastSeenWhatsNewVersion] a single time
     * and, if it is older than [BuildConfig.VERSION_CODE], persists the new version
     * and invokes [onShow]. Callers should only trigger this after the main shell is
     * reached (post permission gate) so it never interrupts the start flow.
     *
     * @param onShow invoked on the calling (main) dispatcher exactly once when the
     *   current build has not yet had its highlights shown.
     */
    fun maybeShowWhatsNew(onShow: () -> Unit) {
        viewModelScope.launch {
            val lastSeen = appState.lastSeenWhatsNewVersion.first()
            val current = BuildConfig.VERSION_CODE
            if (lastSeen < current) {
                appState.setLastSeenWhatsNewVersion(current)
                onShow()
            }
        }
    }
}
