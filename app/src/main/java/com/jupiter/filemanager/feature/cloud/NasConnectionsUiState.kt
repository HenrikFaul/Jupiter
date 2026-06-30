package com.jupiter.filemanager.feature.cloud

import com.jupiter.filemanager.domain.model.RemoteConnection

/**
 * Immutable UI state for [NasConnectionsScreen].
 *
 * Remote connections are user-created definitions persisted via the connection
 * repository so they survive process death. Adding a connection now performs a
 * real reachability test against the configured protocol backend (SMB/NAS,
 * SFTP, FTP/FTPS, WebDAV) before the definition is saved; the in-flight test and
 * any failure are surfaced through [isTesting] and [testError].
 *
 * @param isLoading true while the initial connection listing is being collected.
 * @param connections the user's configured remote connections, sorted by name.
 * @param showAddDialog whether the "Add connection" dialog is currently visible.
 * @param isTesting true while a connection test is in flight for the add dialog.
 * @param testError a human-readable error from the most recent failed test, or null.
 */
data class NasConnectionsUiState(
    val isLoading: Boolean = true,
    val connections: List<RemoteConnection> = emptyList(),
    val showAddDialog: Boolean = false,
    val isTesting: Boolean = false,
    val testError: String? = null,
) {
    /** Convenience flag for rendering the empty state. */
    val isEmpty: Boolean get() = !isLoading && connections.isEmpty()
}
