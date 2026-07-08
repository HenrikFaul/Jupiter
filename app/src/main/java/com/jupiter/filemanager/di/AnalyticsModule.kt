package com.jupiter.filemanager.di

import com.jupiter.filemanager.core.analytics.Analytics
import com.jupiter.filemanager.core.analytics.NoOpAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the [Analytics] interface to the default [NoOpAnalytics] implementation.
 *
 * [NoOpAnalytics] is a `@Singleton`-annotated class with an `@Inject` constructor, so Hilt
 * knows how to create it; this module only declares the interface binding. The default sink
 * is a privacy-preserving no-op (analytics OFF by default), so wiring it app-wide introduces
 * no behavioral change. A real analytics backend can replace this binding later.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    abstract fun bindAnalytics(impl: NoOpAnalytics): Analytics
}
