package com.jupiter.filemanager.data.remote

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteEntry
import com.jupiter.filemanager.domain.remote.RemoteCredentials
import com.jupiter.filemanager.domain.remote.RemoteFileSource
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient

/**
 * FTP (and best-effort FTPS) implementation of [RemoteFileSource] backed by
 * Apache commons-net. Each call opens its own connection, performs the work and
 * tears the connection down again so there is no shared session to manage.
 *
 * All blocking IO runs on the injected [io] dispatcher and every failure path is
 * translated into an [AppResult.Failure] so a network error can never crash the
 * app.
 */
@Singleton
class FtpFileSource @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : RemoteFileSource {

    override val type: ConnectionType = ConnectionType.FTP

    override suspend fun testConnection(credentials: RemoteCredentials): AppResult<Unit> =
        withContext(io) {
            runFtp(credentials) {
                // A successful connect + login is sufficient proof of reachability.
            }
        }

    override suspend fun list(
        credentials: RemoteCredentials,
        path: String,
    ): AppResult<List<RemoteEntry>> = withContext(io) {
        runFtp(credentials) { client ->
            val dir = path.ifEmpty { "/" }
            client.listFiles(dir)
                .asList()
                .filterNotNull()
                .filter { it.name != "." && it.name != ".." }
                .map { ftpFile ->
                    RemoteEntry(
                        name = ftpFile.name,
                        path = joinPath(dir, ftpFile.name),
                        isDirectory = ftpFile.isDirectory,
                        sizeBytes = ftpFile.size,
                        lastModified = ftpFile.timestamp?.timeInMillis ?: 0L,
                    )
                }
        }
    }

    override suspend fun download(
        credentials: RemoteCredentials,
        remotePath: String,
        destination: File,
    ): AppResult<Unit> = withContext(io) {
        runFtp(credentials) { client ->
            destination.parentFile?.mkdirs()
            val ok = destination.outputStream().use { out ->
                client.retrieveFile(remotePath, out)
            }
            if (!ok) {
                throw java.io.IOException(
                    "FTP download failed for '$remotePath' (reply: ${client.replyString?.trim()})",
                )
            }
        }
    }

    /**
     * Opens an FTP/FTPS connection, logs in, configures passive/binary mode and
     * runs [block] with the live client, guaranteeing logout + disconnect and
     * mapping any thrown exception to [AppResult.Failure].
     */
    private inline fun <T> runFtp(
        credentials: RemoteCredentials,
        block: (FTPClient) -> T,
    ): AppResult<T> {
        val client: FTPClient = if (credentials.type == ConnectionType.FTPS) {
            FTPSClient()
        } else {
            FTPClient()
        }
        return try {
            val port = if (credentials.port > 0) credentials.port else 21
            client.connect(credentials.host, port)

            val loggedIn = client.login(
                credentials.username ?: "anonymous",
                credentials.password ?: "",
            )
            if (!loggedIn) {
                throw java.io.IOException(
                    "FTP login failed (reply: ${client.replyString?.trim()})",
                )
            }

            // For FTPS the control channel is already TLS after login, but the
            // DATA channel is cleartext unless we negotiate protection. Without
            // PBSZ 0 + PROT P every listing/transfer would travel unencrypted.
            if (client is FTPSClient) {
                client.execPBSZ(0)
                client.execPROT("P")
                if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                    throw java.io.IOException(
                        "FTPS server rejected PROT P; refusing to use a cleartext data channel " +
                            "(reply: ${client.replyString?.trim()})",
                    )
                }
            }

            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val result = block(client)
            AppResult.Success(result)
        } catch (t: Throwable) {
            AppResult.Failure(
                AppError.Io(
                    detail = t.message ?: "FTP operation failed",
                    cause = t,
                ),
            )
        } finally {
            runCatching { if (client.isConnected) client.logout() }
            runCatching { if (client.isConnected) client.disconnect() }
        }
    }

    private fun joinPath(parent: String, name: String): String {
        if (parent.isEmpty() || parent == "/") return "/$name"
        return if (parent.endsWith("/")) "$parent$name" else "$parent/$name"
    }
}
