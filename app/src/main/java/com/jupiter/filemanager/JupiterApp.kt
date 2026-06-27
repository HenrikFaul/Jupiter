package com.jupiter.filemanager

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point for Jupiter.
 *
 * Annotated with [HiltAndroidApp] to trigger Hilt's code generation and create
 * the application-level dependency container that all other injected components
 * (activities, view models, repositories) are scoped under.
 */
@HiltAndroidApp
class JupiterApp : Application()
