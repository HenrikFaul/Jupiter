package com.jupiter.filemanager.feature.cloud

import com.jupiter.filemanager.domain.model.RemoteConnection

/**
 * Immutable UI state for [NasConnectionsScreen].
 *
 * Remote connections are user-created definitions persisted via the connection
 * repository so they survive process death. No live protocol I/O backend
 * (SMB/SFTP/FTP/WebDAV/NAS) is wired yet, so every persisted [RemoteConnection]
 * is surfaced honestly as "Offline"; the UI presents "Connect" / "Coming soon"
 * affordances rather than fabricating live reachability state.
 *
 * @param isLoading true while the initial connection listing is being collected.
 * @param connections the user's configured remote connections, sorted by name.
 * @param showAddDialog whether the "Add connection" dialog is currently visible.
 */
data class NasConnectionsUiState(
    val isLoading: Boolean = true,
    val connections: List<RemoteConnection> = emptyList(),
    val showAddDialog: Boolean = false,
) {
    /** Convenience flag for rendering the empty state. */
    val isEmpty: Boolean get() = !isLoading && connections.isEmpty()
}
