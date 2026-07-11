package com.jupiter.filemanager.data.apps

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
     * Queries storage for every installed app, emitting PARTIAL results as it scans so the screen
     * never blocks for the several seconds the full walk takes on a device with hundreds of apps.
     * Returns a single `permissionRequired = true` frame when Usage-access is not granted. Never
     * throws (each per-app query is guarded; one failure never aborts the aggregation).
     *
     * Completeness (the v0.29 screen showed ~10 GB of ~95 GB) rests on two fixes:
     * 1. the manifest holds QUERY_ALL_PACKAGES, so `getInstalledApplications` actually
     *    returns user-installed apps — package-visibility filtering (Android 11+) otherwise
     *    hides exactly the biggest consumers (games, messengers), leaving only system apps;
     * 2. each package is queried on the volume it actually lives on
     *    ([ApplicationInfo.storageUuid], adoptable-storage safe), not blindly on the default.
     *
     * Deliberately NOT used: `StorageStatsManager.queryStatsForUser` as a cross-check. Its
     * numbers are incomparable with a per-package sum — its dataBytes includes the user's
     * ENTIRE shared storage (photos, downloads…), and its appBytes walks the device-wide
     * `/data/app` (other profiles' code) plus dalvik-cache — so "aggregate − per-app sum"
     * measures mostly things that are not apps of this user. An adversarial review confirmed
     * it would have inflated the header on essentially every real device.
     *
     * Emission contract:
     * 1. If Usage-access is missing → a single `permissionRequired = true` frame.
     * 2. Otherwise an immediate empty `scanning = true` frame so the UI can drop the grant prompt
     *    and show "Scanning…" AT ONCE (the grant can lag, and querying 700+ packages is slow — the
     *    old single-shot [query] left the prompt on screen for 9–15 s).
     * 3. A `scanning = true` frame after the first [FIRST_BATCH] apps, then every [EMIT_EVERY] more,
     *    each carrying the apps gathered so far sorted by size — the list fills progressively.
     * 4. A final `scanning = false` frame with the complete, sorted result.
     *
     * Each partial is sorted by size so the biggest consumers bubble to the top as they are found.
     */
    fun queryStream(): Flow<AppStorageOverview> = flow {
        if (!hasUsageAccess()) {
            emit(AppStorageOverview(permissionRequired = true))
            return@flow
        }
        val stats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        if (stats == null) {
            emit(AppStorageOverview(permissionRequired = false))
            return@flow
        }
        val packageManager = context.packageManager
        val installed = runCatching {
            packageManager.getInstalledApplications(0)
        }.getOrDefault(emptyList())
        val user = Process.myUserHandle()

        // Immediate frame: leaves the grant prompt and shows "Scanning…" without waiting for the walk.
        emit(AppStorageOverview(permissionRequired = false, scanning = true))

        val apps = ArrayList<AppStorageInfo>(installed.size)
        var lastEmittedAt = 0
        for (info in installed) {
            currentCoroutineContext().ensureActive()
            val uuid = info.storageUuid ?: StorageManager.UUID_DEFAULT
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
            // Publish the first handful right away, then in batches, so the user sees the list grow.
            val reachedFirstBatch = apps.size == FIRST_BATCH
            val reachedNextBatch = apps.size - lastEmittedAt >= EMIT_EVERY
            if (reachedFirstBatch || reachedNextBatch) {
                lastEmittedAt = apps.size
                emit(snapshot(apps, scanning = true))
            }
        }
        emit(snapshot(apps, scanning = false))
    }.flowOn(dispatcher)

    /** Builds an overview from a COPY of the accumulated apps, sorted by size descending. */
    private fun snapshot(apps: List<AppStorageInfo>, scanning: Boolean): AppStorageOverview {
        val sorted = apps.sortedByDescending { it.totalBytes }
        return AppStorageOverview(
            apps = sorted,
            totalBytes = sorted.sumOf { it.totalBytes },
            cacheBytes = sorted.sumOf { it.cacheBytes },
            permissionRequired = false,
            scanning = scanning,
        )
    }

    private companion object {
        /** Emit the very first apps as soon as this many are gathered, so the list appears fast. */
        const val FIRST_BATCH = 5

        /** After the first batch, publish an updated snapshot every this-many more apps. */
        const val EMIT_EVERY = 25
    }
}
