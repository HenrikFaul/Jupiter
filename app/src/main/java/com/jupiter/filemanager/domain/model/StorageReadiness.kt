package com.jupiter.filemanager.domain.model

import com.jupiter.filemanager.data.index.IndexStatus

enum class PipelineStatus {
    EMPTY,
    RUNNING,
    PARTIAL,
    COMPLETE,
    FAILED,
}

data class Coverage(
    val ready: Long,
    val total: Long?,
    val state: PipelineStatus,
)

/**
 * One read model for screens that need to decide whether storage data is complete, partial, or
 * stale. Metadata readiness comes from Room index_state; descriptor readiness is measured from
 * the index rows so Cleanup/Home/Analytics/Search can stop inventing separate completion rules.
 */
data class StorageReadiness(
    val metadata: Coverage,
    val exactHashes: Coverage,
    val imageDescriptors: Coverage,
    val structuralDescriptors: Coverage,
    val isStale: Boolean,
    val lastCompleteAtMillis: Long?,
) {
    companion object {
        fun metadataState(status: IndexStatus, hasCompleteGeneration: Boolean): PipelineStatus =
            when (status) {
                IndexStatus.EMPTY -> PipelineStatus.EMPTY
                IndexStatus.RUNNING -> if (hasCompleteGeneration) PipelineStatus.PARTIAL else PipelineStatus.RUNNING
                IndexStatus.COMPLETE -> PipelineStatus.COMPLETE
                IndexStatus.DIRTY -> PipelineStatus.PARTIAL
                IndexStatus.FAILED -> PipelineStatus.FAILED
            }
    }
}
