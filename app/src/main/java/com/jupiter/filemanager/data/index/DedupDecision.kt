package com.jupiter.filemanager.data.index

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Persisted duplicate-arrival decision. The [decisionKey] is canonical for the unordered file
 * pair/group + decision type + algorithm version, so the same pair cannot alert again after a
 * process restart or another MediaStore observer burst.
 */
@Entity(tableName = "dedup_decision")
data class DedupDecision(
    @PrimaryKey val decisionKey: String,
    val kind: String,
    val newPath: String,
    val existingPaths: String,
    val algorithmVersion: Int,
    val createdAt: Long,
)

@Dao
interface DedupDecisionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(decision: DedupDecision): Long

    @Query("SELECT COUNT(*) FROM dedup_decision WHERE decisionKey = :decisionKey")
    suspend fun exists(decisionKey: String): Int
}
