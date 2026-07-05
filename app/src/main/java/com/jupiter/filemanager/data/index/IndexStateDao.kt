package com.jupiter.filemanager.data.index

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Data-access for the per-volume [IndexState] life-cycle row. */
@Dao
interface IndexStateDao {

    @Upsert
    suspend fun upsert(state: IndexState)

    @Query("SELECT * FROM index_state WHERE volumeId = :volumeId")
    suspend fun get(volumeId: String): IndexState?

    @Query("SELECT * FROM index_state WHERE volumeId = :volumeId")
    fun observe(volumeId: String): Flow<IndexState?>

    @Query("DELETE FROM index_state WHERE volumeId = :volumeId")
    suspend fun delete(volumeId: String)
}
