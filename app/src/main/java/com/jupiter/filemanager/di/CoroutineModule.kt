package com.jupiter.filemanager.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/**
 * Qualifies a process-lifetime [CoroutineScope]. Use it for fire-and-forget work that must
 * OUTLIVE the component that starts it — e.g. scheduling the index survey from a ViewModel that
 * navigation is about to clear (a `viewModelScope` job would be cancelled mid-flight). A
 * [SupervisorJob] keeps one failed child from tearing the scope down.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @IoDispatcher
    fun providesIo(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun providesDefault(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun providesMain(): CoroutineDispatcher = Dispatchers.Main

    /**
     * A single application-lifetime scope (never cancelled while the process lives). Backed by
     * [Dispatchers.IO] since its callers do light disk-backed reads (DataStore / Room) before
     * enqueuing WorkManager jobs.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun providesApplicationScope(@IoDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}
