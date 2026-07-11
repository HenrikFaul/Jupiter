package com.jupiter.filemanager.domain.model

/**
 * Storage occupied by a single installed application, as reported by the platform's
 * `StorageStatsManager`. This is how a file manager accounts for the large slice of a
 * device's used space that lives in app-private storage (`Android/data`, `Android/obb`,
 * APKs, caches) — which the filesystem itself cannot enumerate on Android 11+.
 *
 * @property packageName the application's package id.
 * @property label the human-readable app name.
 * @property appBytes APK / app code size.
 * @property dataBytes ALL app data as the platform reports it — this INCLUDES [cacheBytes]
 *   (see `StorageStats.getDataBytes`), plus external app-private data.
 * @property cacheBytes cached data (clearable); a subset of [dataBytes].
 * @property isSystemApp whether this is a system/pre-installed app.
 */
data class AppStorageInfo(
    val packageName: String,
    val label: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val isSystemApp: Boolean,
) {
    /**
     * Total storage this app occupies. `StorageStats.getDataBytes` already INCLUDES the
     * cache, so the total is code + data — adding [cacheBytes] again would double-count it
     * (that inflation bug shipped in v0.29 and is fixed here).
     */
    val totalBytes: Long get() = appBytes + dataBytes

    /** App data excluding the clearable cache — what Settings shows as "Data". */
    val dataBytesExcludingCache: Long get() = (dataBytes - cacheBytes).coerceAtLeast(0L)
}

/**
 * Aggregate view of per-app storage for the App-storage screen.
 *
 * @property apps apps sorted by [AppStorageInfo.totalBytes] descending.
 * @property totalBytes sum across the listed apps (complete thanks to QUERY_ALL_PACKAGES;
 *   apps of other profiles — e.g. a work profile — are out of this screen's scope).
 * @property cacheBytes sum of clearable caches across the listed apps.
 * @property permissionRequired true when Usage-access has not been granted, so no stats
 *   could be read; the screen should prompt the user to grant it.
 * @property scanning true while this is a PARTIAL result emitted mid-scan (the per-app query
 *   walks every installed package and takes several seconds on a full device); the screen shows
 *   the apps gathered so far plus a "Scanning…" indicator instead of a blank prompt. False on the
 *   final, complete emission.
 */
data class AppStorageOverview(
    val apps: List<AppStorageInfo> = emptyList(),
    val totalBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val permissionRequired: Boolean = false,
    val scanning: Boolean = false,
)
