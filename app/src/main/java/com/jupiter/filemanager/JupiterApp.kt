package com.jupiter.filemanager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jupiter.filemanager.data.index.DownloadIndexObserver
import dagger.hilt.android.HiltAndroidApp
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
 */
@HiltAndroidApp
class JupiterApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Keeps the persistent file index live for files that arrive outside of in-app
     * operations (e.g. downloads), indexing them immediately and flagging content
     * duplicates. Started once here so the index is already current on app open.
     */
    @Inject
    lateinit var downloadIndexObserver: DownloadIndexObserver

    override fun onCreate() {
        super.onCreate()
        // Best-effort: a failure to register the observer must never crash startup.
        runCatching { downloadIndexObserver.start() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
