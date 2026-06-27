package com.jupiter.filemanager.feature.privacy

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.PrivacyLevel
import com.jupiter.filemanager.domain.model.PrivacyReport
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Derives a [PrivacyReport] from real device signals:
 *
 * - **Encrypted files**: the number of items currently stored in the vault.
 * - **Hidden files**: a best-effort count of hidden entries directly under the
 *   primary external storage root (only meaningful when "show hidden" is on in
 *   settings; otherwise reported as 0 to avoid an unreliable scan).
 * - **Shared links / apps with access**: 0, since those signals require a backend
 *   that is not part of this on-device build.
 *
 * The overall [PrivacyLevel] is computed with a simple, honest heuristic from the
 * derived counts.
 */
@HiltViewModel
class PrivacyDashboardViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val settings: SettingsDataStore,
    private val fileRepository: FileRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyDashboardUiState())
    val uiState: StateFlow<PrivacyDashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Recomputes the privacy report from current device state. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val report = runCatching { buildReport() }.getOrElse { throwable ->
                _uiState.value = PrivacyDashboardUiState(
                    isLoading = false,
                    report = null,
                    errorMessage = throwable.message ?: "Unable to compute privacy report.",
                )
                return@launch
            }
            _uiState.value = PrivacyDashboardUiState(
                isLoading = false,
                report = report,
                errorMessage = null,
            )
        }
    }

    private suspend fun buildReport(): PrivacyReport {
        val encryptedFiles = when (val result = vaultRepository.listVaultFiles()) {
            is AppResult.Success -> result.data.size
            is AppResult.Failure -> 0
        }

        val showHidden = settings.showHidden.first()
        val hiddenFiles = if (showHidden) countHiddenFiles() else 0

        val sharedLinks = 0
        val appsWithAccess = 0

        val level = deriveLevel(
            encryptedFiles = encryptedFiles,
            hiddenFiles = hiddenFiles,
        )
        val dataExposure = describeExposure(level, hiddenFiles)

        return PrivacyReport(
            level = level,
            encryptedFiles = encryptedFiles,
            hiddenFiles = hiddenFiles,
            sharedLinks = sharedLinks,
            appsWithAccess = appsWithAccess,
            dataExposure = dataExposure,
        )
    }

    /**
     * Best-effort count of hidden entries directly under the primary external
     * storage root. Runs on the IO dispatcher and tolerates listing failures by
     * returning 0.
     */
    private suspend fun countHiddenFiles(): Int = withContext(ioDispatcher) {
        val root = Environment.getExternalStorageDirectory()?.absolutePath ?: return@withContext 0
        when (val result = fileRepository.listFiles(root, SortOption(), FilterOption())) {
            is AppResult.Success -> result.data.count { it.isHidden }
            is AppResult.Failure -> 0
        }
    }

    private fun deriveLevel(encryptedFiles: Int, hiddenFiles: Int): PrivacyLevel = when {
        encryptedFiles > 0 -> PrivacyLevel.GOOD
        hiddenFiles > 0 -> PrivacyLevel.FAIR
        else -> PrivacyLevel.AT_RISK
    }

    private fun describeExposure(level: PrivacyLevel, hiddenFiles: Int): String = when (level) {
        PrivacyLevel.GOOD ->
            "Sensitive files are protected in your encrypted vault."
        PrivacyLevel.FAIR ->
            if (hiddenFiles > 0) {
                "Some files are hidden but none are encrypted. Move private files to the vault."
            } else {
                "No critical exposure detected, but no files are encrypted yet."
            }
        PrivacyLevel.AT_RISK ->
            "No files are encrypted. Add sensitive files to the vault to protect them."
    }
}
