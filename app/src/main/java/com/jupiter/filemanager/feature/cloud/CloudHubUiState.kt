package com.jupiter.filemanager.feature.cloud

import com.jupiter.filemanager.domain.model.CloudAccount

/**
 * Immutable UI state for [CloudHubScreen].
 *
 * Cloud accounts are user-created link entries persisted via the connection
 * repository. No live provider authentication or quota backend is wired yet, so
 * every persisted [CloudAccount] is surfaced honestly as "Not connected" with
 * zero usage; the UI presents "Connect" / "Coming soon" affordances rather than
 * fabricating live state.
 *
 * @param isLoading true while the initial account listing is being collected.
 * @param accounts the user's linked cloud accounts, sorted by display name.
 * @param showAddSheet whether the "Add cloud account" bottom sheet is visible.
 */
data class CloudHubUiState(
    val isLoading: Boolean = true,
    val accounts: List<CloudAccount> = emptyList(),
    val showAddSheet: Boolean = false,
) {
    /** Convenience flag for rendering the empty state. */
    val isEmpty: Boolean get() = !isLoading && accounts.isEmpty()
}
