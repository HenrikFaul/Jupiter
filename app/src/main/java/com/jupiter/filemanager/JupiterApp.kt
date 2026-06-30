package com.jupiter.filemanager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
