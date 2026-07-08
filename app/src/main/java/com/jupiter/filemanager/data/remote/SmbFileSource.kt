package com.jupiter.filemanager.data.remote

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteEntry
import com.jupiter.filemanager.domain.remote.RemoteCredentials
import com.jupiter.filemanager.domain.remote.RemoteFileSource
import java.io.File
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * SMB/CIFS file source backed by SMBJ. Also serves NAS connections.
 *
 * The share name is the first path segment of [RemoteCredentials.shareOrBasePath];
 * everything after it is the in-share path. The share root is represented by the
 * empty string and path separators within a share are backslashes.
 *
 * Each call opens and closes its own client/connection/session/share so there is
 * no shared state. All blocking IO runs on the injected IO dispatcher and every
 * failure is mapped to [AppResult.Failure] so a network error never crashes the app.
 */
@Singleton
class SmbFileSource @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : RemoteFileSource {

    override val type: ConnectionType = ConnectionType.SMB

    override suspend fun testConnection(credentials: RemoteCredentials): AppResult<Unit> =
        withContext(io) {
            runCatching {
                withShare(credentials) { _, _ -> }
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = { e -> AppResult.Failure(AppError.Io(smbMessage(e), e)) },
            )
        }

    override suspend fun list(
        credentials: RemoteCredentials,
        path: String,
    ): AppResult<List<RemoteEntry>> = withContext(io) {
        runCatching {
            withShare(credentials) { share, _ ->
                val relPath = normalizeInSharePath(path)
                share.list(relPath)
                    .asSequence()
                    .filter { it.fileName != "." && it.fileName != ".." }
                    .map { info -> toRemoteEntry(info, relPath) }
                    .toList()
            }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { e -> AppResult.Failure(AppError.Io(smbMessage(e), e)) },
        )
    }

    override suspend fun download(
        credentials: RemoteCredentials,
        remotePath: String,
        destination: File,
    ): AppResult<Unit> = withContext(io) {
        runCatching {
            withShare(credentials) { share, _ ->
                val relPath = normalizeInSharePath(remotePath)
                destination.parentFile?.mkdirs()
                share.openFile(
                    relPath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                ).use { file ->
                    file.inputStream.use { input ->
                        destination.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { e -> AppResult.Failure(AppError.Io(smbMessage(e), e)) },
        )
    }

    /**
     * Opens a client/connection/session/share for [credentials] and invokes [block]
     * with the connected [DiskShare] and the resolved in-share root path. All
     * resources are closed when [block] returns (or throws).
     */
    private inline fun <R> withShare(
        credentials: RemoteCredentials,
        block: (share: DiskShare, sharePath: String) -> R,
    ): R {
        val shareName = shareNameOf(credentials.shareOrBasePath)
        val auth = AuthenticationContext(
            credentials.username ?: "",
            (credentials.password ?: "").toCharArray(),
            credentials.domain,
        )
        SMBClient().use { client ->
            client.connect(credentials.host).use { connection ->
                connection.authenticate(auth).use { session ->
                    val share = session.connectShare(shareName) as DiskShare
                    share.use { disk ->
                        return block(disk, basePathOf(credentials.shareOrBasePath))
                    }
                }
            }
        }
    }

    private fun toRemoteEntry(
        info: FileIdBothDirectoryInformation,
        parentRelPath: String,
    ): RemoteEntry {
        val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
        val childPath = if (parentRelPath.isEmpty()) {
            info.fileName
        } else {
            parentRelPath.trim('\\') + "\\" + info.fileName
        }
        return RemoteEntry(
            name = info.fileName,
            path = childPath,
            isDirectory = isDir,
            sizeBytes = info.endOfFile,
            lastModified = info.lastWriteTime.toEpochMillis(),
        )
    }

    /** Returns the share name (first path segment) from a share-or-base path. */
    private fun shareNameOf(shareOrBasePath: String?): String {
        val cleaned = (shareOrBasePath ?: "")
            .replace('\\', '/')
            .trim('/')
        if (cleaned.isEmpty()) return ""
        return cleaned.substringBefore('/')
    }

    /** Returns the in-share base path (everything after the share name). */
    private fun basePathOf(shareOrBasePath: String?): String {
        val cleaned = (shareOrBasePath ?: "")
            .replace('\\', '/')
            .trim('/')
        if (cleaned.isEmpty() || !cleaned.contains('/')) return ""
        return cleaned.substringAfter('/').replace('/', '\\').trim('\\')
    }

    /** Normalizes a caller-supplied path to SMBJ form (backslashes, no leading/trailing slash). */
    private fun normalizeInSharePath(path: String): String =
        path.replace('/', '\\').trim('\\')

    private fun smbMessage(e: Throwable): String =
        e.message ?: ("SMB error: " + e.javaClass.simpleName)
}
