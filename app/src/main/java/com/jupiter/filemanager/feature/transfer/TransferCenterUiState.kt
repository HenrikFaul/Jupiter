package com.jupiter.filemanager.feature.transfer

import com.jupiter.filemanager.domain.model.TransferStatus
import com.jupiter.filemanager.domain.model.TransferTask

/**
 * Tabs available in the Transfer Center.
 *
 * [TRANSFERS] shows in-flight / queued transfers; [HISTORY] shows finished
 * (completed or failed) transfers.
 */
enum class TransferCenterTab { TRANSFERS, HISTORY }

/**
 * Immutable UI state for the Transfer Center screen.
 *
 * Produced by [TransferCenterViewModel] from the real [com.jupiter.filemanager.domain.repository.TransferRepository].
 * No live transfer backend is wired yet, so [transfers] start empty and the
 * screen surfaces honest empty states rather than fabricating progress. The
 * tabbed lists are derived from a single source list by [TransferStatus]:
 *
 * - [activeTransfers]: tasks that are pending, in progress, or paused.
 * - [historyTransfers]: tasks that have reached a terminal state (completed / failed).
 *
 * @property transfers the full, unfiltered task list straight from the repository.
 * @property selectedTab the currently selected tab in the UI.
 * @property isLoading true while the initial collection has not produced a value yet.
 */
data class TransferCenterUiState(
    val transfers: List<TransferTask> = emptyList(),
    val selectedTab: TransferCenterTab = TransferCenterTab.TRANSFERS,
    val isLoading: Boolean = true,
) {
    /** Transfers that are still pending, in progress, or paused. */
    val activeTransfers: List<TransferTask>
        get() = transfers.filter {
            it.status == TransferStatus.PENDING ||
                it.status == TransferStatus.IN_PROGRESS ||
                it.status == TransferStatus.PAUSED
        }

    /** Transfers that have finished (completed or failed). */
    val historyTransfers: List<TransferTask>
        get() = transfers.filter {
            it.status == TransferStatus.COMPLETED || it.status == TransferStatus.FAILED
        }

    /** True when there are completed/failed transfers that can be cleared. */
    val hasClearableHistory: Boolean
        get() = historyTransfers.isNotEmpty()
}
