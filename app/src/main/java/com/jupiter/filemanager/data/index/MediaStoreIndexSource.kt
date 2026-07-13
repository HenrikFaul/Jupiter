package com.jupiter.filemanager.data.index

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import com.jupiter.filemanager.core.util.extensionOf
import com.jupiter.filemanager.core.util.fileTypeFor
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-wide, near-instant file enumeration backed by [MediaStore].
 *
 * The classic way to build a file index — recursively walking `/storage/emulated/0`
 * with `java.io` — is pathologically slow on modern Android because shared storage is
 * a FUSE/sdcardfs passthrough: every `stat`/`readdir` is a userspace round-trip, and
 * building a full `FileItem` per entry costs ~6 syscalls. For a 200 GB volume with tens
 * of thousands of files this takes tens of minutes.
 *
 * MediaStore is Android's OWN pre-built, continuously-maintained index of the shared
 * volume. A single projected cursor over [MediaStore.Files] returns the path, size,
 * modified-time and MIME of every scanned file **with zero per-file syscalls and no
 * filesystem walk** — tens of thousands of rows in well under a second. This is exactly
 * how fast file managers (Files by Google, Solid Explorer, …) achieve instant listings.
 *
 * This source covers the vast majority of user-visible files on the primary volume; a
 * lightweight supplemental walk (elsewhere) can fill any gaps MediaStore hasn't scanned.
 * Every query runs off the main thread, guards its cursor with [use], and never throws:
 * any failure (missing permission, provider hiccup) yields 0 rows so the caller degrades
 * gracefully to the filesystem walk.
 */
@Singleton
class MediaStoreIndexSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : NewFileSource {

    /**
     * Returns the number of files MediaStore knows about on the external volume, used
     * as an instant denominator for a real progress percentage. Returns 0 on failure.
     */
    suspend fun count(): Int = withContext(dispatcher) {
        runCatching {
            // APPROXIMATE total (includes directory/excluded/null-path rows that streaming
            // skips), used only as an early progress denominator — the worker snaps the
            // percentage to a true 100% on completion, so a slightly-high total is fine.
            context.contentResolver.query(
                COLLECTION,
                arrayOf(MediaStore.Files.FileColumns._ID),
                null,
                null,
                null,
            )?.use { it.count } ?: 0
        }.getOrDefault(0)
    }

    /**
     * Streams every file MediaStore has scanned on the external volume as [FileItem]s,
     * delivered to [onBatch] in chunks of [batchSize] so the caller can flush each batch
     * into Room without materialising the whole set in memory. Rows without a usable real
     * path, and entries under app-private/cache directories (mirroring the survey's own
     * exclusions), are skipped. Returns the total number of items emitted.
     *
     * Cancellable: honours coroutine cancellation between rows. Never throws — a provider
     * failure simply ends the stream early with whatever was emitted so far.
     */
    suspend fun forEachBatch(
        batchSize: Int,
        onBatch: suspend (List<FileItem>) -> Unit,
    ): Int = withContext(dispatcher) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATA,
        )

        val cursor: Cursor = runCatching {
            context.contentResolver.query(COLLECTION, projection, null, null, null)
        }.getOrNull() ?: return@withContext 0

        var emitted = 0
        cursor.use { c ->
            val nameIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val dateIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val dataIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)

            val batch = ArrayList<FileItem>(batchSize)
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val item = runCatching {
                    mapRow(c, nameIdx, sizeIdx, dateIdx, mimeIdx, dataIdx)
                }.getOrNull() ?: continue
                batch.add(item)
                if (batch.size >= batchSize) {
                    onBatch(batch.toList())
                    emitted += batch.size
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                onBatch(batch.toList())
                emitted += batch.size
            }
        }
        emitted
    }

    /**
     * One newly-observed file from a MediaStore delta query: the [FileItem], MediaStore `_id`, and
     * generation marker. Generation is authoritative on Android 11+; id is the Android 10 fallback.
     */
    data class NewFile(
        val item: FileItem,
        val id: Long,
        /** MediaStore GENERATION_MODIFIED; falls back to `_id` on older Android. */
        val generation: Long = id,
    )

    override suspend fun currentVersion(): String? = withContext(dispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext null
        val version = try {
            MediaStore.getVersion(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } catch (failure: Exception) {
            throw MediaStoreDeltaQueryException("MediaStore version probe failed", failure)
        }
        version.takeIf { it.isNotBlank() }
            ?: throw MediaStoreDeltaQueryException("MediaStore returned an empty version")
    }

    override suspend fun currentGeneration(): Long? = withContext(dispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext null
        try {
            MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } catch (failure: Exception) {
            throw MediaStoreDeltaQueryException("MediaStore generation probe failed", failure)
        }
    }

    /**
     * Android 11+ metadata delta. `GENERATION_MODIFIED` advances both when a row is inserted and
     * when an in-progress download is finalized. Filtering `IS_PENDING = 0` avoids hashing partial
     * bytes without losing the later completion update, which receives a newer generation.
     */
    override suspend fun queryChangedSinceGeneration(
        sinceGeneration: Long,
        limit: Int,
    ): List<NewFile> = withContext(dispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext emptyList()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.GENERATION_MODIFIED,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATA,
        )
        val selection = "${MediaStore.Files.FileColumns.GENERATION_MODIFIED} > ? AND " +
            "${MediaStore.MediaColumns.IS_PENDING} = 0"
        val args = arrayOf(sinceGeneration.toString())
        val order = "${MediaStore.Files.FileColumns.GENERATION_MODIFIED} ASC, " +
            "${MediaStore.Files.FileColumns._ID} ASC"
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val cursor = try {
            context.contentResolver.query(collection, projection, selection, args, order)
                ?: throw MediaStoreDeltaQueryException("MediaStore returned no delta cursor")
        } catch (failure: MediaStoreDeltaQueryException) {
            throw failure
        } catch (failure: Exception) {
            throw MediaStoreDeltaQueryException("MediaStore generation delta query failed", failure)
        }
        cursor.use { c ->
            val idIdx = c.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val generationIdx = c.getColumnIndex(MediaStore.Files.FileColumns.GENERATION_MODIFIED)
            val nameIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val dateIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val dataIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val out = ArrayList<NewFile>(minOf(limit, 128))
            var boundaryGeneration: Long? = null
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val generation = if (generationIdx >= 0 && !c.isNull(generationIdx)) {
                    c.getLong(generationIdx)
                } else {
                    continue
                }
                // The persisted cursor is generation-only. Once the requested page is full,
                // retain every row sharing its final generation and stop before the next one;
                // otherwise `generation > checkpoint` would permanently skip a split tie.
                if (out.size >= limit && boundaryGeneration != null && generation != boundaryGeneration) {
                    break
                }
                val item = runCatching {
                    mapRow(c, nameIdx, sizeIdx, dateIdx, mimeIdx, dataIdx)
                }.getOrNull() ?: continue
                val id = if (idIdx >= 0 && !c.isNull(idIdx)) c.getLong(idIdx) else continue
                out += NewFile(item = item, id = id, generation = generation)
                if (out.size == limit) boundaryGeneration = generation
            }
            out
        }
    }

    /**
     * The highest MediaStore `_id` currently observable, or 0 when empty/unreadable. `_id` is the
     * SQLite rowid — strictly increasing per insert — so it is used to establish the initial
     * reconciler baseline WITHOUT alerting on the existing library. Sorts by the plain `_ID`
     * column (portable; no SQL functions, which the Android 11+ query validator can reject).
     */
    override suspend fun maxObservedId(): Long = withContext(dispatcher) {
        runCatching {
            context.contentResolver.query(
                COLLECTION,
                arrayOf(MediaStore.Files.FileColumns._ID),
                null,
                null,
                "${MediaStore.Files.FileColumns._ID} DESC",
            )?.use { c ->
                val idIdx = c.getColumnIndex(MediaStore.Files.FileColumns._ID)
                if (c.moveToFirst() && idIdx >= 0) c.getLong(idIdx) else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    /**
     * Files whose MediaStore `_id` is strictly greater than [sinceId] — i.e. everything INSERTED
     * since the reconciler's last checkpoint — id-ascending so the checkpoint advances
     * monotonically and GAP-FREE. Capped at [limit] per call (the reconciler loops until drained).
     * Never throws.
     *
     * Keys on `_ID` rather than `date_added`: `_id` is unique and strictly increasing, so — unlike
     * a 1-second-granularity date — no two files ever tie, and a bulk import of hundreds of files
     * in one second paginates cleanly with `_id > checkpoint` (no straddle-the-page skip). A newly
     * downloaded/captured/received/copied file always gets a fresh higher `_id`. Plain columns
     * only in selection/sort (portable across Android 10-15; the Android 11+ MediaStore validator
     * rejects SQL functions / `LIMIT`-in-sort). This is the robust replacement for trusting a
     * ContentObserver to carry the exact new file.
     */
    override suspend fun queryNewSince(sinceId: Long, limit: Int): List<NewFile> = withContext(dispatcher) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATA,
        )
        // Android 10 fallback still must not consume a row while the producer is writing it.
        // The trailing observer pass sees the same id after finalization; because the pending row
        // was excluded, the id checkpoint has not advanced past the ordinary single-download case.
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Files.FileColumns._ID} > ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        } else {
            "${MediaStore.Files.FileColumns._ID} > ?"
        }
        val args = arrayOf(sinceId.toString())
        val order = "${MediaStore.Files.FileColumns._ID} ASC"

        runCatching {
            context.contentResolver.query(COLLECTION, projection, selection, args, order)
        }.getOrNull()?.use { c ->
            val idIdx = c.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val nameIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val dateIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val dataIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val out = ArrayList<NewFile>(minOf(limit, 128))
            while (c.moveToNext() && out.size < limit) {
                currentCoroutineContext().ensureActive()
                val item = runCatching {
                    mapRow(c, nameIdx, sizeIdx, dateIdx, mimeIdx, dataIdx)
                }.getOrNull() ?: continue
                val id = if (idIdx >= 0 && !c.isNull(idIdx)) c.getLong(idIdx) else continue
                out.add(NewFile(item, id))
            }
            out
        } ?: emptyList()
    }

    private fun mapRow(
        cursor: Cursor,
        nameIdx: Int,
        sizeIdx: Int,
        dateIdx: Int,
        mimeIdx: Int,
        dataIdx: Int,
    ): FileItem? {
        // A real filesystem path is required so the row aligns with the walk-built index
        // (same primary key) and stays openable by the rest of the app.
        val data = if (dataIdx >= 0 && !cursor.isNull(dataIdx)) cursor.getString(dataIdx) else null
        if (data.isNullOrEmpty()) return null
        if (isExcluded(data)) return null

        val rawName = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else null
        val name = rawName?.takeIf { it.isNotEmpty() } ?: data.substringAfterLast('/')
        if (name.isEmpty()) return null

        val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
        // DATE_MODIFIED is epoch SECONDS; the rest of the app uses millis.
        val dateSeconds = if (dateIdx >= 0 && !cursor.isNull(dateIdx)) cursor.getLong(dateIdx) else 0L
        val mime = if (mimeIdx >= 0 && !cursor.isNull(mimeIdx)) cursor.getString(mimeIdx) else null

        // Skip DIRECTORY rows. MediaStore.Files contains folder rows; indexing them as
        // isDirectory=false would corrupt the file-only queries (fileCount/isPopulated/
        // largeFiles/duplicates) and make search return folders as files. There is no
        // public FORMAT column to key on, so verify with a single stat — but ONLY for the
        // ambiguous rows (no MIME and no size), which is essentially just directories and a
        // few empty files. Media/documents (mime set or size > 0) skip the stat entirely, so
        // this costs ~one syscall per folder, not per file. Empty REGULAR files are kept.
        if (mime.isNullOrEmpty() && size <= 0L && runCatching { File(data).isDirectory }.getOrDefault(false)) {
            return null
        }

        return FileItem(
            path = data,
            name = name,
            isDirectory = false,
            sizeBytes = size,
            lastModified = dateSeconds * 1000L,
            // Extensionless camera/download files still have a MediaStore MIME type. Feed it into
            // the shared classifier so they are eligible for perceptual image backfill.
            type = fileTypeFor(name, isDirectory = false, mimeType = mime),
            extension = extensionOf(name),
            mimeType = mime,
            isHidden = name.startsWith('.'),
            childCount = null,
        )
    }

    /** Shared exclusion set — the single source of truth used by every enumerator. */
    private fun isExcluded(path: String): Boolean =
        com.jupiter.filemanager.core.util.StorageExclusions.isExcluded(path)

    private companion object {
        /** The generic files collection: every file MediaStore has scanned, not just media. */
        val COLLECTION = MediaStore.Files.getContentUri("external")
    }
}

/** A retryable provider failure; the reconciler must not settle its generation past this gap. */
class MediaStoreDeltaQueryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
