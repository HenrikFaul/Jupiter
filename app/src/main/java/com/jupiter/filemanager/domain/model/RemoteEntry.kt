package com.jupiter.filemanager.domain.model

/**
 * A single entry returned by a remote file source (SMB/SFTP/FTP/WebDAV) when
 * listing a directory. [path] is absolute within the connection's share/host.
 */
data class RemoteEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
)
