package com.jupiter.filemanager.data.remote

import android.content.Context
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteEntry
import com.jupiter.filemanager.domain.remote.RemoteCredentials
import com.jupiter.filemanager.domain.remote.RemoteFileSource
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
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
        // Trust-on-first-use host-key verification. We persist accepted keys to a
        // known_hosts file in the app's private filesDir. With StrictHostKeyChecking
        // = "ask" JSch REJECTS (throws) when a previously-recorded key changes
        // (potential MITM) and otherwise consults our UserInfo for unknown hosts,
        // where we accept-and-record the key exactly once.
        jsch.setKnownHosts(knownHostsFile().absolutePath)

        val port = if (credentials.port > 0) credentials.port else 22
        val session = jsch.getSession(credentials.username, credentials.host, port)
        session.setPassword(credentials.password)
        // "ask" + a yes-answering UserInfo == TOFU: accept new hosts, reject changed keys.
        session.setConfig("StrictHostKeyChecking", "ask")
        session.userInfo = TofuUserInfo
        session.connect(15000)
        return session
    }

    /**
     * The TOFU known_hosts store. Created lazily; failure to create the parent
     * directory is non-fatal (JSch falls back to an in-memory repository), so a
     * single session still verifies the key for its own lifetime.
     */
    private fun knownHostsFile(): File {
        val dir = File(context.filesDir, "ssh")
        runCatching { dir.mkdirs() }
        return File(dir, "known_hosts")
    }

    /**
     * Answers JSch's host-key prompts for the TOFU policy. With
     * StrictHostKeyChecking="ask", JSch calls [promptYesNo] for BOTH an UNKNOWN host
     * (first connect — "The authenticity of host ... can't be established") AND a
     * CHANGED key ("WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!"). We must answer
     * yes ONLY to the unknown-host prompt — recording/pinning the key on first connect —
     * and NO to the changed-key warning, so JSch throws and the connection is rejected
     * (defeating a MITM that swaps the host key on a later connect).
     */
    private object TofuUserInfo : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassword(message: String?): Boolean = false
        override fun promptPassphrase(message: String?): Boolean = false
        override fun promptYesNo(message: String?): Boolean =
            message?.contains("REMOTE HOST IDENTIFICATION HAS CHANGED") != true
        override fun showMessage(message: String?) { /* headless: nothing to show */ }
    }

    private fun openChannel(session: Session): ChannelSftp {
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect()
        return channel
    }
}
