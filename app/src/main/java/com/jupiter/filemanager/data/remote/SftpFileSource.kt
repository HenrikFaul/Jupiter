package com.jupiter.filemanager.data.remote

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
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

/**
 * SFTP-backed [RemoteFileSource] implemented with JSch. Each call opens its own
 * session + sftp channel and tears them down in a finally block so there is no
 * shared session lifecycle to manage. All blocking IO runs on [io].
 */
@Singleton
class SftpFileSource @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : RemoteFileSource {

    override val type: ConnectionType = ConnectionType.SFTP

    override suspend fun testConnection(credentials: RemoteCredentials): AppResult<Unit> =
        withContext(io) {
            var session: Session? = null
            var channel: ChannelSftp? = null
            try {
                session = openSession(credentials)
                channel = openChannel(session)
                AppResult.Success(Unit)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Io(t.message ?: "SFTP connection failed", t))
            } finally {
                channel?.runCatching { disconnect() }
                session?.runCatching { disconnect() }
            }
        }

    override suspend fun list(
        credentials: RemoteCredentials,
        path: String,
    ): AppResult<List<RemoteEntry>> = withContext(io) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = openSession(credentials)
            channel = openChannel(session)
            val target = if (path.isBlank()) "/" else path
            @Suppress("UNCHECKED_CAST")
            val raw = channel.ls(target) as java.util.Vector<*>
            val entries = raw
                .map { it as ChannelSftp.LsEntry }
                .filter { it.filename != "." && it.filename != ".." }
                .map { entry ->
                    val attrs = entry.attrs
                    val base = target.trimEnd('/')
                    val childPath = if (base.isEmpty()) "/${entry.filename}" else "$base/${entry.filename}"
                    RemoteEntry(
                        name = entry.filename,
                        path = childPath,
                        isDirectory = attrs.isDir,
                        sizeBytes = attrs.size,
                        lastModified = attrs.mTime.toLong() * 1000L,
                    )
                }
            AppResult.Success(entries)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Io(t.message ?: "SFTP list failed", t))
        } finally {
            channel?.runCatching { disconnect() }
            session?.runCatching { disconnect() }
        }
    }

    override suspend fun download(
        credentials: RemoteCredentials,
        remotePath: String,
        destination: File,
    ): AppResult<Unit> = withContext(io) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            destination.parentFile?.mkdirs()
            session = openSession(credentials)
            channel = openChannel(session)
            channel.get(remotePath, destination.absolutePath)
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Io(t.message ?: "SFTP download failed", t))
        } finally {
            channel?.runCatching { disconnect() }
            session?.runCatching { disconnect() }
        }
    }

    private fun openSession(credentials: RemoteCredentials): Session {
        val jsch = JSch()
        val port = if (credentials.port > 0) credentials.port else 22
        val session = jsch.getSession(credentials.username, credentials.host, port)
        session.setPassword(credentials.password)
        session.setConfig("StrictHostKeyChecking", "no")
        session.connect(15000)
        return session
    }

    private fun openChannel(session: Session): ChannelSftp {
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect()
        return channel
    }
}
