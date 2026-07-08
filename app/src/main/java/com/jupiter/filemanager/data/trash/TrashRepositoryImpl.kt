package com.jupiter.filemanager.data.trash

import android.content.Context
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.file.FileSystemDataSource
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.TrashItem
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import com.jupiter.filemanager.domain.repository.TrashRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room- and filesystem-backed implementation of [TrashRepository].
 *
 * The Recycle Bin lives under [Context.getExternalFilesDir] with the sub-path
 * `"trash"` — inside `Android/data/<pkg>/files/`, which is app-private and
 * already excluded from every storage scan/index walk, so no scanner changes
 * are needed.
 *
 * **NO DATA LOSS is the cardinal rule.** [moveToTrash] only removes the source
 * once a complete copy is confirmed in the trash; every failure path leaves the
 * source intact and reports failure rather than risking a partial/hard delete.
 * All blocking IO is confined to [ioDispatcher].
 */
@Singleton
class TrashRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrashDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val indexRepository: FileIndexRepository,
    private val fileSystemDataSource: FileSystemDataSource,
) : TrashRepository {

    /**
     * Resolves the app-private trash root under
     * `Android/data/<pkg>/files/trash`, creating it if needed. Returns null
     * only when external app storage is currently unavailable.
     */
    private fun trashRoot(): File? =
        context.getExternalFilesDir(TRASH_DIR_NAME)?.also { it.mkdirs() }

    override suspend fun moveToTrash(item: FileItem): Boolean = withContext(ioDispatcher) {
        val source = File(item.path)
        if (!source.exists()) return@withContext false

        val root = trashRoot() ?: return@withContext false
        if (!root.isDirectory) return@withContext false

        val id = uniqueId(root, item)
        val target = File(root, id)

        // Physically relocate the file. On ANY failure the helper preserves the
        // source and returns false — the file is never left hard-deleted.
        val moved = movePath(source, target)
        if (!moved) return@withContext false

        // Persist the audit row only after the file is safely in the trash. If
        // the DB write itself fails, roll the move back so trash + index stay
        // consistent and the caller can preserve the source.
        try {
            dao.insert(
                TrashEntry(
                    id = id,
                    originalPath = source.absolutePath,
                    trashedPath = target.absolutePath,
                    name = item.name,
                    sizeBytes = item.sizeBytes,
                    isDirectory = item.isDirectory,
                    deletedAt = System.currentTimeMillis(),
                ),
            )
            // BEST-EFFORT: drop the trashed source (and, if a directory, its subtree) from the live
            // index. Wrapped so an index failure can never compromise trash safety.
            runCatching { indexRepository.removeByPath(item.path) }
            true
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            // Audit row could not be written, so the bytes in `target` are untracked.
            // The file survives in `target`; to keep it visible and filesystem-tracked
            // we move it BACK out of the trash to a non-clashing location next to the
            // original. We must NEVER simply delete `target` here — after a cross-volume
            // move the source may be only a partial leftover, so `target` can be the
            // only complete copy. movePath only removes `target` once it has verified a
            // complete copy at the destination; if it can't, the bytes stay in `target`
            // (an orphan trash payload, not a loss). Either way report failure via false.
            val restoreDest = if (source.exists()) uniqueRestoreTarget(source) else source
            runCatching { restoreDest.parentFile?.mkdirs() }
            movePath(target, restoreDest)
            false
        }
    }

    override fun observeTrash(): Flow<List<TrashItem>> =
        dao.observeAll()
            .map { entries -> entries.map(::toItem) }
            .flowOn(ioDispatcher)

    override fun count(): Flow<Int> = dao.count().flowOn(ioDispatcher)

    override suspend fun restore(id: String): AppResult<Unit> = withContext(ioDispatcher) {
        val entry = dao.getById(id)
            ?: return@withContext AppResult.Failure(AppError.NotFound(id))

        val trashed = File(entry.trashedPath)
        if (!trashed.exists()) {
            // The physical payload is gone; drop the dangling row and report.
            dao.delete(id)
            return@withContext AppResult.Failure(
                AppError.Io("Trashed item is missing and cannot be restored"),
            )
        }

        // Never overwrite: if the original location is occupied, restore the
        // item alongside under a non-clashing "name (restored)" variant.
        val original = File(entry.originalPath)
        val destination = if (original.exists()) uniqueRestoreTarget(original) else original

        destination.parentFile?.mkdirs()
        if (movePath(trashed, destination)) {
            dao.delete(id)
            // BEST-EFFORT: re-index the restored payload at its recovered location. Wrapped so an
            // index failure can never turn a successful restore into a reported failure.
            runCatching {
                if (destination.exists()) {
                    indexRepository.indexFile(fileSystemDataSource.toFileItem(destination))
                }
            }
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(AppError.Io("Could not restore \"" + entry.name + "\""))
        }
    }

    override suspend fun deletePermanently(id: String): AppResult<Unit> =
        withContext(ioDispatcher) {
            val entry = dao.getById(id)
                ?: return@withContext AppResult.Failure(AppError.NotFound(id))

            val trashed = File(entry.trashedPath)
            val removed = !trashed.exists() || trashed.deleteRecursively()
            if (!removed) {
                return@withContext AppResult.Failure(
                    AppError.Io("Could not permanently delete \"" + entry.name + "\""),
                )
            }
            dao.delete(id)
            AppResult.Success(Unit)
        }

    override suspend fun emptyAll(): AppResult<Unit> = withContext(ioDispatcher) {
        val entries = dao.getAll()
        var failed = false
        for (entry in entries) {
            val trashed = File(entry.trashedPath)
            if (trashed.exists() && !trashed.deleteRecursively()) {
                failed = true
            } else {
                dao.delete(entry.id)
            }
        }
        if (failed) {
            AppResult.Failure(AppError.Io("Some items could not be removed from the Recycle Bin"))
        } else {
            // Clear any stragglers (e.g. rows whose payload was already gone).
            dao.clearAll()
            AppResult.Success(Unit)
        }
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    /**
     * Relocates [source] to [target], preferring an atomic same-volume rename
     * and falling back to a verified copy-then-delete across volumes.
     *
     * Guarantees NO DATA LOSS: returns `true` only when [target] holds a
     * complete copy; on every failure path [source] is left intact (or, if a
     * verified copy already exists, that copy is retained) and `false` is
     * returned.
     */
    private suspend fun movePath(source: File, target: File): Boolean {
        // Same-volume path: renameTo is atomic — it either fully succeeds or is
        // a no-op, so it can never leave a partial state.
        if (source.renameTo(target)) return true

        // Cross-volume (or rename otherwise refused): copy, verify, then delete.
        return try {
            if (target.exists()) target.deleteRecursively()
            source.copyRecursively(target, overwrite = false)
            coroutineContext.ensureActive()

            if (!isCopyComplete(source, target)) {
                // Incomplete copy: discard it, keep the source untouched.
                target.deleteRecursively()
                return false
            }

            // The target now holds a COMPLETE, verified copy — the bytes are safely
            // relocated. From here NO DATA LOSS is possible, so the target must NEVER
            // be deleted. deleteRecursively() on a directory can partially succeed and
            // then return false; a leftover partial source is far preferable to
            // destroying the only complete copy. Best-effort remove the source and
            // report success regardless.
            source.deleteRecursively()
            true
        } catch (ce: CancellationException) {
            // Cancelled mid-copy: the target is at most a partial copy and the source
            // is untouched, so discarding the partial target loses nothing.
            runCatching { target.deleteRecursively() }
            throw ce
        } catch (t: Throwable) {
            // Failure during the copy: the target is at most partial and the source
            // is intact, so discarding the partial copy is safe.
            runCatching { target.deleteRecursively() }
            false
        }
    }

    /**
     * Verifies [target] is a faithful copy of [source]: matching type, and for
     * files matching length, for directories matching immediate child count.
     */
    private fun isCopyComplete(source: File, target: File): Boolean {
        if (!target.exists()) return false
        if (source.isDirectory) {
            if (!target.isDirectory) return false
            val sourceChildren = source.list()?.size ?: return false
            val targetChildren = target.list()?.size ?: return false
            return sourceChildren == targetChildren
        }
        return target.isFile && target.length() == source.length()
    }

    /**
     * Derives a collision-free id for [item] under [root]. Based on the stable
     * `originalPath.hashCode()` plus the name (no time/random component), then
     * numerically suffixed until the on-disk name is free.
     */
    private fun uniqueId(root: File, item: FileItem): String {
        val base = item.path.hashCode().toString() + "_" + item.name
        if (!File(root, base).exists()) return base
        var suffix = 1
        while (true) {
            val candidate = base + "_" + suffix
            if (!File(root, candidate).exists()) return candidate
            suffix++
        }
    }

    /**
     * Produces a non-clashing restore destination alongside [original],
     * appending `" (restored)"` (then `" (restored N)"`) until the path is free.
     */
    private fun uniqueRestoreTarget(original: File): File {
        val parent = original.parentFile
        val name = original.name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""

        var candidate = File(parent, stem + " (restored)" + ext)
        var n = 1
        while (candidate.exists()) {
            candidate = File(parent, stem + " (restored " + n + ")" + ext)
            n++
        }
        return candidate
    }

    private fun toItem(entry: TrashEntry): TrashItem = TrashItem(
        id = entry.id,
        originalPath = entry.originalPath,
        name = entry.name,
        sizeBytes = entry.sizeBytes,
        isDirectory = entry.isDirectory,
        deletedAt = entry.deletedAt,
    )

    private companion object {
        const val TRASH_DIR_NAME = "trash"
    }
}
