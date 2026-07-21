package com.jupiter.filemanager.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jupiter.filemanager.core.util.DefaultPathPolicy
import com.jupiter.filemanager.core.util.PathPolicy
import com.jupiter.filemanager.data.index.AndroidDuplicateNotificationPublisher
import com.jupiter.filemanager.data.index.ArrivalInspector
import com.jupiter.filemanager.data.index.DedupCheckpointStore
import com.jupiter.filemanager.data.index.DedupDecisionDao
import com.jupiter.filemanager.data.index.DuplicateDetector
import com.jupiter.filemanager.data.index.DuplicateNotificationPublisher
import com.jupiter.filemanager.data.index.CompactMetadataCodec
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
 * forces a full re-survey AND a full photo re-analysis). Every supported hop is explicit and no
 * destructive fallback is enabled.
 */
@Module
@InstallIn(SingletonComponent::class)
object IndexModule {

    /** v1 → v2: preserve the original index while adding its co-located lifecycle authority. */
    internal val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE file_index ADD COLUMN lastSeenGeneration INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `index_state` (" +
                    "`volumeId` TEXT NOT NULL, `metadataStatus` TEXT NOT NULL, " +
                    "`activeScanGeneration` INTEGER NOT NULL, " +
                    "`lastCompleteGeneration` INTEGER NOT NULL, `scanStartedAt` INTEGER NOT NULL, " +
                    "`scanCompletedAt` INTEGER NOT NULL, `filesSeen` INTEGER NOT NULL, " +
                    "`lastError` TEXT, PRIMARY KEY(`volumeId`))",
            )
        }
    }

    /** v2 → v3: first-generation image dHash, nullable so existing rows backfill in place. */
    internal val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE file_index ADD COLUMN perceptualHash INTEGER")
        }
    }

    /** v3 → v4: shared type-aware structural hash, again additive and lossless. */
    internal val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE file_index ADD COLUMN structuralHash INTEGER")
        }
    }

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

    /**
     * v6 → v7: turns a duplicate decision into a durable notification outbox. Historical rows are
     * intentionally PENDING: they are collapsed into a single retry summary, so an alert that was
     * silently blocked by Android before this migration is not lost forever or replayed as spam.
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE dedup_decision ADD COLUMN deliveryState TEXT NOT NULL " +
                    "DEFAULT 'PENDING'",
            )
            db.execSQL(
                "ALTER TABLE dedup_decision ADD COLUMN deliveryAttempts INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE dedup_decision ADD COLUMN lastDeliveryAttemptAt INTEGER NOT NULL " +
                    "DEFAULT 0",
            )
            db.execSQL("ALTER TABLE dedup_decision ADD COLUMN deliveredAt INTEGER")
            db.execSQL("ALTER TABLE dedup_decision ADD COLUMN lastDeliveryFailure TEXT")
        }
    }

    /**
     * v7 → v8: prior backfill code accidentally persisted the `UNHASHABLE` marker for *transient*
     * BitmapFactory/provider/database failures. Those rows were then excluded forever from the
     * visual-match pipeline, producing a false final `Similar photos (0)` on real galleries. Clear
     * only those image descriptors once so the corrected worker retries them. Genuine corrupt files
     * are harmless: the current source explicitly writes the sentinel again after this one retry.
     */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE file_index SET perceptualHash = NULL, phash = NULL, ahash = NULL " +
                    "WHERE isDirectory = 0 AND typeName = 'IMAGE' " +
                    "AND perceptualHash = -9223372036854775808",
            )
        }
    }

    /**
     * v8 → v9: media similarity moves from one unsafe 64-bit thumbnail hash to a versioned,
     * ordered multi-sample signature plus duration/page-count gate. Existing text/archive/image
     * descriptors stay intact. Only VIDEO/PDF/AUDIO structural descriptors are requeued because
     * comparing their v1 values with v2 would recreate the reported false-positive clusters.
     */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE file_index ADD COLUMN structuralSignature TEXT")
            db.execSQL("ALTER TABLE file_index ADD COLUMN structuralExtent INTEGER")
            db.execSQL(
                "ALTER TABLE file_index ADD COLUMN structuralVersion INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "UPDATE file_index SET structuralHash = NULL, structuralSignature = NULL, " +
                    "structuralExtent = NULL, structuralVersion = 0 " +
                    "WHERE isDirectory = 0 AND typeName IN ('VIDEO', 'PDF', 'AUDIO')",
            )
        }
    }

    /**
     * v9 -> v10: compact high-volume duplicate metadata without re-reading any user file.
     * Production SHA-1 strings become 20-byte BLOBs, and comma-separated media vectors become
     * 8*N-byte BLOBs. Invalid/non-standard legacy tokens are deliberately retained as TEXT so the
     * migration is lossless. One packed geometry INTEGER supports image/video aspect-ratio vetoes.
     */
    internal val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE file_index ADD COLUMN contentDigest BLOB")
            db.execSQL("ALTER TABLE file_index ADD COLUMN quickDigest BLOB")
            db.execSQL("ALTER TABLE file_index ADD COLUMN structuralSignatureBlob BLOB")
            db.execSQL("ALTER TABLE file_index ADD COLUMN visualGeometry INTEGER")

            migrateContentDigests(db)
            migrateQuickDigests(db)
            migrateMediaSignatures(db)

            // New production lookups use the compact digest. The old TEXT column remains only as
            // a lossless fallback, so retaining its index would waste pages on almost-all NULLs.
            db.execSQL("DROP INDEX IF EXISTS `index_file_index_contentHash`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_file_index_contentDigest` " +
                    "ON `file_index` (`contentDigest`)",
            )
        }
    }

    /**
     * v10 -> v11: make the image stack explicitly versioned. This is one additive small INTEGER;
     * no user file is read during database open. Fully valid v10 rows keep their ready state,
     * while rows missing the v10-added geometry remain version 0 and are truthfully requeued so
     * the aspect-ratio veto cannot be reported ready while silently inactive.
     */
    internal val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE file_index ADD COLUMN perceptualVersion INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "UPDATE file_index SET perceptualVersion = 1 WHERE isDirectory = 0 " +
                    "AND typeName = 'IMAGE' AND ((perceptualHash = -9223372036854775808 " +
                    "AND phash = -9223372036854775808 AND ahash = -9223372036854775808) " +
                    "OR (perceptualHash IS NOT NULL AND phash IS NOT NULL AND ahash IS NOT NULL " +
                    "AND perceptualHash != -9223372036854775808 " +
                    "AND phash != -9223372036854775808 AND ahash != -9223372036854775808 " +
                    "AND visualGeometry IS NOT NULL))",
            )
        }
    }

    private fun migrateContentDigests(db: SupportSQLiteDatabase) {
        val update = db.compileStatement(
            "UPDATE file_index SET contentDigest = ?, contentHash = NULL WHERE path = ?",
        )
        db.query("SELECT path, contentHash FROM file_index WHERE contentHash IS NOT NULL").use { cursor ->
            while (cursor.moveToNext()) {
                val digest = CompactMetadataCodec.sha1ToBytes(cursor.getString(1)) ?: continue
                update.clearBindings()
                update.bindBlob(1, digest)
                update.bindString(2, cursor.getString(0))
                update.executeUpdateDelete()
            }
        }
    }

    private fun migrateQuickDigests(db: SupportSQLiteDatabase) {
        val update = db.compileStatement(
            "UPDATE file_index SET quickDigest = ?, quickHash = NULL WHERE path = ?",
        )
        db.query("SELECT path, quickHash FROM file_index WHERE quickHash IS NOT NULL").use { cursor ->
            while (cursor.moveToNext()) {
                val digest = CompactMetadataCodec.legacyQuickHashToBytes(cursor.getString(1))
                    ?: continue
                update.clearBindings()
                update.bindBlob(1, digest)
                update.bindString(2, cursor.getString(0))
                update.executeUpdateDelete()
            }
        }
    }

    private fun migrateMediaSignatures(db: SupportSQLiteDatabase) {
        val update = db.compileStatement(
            "UPDATE file_index SET structuralSignatureBlob = ?, structuralSignature = NULL " +
                "WHERE path = ?",
        )
        db.query(
            "SELECT path, structuralSignature FROM file_index " +
                "WHERE structuralVersion = 2 AND structuralSignature IS NOT NULL",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val signature = CompactMetadataCodec.legacyLongVectorToBlob(cursor.getString(1))
                    ?: continue
                update.clearBindings()
                update.bindBlob(1, signature)
                update.bindString(2, cursor.getString(0))
                update.executeUpdateDelete()
            }
        }
    }

    /** Single ordered source of truth shared by production and full-chain migration tests. */
    internal fun allMigrations(): Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
    )

    @Provides
    @Singleton
    fun provideFileIndexDatabase(
        @ApplicationContext context: Context,
    ): FileIndexDatabase =
        Room.databaseBuilder(context, FileIndexDatabase::class.java, "jupiter_index.db")
            .addMigrations(*allMigrations())
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
    abstract fun bindDuplicateNotificationPublisher(
        impl: AndroidDuplicateNotificationPublisher,
    ): DuplicateNotificationPublisher

    @Binds
    abstract fun bindMediaFingerprintSource(
        impl: AndroidMediaFingerprintSource,
    ): MediaFingerprintSource

    @Binds
    abstract fun bindStorageAccessGate(impl: StorageAccessManager): StorageAccessGate

    @Binds
    abstract fun bindPathPolicy(impl: DefaultPathPolicy): PathPolicy
}
