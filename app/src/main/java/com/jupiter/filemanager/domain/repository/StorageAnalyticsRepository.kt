package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.StorageOverview
import kotlinx.coroutines.flow.Flow

/**
 * Provides storage analytics: an overview of how space is used, discovery of
 * large files, and detection of duplicate files for cleanup purposes.
 */
interface StorageAnalyticsRepository {

    /**
     * Produces a categorized overview of the primary storage volume, grouping
     * usage by file type into [com.jupiter.filemanager.domain.model.CategoryUsage] entries.
     */
    suspend fun storageOverview(): AppResult<StorageOverview>

    /**
     * Streams files under [rootPath] whose size is at least [minSizeBytes],
     * emitting each match as it is found.
     */
    fun findLargeFiles(rootPath: String, minSizeBytes: Long): Flow<FileItem>

    /**
     * Streams groups of duplicate files discovered under [rootPath]. Files are
     * grouped first by size and then by content hash; each emitted
     * [DuplicateGroup] contains two or more identical files.
     */
    fun findDuplicates(rootPath: String): Flow<DuplicateGroup>
}
