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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
     * 2. Otherwise an immediate empty `scanning = true` frame (with the total package count) so the
     *    UI can drop the grant prompt, show "Scanning…" and a determinate progress bar AT ONCE.
     * 3. A `scanning = true` frame after the first [FIRST_BATCH] packages, then every [EMIT_EVERY]
     *    more, each carrying the apps gathered so far (size-sorted) and the scanned/total counts so
     *    the progress bar advances and the list fills.
     * 4. A final `scanning = false` frame with the complete, sorted result.
     *
     * The per-package stat is a binder round-trip to the system; measured SEQUENTIALLY that is the
     * 9–15 s the user saw. Here the packages are measured with bounded PARALLELISM
     * ([SCAN_CONCURRENCY] at a time), which cuts the wall-clock several-fold, and steady progress
     * emissions keep the UI actively recomposing (so the result appears on its own, without needing
     * a touch to force a frame). Each partial is size-sorted so the biggest consumers surface first.
     */
    fun queryStream(): Flow<AppStorageOverview> = channelFlow {
        if (!hasUsageAccess()) {
            send(AppStorageOverview(permissionRequired = true))
            return@channelFlow
        }
        val stats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        if (stats == null) {
            send(AppStorageOverview(permissionRequired = false))
            return@channelFlow
        }
        val packageManager = context.packageManager
        val installed = runCatching {
            packageManager.getInstalledApplications(0)
        }.getOrDefault(emptyList())
        val user = Process.myUserHandle()
        val total = installed.size

        // Immediate frame: leaves the grant prompt and shows "Scanning… 0/N" without waiting.
        send(AppStorageOverview(permissionRequired = false, scanning = true, scannedCount = 0, totalCount = total))

        val apps = ArrayList<AppStorageInfo>(total)
        val lock = Any()
        var processed = 0
        val semaphore = Semaphore(SCAN_CONCURRENCY)

        // Measure every package with bounded parallelism; publish a progress snapshot at the first
        // batch and then every EMIT_EVERY packages. The shared list, counter, and emission all happen
        // under [lock] so progress is consistent and delivered in monotonic order across workers.
        coroutineScope {
            for (info in installed) {
                launch {
                    semaphore.withPermit {
                        currentCoroutineContext().ensureActive()
                        val uuid = info.storageUuid ?: StorageManager.UUID_DEFAULT
                        val stat = runCatching {
                            stats.queryStatsForPackage(uuid, info.packageName, user)
                        }.getOrNull()
                        val item = if (stat != null) {
                            val label = runCatching { packageManager.getApplicationLabel(info).toString() }
                                .getOrDefault(info.packageName)
                            AppStorageInfo(
                                packageName = info.packageName,
                                label = label,
                                appBytes = stat.appBytes,
                                dataBytes = stat.dataBytes,
                                cacheBytes = stat.cacheBytes,
                                isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            )
                        } else {
                            null
                        }
                        // Emit under [lock] so progress frames are delivered in monotonic scanned
                        // order (parallel workers otherwise race and the bar could jump backwards).
                        // trySend never suspends, so holding the lock across it is safe.
                        synchronized(lock) {
                            if (item != null) apps.add(item)
                            processed++
                            if (processed == FIRST_BATCH || processed % EMIT_EVERY == 0) {
                                trySend(snapshot(apps, scanning = true, scanned = processed, total = total))
                            }
                        }
                    }
                }
            }
        }
        // Authoritative final frame (guaranteed delivery via suspending send).
        val finalApps = synchronized(lock) { apps.toList() }
        send(snapshot(finalApps, scanning = false, scanned = total, total = total))
    }
        // Conflate progress frames: only the latest partial matters, so a slow collector never lags
        // behind the scan (and never backs up the producer). The final frame is a suspending send.
        .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .flowOn(dispatcher)

    /** Builds an overview from a snapshot COPY of the accumulated apps, sorted by size descending. */
    private fun snapshot(
        apps: List<AppStorageInfo>,
        scanning: Boolean,
        scanned: Int,
        total: Int,
    ): AppStorageOverview {
        val sorted = apps.sortedByDescending { it.totalBytes }
        return AppStorageOverview(
            apps = sorted,
            totalBytes = sorted.sumOf { it.totalBytes },
            cacheBytes = sorted.sumOf { it.cacheBytes },
            permissionRequired = false,
            scanning = scanning,
            scannedCount = scanned,
            totalCount = total,
        )
    }

    private companion object {
        /** Emit the very first apps as soon as this many are measured, so the list appears fast. */
        const val FIRST_BATCH = 5

        /** After the first batch, publish an updated snapshot every this-many more packages. */
        const val EMIT_EVERY = 15

        /** Concurrent per-package stat binder calls — cuts a 700-app scan from ~10 s to a second or two. */
        const val SCAN_CONCURRENCY = 8
    }
}
