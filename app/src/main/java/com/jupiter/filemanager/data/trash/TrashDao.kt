package com.jupiter.filemanager.data.trash

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for the Recycle Bin.
 *
 * Mutating members are `suspend`; observable reads return [Flow] so the UI
 * refreshes automatically as items are trashed, restored, or purged.
 */
@Dao
interface TrashDao {

    /** Inserts a newly trashed entry. */
    @Insert
    suspend fun insert(entry: TrashEntry)

    /** Observes all trashed entries, most-recently deleted first. */
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<TrashEntry>>

    /** Returns the entry with the given [id], or null when absent. */
    @Query("SELECT * FROM trash WHERE id = :id")
    suspend fun getById(id: String): TrashEntry?

    /** Deletes the row with the given [id]. */
    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun delete(id: String)

    /** Removes every trash row. */
    @Query("DELETE FROM trash")
    suspend fun clearAll()

    /** Observes the number of items currently in the Recycle Bin. */
    @Query("SELECT COUNT(*) FROM trash")
    fun count(): Flow<Int>

    /** Returns every trash row as a one-shot snapshot (used when emptying). */
    @Query("SELECT * FROM trash")
    suspend fun getAll(): List<TrashEntry>
}
