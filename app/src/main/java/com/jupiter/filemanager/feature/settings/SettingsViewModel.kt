package com.jupiter.filemanager.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.jupiter.filemanager.data.index.IndexingScheduler
import com.jupiter.filemanager.data.index.IndexingWorker
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.IndexStats
import com.jupiter.filemanager.domain.model.ThemeMode
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import com.jupiter.filemanager.domain.repository.IndexStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes persisted user preferences as a single [StateFlow] and forwards
 * mutations to [SettingsDataStore].
 *
 * Reads are composed from the individual preference flows so the UI always
 * reflects the latest persisted values; writes are dispatched on
 * [viewModelScope] and persisted asynchronously by the DataStore.
 *
 * The original appearance/browsing/assistant preferences are preserved; the
 * personalization (accent color, AMOLED black, dynamic color) and privacy
 * (analytics opt-in) preferences are additive and default to values that keep
 * the current behavior unchanged.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val indexRepository: FileIndexRepository,
    private val indexStateRepository: IndexStateRepository,
    private val indexingScheduler: IndexingScheduler,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.themeMode,
        settings.showHidden,
        settings.dualPaneEnabled,
        settings.aiEnabled,
        settings.aiApiKey,
        settings.accentColorArgb,
        settings.amoledBlack,
        settings.dynamicColor,
        settings.analyticsOptIn,
        settings.indexingEnabled,
        indexRepository.stats(),
        indexingScheduler.observeStatus(),
    ) { values ->
        val stats = values[10] as IndexStats
        val workInfo = values[11] as WorkInfo?
        val running = workInfo?.state == WorkInfo.State.RUNNING
        // While running, read the live indexed/total the worker publishes so the UI can
        // show a real percentage instead of an indeterminate spinner.
        val indexedSoFar = workInfo?.progress?.getInt(IndexingWorker.KEY_INDEXED_COUNT, 0) ?: 0
        val indexTotal = workInfo?.progress?.getInt(IndexingWorker.KEY_TOTAL_COUNT, 0) ?: 0
        SettingsUiState(
            themeMode = values[0] as ThemeMode,
            showHidden = values[1] as Boolean,
            dualPaneEnabled = values[2] as Boolean,
            aiEnabled = values[3] as Boolean,
            aiApiKey = values[4] as String,
            accentColorArgb = values[5] as Long,
            amoledBlack = values[6] as Boolean,
            dynamicColor = values[7] as Boolean,
            analyticsOptIn = values[8] as Boolean,
            indexingEnabled = values[9] as Boolean,
            indexedCount = stats.indexedCount,
            indexing = running || workInfo?.state == WorkInfo.State.ENQUEUED,
            indexProgressCurrent = indexedSoFar,
            indexProgressTotal = indexTotal,
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

    /**
     * Persists the selected accent color packed as ARGB. A value of 0L means
     * "use the dynamic/brand default", preserving the current look.
     */
    fun setAccentColorArgb(value: Long) {
        viewModelScope.launch {
            settings.setAccentColorArgb(value)
        }
    }

    /** Persists whether dark theme should use pure-black (AMOLED) surfaces. */
    fun setAmoledBlack(value: Boolean) {
        viewModelScope.launch {
            settings.setAmoledBlack(value)
        }
    }

    /** Persists whether Material You dynamic color is enabled (on S+). */
    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch {
            settings.setDynamicColor(value)
        }
    }

    /**
     * Persists the anonymous-analytics opt-in. Defaults to off; analytics only
     * ever runs after an explicit opt-in here.
     */
    fun setAnalyticsOptIn(value: Boolean) {
        viewModelScope.launch {
            settings.setAnalyticsOptIn(value)
        }
    }

    /**
     * Persists whether the persistent file index is enabled. Enabling it kicks
     * off an immediate rebuild so search benefits from cached metadata right
     * away; disabling it clears the cached entries to free storage. Both the
     * rebuild and the clear are best-effort and never block the UI.
     */
    fun setIndexingEnabled(value: Boolean) {
        viewModelScope.launch {
            settings.setIndexingEnabled(value)
            if (value) {
                indexingScheduler.rebuildNow()
                indexingScheduler.schedulePeriodicRefresh()
            } else {
                // Disabling means STOP: cancel any running survey, clear the cached rows, and
                // reset the life-cycle state to EMPTY so the index is not considered complete
                // and startup does not silently re-enable it (JupiterApp also checks enabled).
                indexingScheduler.cancel()
                runCatching { indexRepository.clear() }
                runCatching { indexStateRepository.reset() }
            }
        }
    }

    /**
     * Enqueues (or restarts) a full index rebuild via the [IndexingScheduler].
     * The scheduler swallows enqueue failures, so this never crashes the screen.
     */
    fun rebuildIndex() {
        indexingScheduler.rebuildNow()
    }
}
