package com.jupiter.filemanager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.jupiter.filemanager.data.index.DownloadIndexObserver
import com.jupiter.filemanager.data.index.IndexingScheduler
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.data.trash.TrashScheduler
import com.jupiter.filemanager.domain.repository.IndexStateRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point for Jupiter.
 *
 * Annotated with [HiltAndroidApp] to trigger Hilt's code generation and create
 * the application-level dependency container that all other injected components
 * (activities, view models, repositories, workers) are scoped under.
 *
 * Implements [Configuration.Provider] so WorkManager uses the Hilt-aware
 * [HiltWorkerFactory] when instantiating workers (e.g.
 * [com.jupiter.filemanager.data.automation.AutomationWorker]); this lets workers
 * receive constructor-injected dependencies. WorkManager initializes on demand and
 * reads this configuration lazily on first use.
 *
 * Implements [ImageLoaderFactory] so Coil uses an app-wide [ImageLoader] that has
 * [VideoFrameDecoder] registered. This makes `AsyncImage` render an actual video
 * frame for video files everywhere in the app (thumbnails, previews) instead of a
 * fallback icon. Coil discovers this loader automatically because the [Application]
 * implements [ImageLoaderFactory].
 */
@HiltAndroidApp
class JupiterApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Keeps the persistent file index live for files that arrive outside of in-app
     * operations (e.g. downloads), indexing them immediately and flagging content
     * duplicates. Started once here so the index is already current on app open.
     */
    @Inject
    lateinit var downloadIndexObserver: DownloadIndexObserver

    /** Schedules the background index survey. */
    @Inject
    lateinit var indexingScheduler: IndexingScheduler

    /** Schedules the daily Recycle-Bin auto-delete (retention) sweep. */
    @Inject
    lateinit var trashScheduler: TrashScheduler

    /** User preference for whether indexing is enabled at all. */
    @Inject
    lateinit var settings: SettingsDataStore

    /** Authoritative index life-cycle state (completeness lives in Room, not a flag). */
    @Inject
    lateinit var indexStateRepository: IndexStateRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Recycle-Bin retention: schedule the daily auto-delete sweep. The worker no-ops when the
        // "auto-delete after N days" setting is OFF (the default), so keeping it scheduled is safe.
        runCatching { trashScheduler.schedulePeriodicPurge() }

        // Desktop-app-style indexing: build the survey in the background only when indexing
        // is ENABLED and the last survey did NOT reach COMPLETE. Completeness is a Room state
        // (EMPTY/RUNNING/COMPLETE/DIRTY/FAILED), NOT a row count — a partial/interrupted index
        // is never treated as finished. If the user disabled indexing we never auto-start it.
        // KEEP policy means a build already in progress is never restarted.
        appScope.launch {
            runCatching {
                // v10 converts high-volume descriptor TEXT to compact BLOBs during migration;
                // VACUUM must run later, outside Room's migration transaction, to return the freed
                // pages to the phone. Versioned + idempotent, and independent of indexing opt-in.
                indexingScheduler.ensureMetadataCompaction()
                val enabled = settings.indexingEnabled.first()
                // The live MediaStore delta observer is part of the indexing pipeline — it only
                // starts when indexing is enabled (and Settings tears it down on disable), so
                // "indexing off" really means nothing observes, schedules, or writes.
                if (enabled) {
                    runCatching { downloadIndexObserver.start() }
                }
                if (enabled && !indexStateRepository.isMetadataComplete()) {
                    indexingScheduler.ensureIndexed()
                }
                // Long-term freshness: a periodic 15-minute kicker re-runs the survey in the
                // background so the index keeps itself up to date even when the app stays
                // closed. KEEP: never duplicates, never restarts an in-flight one.
                if (enabled) {
                    indexingScheduler.schedulePeriodicRefresh()
                    // Fingerprint any images still missing a perceptual hash (first run
                    // after this feature ships covers the whole existing library).
                    indexingScheduler.ensurePerceptualBackfill()
                    indexingScheduler.ensureStructuralBackfill()
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * App-wide Coil loader with video-frame decoding enabled so `AsyncImage`
     * renders a real frame for video files (thumbnails and previews) instead of
     * falling back to a placeholder icon.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
