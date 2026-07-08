package com.jupiter.filemanager.data.remote

import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.remote.RemoteFileSource
import com.jupiter.filemanager.domain.remote.RemoteSourceProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a [ConnectionType] to its protocol-specific [RemoteFileSource].
 *
 * Routing rules:
 *  - SMB / NAS  -> [SmbFileSource]
 *  - SFTP       -> [SftpFileSource]
 *  - FTP / FTPS -> [FtpFileSource]
 *  - WEBDAV     -> [WebDavFileSource]
 *  - anything else (NFS, ...) -> null (unsupported)
 */
@Singleton
class RemoteSourceProviderImpl @Inject constructor(
    private val smb: SmbFileSource,
    private val sftp: SftpFileSource,
    private val ftp: FtpFileSource,
    private val webdav: WebDavFileSource,
) : RemoteSourceProvider {

    override fun sourceFor(type: ConnectionType): RemoteFileSource? = when (type) {
        ConnectionType.SMB, ConnectionType.NAS -> smb
        ConnectionType.SFTP -> sftp
        ConnectionType.FTP, ConnectionType.FTPS -> ftp
        ConnectionType.WEBDAV -> webdav
        ConnectionType.NFS -> null
    }
}
