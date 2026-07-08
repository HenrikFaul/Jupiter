package com.jupiter.filemanager.di

import com.jupiter.filemanager.data.remote.RemoteAccessRepositoryImpl
import com.jupiter.filemanager.data.remote.RemoteSourceProviderImpl
import com.jupiter.filemanager.domain.remote.RemoteSourceProvider
import com.jupiter.filemanager.domain.repository.RemoteAccessRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the remote-access data-layer implementations to their domain-layer
 * interfaces so they can be injected throughout the app.
 *
 *  - [RemoteSourceProvider]   -> [RemoteSourceProviderImpl]
 *  - [RemoteAccessRepository] -> [RemoteAccessRepositoryImpl]
 *
 * Each implementation is a `@Singleton`-annotated class with an `@Inject`
 * constructor, so Hilt knows how to construct it; this module only declares the
 * interface-to-implementation binding via `@Binds`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteModule {

    @Binds
    abstract fun bindRemoteSourceProvider(impl: RemoteSourceProviderImpl): RemoteSourceProvider

    @Binds
    abstract fun bindRemoteAccessRepository(impl: RemoteAccessRepositoryImpl): RemoteAccessRepository
}
