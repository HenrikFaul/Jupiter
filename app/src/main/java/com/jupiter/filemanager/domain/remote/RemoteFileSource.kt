package com.jupiter.filemanager.domain.remote

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteEntry
import java.io.File

/**
 * A protocol-specific remote file source. Implementations are stateless: each
 * call opens, uses and closes its own connection so there is no shared session
 * lifecycle to manage. All implementations must run their blocking IO on an IO
 * dispatcher.
 */
interface RemoteFileSource {
    val type: ConnectionType
    suspend fun testConnection(credentials: RemoteCredentials): AppResult<Unit>
    suspend fun list(credentials: RemoteCredentials, path: String): AppResult<List<RemoteEntry>>
    suspend fun download(credentials: RemoteCredentials, remotePath: String, destination: File): AppResult<Unit>
}
