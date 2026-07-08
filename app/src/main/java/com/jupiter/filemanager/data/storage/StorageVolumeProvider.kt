package com.jupiter.filemanager.data.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.jupiter.filemanager.domain.model.StorageVolumeInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers the device's mounted storage volumes and reports capacity figures.
 *
 * The primary (internal) volume is derived from [Environment.getExternalStorageDirectory].
 * Removable volumes (SD cards, USB OTG) are discovered via the app-private roots returned by
 * [Context.getExternalFilesDirs] — the first entry maps to primary storage and any additional
 * entries map to secondary/removable volumes. For each volume we walk up from the app-private
 * directory to the volume mount root so that browsing starts at the user-visible storage root.
 */
@Singleton
class StorageVolumeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Returns the absolute path of the primary external storage root, e.g. {@code /storage/emulated/0}.
     */
    fun primaryRoot(): String =
        Environment.getExternalStorageDirectory()?.absolutePath ?: DEFAULT_PRIMARY_ROOT

    /**
     * Enumerates all currently mounted, readable storage volumes.
     *
     * The primary internal volume is always listed first; removable volumes follow in the order
     * the platform reports them. Volumes whose capacity cannot be read (unmounted or inaccessible)
     * are skipped so callers never see zero-capacity ghost entries.
     */
    fun volumes(): List<StorageVolumeInfo> {
        val result = LinkedHashMap<String, StorageVolumeInfo>()

        // --- Primary (internal) storage ---------------------------------------------------------
        val primaryRoot = primaryRoot()
        buildVolume(
            rootPath = primaryRoot,
            id = "primary",
            label = "Internal storage",
            isRemovable = false,
            isPrimary = true,
        )?.let { result[normalizeRoot(it.rootPath)] = it }

        // --- Secondary / removable storage ------------------------------------------------------
        // getExternalFilesDirs returns one app-private dir per volume; index 0 is primary storage,
        // any further entries correspond to removable volumes (SD cards / USB OTG).
        val appDirs: Array<out File?> = runCatching { context.getExternalFilesDirs(null) }
            .getOrNull() ?: emptyArray()

        appDirs.forEachIndexed { index, appDir ->
            if (appDir == null) return@forEachIndexed
            // Index 0 is the primary volume which we have already added above.
            if (index == 0) return@forEachIndexed

            val mountRoot = resolveVolumeRoot(appDir) ?: return@forEachIndexed
            val normalized = normalizeRoot(mountRoot)
            if (result.containsKey(normalized)) return@forEachIndexed

            val state = runCatching { Environment.getExternalStorageState(appDir) }.getOrNull()
            if (state != null && state != Environment.MEDIA_MOUNTED &&
                state != Environment.MEDIA_MOUNTED_READ_ONLY
            ) {
                return@forEachIndexed
            }

            buildVolume(
                rootPath = mountRoot,
                id = "removable_$index",
                label = labelForRemovable(mountRoot, index),
                isRemovable = true,
                isPrimary = false,
            )?.let { result[normalized] = it }
        }

        return result.values.toList()
    }

    /**
     * Builds a [StorageVolumeInfo] for [rootPath] using [StatFs], or null when the volume's
     * capacity cannot be queried (path missing, not a directory, or inaccessible).
     */
    private fun buildVolume(
        rootPath: String,
        id: String,
        label: String,
        isRemovable: Boolean,
        isPrimary: Boolean,
    ): StorageVolumeInfo? {
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) return null

        val stats = runCatching { StatFs(rootPath) }.getOrNull() ?: return null
        val blockSize = stats.blockSizeLong
        val totalBytes = stats.blockCountLong * blockSize
        val availableBytes = stats.availableBlocksLong * blockSize
        if (totalBytes <= 0L) return null

        return StorageVolumeInfo(
            id = id,
            label = label,
            rootPath = rootPath,
            totalBytes = totalBytes,
            availableBytes = availableBytes,
            isRemovable = isRemovable,
            isPrimary = isPrimary,
        )
    }

    /**
     * Translates an app-private external directory such as
     * {@code /storage/XXXX-XXXX/Android/data/<pkg>/files} into the user-visible volume root
     * {@code /storage/XXXX-XXXX}. Returns null when the structure is unexpected.
     */
    private fun resolveVolumeRoot(appDir: File): String? {
        // Walk up until we leave the "Android" segment, which marks the boundary between the
        // app-private subtree and the volume root.
        var current: File? = appDir
        while (current != null) {
            val parent = current.parentFile ?: break
            if (current.name.equals("Android", ignoreCase = true)) {
                return parent.absolutePath
            }
            current = parent
        }
        return null
    }

    /**
     * Produces a human-readable label for a removable volume, preferring the platform-reported
     * description on supported API levels and falling back to the volume identifier on the path.
     */
    private fun labelForRemovable(mountRoot: String, index: Int): String {
        val platformLabel = platformVolumeDescription(mountRoot)
        if (!platformLabel.isNullOrBlank()) return platformLabel

        val volumeName = File(mountRoot).name
        return if (volumeName.isNotBlank()) "SD card ($volumeName)" else "SD card $index"
    }

    /**
     * Asks the platform [StorageManager] for the user-facing description of the volume that
     * contains [mountRoot]. Available on API 24+; returns null when unavailable.
     */
    private fun platformVolumeDescription(mountRoot: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            ?: return null
        val volume = runCatching { storageManager.getStorageVolume(File(mountRoot)) }.getOrNull()
            ?: return null
        return runCatching { volume.getDescription(context) }.getOrNull()
    }

    /** Normalizes a root path for de-duplication, stripping any trailing separator. */
    private fun normalizeRoot(path: String): String {
        val trimmed = path.trimEnd('/')
        return trimmed.ifEmpty { "/" }
    }

    private companion object {
        const val DEFAULT_PRIMARY_ROOT = "/storage/emulated/0"
    }
}
