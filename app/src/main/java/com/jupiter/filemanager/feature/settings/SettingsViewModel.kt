package com.jupiter.filemanager.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.jupiter.filemanager.data.index.IndexingScheduler
import com.jupiter.filemanager.data.index.IndexingWorker
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.data.trash.TrashScheduler
import com.jupiter.filemanager.data.vault.VaultPinMutationResult
import com.jupiter.filemanager.data.vault.VaultSecurityStore
import com.jupiter.filemanager.domain.model.IndexStats
import com.jupiter.filemanager.domain.model.SortOption
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
    private val trashScheduler: TrashScheduler,
    private val downloadIndexObserver: com.jupiter.filemanager.data.index.DownloadIndexObserver,
    private val vaultSecurityStore: VaultSecurityStore,
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
        settings.trashAutoDeleteDays,
        settings.sortOption,
        settings.groupFilesByType,
        settings.confirmBeforeTrash,
        settings.vaultBiometricLock,
        settings.vaultAutoLockMinutes,
        settings.appLanguageTag,
        vaultSecurityStore.pinConfigured,
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
            trashAutoDeleteDays = values[12] as Int,
            defaultSortOption = values[13] as SortOption,
            groupFilesByType = values[14] as Boolean,
            confirmBeforeTrash = values[15] as Boolean,
            vaultBiometricLock = values[16] as Boolean,
            vaultAutoLockMinutes = values[17] as Int,
            appLanguageTag = values[18] as String,
            vaultPinConfigured = values[19] as Boolean,
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

    fun setSortOption(option: SortOption) {
        viewModelScope.launch {
            settings.setSortOption(option)
        }
    }

    fun setGroupFilesByType(value: Boolean) {
        viewModelScope.launch {
            settings.setGroupFilesByType(value)
        }
    }

    fun setConfirmBeforeTrash(value: Boolean) {
        viewModelScope.launch {
            settings.setConfirmBeforeTrash(value)
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
                // Re-enable the whole pipeline: live delta observer + rebuild + periodic refresh.
                runCatching { downloadIndexObserver.start() }
                indexingScheduler.rebuildNow()
                indexingScheduler.schedulePeriodicRefresh()
            } else {
                // Disabling means STOP EVERYTHING: tear down the live MediaStore observer (it used
                // to keep firing after disable), cancel any running survey, clear the cached rows,
                // and reset the life-cycle state to EMPTY so the index is not considered complete
                // and startup does not silently re-enable it (JupiterApp also checks enabled).
                runCatching { downloadIndexObserver.stop() }
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

    /**
     * Persists the Recycle-Bin auto-delete retention window (in days; 0 = OFF, never auto-delete).
     * When a positive window is chosen we run a purge PROMPTLY (not just on the daily cadence) so a
     * newly-set/shortened window takes effect right away — items already past it are cleared now.
     */
    fun setTrashAutoDeleteDays(days: Int) {
        viewModelScope.launch {
            settings.setTrashAutoDeleteDays(days)
            if (days > 0) trashScheduler.purgeNow()
        }
    }

    fun setVaultAutoLockMinutes(minutes: Int) {
        viewModelScope.launch {
            settings.setVaultAutoLockMinutes(minutes)
        }
    }

    /**
     * Applies the selected per-app locale through AppCompat and mirrors the applied tag into
     * DataStore. Persistence completes before AppCompat is allowed to recreate the Activity.
     */
    fun setAppLanguageTag(languageTag: String?) {
        val locales = LocaleListCompat.forLanguageTags(languageTag.orEmpty())
        val appliedTag = primaryLanguageTag(locales)
        viewModelScope.launch {
            settings.setAppLanguageTag(appliedTag)
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /** Reconciles DataStore with the locale currently applied by AppCompat/platform settings. */
    fun refreshAppLanguageTag() {
        val appliedTag = primaryLanguageTag(AppCompatDelegate.getApplicationLocales())
        viewModelScope.launch {
            settings.setAppLanguageTag(appliedTag)
        }
    }

    /**
     * Changes biometric protection through [VaultSecurityStore], which rejects disabling it
     * unless a PIN is already configured. [onResult] receives false when policy or IO rejects it.
     */
    fun setVaultBiometricLock(value: Boolean, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(runCatching { vaultSecurityStore.setBiometricLock(value) }.getOrDefault(false))
        }
    }

    /**
     * Configures a Vault PIN. Ownership transfers at method entry: Jupiter synchronously copies
     * the input, immediately zeroes the caller-owned array, and always wipes its private copy.
     * Only a salted PBKDF2 verifier can reach persistent storage.
     */
    fun configureVaultPin(
        pin: CharArray,
        onResult: (VaultPinMutationResult) -> Unit = {},
    ) {
        val transientPin = VaultPinInputOwnership.take(pin)
        val job = viewModelScope.launch {
            val result = try {
                runCatching { vaultSecurityStore.configurePin(transientPin) }
                    .getOrDefault(VaultPinMutationResult.PERSISTENCE_FAILED)
            } finally {
                transientPin.fill('\u0000')
            }
            onResult(result)
        }
        // Also covers cancellation before the coroutine body gets a chance to start.
        job.invokeOnCompletion { transientPin.fill('\u0000') }
    }

    /** Verifies a transferred PIN using the same copy-now/zero-now ownership contract. */
    fun verifyVaultPin(pin: CharArray, onResult: (Boolean) -> Unit) {
        val transientPin = VaultPinInputOwnership.take(pin)
        val job = viewModelScope.launch {
            val verified = try {
                runCatching { vaultSecurityStore.verifyPin(transientPin) }.getOrDefault(false)
            } finally {
                transientPin.fill('\u0000')
            }
            onResult(verified)
        }
        job.invokeOnCompletion { transientPin.fill('\u0000') }
    }

    /** Clearing the PIN restores biometric protection before deleting the verifier. */
    fun clearVaultPin(onResult: (VaultPinMutationResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { vaultSecurityStore.clearPin() }
                .getOrDefault(VaultPinMutationResult.PERSISTENCE_FAILED)
            onResult(result)
        }
    }

    private fun primaryLanguageTag(locales: LocaleListCompat): String =
        locales.toLanguageTags().substringBefore(',').trim()
}

/** Pure, synchronously testable ownership boundary for transient Settings PIN input. */
internal object VaultPinInputOwnership {
    fun take(callerOwnedPin: CharArray): CharArray = callerOwnedPin.copyOf().also {
        callerOwnedPin.fill('\u0000')
    }
}
