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
 * @property dataBytes app data (incl. external app-private data).
 * @property cacheBytes cached data (clearable).
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
    /** Total storage this app occupies (code + data + cache). */
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

/**
 * Aggregate view of per-app storage for the App-storage screen.
 *
 * @property apps apps sorted by [AppStorageInfo.totalBytes] descending.
 * @property totalBytes sum across all apps.
 * @property cacheBytes sum of clearable caches across all apps.
 * @property permissionRequired true when Usage-access has not been granted, so no stats
 *   could be read; the screen should prompt the user to grant it.
 */
data class AppStorageOverview(
    val apps: List<AppStorageInfo> = emptyList(),
    val totalBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val permissionRequired: Boolean = false,
)
