package com.jupiter.filemanager.data.index

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

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
    @ColumnInfo(defaultValue = "'PENDING'")
    val deliveryState: String = DedupDeliveryState.PENDING.name,
    @ColumnInfo(defaultValue = "0")
    val deliveryAttempts: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val lastDeliveryAttemptAt: Long = 0,
    val deliveredAt: Long? = null,
    val lastDeliveryFailure: String? = null,
)

/** Durable notification-delivery lifecycle for a duplicate decision. */
enum class DedupDeliveryState {
    PENDING,
    DELIVERING,
    BLOCKED,
    FAILED,
    DELIVERED,
}

@Dao
interface DedupDecisionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(decision: DedupDecision): Long

    @Query("SELECT COUNT(*) FROM dedup_decision WHERE decisionKey = :decisionKey")
    suspend fun exists(decisionKey: String): Int

    @Query("SELECT * FROM dedup_decision WHERE decisionKey = :decisionKey")
    suspend fun get(decisionKey: String): DedupDecision?

    @Query(
        "SELECT * FROM dedup_decision " +
            "WHERE deliveryState != 'DELIVERED' " +
            "AND (deliveryState != 'DELIVERING' OR lastDeliveryAttemptAt <= :staleBefore) " +
            "ORDER BY createdAt ASC LIMIT :limit",
    )
    suspend fun deliveryCandidates(staleBefore: Long, limit: Int): List<DedupDecision>

    @Query(
        "UPDATE dedup_decision SET deliveryState = 'DELIVERING', " +
            "deliveryAttempts = deliveryAttempts + 1, lastDeliveryAttemptAt = :attemptedAt, " +
            "lastDeliveryFailure = NULL " +
            "WHERE decisionKey = :decisionKey AND deliveryState != 'DELIVERED' " +
            "AND (deliveryState != 'DELIVERING' OR lastDeliveryAttemptAt <= :staleBefore)",
    )
    suspend fun claimDelivery(decisionKey: String, attemptedAt: Long, staleBefore: Long): Int

    @Query(
        "UPDATE dedup_decision SET deliveryState = 'DELIVERED', deliveredAt = :deliveredAt, " +
            "lastDeliveryFailure = NULL WHERE decisionKey IN (:decisionKeys) " +
            "AND deliveryState = 'DELIVERING'",
    )
    suspend fun markDelivered(decisionKeys: List<String>, deliveredAt: Long)

    @Query(
        "UPDATE dedup_decision SET deliveryState = :state, lastDeliveryFailure = :failure " +
            "WHERE decisionKey IN (:decisionKeys) AND deliveryState = 'DELIVERING'",
    )
    suspend fun releaseDelivery(decisionKeys: List<String>, state: String, failure: String?)

    /**
     * Atomically claims a bounded retry batch. The conditional row update prevents two observer
     * bursts (or foreground retry + observer) from publishing the same decision concurrently.
     * A process death leaves DELIVERING rows recoverable after [staleBefore].
     */
    @Transaction
    suspend fun claimDeliveryBatch(
        attemptedAt: Long,
        staleBefore: Long,
        limit: Int,
    ): List<DedupDecision> = deliveryCandidates(staleBefore, limit).filter { candidate ->
        claimDelivery(candidate.decisionKey, attemptedAt, staleBefore) == 1
    }
}
