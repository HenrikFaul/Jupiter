package com.jupiter.filemanager.feature.vault

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the encrypted Vault. */
data class VaultUiState(
    val isInitialized: Boolean = false,
    val isUnlocked: Boolean = false,
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
    val securityPolicyLoaded: Boolean = false,
    val pinConfigured: Boolean = false,
    val deviceCredentialUnlockEnabled: Boolean = true,
    val autoLockMinutes: Int = DEFAULT_VAULT_AUTO_LOCK_MINUTES,
    val isAuthenticating: Boolean = false,
    val authenticationError: String? = null,
    val pendingImportAwaitingAuthentication: Boolean = false,
)

/**
 * Drives the Vault without ever exposing an unauthenticated `unlock()` operation.
 *
 * A session can start only after either a successful system biometric/device-credential prompt
 * or a real [VaultRuntimeSecurity.verifyPin] result. Every session has a generation token, so a
 * repository operation that finishes after a background/timeout lock cannot repopulate the UI
 * with sensitive file metadata. The persisted 1/5/15/30-minute setting is enforced as an
 * inactivity timeout and is reset by [recordUserInteraction] and Vault operations.
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val runtimeSecurity: VaultRuntimeSecurity,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private var autoLockJob: Job? = null
    private var sessionGeneration: Long = 0L
    private var authenticationGeneration: Long = 0L
    private var authenticationAttempt: AuthenticationAttempt = AuthenticationAttempt.NONE
    private var hostInForeground: Boolean = true
    private var pendingDeviceAuthenticationSuccess: Boolean = false
    private var pendingImportUri: Uri? = null

    /**
     * Non-sensitive launch provenance for Android's Activity Result redelivery.
     *
     * The system may recreate the Activity (and, after process death, this ViewModel) while the
     * document picker is on top. Persisting only this boolean in [SavedStateHandle] lets the
     * recreated destination recognize the one expected callback without ever persisting its URI.
     */
    private var documentPickerAwaitingResult: Boolean
        get() = savedStateHandle[DOCUMENT_PICKER_AWAITING_RESULT_KEY] ?: false
        set(value) {
            if (value) {
                savedStateHandle[DOCUMENT_PICKER_AWAITING_RESULT_KEY] = true
            } else {
                savedStateHandle.remove<Boolean>(DOCUMENT_PICKER_AWAITING_RESULT_KEY)
            }
        }

    init {
        observeSecurityPolicy()
        loadInitializationState()
    }

    /**
     * Starts a system-authentication attempt. The caller must show [androidx.biometric.BiometricPrompt]
     * and call [onDeviceAuthenticationSucceeded] only from its success callback.
     */
    fun beginDeviceAuthentication(): Boolean {
        val state = _uiState.value
        if (
            !state.securityPolicyLoaded ||
            state.isUnlocked ||
            state.isLoading ||
            state.isAuthenticating ||
            !state.deviceCredentialUnlockEnabled
        ) {
            return false
        }

        authenticationGeneration += 1L
        authenticationAttempt = AuthenticationAttempt.DEVICE_CREDENTIAL
        _uiState.update {
            it.copy(isAuthenticating = true, authenticationError = null, error = null)
        }
        return true
    }

    /** Completes only an outstanding system-authentication attempt. */
    fun onDeviceAuthenticationSucceeded() {
        if (
            authenticationAttempt != AuthenticationAttempt.DEVICE_CREDENTIAL ||
            !_uiState.value.isAuthenticating ||
            !_uiState.value.deviceCredentialUnlockEnabled
        ) {
            return
        }
        if (!hostInForeground) {
            // Device credential UI can temporarily stop the host Activity. Keep the successful
            // result pending, but never expose Vault data until the Activity is foreground again.
            pendingDeviceAuthenticationSuccess = true
            return
        }
        completeAuthenticatedUnlock()
    }

    /** Cancels the outstanding system prompt without weakening the locked state. */
    fun onDeviceAuthenticationFailed(message: String? = null) {
        if (authenticationAttempt != AuthenticationAttempt.DEVICE_CREDENTIAL) return
        authenticationGeneration += 1L
        authenticationAttempt = AuthenticationAttempt.NONE
        pendingDeviceAuthenticationSuccess = false
        _uiState.update {
            it.copy(
                isAuthenticating = false,
                authenticationError = message?.takeIf(String::isNotBlank),
            )
        }
    }

    /** Clears a rendered authentication error without affecting the locked/unlocked state. */
    fun dismissAuthenticationError() {
        _uiState.update { it.copy(authenticationError = null) }
    }

    /**
     * Verifies a configured PIN using the encrypted, salted verifier and unlocks only on success.
     * The caller-owned array is copied synchronously; Jupiter wipes its private copy in `finally`.
     */
    fun verifyVaultPin(pin: CharArray) {
        val state = _uiState.value
        if (
            !state.securityPolicyLoaded ||
            !state.pinConfigured ||
            state.isUnlocked ||
            state.isLoading ||
            state.isAuthenticating
        ) {
            return
        }

        val transientPin = pin.copyOf()
        authenticationGeneration += 1L
        val attemptGeneration = authenticationGeneration
        authenticationAttempt = AuthenticationAttempt.PIN
        _uiState.update {
            it.copy(isAuthenticating = true, authenticationError = null, error = null)
        }

        viewModelScope.launch {
            val verified = try {
                runCatching { runtimeSecurity.verifyPin(transientPin) }.getOrDefault(false)
            } finally {
                transientPin.fill('\u0000')
            }

            if (
                attemptGeneration != authenticationGeneration ||
                authenticationAttempt != AuthenticationAttempt.PIN ||
                !_uiState.value.isAuthenticating
            ) {
                return@launch
            }

            if (verified) {
                completeAuthenticatedUnlock()
            } else {
                authenticationAttempt = AuthenticationAttempt.NONE
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        authenticationError = "Incorrect PIN. Vault stays locked.",
                    )
                }
            }
        }
    }

    /** Explicitly ends the current session and removes sensitive item metadata from memory. */
    fun lock() {
        lockInternal(
            preserveTrustedExternalFlow = false,
            preservePendingImport = false,
        )
    }

    private fun lockInternal(
        preserveTrustedExternalFlow: Boolean,
        preservePendingImport: Boolean,
    ) {
        sessionGeneration += 1L
        authenticationGeneration += 1L
        authenticationAttempt = AuthenticationAttempt.NONE
        pendingDeviceAuthenticationSuccess = false
        if (!preserveTrustedExternalFlow) documentPickerAwaitingResult = false
        if (!preservePendingImport) pendingImportUri = null
        autoLockJob?.cancel()
        autoLockJob = null
        _uiState.update {
            it.copy(
                isUnlocked = false,
                items = emptyList(),
                isLoading = false,
                error = null,
                infoMessage = null,
                isAuthenticating = false,
                authenticationError = null,
                pendingImportAwaitingAuthentication =
                    preservePendingImport && pendingImportUri != null,
            )
        }
    }

    /**
     * Locks an authenticated session whenever the host leaves the foreground. A device-credential
     * prompt may itself stop the host Activity, so its still-locked challenge is retained until
     * BiometricPrompt delivers a terminal callback; no Vault data is exposed during that window.
     */
    fun onHostStopped() {
        hostInForeground = false
        if (documentPickerAwaitingResult) {
            // The external picker is trusted only to return a URI. The authenticated session and
            // all visible metadata are still destroyed while it is on top.
            lockInternal(
                preserveTrustedExternalFlow = true,
                preservePendingImport = false,
            )
            return
        }
        if (
            !_uiState.value.isUnlocked &&
            authenticationAttempt == AuthenticationAttempt.DEVICE_CREDENTIAL &&
            _uiState.value.isAuthenticating
        ) {
            return
        }
        lock()
    }

    /** Makes a system-prompt success usable only after the host is foreground again. */
    fun onHostStarted() {
        hostInForeground = true
        if (
            pendingDeviceAuthenticationSuccess &&
            authenticationAttempt == AuthenticationAttempt.DEVICE_CREDENTIAL &&
            _uiState.value.isAuthenticating
        ) {
            pendingDeviceAuthenticationSuccess = false
            completeAuthenticatedUnlock()
        }
    }

    /**
     * Marks the next OpenDocument callback as trusted before launching external UI. The host will
     * still lock on STOP; this marker grants no data access and carries no file metadata.
     */
    fun beginDocumentPicker(): Boolean {
        val state = _uiState.value
        if (!state.isUnlocked || state.isLoading || documentPickerAwaitingResult) {
            return false
        }
        recordUserInteraction()
        documentPickerAwaitingResult = true
        return true
    }

    /**
     * Accepts a URI only from the explicitly-started picker and requires fresh authentication
     * before importing it. A cancellation or stale/untrusted callback is discarded.
     */
    fun onDocumentPickerResult(sourceUri: Uri?) {
        if (!documentPickerAwaitingResult) return
        documentPickerAwaitingResult = false

        if (sourceUri == null) {
            pendingImportUri = null
            _uiState.update { it.copy(pendingImportAwaitingAuthentication = false) }
            return
        }

        // Even providers that do not stop the host must cross a fresh authentication boundary.
        lockInternal(
            preserveTrustedExternalFlow = false,
            preservePendingImport = false,
        )
        pendingImportUri = sourceUri
        _uiState.update { it.copy(pendingImportAwaitingAuthentication = true) }
    }

    /** Cancels the memory-only pending URI without importing or persisting it. */
    fun cancelPendingImport() {
        pendingImportUri = null
        _uiState.update { it.copy(pendingImportAwaitingAuthentication = false) }
    }

    /** Resets the persisted inactivity window while an authenticated session is active. */
    fun recordUserInteraction() {
        if (_uiState.value.isUnlocked) scheduleAutoLock()
    }

    /** Reloads the list of encrypted entries for the current authenticated session. */
    fun refresh() {
        val session = beginOperation() ?: return
        viewModelScope.launch {
            val initialized = vaultRepository.isVaultInitialized()
            when (val result = vaultRepository.listVaultFiles()) {
                is AppResult.Success -> updateCurrentSession(session) {
                    it.copy(
                        isInitialized = initialized,
                        items = result.data,
                        isLoading = false,
                        error = null,
                    )
                }

                is AppResult.Failure -> updateCurrentSession(session) {
                    it.copy(
                        isInitialized = initialized,
                        isLoading = false,
                        error = result.error.displayMessage,
                    )
                }
            }
        }
    }

    /** Encrypts and imports a filesystem path into the current authenticated Vault session. */
    fun importFile(sourcePath: String) {
        val session = beginOperation() ?: return
        viewModelScope.launch {
            when (val result = vaultRepository.importToVault(sourcePath)) {
                is AppResult.Success -> reloadAfterMutation(session)
                is AppResult.Failure -> updateCurrentSession(session) {
                    it.copy(isLoading = false, error = result.error.displayMessage)
                }
            }
        }
    }

    /**
     * Encrypts a Storage Access Framework document. The content URI remains a URI and is never
     * downgraded to a filesystem path, preserving support for virtual/cloud providers.
     */
    fun importDocument(sourceUri: Uri) {
        val session = beginOperation() ?: return
        viewModelScope.launch {
            when (val result = vaultRepository.importToVault(sourceUri)) {
                is AppResult.Success -> reloadAfterMutation(session)
                is AppResult.Failure -> updateCurrentSession(session) {
                    it.copy(isLoading = false, error = result.error.displayMessage)
                }
            }
        }
    }

    /** Decrypts an entry to the chosen existing destination without removing the Vault copy. */
    fun exportItem(item: FileItem, destinationDir: String) {
        val session = beginOperation() ?: return
        viewModelScope.launch {
            when (val result = vaultRepository.exportFromVault(item, destinationDir)) {
                is AppResult.Success -> updateCurrentSession(session) {
                    it.copy(
                        isLoading = false,
                        error = null,
                        infoMessage = "Exported ${result.data.name} to Downloads",
                    )
                }

                is AppResult.Failure -> updateCurrentSession(session) {
                    it.copy(isLoading = false, error = result.error.displayMessage)
                }
            }
        }
    }

    /** Permanently removes an entry after the screen's named confirmation. */
    fun deleteItem(item: FileItem) {
        val session = beginOperation() ?: return
        viewModelScope.launch {
            when (val result = vaultRepository.deleteFromVault(item)) {
                is AppResult.Success -> reloadAfterMutation(session)
                is AppResult.Failure -> updateCurrentSession(session) {
                    it.copy(isLoading = false, error = result.error.displayMessage)
                }
            }
        }
    }

    /** Surfaces a UI-side preflight failure such as an unavailable export directory. */
    fun reportError(message: String) {
        if (!_uiState.value.isUnlocked || _uiState.value.isLoading) return
        recordUserInteraction()
        _uiState.update { it.copy(error = message, infoMessage = null) }
    }

    /** Consumes the transient export-success message after Snackbar delivery. */
    fun dismissInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    private fun observeSecurityPolicy() {
        viewModelScope.launch {
            combine(
                runtimeSecurity.pinConfigured,
                runtimeSecurity.biometricUnlockEnabled,
                runtimeSecurity.autoLockMinutes,
            ) { pinConfigured, biometricEnabled, autoLockMinutes ->
                RuntimePolicy(
                    pinConfigured = pinConfigured,
                    // A corrupt/legacy "both off" state must fail safe to device authentication.
                    deviceCredentialUnlockEnabled = biometricEnabled || !pinConfigured,
                    autoLockMinutes = normalizeAutoLockMinutes(autoLockMinutes),
                )
            }.collect { policy ->
                val timeoutChanged = _uiState.value.autoLockMinutes != policy.autoLockMinutes
                _uiState.update {
                    it.copy(
                        securityPolicyLoaded = true,
                        pinConfigured = policy.pinConfigured,
                        deviceCredentialUnlockEnabled = policy.deviceCredentialUnlockEnabled,
                        autoLockMinutes = policy.autoLockMinutes,
                    )
                }
                if (timeoutChanged && _uiState.value.isUnlocked) scheduleAutoLock()
            }
        }
    }

    private fun completeAuthenticatedUnlock() {
        authenticationGeneration += 1L
        authenticationAttempt = AuthenticationAttempt.NONE
        pendingDeviceAuthenticationSuccess = false
        sessionGeneration += 1L
        val importAfterAuthentication = pendingImportUri
        pendingImportUri = null
        _uiState.update {
            it.copy(
                isUnlocked = true,
                items = emptyList(),
                isLoading = false,
                error = null,
                infoMessage = null,
                isAuthenticating = false,
                authenticationError = null,
                pendingImportAwaitingAuthentication = false,
            )
        }
        scheduleAutoLock()
        if (importAfterAuthentication == null) {
            refresh()
        } else {
            importDocument(importAfterAuthentication)
        }
    }

    /** Atomically reserves the single-operation busy guard and returns this session's token. */
    private fun beginOperation(): Long? {
        val state = _uiState.value
        if (!state.isUnlocked || state.isLoading) return null
        recordUserInteraction()
        _uiState.update { it.copy(isLoading = true, error = null, infoMessage = null) }
        return sessionGeneration
    }

    private inline fun updateCurrentSession(
        expectedGeneration: Long,
        transform: (VaultUiState) -> VaultUiState,
    ) {
        if (expectedGeneration != sessionGeneration || !_uiState.value.isUnlocked) return
        _uiState.update(transform)
    }

    private fun scheduleAutoLock() {
        autoLockJob?.cancel()
        if (!_uiState.value.isUnlocked) {
            autoLockJob = null
            return
        }

        val expectedGeneration = sessionGeneration
        val timeoutMillis = _uiState.value.autoLockMinutes * MILLIS_PER_MINUTE
        autoLockJob = viewModelScope.launch {
            delay(timeoutMillis)
            if (expectedGeneration == sessionGeneration && _uiState.value.isUnlocked) {
                if (documentPickerAwaitingResult) {
                    lockInternal(
                        preserveTrustedExternalFlow = true,
                        preservePendingImport = false,
                    )
                } else {
                    lock()
                }
            }
        }
    }

    private fun loadInitializationState() {
        viewModelScope.launch {
            val initialized = vaultRepository.isVaultInitialized()
            _uiState.update { it.copy(isInitialized = initialized) }
        }
    }

    private suspend fun reloadAfterMutation(expectedGeneration: Long) {
        val initialized = vaultRepository.isVaultInitialized()
        when (val result = vaultRepository.listVaultFiles()) {
            is AppResult.Success -> updateCurrentSession(expectedGeneration) {
                it.copy(
                    isInitialized = initialized,
                    items = result.data,
                    isLoading = false,
                    error = null,
                )
            }

            is AppResult.Failure -> updateCurrentSession(expectedGeneration) {
                it.copy(
                    isInitialized = initialized,
                    isLoading = false,
                    error = result.error.displayMessage,
                )
            }
        }
    }

    private enum class AuthenticationAttempt {
        NONE,
        DEVICE_CREDENTIAL,
        PIN,
    }

    private data class RuntimePolicy(
        val pinConfigured: Boolean,
        val deviceCredentialUnlockEnabled: Boolean,
        val autoLockMinutes: Int,
    )
}

private const val DEFAULT_VAULT_AUTO_LOCK_MINUTES = 5
private const val MILLIS_PER_MINUTE = 60_000L
private const val DOCUMENT_PICKER_AWAITING_RESULT_KEY = "vault_document_picker_awaiting_result"
private val SUPPORTED_VAULT_AUTO_LOCK_MINUTES = setOf(1, 5, 15, 30)

private fun normalizeAutoLockMinutes(value: Int): Int =
    value.takeIf(SUPPORTED_VAULT_AUTO_LOCK_MINUTES::contains)
        ?: DEFAULT_VAULT_AUTO_LOCK_MINUTES
