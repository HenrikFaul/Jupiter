package com.jupiter.filemanager.domain.model

/** The kind of long-running file operation a [FileOperationProgress] describes. */
enum class FileOperationType { COPY, MOVE, DELETE, COMPRESS, EXTRACT }

/** Lifecycle state of a file operation. */
enum class OperationState { RUNNING, COMPLETED, FAILED, CANCELLED }

/**
 * Snapshot of an in-flight (or finished) file operation, emitted repeatedly via a Flow
 * so the UI can render progress, completion, failure, or cancellation.
 */
data class FileOperationProgress(
    val type: FileOperationType,
    val state: OperationState,
    val processedItems: Int = 0,
    val totalItems: Int = 0,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val currentFileName: String = "",
    val errorMessage: String? = null,
) {
    /** Progress as a fraction in [0f, 1f]; 0f when the total size is unknown. */
    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}
