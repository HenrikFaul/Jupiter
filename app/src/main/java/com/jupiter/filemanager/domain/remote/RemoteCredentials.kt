package com.jupiter.filemanager.domain.remote

import com.jupiter.filemanager.domain.model.ConnectionType

/**
 * Connection coordinates + secret needed to talk to a remote host. Passwords are
 * never persisted in plaintext alongside connection metadata; they are supplied
 * from the encrypted credential store at call time.
 *
 * @property shareOrBasePath SMB share name, or WebDAV base path; null/empty otherwise.
 */
data class RemoteCredentials(
    val type: ConnectionType,
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
    val shareOrBasePath: String?,
    val domain: String? = null,
)
