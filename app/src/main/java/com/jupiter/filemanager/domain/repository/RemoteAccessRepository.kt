package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteEntry

/**
 * Application-facing entry point for interacting with remote file systems
 * (SMB/NAS, SFTP, FTP/FTPS, WebDAV). Resolves stored connections and their
 * credentials, routes to the appropriate protocol-specific [com.jupiter.filemanager.domain.remote.RemoteFileSource],
 * and exposes high-level operations used by the UI layer.
 *
 * Implementations must never throw for network/IO failures: every operation
 * returns an [AppResult] so callers can handle errors explicitly.
 */
interface RemoteAccessRepository {

    /**
     * Verifies that a connection can be established with the supplied parameters
     * without persisting anything. Used by the add-connection flow before saving.
     */
    suspend fun testConnection(
        type: ConnectionType,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        basePath: String?,
    ): AppResult<Unit>

    /**
     * Lists the entries under [path] for the stored connection identified by
     * [connectionId]. An empty [path] (or the protocol's default root) lists the
     * connection's root/share.
     */
    suspend fun list(connectionId: String, path: String): AppResult<List<RemoteEntry>>

    /**
     * Downloads [remotePath] from the stored connection identified by
     * [connectionId] into local storage under [fileName].
     *
     * @return the absolute local path of the downloaded file on success.
     */
    suspend fun download(
        connectionId: String,
        remotePath: String,
        fileName: String,
    ): AppResult<String>

    /**
     * Returns the protocol-appropriate default root path used when first opening
     * a connection: SMB/NAS -> "" (share root); SFTP/FTP/FTPS -> "/"; WEBDAV -> "/".
     */
    fun defaultRootPath(type: ConnectionType): String
}
