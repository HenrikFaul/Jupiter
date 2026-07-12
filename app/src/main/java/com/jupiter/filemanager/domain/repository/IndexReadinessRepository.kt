package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.domain.model.StorageReadiness
import kotlinx.coroutines.flow.Flow

interface IndexReadinessRepository {
    fun observeReadiness(): Flow<StorageReadiness>
    suspend fun currentReadiness(): StorageReadiness
}
