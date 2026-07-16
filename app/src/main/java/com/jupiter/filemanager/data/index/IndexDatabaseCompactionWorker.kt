package com.jupiter.filemanager.data.index

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jupiter.filemanager.di.IoDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * One-time physical compaction after the v10 TEXT -> BLOB migration.
 *
 * Updating rows and dropping the old TEXT index makes pages reusable, but SQLite does not shrink
 * the database file automatically. VACUUM is deliberately deferred out of the Room migration
 * transaction and WorkManager retries if another index transaction temporarily owns the database.
 */
@HiltWorker
class IndexDatabaseCompactionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: FileIndexDatabase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(dispatcher) {
        val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getInt(KEY_COMPACTED_SCHEMA, 0) >= TARGET_SCHEMA_VERSION) {
            return@withContext Result.success()
        }
        try {
            val sqlite = database.openHelper.writableDatabase
            // Return WAL pages first, then rebuild the main file without the retired TEXT payloads
            // and old contentHash index. No user file is read and no index row is discarded.
            sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                while (cursor.moveToNext()) Unit
            }
            sqlite.execSQL("VACUUM")
            sqlite.execSQL("PRAGMA optimize")
            preferences.edit().putInt(KEY_COMPACTED_SCHEMA, TARGET_SCHEMA_VERSION).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "jupiscan-index-compact-v10"
        internal const val TARGET_SCHEMA_VERSION = 10
        private const val PREFERENCES = "jupiscan_index_maintenance"
        private const val KEY_COMPACTED_SCHEMA = "compacted_schema"
    }
}
