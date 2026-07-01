package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.IndexStats
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of [FileIndexRepository].
 *
 * All blocking IO is confined to [ioDispatcher]; observable reads are moved off
 * the collecting thread via [flowOn].
 */
@Singleton
class FileIndexRepositoryImpl @Inject constructor(
    private val dao: FileIndexDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FileIndexRepository {

    override fun observeChildren(parentPath: String): Flow<List<FileItem>> =
        dao.childrenOf(parentPath)
            .map { entries -> entries.map(::toFileItem) }
            .flowOn(ioDispatcher)

    override fun search(query: String): Flow<List<FileItem>> {
        val pattern = "%" + query + "%"
        return dao.searchByName(pattern)
            .map { entries -> entries.map(::toFileItem) }
            .flowOn(ioDispatcher)
    }

    override suspend fun replaceChildren(parentPath: String, items: List<FileItem>) =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            dao.upsertAll(items.map { toEntry(it, now) })
            dao.deleteStale(parentPath, items.map { it.path })
        }

    override suspend fun upsert(items: List<FileItem>) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        dao.upsertAll(items.map { toEntry(it, now) })
    }

    override suspend fun hashForUnchanged(
        path: String,
        sizeBytes: Long,
        lastModified: Long,
    ): String? = withContext(ioDispatcher) {
        dao.hashIfUnchanged(path, sizeBytes, lastModified)
    }

    override suspend fun putHash(item: FileItem, hash: String) = withContext(ioDispatcher) {
        val existing = dao.getByPath(item.path)
        val base = existing ?: toEntry(item, System.currentTimeMillis())
        dao.upsertAll(listOf(base.copy(contentHash = hash, indexedAt = System.currentTimeMillis())))
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        dao.clear()
    }

    override fun stats(): Flow<IndexStats> =
        dao.count()
            .map { count ->
                IndexStats(indexedCount = count, lastIndexedAt = dao.maxIndexedAt() ?: 0L)
            }
            .flowOn(ioDispatcher)

    // --- Mapping -------------------------------------------------------------

    private fun toFileItem(entry: FileIndexEntry): FileItem = FileItem(
        path = entry.path,
        name = entry.name,
        isDirectory = entry.isDirectory,
        sizeBytes = entry.sizeBytes,
        lastModified = entry.lastModified,
        type = runCatching { FileType.valueOf(entry.typeName) }.getOrDefault(FileType.OTHER),
        extension = entry.extension,
        mimeType = null,
        isHidden = entry.name.startsWith("."),
        childCount = null,
    )

    private fun toEntry(item: FileItem, indexedAt: Long): FileIndexEntry = FileIndexEntry(
        path = item.path,
        parentPath = item.parentPath ?: "",
        name = item.name,
        sizeBytes = item.sizeBytes,
        lastModified = item.lastModified,
        typeName = item.type.name,
        isDirectory = item.isDirectory,
        extension = item.extension,
        contentHash = null,
        indexedAt = indexedAt,
    )
}
