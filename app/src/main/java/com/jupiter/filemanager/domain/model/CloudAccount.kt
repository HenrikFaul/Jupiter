package com.jupiter.filemanager.domain.model

/**
 * Supported cloud storage providers.
 */
enum class CloudProvider {
    GOOGLE_DRIVE,
    DROPBOX,
    ONEDRIVE,
    ICLOUD,
    BOX,
    WEBDAV,
}

/**
 * A user-linked cloud storage account.
 *
 * @param id stable unique identifier for the account.
 * @param provider the cloud provider backing this account.
 * @param displayName human-readable name shown to the user.
 * @param usedBytes bytes currently used in the account's quota.
 * @param totalBytes total bytes available in the account's quota.
 * @param isConnected whether the account is currently authenticated/connected.
 */
data class CloudAccount(
    val id: String,
    val provider: CloudProvider,
    val displayName: String,
    val usedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isConnected: Boolean = false,
)
