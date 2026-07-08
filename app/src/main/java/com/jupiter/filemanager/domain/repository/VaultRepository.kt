package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem

/**
 * Manages the encrypted vault: a private, encrypted area where files can be
 * imported, listed, exported, and removed.
 */
interface VaultRepository {

    /**
     * Returns true when the vault storage has been initialized and is ready to use.
     */
    suspend fun isVaultInitialized(): Boolean

    /**
     * Lists the files currently stored in the vault. Each returned [FileItem]'s
     * path references a vault entry.
     */
    suspend fun listVaultFiles(): AppResult<List<FileItem>>

    /**
     * Encrypts and imports the file at [sourcePath] into the vault, returning the
     * resulting vault [FileItem].
     */
    suspend fun importToVault(sourcePath: String): AppResult<FileItem>

    /**
     * Decrypts [vaultItem] and writes it to [destinationDir], returning the
     * exported [FileItem] at its new location.
     */
    suspend fun exportFromVault(vaultItem: FileItem, destinationDir: String): AppResult<FileItem>

    /**
     * Permanently removes [vaultItem] from the vault.
     */
    suspend fun deleteFromVault(vaultItem: FileItem): AppResult<Unit>
}
