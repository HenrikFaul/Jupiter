package com.jupiter.filemanager.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Vault screen.
 *
 * @property isInitialized whether the encrypted vault storage has been set up and is ready.
 * @property isUnlocked whether the user has unlocked the vault for this session.
 * @property items the files currently stored inside the vault.
 * @property isLoading whether a vault operation (unlock, refresh, import/export/delete) is in progress.
 * @property error a human-readable error message, or null when there is none.
 */
data class VaultUiState(
    val isInitialized: Boolean = false,
    val isUnlocked: Boolean = false,
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Drives the Vault screen.
 *
 * On creation the ViewModel queries [VaultRepository.isVaultInitialized] to determine
 * whether the encrypted vault already exists. The vault stays locked until [unlock] is
 * called, at which point its contents are loaded via [refresh]. Files can be brought in
 * from the regular file system with [importFile], pulled back out with [exportItem], and
 * permanently removed with [deleteItem].
 *
 * All encryption and IO is performed inside the repository's suspend functions on a
 * background dispatcher; this ViewModel only orchestrates collection on `viewModelScope`
 * and never touches the file system directly.
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        loadInitializationState()
    }

    /**
     * Unlocks the vault and loads its contents.
     *
     * Marks the vault as unlocked and, if it has been initialized, refreshes the file
     * list. If the vault is not yet initialized it is still treated as unlocked so the
     * UI can offer to import the first file (which initializes the vault on demand).
     */
    fun unlock() {
        _uiState.update { it.copy(isUnlocked = true, error = null) }
        refresh()
    }

    /**
     * Reloads the list of files stored in the vault.
     *
     * No-ops while the vault is locked. Refreshes [VaultUiState.isInitialized] and the
     * file list together so the UI reflects on-demand initialization performed by
     * earlier imports.
     */
    fun refresh() {
        if (!_uiState.value.isUnlocked) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val initialized = vaultRepository.isVaultInitialized()
            when (val result = vaultRepository.listVaultFiles()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isInitialized = initialized,
                        items = result.data,
                        isLoading = false,
                        error = null,
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        isInitialized = initialized,
                        isLoading = false,
                        error = result.error.displayMessage,
                    )
                }
            }
        }
    }

    /**
     * Encrypts and imports the file at [sourcePath] into the vault, refreshing the
     * file list on success. No-ops while the vault is locked.
     */
    fun importFile(sourcePath: String) {
        if (!_uiState.value.isUnlocked) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = vaultRepository.importToVault(sourcePath)) {
                is AppResult.Success -> reloadAfterMutation()
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error.displayMessage)
                }
            }
        }
    }

    /**
     * Decrypts [item] and writes it to [destinationDir]. The vault contents are left
     * unchanged, so the file list is not reloaded; only the loading and error flags
     * are updated. No-ops while the vault is locked.
     */
    fun exportItem(item: FileItem, destinationDir: String) {
        if (!_uiState.value.isUnlocked) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = vaultRepository.exportFromVault(item, destinationDir)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isLoading = false, error = null)
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error.displayMessage)
                }
            }
        }
    }

    /**
     * Permanently removes [item] from the vault, refreshing the file list on success.
     * No-ops while the vault is locked.
     */
    fun deleteItem(item: FileItem) {
        if (!_uiState.value.isUnlocked) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = vaultRepository.deleteFromVault(item)) {
                is AppResult.Success -> reloadAfterMutation()
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error.displayMessage)
                }
            }
        }
    }

    /** Loads the initial vault initialization state without unlocking it. */
    private fun loadInitializationState() {
        viewModelScope.launch {
            val initialized = vaultRepository.isVaultInitialized()
            _uiState.update { it.copy(isInitialized = initialized) }
        }
    }

    /**
     * Reloads vault contents after a mutation (import/delete). Refreshes
     * initialization state and the file list, surfacing any listing error while
     * keeping the prior items in place on failure.
     */
    private suspend fun reloadAfterMutation() {
        val initialized = vaultRepository.isVaultInitialized()
        when (val result = vaultRepository.listVaultFiles()) {
            is AppResult.Success -> _uiState.update {
                it.copy(
                    isInitialized = initialized,
                    items = result.data,
                    isLoading = false,
                    error = null,
                )
            }

            is AppResult.Failure -> _uiState.update {
                it.copy(
                    isInitialized = initialized,
                    isLoading = false,
                    error = result.error.displayMessage,
                )
            }
        }
    }
}
