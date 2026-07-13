package com.jupiter.filemanager.data.storage

import android.app.usage.StorageStatsManager
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
    private val primaryCapacityResolver: PrimaryStorageCapacityResolver,
) {

    /** Convenience constructor retained for focused tests and non-Hilt callers. */
    internal constructor(context: Context) : this(
        context = context,
        primaryCapacityResolver = PrimaryStorageCapacityResolver(context),
    )

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
        val fileSystemTotalBytes = stats.blockCountLong * blockSize
        val fileSystemAvailableBytes = stats.availableBlocksLong * blockSize
        val capacity = if (isPrimary) {
            primaryCapacityResolver.resolve(
                fileSystemTotalBytes = fileSystemTotalBytes,
                fileSystemAvailableBytes = fileSystemAvailableBytes,
            )
        } else {
            PrimaryStorageCapacity(
                totalBytes = fileSystemTotalBytes,
                availableBytes = fileSystemAvailableBytes,
            )
        }
        val totalBytes = capacity.totalBytes
        val availableBytes = capacity.availableBytes
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

/**
 * Resolves the primary device's whole advertised capacity instead of exposing only the size of
 * the emulated-user-data filesystem. The latter excludes system/reserved partitions and is why a
 * 256 GB phone can otherwise appear as roughly 222 GiB in a third-party file manager.
 *
 * Android's storage service is the authoritative source used by system storage surfaces. The same
 * marketed-size rounding used by the Android framework is retained only when that API is absent or
 * restricted. The readable filesystem capacity remains the lower bound in every case.
 */
@Singleton
class PrimaryStorageCapacityResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    internal fun resolve(
        fileSystemTotalBytes: Long,
        fileSystemAvailableBytes: Long,
    ): PrimaryStorageCapacity {
        if (fileSystemTotalBytes <= 0L) {
            return PrimaryStorageCapacity(fileSystemTotalBytes, fileSystemAvailableBytes)
        }

        val stats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        val platformTotal = runCatching { stats?.getTotalBytes(StorageManager.UUID_DEFAULT) }
            .getOrNull() ?: 0L
        val platformAvailable = runCatching { stats?.getFreeBytes(StorageManager.UUID_DEFAULT) }
            .getOrNull() ?: -1L
        val totalBytes = selectPrimaryCapacity(
            fileSystemTotalBytes = fileSystemTotalBytes,
            platformTotalBytes = platformTotal,
        )
        val availableBytes = selectPrimaryAvailableBytes(
            fileSystemAvailableBytes = fileSystemAvailableBytes,
            platformTotalBytes = platformTotal,
            platformAvailableBytes = platformAvailable,
            selectedTotalBytes = totalBytes,
        )

        return PrimaryStorageCapacity(
            totalBytes = totalBytes,
            availableBytes = availableBytes,
        )
    }
}

internal data class PrimaryStorageCapacity(
    val totalBytes: Long,
    val availableBytes: Long,
)

/** Pure selection policy, split out so capacity semantics are covered without Android mocks. */
internal fun selectPrimaryCapacity(
    fileSystemTotalBytes: Long,
    platformTotalBytes: Long,
): Long {
    if (fileSystemTotalBytes <= 0L) return fileSystemTotalBytes
    // A successful platform result is authoritative and already follows Android's user-display
    // semantics. Framework-style bucket rounding is only a fallback when that API is unavailable.
    return if (platformTotalBytes > 0L) {
        maxOf(fileSystemTotalBytes, platformTotalBytes)
    } else {
        maxOf(fileSystemTotalBytes, roundToAdvertisedStorageSize(fileSystemTotalBytes))
    }
}

internal fun selectPrimaryAvailableBytes(
    fileSystemAvailableBytes: Long,
    platformTotalBytes: Long,
    platformAvailableBytes: Long,
    selectedTotalBytes: Long,
): Long {
    return if (
        platformTotalBytes > 0L && platformAvailableBytes in 0L..selectedTotalBytes
    ) {
        platformAvailableBytes
    } else {
        fileSystemAvailableBytes.coerceIn(0L, selectedTotalBytes.coerceAtLeast(0L))
    }
}

/**
 * Mirrors Android's marketed storage-size buckets: 1, 2, 4 … 512 multiplied by powers of 1000.
 * For example, a 238.4 billion-byte user-data filesystem resolves to a 256 billion-byte device.
 */
internal fun roundToAdvertisedStorageSize(bytes: Long): Long {
    if (bytes <= 0L) return bytes
    var value = 1L
    var magnitude = 1L
    while (value <= Long.MAX_VALUE / magnitude && value * magnitude < bytes) {
        if (value < 512L) {
            value *= 2L
        } else {
            value = 1L
            if (magnitude > Long.MAX_VALUE / 1000L) return bytes
            magnitude *= 1000L
        }
    }
    return if (value <= Long.MAX_VALUE / magnitude) value * magnitude else bytes
}
