package com.jupiter.filemanager.data.index

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.jupiter.filemanager.core.util.extensionOf
import com.jupiter.filemanager.core.util.fileTypeFor
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import dagger.hilt.android.qualifiers.ApplicationContext
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
) {

    /**
     * Returns the number of files MediaStore knows about on the external volume, used
     * as an instant denominator for a real progress percentage. Returns 0 on failure.
     */
    suspend fun count(): Int = withContext(dispatcher) {
        runCatching {
            // Exclude directory rows from the denominator so the progress % isn't
            // systematically dragged below 100% by folders that forEachBatch skips. This
            // is still an APPROXIMATE total (excluded-segment / null-path rows are also
            // skipped when streaming), so the worker snaps progress to 100% on completion.
            context.contentResolver.query(
                COLLECTION,
                arrayOf(MediaStore.Files.FileColumns._ID),
                "${MediaStore.Files.FileColumns.FORMAT} IS NULL OR " +
                    "${MediaStore.Files.FileColumns.FORMAT} != ?",
                arrayOf(FORMAT_ASSOCIATION.toString()),
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
            MediaStore.Files.FileColumns.FORMAT,
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
            val formatIdx = c.getColumnIndex(MediaStore.Files.FileColumns.FORMAT)

            val batch = ArrayList<FileItem>(batchSize)
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val item = runCatching {
                    mapRow(c, nameIdx, sizeIdx, dateIdx, mimeIdx, dataIdx, formatIdx)
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

    private fun mapRow(
        cursor: Cursor,
        nameIdx: Int,
        sizeIdx: Int,
        dateIdx: Int,
        mimeIdx: Int,
        dataIdx: Int,
        formatIdx: Int,
    ): FileItem? {
        // A real filesystem path is required so the row aligns with the walk-built index
        // (same primary key) and stays openable by the rest of the app.
        val data = if (dataIdx >= 0 && !cursor.isNull(dataIdx)) cursor.getString(dataIdx) else null
        if (data.isNullOrEmpty()) return null
        if (isExcluded(data)) return null

        // Skip DIRECTORY rows. MediaStore.Files contains folder rows (MtpConstants
        // FORMAT_ASSOCIATION); indexing them as isDirectory=false would corrupt the
        // file-only queries (fileCount/isPopulated/largeFiles/duplicates) and make search
        // return folders as files. FORMAT is the reliable signal — a `_data` path never
        // carries a trailing slash, so the old size/slash heuristic was dead code.
        // Directory index rows themselves are filled in by the browser's per-directory
        // self-heal, so dropping them here is safe. Empty (size 0) REGULAR files are kept.
        val format = if (formatIdx >= 0 && !cursor.isNull(formatIdx)) cursor.getInt(formatIdx) else -1
        if (format == FORMAT_ASSOCIATION) return null

        val rawName = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else null
        val name = rawName?.takeIf { it.isNotEmpty() } ?: data.substringAfterLast('/')
        if (name.isEmpty()) return null

        val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L

        // DATE_MODIFIED is epoch SECONDS; the rest of the app uses millis.
        val dateSeconds = if (dateIdx >= 0 && !cursor.isNull(dateIdx)) cursor.getLong(dateIdx) else 0L
        val mime = if (mimeIdx >= 0 && !cursor.isNull(mimeIdx)) cursor.getString(mimeIdx) else null

        return FileItem(
            path = data,
            name = name,
            isDirectory = false,
            sizeBytes = size,
            lastModified = dateSeconds * 1000L,
            type = fileTypeFor(name, isDirectory = false),
            extension = extensionOf(name),
            mimeType = mime,
            isHidden = name.startsWith('.'),
            childCount = null,
        )
    }

    /** Mirrors [IndexingWorker]'s survey exclusions so both agree on scope. */
    private fun isExcluded(path: String): Boolean {
        val lower = path.lowercase()
        return EXCLUDED_SEGMENTS.any { lower.contains("/$it/") || lower.endsWith("/$it") }
    }

    private companion object {
        /** The generic files collection: every file MediaStore has scanned, not just media. */
        val COLLECTION = MediaStore.Files.getContentUri("external")

        /**
         * MTP object format for a directory (MtpConstants.FORMAT_ASSOCIATION). MediaStore
         * stores this in FileColumns.FORMAT for folder rows; used to skip directories.
         */
        const val FORMAT_ASSOCIATION = 12289

        val EXCLUDED_SEGMENTS = listOf(
            "android/data",
            "android/obb",
            ".thumbnails",
            ".trashed",
        )
    }
}
