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

    /** Source of truth for whether the last index survey COMPLETED. */
    @Inject
    lateinit var settings: SettingsDataStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Best-effort: a failure to register the observer must never crash startup.
        runCatching { downloadIndexObserver.start() }

        // Desktop-app-style indexing: build the survey in the background whenever the last
        // one did NOT complete — a partial/interrupted index (rows present but scan killed)
        // is not trustworthy, so gate on completion, NOT on a non-empty row count. KEEP
        // policy means a build already in progress is never restarted. After a successful
        // build the real-time delta hooks + the download observer keep it live, so opening
        // the app never triggers a deep scan.
        appScope.launch {
            runCatching {
                if (!settings.indexComplete.first()) {
                    indexingScheduler.ensureIndexed()
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
