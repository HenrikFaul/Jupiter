package com.jupiter.filemanager.di

import com.jupiter.filemanager.data.bookmark.BookmarkRepositoryImpl
import com.jupiter.filemanager.data.file.FileRepositoryImpl
import com.jupiter.filemanager.data.storage.StorageAnalyticsRepositoryImpl
import com.jupiter.filemanager.data.vault.VaultRepositoryImpl
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import com.jupiter.filemanager.domain.repository.VaultRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the concrete data-layer repository implementations to their domain-layer
 * interfaces so they can be injected throughout the app.
 *
 * Each implementation is a `@Singleton`-annotated class with an `@Inject` constructor,
 * so Hilt knows how to create it; this module only declares the interface-to-impl
 * binding via `@Binds`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds
    abstract fun bindStorageAnalyticsRepository(impl: StorageAnalyticsRepositoryImpl): StorageAnalyticsRepository

    @Binds
    abstract fun bindVaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository
}
