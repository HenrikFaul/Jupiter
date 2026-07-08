package com.jupiter.filemanager.feature.cloud

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.cloud.AuthorizationOutcome
import com.jupiter.filemanager.data.cloud.DriveAuthManager
import com.jupiter.filemanager.data.cloud.GoogleIdentity
import com.jupiter.filemanager.domain.model.CloudAccount
import com.jupiter.filemanager.domain.model.CloudProvider
import com.jupiter.filemanager.domain.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs [CloudHubScreen]. Streams the user's linked cloud accounts from
 * [ConnectionRepository] and exposes add/remove actions plus add-sheet
 * visibility.
 *
 * Google Drive accounts are authenticated end-to-end through [DriveAuthManager]:
 * the system account chooser yields a [GoogleIdentity], an Identity authorization
 * grants the Drive scope (optionally via a UI-launched consent screen), and the
 * resulting access token is exchanged for the account's real email and storage
 * quota, which are persisted via [ConnectionRepository.updateCloudAccount].
 * Other providers remain link-only ("Coming soon") until their backends land.
 */
@HiltViewModel
class CloudHubViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val driveAuthManager: DriveAuthManager,
) : ViewModel() {

    private val showAddSheet = MutableStateFlow(false)
    private val connectingAccountId = MutableStateFlow<String?>(null)
    private val pendingConsent = MutableStateFlow<IntentSender?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    /**
     * Identities captured during [connect] and replayed in [onConsentResult],
     * keyed by account id, so the consent leg can resume the flow without a
     * second sign-in. Cleared once the flow finishes.
     */
    private val pendingIdentities = ConcurrentHashMap<String, GoogleIdentity>()

    /** Whether the build is configured for live Google Drive sign-in. */
    val isDriveConfigured: Boolean get() = driveAuthManager.isConfigured()

    val uiState: StateFlow<CloudHubUiState> =
        combine(
            connectionRepository.observeCloudAccounts(),
            showAddSheet,
            connectingAccountId,
            pendingConsent,
            errorMessage,
        ) { accounts, showSheet, connectingId, consent, error ->
            CloudHubUiState(
                isLoading = false,
                accounts = accounts,
                showAddSheet = showSheet,
                connectingAccountId = connectingId,
                pendingConsent = consent,
                errorMessage = error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CloudHubUiState(isLoading = true),
        )

    /** Reveals the "Add cloud account" bottom sheet. */
    fun onAddRequested() {
        showAddSheet.value = true
    }

    /** Hides the "Add cloud account" bottom sheet without linking anything. */
    fun onDismissAddSheet() {
        showAddSheet.value = false
    }

    /**
     * Links a new cloud account for the given [provider] under [displayName]
     * (trimmed). Blank names are ignored. Closes the add sheet on submit.
     */
    fun onAddAccount(provider: CloudProvider, displayName: String) {
        val name = displayName.trim()
        showAddSheet.value = false
        if (name.isEmpty()) return
        viewModelScope.launch {
            connectionRepository.addCloudAccount(provider, name)
        }
    }

    /** Removes the linked cloud account identified by [id]. */
    fun onRemoveAccount(id: String) {
        pendingIdentities.remove(id)
        viewModelScope.launch {
            connectionRepository.removeCloudAccount(id)
        }
    }

    /** Clears the transient error message after it has been surfaced. */
    fun onErrorShown() {
        errorMessage.value = null
    }

    /** Acknowledges that the screen has launched the pending consent intent. */
    fun onConsentLaunched() {
        pendingConsent.value = null
    }

    /**
     * Starts the Google Drive connect flow for [account]: signs in via the
     * system account chooser, then requests Drive authorization. If consent is
     * required, the [IntentSender] is surfaced through [CloudHubUiState.pendingConsent]
     * for the screen to launch (resumed via [onConsentResult]); otherwise the
     * access token is exchanged for the account's quota immediately.
     */
    fun connect(activity: Activity, account: CloudAccount) {
        if (connectingAccountId.value != null) return
        connectingAccountId.value = account.id
        viewModelScope.launch {
            when (val signIn = driveAuthManager.signIn(activity)) {
                is AppResult.Failure -> finishWithError(signIn.error.displayMessage)
                is AppResult.Success -> authorizeThenFetch(activity, account, signIn.data)
            }
        }
    }

    private suspend fun authorizeThenFetch(
        activity: Activity,
        account: CloudAccount,
        identity: GoogleIdentity,
    ) {
        when (val outcome = driveAuthManager.authorize(activity)) {
            is AuthorizationOutcome.NeedsConsent -> {
                // Hold the identity so onConsentResult can resume the flow.
                pendingIdentities[account.id] = identity
                pendingConsent.value = outcome.intentSender
            }

            is AuthorizationOutcome.Token ->
                fetchAndPersist(account, identity, outcome.accessToken)

            is AuthorizationOutcome.Error -> finishWithError(outcome.message)
        }
    }

    /**
     * Resumes the connect flow after the screen launched the consent intent and
     * received its [data]. Extracts the granted access token and exchanges it
     * for the account's quota.
     */
    fun onConsentResult(activity: Activity, data: Intent?, account: CloudAccount) {
        val identity = pendingIdentities[account.id]
        if (identity == null) {
            finishWithError("Drive sign-in expired. Please try connecting again.")
            return
        }
        val accessToken = driveAuthManager.accessTokenFromResolution(activity, data)
        if (accessToken.isNullOrBlank()) {
            finishWithError("Drive access was not granted.")
            return
        }
        viewModelScope.launch {
            fetchAndPersist(account, identity, accessToken)
        }
    }

    private suspend fun fetchAndPersist(
        account: CloudAccount,
        identity: GoogleIdentity,
        accessToken: String,
    ) {
        when (val result = driveAuthManager.fetchConnectedAccount(account, identity, accessToken)) {
            is AppResult.Success -> {
                connectionRepository.updateCloudAccount(result.data)
                pendingIdentities.remove(account.id)
                connectingAccountId.value = null
            }

            is AppResult.Failure -> finishWithError(result.error.displayMessage)
        }
    }

    private fun finishWithError(message: String) {
        connectingAccountId.value?.let { pendingIdentities.remove(it) }
        connectingAccountId.value = null
        pendingConsent.value = null
        errorMessage.value = message
    }
}
