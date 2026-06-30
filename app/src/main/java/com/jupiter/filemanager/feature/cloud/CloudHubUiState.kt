package com.jupiter.filemanager.feature.cloud

import android.content.IntentSender
import com.jupiter.filemanager.domain.model.CloudAccount

/**
 * Immutable UI state for [CloudHubScreen].
 *
 * Cloud accounts are user-created link entries persisted via the connection
 * repository. Google Drive accounts can now be authenticated end-to-end (system
 * account chooser → Drive authorization → real email + storage quota); other
 * providers are still surfaced honestly as "Not connected" with a
 * "Coming soon" affordance until their backends land.
 *
 * @param isLoading true while the initial account listing is being collected.
 * @param accounts the user's linked cloud accounts, sorted by display name.
 * @param showAddSheet whether the "Add cloud account" bottom sheet is visible.
 * @param connectingAccountId id of the account whose connect flow is in flight,
 *   or null when no connection is being established. Drives per-card progress.
 * @param pendingConsent a one-shot [IntentSender] the screen must launch to
 *   collect Drive authorization consent, or null. Cleared once consumed.
 * @param errorMessage a transient, user-facing error to surface (e.g. via a
 *   snackbar), or null. Cleared after it is shown.
 */
data class CloudHubUiState(
    val isLoading: Boolean = true,
    val accounts: List<CloudAccount> = emptyList(),
    val showAddSheet: Boolean = false,
    val connectingAccountId: String? = null,
    val pendingConsent: IntentSender? = null,
    val errorMessage: String? = null,
) {
    /** Convenience flag for rendering the empty state. */
    val isEmpty: Boolean get() = !isLoading && accounts.isEmpty()
}
