package com.jupiter.filemanager.di

import android.content.Context
import androidx.room.Room
import com.jupiter.filemanager.data.trash.TrashDao
import com.jupiter.filemanager.data.trash.TrashDatabase
import com.jupiter.filemanager.data.trash.TrashRepositoryImpl
import com.jupiter.filemanager.domain.repository.TrashRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the recoverable Recycle Bin.
 *
 * Provides the Room [TrashDatabase] + its DAO. Unlike the disposable file-index
 * cache, the trash store is authoritative, so it deliberately does **not** use
 * `fallbackToDestructiveMigration()` — trash metadata must survive upgrades.
 * The schema is at version 1, so no migrations are required yet.
 */
@Module
@InstallIn(SingletonComponent::class)
object TrashModule {

    @Provides
    @Singleton
    fun provideTrashDatabase(
        @ApplicationContext context: Context,
    ): TrashDatabase =
        Room.databaseBuilder(context, TrashDatabase::class.java, "jupiter_trash.db")
            .build()

    @Provides
    fun provideTrashDao(database: TrashDatabase): TrashDao =
        database.trashDao()
}

/**
 * Interface-to-implementation bindings for the trash layer. Kept in a separate
 * abstract module because `@Binds` and `@Provides` cannot coexist in one object.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TrashBindingsModule {

    @Binds
    abstract fun bindTrashRepository(
        impl: TrashRepositoryImpl,
    ): TrashRepository
}
