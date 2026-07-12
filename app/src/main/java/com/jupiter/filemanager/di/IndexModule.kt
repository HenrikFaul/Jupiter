package com.jupiter.filemanager.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jupiter.filemanager.core.util.DefaultPathPolicy
import com.jupiter.filemanager.core.util.PathPolicy
import com.jupiter.filemanager.data.index.ArrivalInspector
import com.jupiter.filemanager.data.index.DedupCheckpointStore
import com.jupiter.filemanager.data.index.DedupDecisionDao
import com.jupiter.filemanager.data.index.DuplicateDetector
import com.jupiter.filemanager.data.index.FileIndexDao
import com.jupiter.filemanager.data.index.FileIndexDatabase
import com.jupiter.filemanager.data.index.FileIndexRepositoryImpl
import com.jupiter.filemanager.data.index.AndroidMediaFingerprintSource
import com.jupiter.filemanager.data.index.IndexStateDao
import com.jupiter.filemanager.data.index.IndexReadinessRepositoryImpl
import com.jupiter.filemanager.data.index.IndexStateRepositoryImpl
import com.jupiter.filemanager.data.index.MediaFingerprintSource
import com.jupiter.filemanager.data.index.MediaStoreIndexSource
import com.jupiter.filemanager.data.index.NewFileSource
import com.jupiter.filemanager.data.index.SettingsDedupCheckpointStore
import com.jupiter.filemanager.data.permission.StorageAccessGate
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import com.jupiter.filemanager.domain.repository.IndexStateRepository
import com.jupiter.filemanager.domain.repository.IndexReadinessRepository
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
 * [FileIndexRepository] to its Room-backed implementation. Known version hops carry a REAL
 * migration (the index holds expensive-to-recompute content/perceptual fingerprints — a wipe
 * forces a full re-survey AND a full photo re-analysis); only unknown/legacy hops fall back to
 * destructive recreation, after which the co-located index_state row correctly reads EMPTY.
 */
@Module
@InstallIn(SingletonComponent::class)
object IndexModule {

    /**
     * v4 → v5: adds the stacked-perceptual columns (phash/ahash), the quick head+tail hash
     * column, and the hot dedup/analytics query indexes — WITHOUT dropping the table, so the
     * fingerprints already computed on-device survive the upgrade. Index names must match
     * Room's generated `index_<table>_<column>` convention exactly.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE file_index ADD COLUMN quickHash TEXT")
            db.execSQL("ALTER TABLE file_index ADD COLUMN phash INTEGER")
            db.execSQL("ALTER TABLE file_index ADD COLUMN ahash INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_index_sizeBytes` ON `file_index` (`sizeBytes`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_index_contentHash` ON `file_index` (`contentHash`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_index_perceptualHash` ON `file_index` (`perceptualHash`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_index_lastSeenGeneration` ON `file_index` (`lastSeenGeneration`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_index_typeName` ON `file_index` (`typeName`)")
        }
    }

    /**
     * v5 → v6: records pipeline/delta state in the co-located index_state row and adds an
     * idempotent duplicate-decision table. This is an in-place migration; the expensive index and
     * fingerprints survive, and duplicate notifications gain restart-proof suppression.
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE index_state ADD COLUMN checkpointJson TEXT")
            db.execSQL("ALTER TABLE index_state ADD COLUMN mediaStoreVersion TEXT")
            db.execSQL("ALTER TABLE index_state ADD COLUMN lastMediaStoreGeneration INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE index_state ADD COLUMN lastDeltaSyncAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dedup_decision` (" +
                    "`decisionKey` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`newPath` TEXT NOT NULL, " +
                    "`existingPaths` TEXT NOT NULL, " +
                    "`algorithmVersion` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`decisionKey`))",
            )
        }
    }

    @Provides
    @Singleton
    fun provideFileIndexDatabase(
        @ApplicationContext context: Context,
    ): FileIndexDatabase =
        Room.databaseBuilder(context, FileIndexDatabase::class.java, "jupiter_index.db")
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .build()

    @Provides
    fun provideFileIndexDao(database: FileIndexDatabase): FileIndexDao =
        database.fileIndexDao()

    @Provides
    fun provideIndexStateDao(database: FileIndexDatabase): IndexStateDao =
        database.indexStateDao()

    @Provides
    fun provideDedupDecisionDao(database: FileIndexDatabase): DedupDecisionDao =
        database.dedupDecisionDao()
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
    abstract fun bindIndexReadinessRepository(
        impl: IndexReadinessRepositoryImpl,
    ): IndexReadinessRepository

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

    @Binds
    abstract fun bindPathPolicy(impl: DefaultPathPolicy): PathPolicy
}
