package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.model.StorageVolumeInfo
import kotlinx.coroutines.flow.Flow

/**
 * Primary abstraction over the device file system used throughout Jupiter.
 *
 * Implementations are expected to perform all blocking IO on a background
 * dispatcher; callers (ViewModels) may invoke these members from
 * `viewModelScope` without additional thread management.
 */
interface FileRepository {

    /**
     * Observes the contents of [path], re-emitting whenever a fresh listing is
     * produced. Results are sorted and filtered according to [sort] and
     * [filter].
     */
    fun observeDirectory(
        path: String,
        sort: SortOption,
        filter: FilterOption,
    ): Flow<AppResult<List<FileItem>>>

    /**
     * Returns a one-shot listing of [path], sorted and filtered according to
     * [sort] and [filter].
     *
     * On a successful disk read the raw listing is also persisted to the file index
     * (best-effort), so browsing keeps the index complete and current for the visited
     * directory without waiting for a full rebuild.
     */
    suspend fun listFiles(
        path: String,
        sort: SortOption,
        filter: FilterOption,
    ): AppResult<List<FileItem>>

    /**
     * Returns the children of [path] straight from the persistent index (sorted and
     * filtered like [listFiles]), or an empty list when the directory is not indexed
     * or the index cannot be read. Used as an instant/offline fallback when a disk
     * read fails; never throws.
     */
    suspend fun listFromIndex(
        path: String,
        sort: SortOption,
        filter: FilterOption,
    ): List<FileItem>

    /** Resolves a single [FileItem] for the given [path]. */
    suspend fun getFile(path: String): AppResult<FileItem>

    /** Creates a new folder named [name] inside [parentPath]. */
    suspend fun createFolder(parentPath: String, name: String): AppResult<FileItem>

    /** Renames [item] to [newName], returning the updated [FileItem]. */
    suspend fun rename(item: FileItem, newName: String): AppResult<FileItem>

    /** Permanently deletes the supplied [items]. */
    suspend fun delete(items: List<FileItem>): AppResult<Unit>

    /**
     * Copies [items] into [destinationPath], emitting incremental
     * [FileOperationProgress] updates. The flow completes when the operation
     * finishes and supports cancellation via flow collection cancellation.
     */
    fun copy(items: List<FileItem>, destinationPath: String): Flow<FileOperationProgress>

    /**
     * Moves [items] into [destinationPath], emitting incremental
     * [FileOperationProgress] updates.
     */
    fun move(items: List<FileItem>, destinationPath: String): Flow<FileOperationProgress>

    /**
     * Recursively searches under [rootPath], emitting each matching [FileItem]
     * as it is discovered. Matching is governed by [filter].
     */
    fun search(rootPath: String, filter: FilterOption): Flow<FileItem>

    /** Returns the absolute path of the primary storage root. */
    fun rootDirectory(): String

    /** Returns metadata describing all available storage volumes. */
    fun storageVolumes(): List<StorageVolumeInfo>
}
