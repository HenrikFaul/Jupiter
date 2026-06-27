package com.jupiter.filemanager.domain.model

/**
 * Direction of a peer-to-peer / network transfer.
 */
enum class TransferDirection { SEND, RECEIVE }

/**
 * Lifecycle status of a single transfer.
 */
enum class TransferStatus { PENDING, IN_PROGRESS, PAUSED, COMPLETED, FAILED }

/**
 * Represents a single file transfer task (nearby / Wi-Fi / cloud).
 *
 * No live transfer backend is wired yet; the repository starts empty and this
 * model is used purely to render honest empty / progress states.
 */
data class TransferTask(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val transferredBytes: Long,
    val status: TransferStatus,
    val direction: TransferDirection,
    val peerName: String? = null,
) {
    /** Completion fraction in the range [0f, 1f]. */
    val fraction: Float
        get() = if (sizeBytes <= 0) 0f else (transferredBytes.toFloat() / sizeBytes).coerceIn(0f, 1f)
}
