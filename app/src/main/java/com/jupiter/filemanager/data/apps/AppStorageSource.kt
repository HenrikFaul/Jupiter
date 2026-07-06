package com.jupiter.filemanager.data.apps

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.AppStorageInfo
import com.jupiter.filemanager.domain.model.AppStorageOverview
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads per-application storage usage from the platform's [StorageStatsManager].
 *
 * A large share of a modern device's used space lives in app-private storage
 * (`Android/data`, `Android/obb`, APKs, caches) that no file manager can enumerate via
 * the filesystem on Android 11+. `StorageStatsManager` is the sanctioned way to account
 * for it — but it requires the special **Usage access** grant (`PACKAGE_USAGE_STATS`,
 * an app-op, not a runtime permission), so callers must first check [hasUsageAccess] and
 * send the user to the system Usage-access settings when it is missing.
 *
 * All work runs off the main thread; every per-app query is guarded so one failure never
 * aborts the whole aggregation.
 */
@Singleton
class AppStorageSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    /**
     * True when the app holds the Usage-access grant needed to read storage stats. Checked
     * via [AppOpsManager] (the op is what the "Usage access" toggle controls).
     */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        return try {
            // unsafeCheckOpNoThrow is API 29+; fall back to checkOpNoThrow on 26–28.
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Queries storage for every installed app on the primary (internal) volume, sorted by
     * total size descending. Returns [AppStorageOverview.permissionRequired] = true (and no
     * apps) when Usage-access is not granted. Never throws.
     */
    suspend fun query(): AppStorageOverview = withContext(dispatcher) {
        if (!hasUsageAccess()) {
            return@withContext AppStorageOverview(permissionRequired = true)
        }
        val stats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            ?: return@withContext AppStorageOverview(permissionRequired = false)
        val packageManager = context.packageManager

        val installed = runCatching {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())

        // Internal storage is identified by the default UUID.
        val uuid = StorageManager.UUID_DEFAULT
        val user = Process.myUserHandle()

        val apps = ArrayList<AppStorageInfo>(installed.size)
        for (info in installed) {
            currentCoroutineContext().ensureActive()
            val stat = runCatching {
                stats.queryStatsForPackage(uuid, info.packageName, user)
            }.getOrNull() ?: continue

            val label = runCatching { packageManager.getApplicationLabel(info).toString() }
                .getOrDefault(info.packageName)
            apps.add(
                AppStorageInfo(
                    packageName = info.packageName,
                    label = label,
                    appBytes = stat.appBytes,
                    dataBytes = stat.dataBytes,
                    cacheBytes = stat.cacheBytes,
                    isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                ),
            )
        }
        apps.sortByDescending { it.totalBytes }

        AppStorageOverview(
            apps = apps,
            totalBytes = apps.sumOf { it.totalBytes },
            cacheBytes = apps.sumOf { it.cacheBytes },
            permissionRequired = false,
        )
    }
}
