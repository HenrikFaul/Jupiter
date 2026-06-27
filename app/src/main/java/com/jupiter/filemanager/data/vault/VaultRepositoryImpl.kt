package com.jupiter.filemanager.data.vault

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.core.util.extensionOf
import com.jupiter.filemanager.core.util.fileTypeFor
import com.jupiter.filemanager.core.util.mimeTypeFor
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.repository.VaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [VaultRepository] backed by Jetpack Security's [EncryptedFile] and a hardware-backed
 * [MasterKey].
 *
 * Encrypted copies of imported files live under `context.filesDir/vault`. Each vault
 * entry is stored as two files that share a random identifier:
 *
 *  - `<id>.enc`  — the AES-GCM encrypted ciphertext produced by [EncryptedFile].
 *  - `<id>.meta` — a small plaintext sidecar holding the original file name and the
 *                  original (plaintext) byte length.
 *
 * The sidecar is needed because the ciphertext length does not equal the plaintext
 * length, and because the original name must never leak into the (potentially
 * world-visible) entry file name. Returned [FileItem]s therefore carry the original
 * name/size/type for display while their [FileItem.path] points at the `.enc` entry so
 * that other vault operations can locate it again.
 *
 * All IO is dispatched on the injected [IoDispatcher]; nothing here touches the main
 * thread.
 */
@Singleton
class VaultRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : VaultRepository {

    /** Directory holding all encrypted vault entries and their metadata sidecars. */
    private val vaultDir: File
        get() = File(context.filesDir, VAULT_DIR_NAME)

    /**
     * Lazily created hardware-backed master key used to derive per-file encryption
     * keys. Built once and reused for the lifetime of this singleton.
     */
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    override suspend fun isVaultInitialized(): Boolean = withContext(dispatcher) {
        val dir = vaultDir
        dir.exists() && dir.isDirectory
    }

    override suspend fun listVaultFiles(): AppResult<List<FileItem>> = withContext(dispatcher) {
        try {
            val dir = vaultDir
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext AppResult.Success(emptyList())
            }

            val entries: Array<File> = try {
                dir.listFiles { _, fileName -> fileName.endsWith(ENC_SUFFIX) }
            } catch (_: SecurityException) {
                null
            } ?: emptyArray()

            val items = entries
                .sortedBy { it.name }
                .map { entry -> vaultEntryToFileItem(entry) }

            AppResult.Success(items)
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.AccessDenied(vaultDir.absolutePath))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Io(e.message ?: "Failed to read vault.", e))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Failed to read vault.", e))
        }
    }

    override suspend fun importToVault(sourcePath: String): AppResult<FileItem> =
        withContext(dispatcher) {
            try {
                val source = File(sourcePath)
                if (!source.exists()) {
                    return@withContext AppResult.Failure(AppError.NotFound(sourcePath))
                }
                if (!source.isFile) {
                    return@withContext AppResult.Failure(
                        AppError.Io("Only files can be imported into the vault."),
                    )
                }
                if (!source.canRead()) {
                    return@withContext AppResult.Failure(AppError.AccessDenied(sourcePath))
                }

                ensureVaultDir()

                val id = UUID.randomUUID().toString()
                val encFile = File(vaultDir, id + ENC_SUFFIX)
                val metaFile = File(vaultDir, id + META_SUFFIX)

                val originalName = source.name.ifEmpty { id }
                val originalSize = runCatching { source.length() }.getOrDefault(0L)
                val originalModified =
                    runCatching { source.lastModified() }.getOrDefault(System.currentTimeMillis())

                // Encrypt the plaintext into the .enc entry.
                val encrypted = buildEncryptedFile(encFile)
                try {
                    source.inputStream().use { input ->
                        encrypted.openFileOutput().use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }
                } catch (e: Exception) {
                    // Roll back a partially written entry so the vault stays consistent.
                    encFile.delete()
                    throw e
                }

                // Persist the metadata sidecar describing the original file.
                try {
                    writeMetadata(metaFile, originalName, originalSize, originalModified)
                } catch (e: Exception) {
                    encFile.delete()
                    metaFile.delete()
                    throw e
                }

                AppResult.Success(
                    fileItemFor(
                        entry = encFile,
                        originalName = originalName,
                        originalSize = originalSize,
                        originalModified = originalModified,
                    ),
                )
            } catch (e: SecurityException) {
                AppResult.Failure(AppError.AccessDenied(sourcePath))
            } catch (e: IOException) {
                AppResult.Failure(AppError.Io(e.message ?: "Failed to import file.", e))
            } catch (e: Exception) {
                AppResult.Failure(AppError.Unknown(e.message ?: "Failed to import file.", e))
            }
        }

    override suspend fun exportFromVault(
        vaultItem: FileItem,
        destinationDir: String,
    ): AppResult<FileItem> = withContext(dispatcher) {
        try {
            val encFile = File(vaultItem.path)
            if (!encFile.exists()) {
                return@withContext AppResult.Failure(AppError.NotFound(vaultItem.path))
            }

            val destDir = File(destinationDir)
            if (!destDir.exists()) {
                if (!destDir.mkdirs()) {
                    return@withContext AppResult.Failure(
                        AppError.Io("Could not create destination directory: " + destinationDir),
                    )
                }
            }
            if (!destDir.isDirectory) {
                return@withContext AppResult.Failure(
                    AppError.Io("Destination is not a directory: " + destinationDir),
                )
            }

            val targetName = vaultItem.name.ifEmpty { encFile.nameWithoutExtension }
            val destination = uniqueDestination(destDir, targetName)

            val encrypted = buildEncryptedFile(encFile)
            try {
                encrypted.openFileInput().use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
            } catch (e: Exception) {
                destination.delete()
                throw e
            }

            val exportedName = destination.name
            val isDirectory = false
            AppResult.Success(
                FileItem(
                    path = destination.absolutePath,
                    name = exportedName,
                    isDirectory = isDirectory,
                    sizeBytes = runCatching { destination.length() }.getOrDefault(vaultItem.sizeBytes),
                    lastModified = runCatching { destination.lastModified() }
                        .getOrDefault(System.currentTimeMillis()),
                    type = fileTypeFor(exportedName, isDirectory),
                    extension = extensionOf(exportedName),
                    mimeType = mimeTypeFor(exportedName),
                    isHidden = exportedName.startsWith('.'),
                    childCount = null,
                    canRead = true,
                    canWrite = true,
                ),
            )
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.AccessDenied(destinationDir))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Io(e.message ?: "Failed to export file.", e))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Failed to export file.", e))
        }
    }

    override suspend fun deleteFromVault(vaultItem: FileItem): AppResult<Unit> =
        withContext(dispatcher) {
            try {
                val encFile = File(vaultItem.path)
                if (!encFile.exists()) {
                    return@withContext AppResult.Failure(AppError.NotFound(vaultItem.path))
                }

                val metaFile = metadataFileFor(encFile)

                val encDeleted = !encFile.exists() || encFile.delete()
                // The sidecar is best-effort; a missing meta file should not fail deletion.
                if (metaFile.exists()) {
                    metaFile.delete()
                }

                if (!encDeleted) {
                    AppResult.Failure(
                        AppError.Io("Could not delete vault entry: " + vaultItem.name),
                    )
                } else {
                    AppResult.Success(Unit)
                }
            } catch (e: SecurityException) {
                AppResult.Failure(AppError.AccessDenied(vaultItem.path))
            } catch (e: IOException) {
                AppResult.Failure(AppError.Io(e.message ?: "Failed to delete vault entry.", e))
            } catch (e: Exception) {
                AppResult.Failure(AppError.Unknown(e.message ?: "Failed to delete vault entry.", e))
            }
        }

    // region Internals --------------------------------------------------------

    /** Ensures the vault directory exists, creating it (and parents) when needed. */
    private fun ensureVaultDir() {
        val dir = vaultDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    /**
     * Builds an [EncryptedFile] handle for [target] using the shared [masterKey] and
     * AES-256-GCM with HKDF-derived per-file keys.
     */
    private fun buildEncryptedFile(target: File): EncryptedFile =
        EncryptedFile.Builder(
            context,
            target,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

    /**
     * Maps an encrypted vault entry file to a display-ready [FileItem], reading the
     * original name/size from the sidecar when present and falling back to safe
     * approximations (entry id and ciphertext length) when it is missing or corrupt.
     */
    private fun vaultEntryToFileItem(entry: File): FileItem {
        val metaFile = metadataFileFor(entry)
        val metadata = readMetadata(metaFile)

        val fallbackName = entry.nameWithoutExtension
        val originalName = metadata?.name?.takeIf { it.isNotEmpty() } ?: fallbackName
        // The ciphertext length is a reasonable approximation when metadata is absent.
        val approxCiphertextSize = runCatching { entry.length() }.getOrDefault(0L)
        val originalSize = metadata?.sizeBytes ?: approxCiphertextSize
        val originalModified = metadata?.lastModified
            ?: runCatching { entry.lastModified() }.getOrDefault(0L)

        return fileItemFor(
            entry = entry,
            originalName = originalName,
            originalSize = originalSize,
            originalModified = originalModified,
        )
    }

    /**
     * Constructs a [FileItem] whose [FileItem.path] references the encrypted [entry] on
     * disk while all displayed metadata reflects the original (plaintext) file.
     */
    private fun fileItemFor(
        entry: File,
        originalName: String,
        originalSize: Long,
        originalModified: Long,
    ): FileItem {
        val isDirectory = false
        return FileItem(
            path = entry.absolutePath,
            name = originalName,
            isDirectory = isDirectory,
            sizeBytes = originalSize,
            lastModified = originalModified,
            type = fileTypeFor(originalName, isDirectory),
            extension = extensionOf(originalName),
            mimeType = mimeTypeFor(originalName),
            isHidden = false,
            childCount = null,
            canRead = true,
            canWrite = true,
        )
    }

    /** Returns the metadata sidecar file paired with an `.enc` [entry]. */
    private fun metadataFileFor(entry: File): File {
        val base = entry.name.removeSuffix(ENC_SUFFIX)
        return File(entry.parentFile, base + META_SUFFIX)
    }

    /** Persists the original file metadata as a small plaintext properties sidecar. */
    private fun writeMetadata(
        metaFile: File,
        name: String,
        sizeBytes: Long,
        lastModified: Long,
    ) {
        val props = Properties().apply {
            setProperty(META_KEY_NAME, name)
            setProperty(META_KEY_SIZE, sizeBytes.toString())
            setProperty(META_KEY_MODIFIED, lastModified.toString())
        }
        metaFile.outputStream().use { output ->
            props.store(output, "Jupiter vault entry metadata")
            output.flush()
        }
    }

    /**
     * Reads the metadata sidecar for a vault entry, returning null when it is absent or
     * cannot be parsed (callers then fall back to approximations).
     */
    private fun readMetadata(metaFile: File): VaultMetadata? {
        if (!metaFile.exists()) return null
        return try {
            val props = Properties()
            metaFile.inputStream().use { input -> props.load(input) }
            val name = props.getProperty(META_KEY_NAME).orEmpty()
            val size = props.getProperty(META_KEY_SIZE)?.toLongOrNull() ?: 0L
            val modified = props.getProperty(META_KEY_MODIFIED)?.toLongOrNull() ?: 0L
            VaultMetadata(name = name, sizeBytes = size, lastModified = modified)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves a non-colliding destination file inside [dir] for [name], appending
     * " (n)" before the extension until a free name is found.
     */
    private fun uniqueDestination(dir: File, name: String): File {
        val initial = File(dir, name)
        if (!initial.exists()) return initial

        val dotIndex = name.lastIndexOf('.')
        val base: String
        val suffix: String
        if (dotIndex > 0) {
            base = name.substring(0, dotIndex)
            suffix = name.substring(dotIndex)
        } else {
            base = name
            suffix = ""
        }

        var counter = 1
        while (true) {
            val candidate = File(dir, base + " (" + counter + ")" + suffix)
            if (!candidate.exists()) return candidate
            counter++
        }
    }

    /** Parsed contents of a vault entry's metadata sidecar. */
    private data class VaultMetadata(
        val name: String,
        val sizeBytes: Long,
        val lastModified: Long,
    )

    // endregion

    private companion object {
        const val VAULT_DIR_NAME: String = "vault"
        const val ENC_SUFFIX: String = ".enc"
        const val META_SUFFIX: String = ".meta"

        const val META_KEY_NAME: String = "name"
        const val META_KEY_SIZE: String = "size"
        const val META_KEY_MODIFIED: String = "modified"
    }
}
