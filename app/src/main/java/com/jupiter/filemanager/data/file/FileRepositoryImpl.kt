package com.jupiter.filemanager.data.file

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.storage.StorageVolumeProvider
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.SortDirection
import com.jupiter.filemanager.domain.model.SortField
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.model.StorageVolumeInfo
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Default [FileRepository] implementation backed by the local file system.
 *
 * Responsibilities split:
 * - Raw enumeration and `File` -> [FileItem] mapping are delegated to
 *   [FileSystemDataSource].
 * - Long-running copy/move operations are delegated to [FileOperationsManager].
 * - Volume metadata is delegated to [StorageVolumeProvider].
 *
 * Sorting and filtering of listings are applied here so the rest of the data
 * source remains a thin, side-effect-free mapper. All blocking IO is confined to
 * the injected [@IoDispatcher][IoDispatcher].
 */
@Singleton
class FileRepositoryImpl @Inject constructor(
    private val dataSource: FileSystemDataSource,
    private val operationsManager: FileOperationsManager,
    private val volumeProvider: StorageVolumeProvider,
    private val indexRepository: FileIndexRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : FileRepository {

    /**
     * Long-lived scope for best-effort, fire-and-forget index self-heal writes so a
     * directory listing is never delayed by a DB write. Survives individual calls (the
     * repository is a [Singleton]); a SupervisorJob keeps one failed write from
     * cancelling others.
     */
    private val indexScope = CoroutineScope(SupervisorJob() + dispatcher)

    override fun observeDirectory(
        path: String,
        sort: SortOption,
        filter: FilterOption,
    ): Flow<AppResult<List<FileItem>>> = flow {
        emit(listFiles(path, sort, filter))
    }.flowOn(dispatcher)

    override suspend fun listFiles(
        path: String,
        sort: SortOption,
        filter: FilterOption,
    ): AppResult<List<FileItem>> = withContext(dispatcher) {
        try {
            val directory = File(path)
            if (!directory.exists()) {
                return@withContext AppResult.Failure(AppError.NotFound(path))
            }
            if (!directory.isDirectory) {
                return@withContext AppResult.Failure(
                    AppError.Io("Not a directory: " + path),
                )
            }
            if (!directory.canRead()) {
                return@withContext AppResult.Failure(AppError.AccessDenied(path))
            }

            val raw = dataSource.listDirectory(path)
            // Self-heal the index for this directory in the BACKGROUND so the returned
            // listing is never delayed by a DB write. Only persist a NON-EMPTY listing: an
            // empty result can be a transient read failure, and pruning the index on it
            // would wipe good cached rows. Skip survey-excluded directories (app-private
            // sandboxes, thumbnail/trash caches) so browsing them never sneaks their files
            // into the index — that would let index-served duplicate/large-file lists
            // surface entries the live scan deliberately excludes. Best-effort.
            if (raw.isNotEmpty() && isIndexableDir(path)) {
                indexScope.launch { runCatching { indexRepository.replaceChildren(path, raw) } }
            }
            val processed = applySortAndFilter(raw, sort, filter)
            AppResult.Success(processed)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.AccessDenied(path))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Io(e.message ?: "IO error listing " + path, e))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Unknown error listing " + path, e))
        }
    }

    override suspend fun listFromIndex(
        path: String,
        sort: SortOption,
        filter: FilterOption,
    ): List<FileItem> = withContext(dispatcher) {
        // Snapshot the indexed children (best-effort); apply the same sort/filter as a
        // disk listing so the result is a drop-in fallback. Any failure yields empty.
        val children = runCatching { indexRepository.observeChildren(path).first() }
            .getOrDefault(emptyList())
        if (children.isEmpty()) emptyList() else applySortAndFilter(children, sort, filter)
    }

    override suspend fun getFile(path: String): AppResult<FileItem> = withContext(dispatcher) {
        try {
            val file = File(path)
            if (!file.exists()) {
                AppResult.Failure(AppError.NotFound(path))
            } else {
                AppResult.Success(dataSource.toFileItem(file))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.AccessDenied(path))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Unknown error reading " + path, e))
        }
    }

    override suspend fun createFolder(
        parentPath: String,
        name: String,
    ): AppResult<FileItem> = withContext(dispatcher) {
        try {
            val parent = File(parentPath)
            if (!parent.exists()) {
                return@withContext AppResult.Failure(AppError.NotFound(parentPath))
            }
            if (!parent.isDirectory) {
                return@withContext AppResult.Failure(
                    AppError.Io("Parent is not a directory: " + parentPath),
                )
            }
            val target = File(parent, name)
            if (target.exists()) {
                return@withContext AppResult.Failure(AppError.AlreadyExists(name))
            }
            if (!target.mkdirs()) {
                return@withContext AppResult.Failure(
                    AppError.Io("Failed to create folder: " + target.absolutePath),
                )
            }
            val created = dataSource.toFileItem(target)
            // Keep the live index in step so the new folder is browsable/searchable
            // without waiting for the next full rebuild. Best-effort; never fails the op.
            runCatching { indexRepository.indexFile(created) }
            AppResult.Success(created)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.AccessDenied(parentPath))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Io(e.message ?: "IO error creating folder", e))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Unknown error creating folder", e))
        }
    }

    override suspend fun rename(
        item: FileItem,
        newName: String,
    ): AppResult<FileItem> = withContext(dispatcher) {
        try {
            val source = File(item.path)
            if (!source.exists()) {
                return@withContext AppResult.Failure(AppError.NotFound(item.path))
            }
            val target = File(source.parentFile, newName)
            if (target.exists()) {
                return@withContext AppResult.Failure(AppError.AlreadyExists(newName))
            }
            if (!source.renameTo(target)) {
                return@withContext AppResult.Failure(
                    AppError.Io("Failed to rename: " + item.path),
                )
            }
            val renamed = dataSource.toFileItem(target)
            // Reflect the rename in the index (drop the old path/subtree, index the new
            // location) so stale entries don't linger until the next rebuild. Best-effort.
            runCatching { indexRepository.onMovedOrRenamed(item.path, renamed) }
            AppResult.Success(renamed)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.AccessDenied(item.path))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Io(e.message ?: "IO error during rename", e))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Unknown error during rename", e))
        }
    }

    override suspend fun delete(items: List<FileItem>): AppResult<Unit> =
        operationsManager.delete(items)

    override fun copy(
        items: List<FileItem>,
        destinationPath: String,
    ): Flow<FileOperationProgress> = operationsManager.copy(items, destinationPath)

    override fun move(
        items: List<FileItem>,
        destinationPath: String,
    ): Flow<FileOperationProgress> = operationsManager.move(items, destinationPath)

    override fun search(
        rootPath: String,
        filter: FilterOption,
    ): Flow<FileItem> = flow {
        val query = filter.query.trim().lowercase()
        for (file in dataSource.walkTopDown(rootPath)) {
            if (!currentCoroutineContext().isActive) return@flow

            // Skip the root itself; only surface descendants.
            if (file.absolutePath == File(rootPath).absolutePath) continue

            // Never surface a vendor recycle-bin / app-private / thumbnail file (e.g. Samsung
            // `Android/.Trash/…`): its bytes don't round-trip through Java's path encoding (or the
            // vendor purges it), so opening it errors with "Not found". This filesystem walk is the
            // single boundary behind BOTH the Recent tab and global search — the last two surfaces
            // that still surfaced these entries. (Category/album/index/analytics reads already exclude
            // them.) Every descendant of an excluded dir is itself excluded, so each is skipped too.
            if (com.jupiter.filemanager.core.util.StorageExclusions.isExcluded(file.absolutePath)) {
                continue
            }

            val item = try {
                dataSource.toFileItem(file)
            } catch (e: SecurityException) {
                continue
            } catch (e: RuntimeException) {
                continue
            }

            if (matchesFilter(item, query, filter)) {
                emit(item)
            }
        }
    }.flowOn(dispatcher)

    override fun rootDirectory(): String = volumeProvider.primaryRoot()

    override fun storageVolumes(): List<StorageVolumeInfo> = volumeProvider.volumes()

    /**
     * True unless [path] is (or lies under) a directory the background survey also
     * excludes — app-private `Android/data`/`Android/obb` sandboxes and thumbnail/trash
     * caches. Keeps the browse-time self-heal consistent with the survey so index-served
     * duplicate/large-file lists never surface files a live scan would skip.
     */
    private fun isIndexableDir(path: String): Boolean =
        !com.jupiter.filemanager.core.util.StorageExclusions.isExcluded(path)

    // region Sorting & filtering ----------------------------------------------

    /**
     * Applies [filter] then orders the result according to [sort]. Hidden entries,
     * a name [FilterOption.query] substring, and an optional [FilterOption.typeFilter]
     * are all honoured before ordering.
     */
    private fun applySortAndFilter(
        items: List<FileItem>,
        sort: SortOption,
        filter: FilterOption,
    ): List<FileItem> {
        val query = filter.query.trim().lowercase()
        val filtered = items.filter { matchesFilter(it, query, filter) }
        return sortItems(filtered, sort)
    }

    /**
     * Returns true when [item] satisfies the supplied [filter]. [query] is the
     * pre-lowercased, pre-trimmed name substring (empty means "no name filter").
     */
    private fun matchesFilter(
        item: FileItem,
        query: String,
        filter: FilterOption,
    ): Boolean {
        if (!filter.showHidden && item.isHidden) return false
        if (query.isNotEmpty() && !item.name.lowercase().contains(query)) return false
        val typeFilter = filter.typeFilter
        if (typeFilter != null && item.type != typeFilter) return false
        return true
    }

    /**
     * Orders [items] per [sort]: optionally grouping directories first, comparing
     * by the selected [SortField], and reversing for [SortDirection.DESCENDING].
     * Folders-first grouping is preserved even under descending direction.
     */
    private fun sortItems(items: List<FileItem>, sort: SortOption): List<FileItem> {
        val fieldComparator: Comparator<FileItem> = when (sort.field) {
            SortField.NAME ->
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.SIZE ->
                compareBy { it.sizeBytes }
            SortField.DATE_MODIFIED ->
                compareBy { it.lastModified }
            SortField.TYPE ->
                compareBy<FileItem> { it.extension.lowercase() }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }

        val directed: Comparator<FileItem> =
            if (sort.direction == SortDirection.DESCENDING) fieldComparator.reversed()
            else fieldComparator

        // Stable tie-breaker on name keeps ordering deterministic for equal keys.
        val withTieBreak: Comparator<FileItem> =
            directed.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

        val finalComparator: Comparator<FileItem> =
            if (sort.foldersFirst) {
                // Directories (isDirectory == true) sort before files regardless of direction.
                compareByDescending<FileItem> { it.isDirectory }.then(withTieBreak)
            } else {
                withTieBreak
            }

        return items.sortedWith(finalComparator)
    }

    // endregion

}
