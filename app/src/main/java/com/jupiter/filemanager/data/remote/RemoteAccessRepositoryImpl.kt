package com.jupiter.filemanager.data.remote

import android.content.Context
import android.os.Environment
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteConnection
import com.jupiter.filemanager.domain.model.RemoteEntry
import com.jupiter.filemanager.domain.remote.RemoteCredentials
import com.jupiter.filemanager.domain.remote.RemoteSourceProvider
import com.jupiter.filemanager.domain.repository.ConnectionRepository
import com.jupiter.filemanager.domain.repository.RemoteAccessRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Default [RemoteAccessRepository] implementation.
 *
 * Resolves a stored [RemoteConnection] by id (via [ConnectionRepository.observeRemotes]),
 * pairs it with its encrypted password from [CredentialStore], routes to the appropriate
 * protocol-specific source via [RemoteSourceProvider], and exposes high-level list/download
 * operations. All blocking IO runs on the injected [io] dispatcher and every failure is
 * surfaced as [AppResult.Failure] — this class never throws for network/IO errors.
 */
@Singleton
class RemoteAccessRepositoryImpl @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val credentialStore: CredentialStore,
    private val sourceProvider: RemoteSourceProvider,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : RemoteAccessRepository {

    override suspend fun testConnection(
        type: ConnectionType,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        basePath: String?,
    ): AppResult<Unit> = withContext(io) {
        val source = sourceProvider.sourceFor(type)
            ?: return@withContext AppResult.Failure(
                AppError.Io("Unsupported connection type: " + type.name),
            )
        val credentials = RemoteCredentials(
            type = type,
            host = host,
            port = port,
            username = username,
            password = password,
            shareOrBasePath = basePath,
        )
        try {
            source.testConnection(credentials)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Io(t.message ?: "Connection test failed", t))
        }
    }

    override suspend fun list(connectionId: String, path: String): AppResult<List<RemoteEntry>> =
        withContext(io) {
            val connection = resolveConnection(connectionId)
                ?: return@withContext AppResult.Failure(AppError.NotFound(connectionId))
            val source = sourceProvider.sourceFor(connection.type)
                ?: return@withContext AppResult.Failure(
                    AppError.Io("Unsupported connection type: " + connection.type.name),
                )
            val credentials = credentialsFor(connection)
            try {
                source.list(credentials, path)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Io(t.message ?: "Failed to list remote directory", t))
            }
        }

    override suspend fun download(
        connectionId: String,
        remotePath: String,
        fileName: String,
    ): AppResult<String> = withContext(io) {
        val connection = resolveConnection(connectionId)
            ?: return@withContext AppResult.Failure(AppError.NotFound(connectionId))
        val source = sourceProvider.sourceFor(connection.type)
            ?: return@withContext AppResult.Failure(
                AppError.Io("Unsupported connection type: " + connection.type.name),
            )
        val credentials = credentialsFor(connection)
        try {
            val destination = resolveDestination(fileName)
            when (val result = source.download(credentials, remotePath, destination)) {
                is AppResult.Success -> AppResult.Success(destination.absolutePath)
                is AppResult.Failure -> result
            }
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Io(t.message ?: "Download failed", t))
        }
    }

    override fun defaultRootPath(type: ConnectionType): String = when (type) {
        ConnectionType.SMB, ConnectionType.NAS, ConnectionType.NFS -> ""
        ConnectionType.SFTP, ConnectionType.FTP, ConnectionType.FTPS, ConnectionType.WEBDAV -> "/"
    }

    /**
     * Resolves the stored connection with the given [connectionId], or `null` if no such
     * connection exists. Reads a single snapshot of the persisted connection list.
     */
    private suspend fun resolveConnection(connectionId: String): RemoteConnection? {
        return try {
            connectionRepository.observeRemotes().first().firstOrNull { it.id == connectionId }
        } catch (t: Throwable) {
            null
        }
    }

    /** Builds [RemoteCredentials] for [connection], pulling its password from the encrypted store. */
    private fun credentialsFor(connection: RemoteConnection): RemoteCredentials = RemoteCredentials(
        type = connection.type,
        host = connection.host,
        port = connection.port,
        username = connection.username,
        password = credentialStore.getPassword(connection.id),
        shareOrBasePath = connection.basePath,
    )

    /**
     * Resolves the local destination file for a download, creating a "JupiterDownloads"
     * subfolder of the public Downloads directory. Falls back to the app-specific external
     * files directory when the public directory is unavailable.
     */
    private fun resolveDestination(fileName: String): File {
        val safeName = sanitizeFileName(fileName)
        val publicDownloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        )
        val baseDir = publicDownloads ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(baseDir, DOWNLOAD_SUBFOLDER)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return File(targetDir, safeName)
    }

    /** Strips any path separators from a remote file name so it cannot escape the target folder. */
    private fun sanitizeFileName(fileName: String): String {
        val trimmed = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
        return trimmed.ifEmpty { "download" }
    }

    private companion object {
        const val DOWNLOAD_SUBFOLDER = "JupiterDownloads"
    }
}
