package com.jupiter.filemanager.domain.model

/**
 * Supported remote/network connection protocols.
 */
enum class ConnectionType {
    SMB,
    FTP,
    SFTP,
    FTPS,
    WEBDAV,
    NFS,
    NAS,
}

/**
 * A user-configured connection to a remote host (network share, NAS, FTP server, etc.).
 *
 * @param id stable unique identifier for the connection.
 * @param displayName human-readable name shown to the user.
 * @param type the protocol used to reach the host.
 * @param host the hostname or IP address of the remote server.
 * @param username optional username used for authentication.
 * @param isOnline whether the connection is currently reachable/online.
 * @param port the network port used to reach the host (0 means protocol default).
 * @param basePath optional share name or base path on the remote host.
 */
data class RemoteConnection(
    val id: String,
    val displayName: String,
    val type: ConnectionType,
    val host: String,
    val username: String? = null,
    val isOnline: Boolean = false,
    val port: Int = 0,
    val basePath: String? = null,
)
