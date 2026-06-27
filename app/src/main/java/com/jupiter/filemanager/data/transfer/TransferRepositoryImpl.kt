package com.jupiter.filemanager.data.transfer

import com.jupiter.filemanager.domain.model.TransferTask
import com.jupiter.filemanager.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory [TransferRepository] implementation.
 *
 * No live transfer backend (nearby / Wi-Fi / cloud) is wired yet, so this starts
 * empty and exposes an honest empty state rather than fabricating fake live
 * progress. The backing [MutableStateFlow] is kept so that a future real
 * transfer engine can push tasks into it without changing the public contract.
 */
@Singleton
class TransferRepositoryImpl @Inject constructor() : TransferRepository {

    private val transfers = MutableStateFlow<List<TransferTask>>(emptyList())

    override fun observeTransfers(): Flow<List<TransferTask>> = transfers.asStateFlow()

    override suspend fun clearCompleted() {
        transfers.update { current ->
            current.filterNot { it.status == com.jupiter.filemanager.domain.model.TransferStatus.COMPLETED }
        }
    }
}
