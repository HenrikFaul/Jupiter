package com.jupiter.filemanager.di

import android.content.Context
import androidx.room.Room
import com.jupiter.filemanager.data.index.ArrivalInspector
import com.jupiter.filemanager.data.index.DedupCheckpointStore
import com.jupiter.filemanager.data.index.DuplicateDetector
import com.jupiter.filemanager.data.index.FileIndexDao
import com.jupiter.filemanager.data.index.FileIndexDatabase
import com.jupiter.filemanager.data.index.FileIndexRepositoryImpl
import com.jupiter.filemanager.data.index.AndroidMediaFingerprintSource
import com.jupiter.filemanager.data.index.IndexStateDao
import com.jupiter.filemanager.data.index.IndexStateRepositoryImpl
import com.jupiter.filemanager.data.index.MediaFingerprintSource
import com.jupiter.filemanager.data.index.MediaStoreIndexSource
import com.jupiter.filemanager.data.index.NewFileSource
import com.jupiter.filemanager.data.index.SettingsDedupCheckpointStore
import com.jupiter.filemanager.data.permission.StorageAccessGate
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import com.jupiter.filemanager.domain.repository.IndexStateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the persistent file index.
 *
 * Provides the Room [FileIndexDatabase] + its DAO and binds
 * [FileIndexRepository] to its Room-backed implementation. The database is a
 * disposable cache, so migrations fall back to destructive recreation.
 */
@Module
@InstallIn(SingletonComponent::class)
object IndexModule {

    @Provides
    @Singleton
    fun provideFileIndexDatabase(
        @ApplicationContext context: Context,
    ): FileIndexDatabase =
        Room.databaseBuilder(context, FileIndexDatabase::class.java, "jupiter_index.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFileIndexDao(database: FileIndexDatabase): FileIndexDao =
        database.fileIndexDao()

    @Provides
    fun provideIndexStateDao(database: FileIndexDatabase): IndexStateDao =
        database.indexStateDao()
}

/**
 * Interface-to-implementation bindings for the index layer. Kept in a separate
 * abstract module because `@Binds` and `@Provides` cannot coexist in one object.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IndexBindingsModule {

    @Binds
    abstract fun bindFileIndexRepository(
        impl: FileIndexRepositoryImpl,
    ): FileIndexRepository

    @Binds
    abstract fun bindIndexStateRepository(
        impl: IndexStateRepositoryImpl,
    ): IndexStateRepository

    @Binds
    abstract fun bindNewFileSource(impl: MediaStoreIndexSource): NewFileSource

    @Binds
    abstract fun bindDedupCheckpointStore(
        impl: SettingsDedupCheckpointStore,
    ): DedupCheckpointStore

    @Binds
    abstract fun bindArrivalInspector(impl: DuplicateDetector): ArrivalInspector

    @Binds
    abstract fun bindMediaFingerprintSource(
        impl: AndroidMediaFingerprintSource,
    ): MediaFingerprintSource

    @Binds
    abstract fun bindStorageAccessGate(impl: StorageAccessManager): StorageAccessGate
}
