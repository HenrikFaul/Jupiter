package com.jupiter.filemanager.data.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.jupiter.filemanager.core.util.StorageExclusions
import com.jupiter.filemanager.core.util.extensionOf
import com.jupiter.filemanager.core.util.fileTypeFor
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.CategoryUsage
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.StorageCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sort orders that can be applied to a category listing. Mapped onto a
 * MediaStore `sortOrder` clause so ordering happens inside the query rather than
 * in-process.
 */
enum class CategorySort {
    DATE_DESC,
    NAME_ASC,
    SIZE_DESC,
}

/**
 * Device-wide, instant category listings backed by [MediaStore].
 *
 * Instead of recursively walking the filesystem (which is slow for tens of
 * thousands of files), each [StorageCategory] is resolved with a single indexed
 * MediaStore query against the appropriate collection. All queries run off the
 * main thread, guard their cursor with [use], and never throw: any failure —
 * including a missing runtime permission — yields an empty list.
 */
@Singleton
class MediaStoreCategorySource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    /**
     * Returns every file belonging to [category] on the primary external volume,
     * ordered by [sort]. Never throws; returns an empty list on any error or when
     * the required media permission has not been granted.
     */
    suspend fun query(
        category: StorageCategory,
        sort: CategorySort = CategorySort.DATE_DESC,
    ): List<FileItem> = withContext(dispatcher) {
        try {
            val request = requestFor(category)
            runQuery(request, sort)
        } catch (_: Throwable) {
            // SecurityException (missing permission), IllegalStateException, etc.
            emptyList()
        }
    }

    /**
     * Returns the total size and file count for [category] — the SAME type-based view the category
     * browser lists, so a summary tile shows exactly what opening the category reveals (e.g. an `.apk`
     * that lives in Download counts toward BOTH the APKs and Downloads categories, matching the
     * browse). Uses a lean projection (path + size only, no per-row `FileItem`) so even a 40k-image
     * library is summed in a single cheap cursor pass. Never throws; returns zero on any error.
     */
    suspend fun summarize(category: StorageCategory): CategoryUsage = withContext(dispatcher) {
        try {
            aggregate(requestFor(category), category)
        } catch (_: Throwable) {
            CategoryUsage(category = category, sizeBytes = 0L, fileCount = 0)
        }
    }

    private fun aggregate(request: QueryRequest, category: StorageCategory): CategoryUsage {
        val projection = arrayOf(request.dataColumn, request.sizeColumn)
        // Mirror runQuery's arg handling: the DOCUMENTS selection embeds its own MIME placeholders.
        val args = when {
            request.selection?.contains(inClause(mimeColumn, DOCUMENT_MIMES.size)) == true ->
                DOCUMENT_MIMES.toTypedArray() + (request.selectionArgs ?: emptyArray())
            else -> request.selectionArgs
        }
        val cursor = context.contentResolver.query(
            request.collection,
            projection,
            request.selection,
            args,
            null,
        ) ?: return CategoryUsage(category = category, sizeBytes = 0L, fileCount = 0)

        return cursor.use { c ->
            val dataIndex = c.getColumnIndex(request.dataColumn)
            val sizeIndex = c.getColumnIndex(request.sizeColumn)
            var totalBytes = 0L
            var count = 0
            while (c.moveToNext()) {
                val data = if (dataIndex >= 0 && !c.isNull(dataIndex)) c.getString(dataIndex) else null
                // Never count vendor recycle-bin / app-private files (they aren't shown in the browse).
                if (data != null && StorageExclusions.isExcluded(data)) continue
                totalBytes += if (sizeIndex >= 0 && !c.isNull(sizeIndex)) c.getLong(sizeIndex) else 0L
                count++
            }
            CategoryUsage(category = category, sizeBytes = totalBytes, fileCount = count)
        }
    }

    // region Query construction ------------------------------------------------

    /** Describes the collection, projection, and selection for a category. */
    private data class QueryRequest(
        val collection: Uri,
        val idColumn: String,
        val nameColumn: String,
        val sizeColumn: String,
        val dateColumn: String,
        val dataColumn: String,
        val mimeColumn: String?,
        val selection: String?,
        val selectionArgs: Array<String>?,
    )

    private fun requestFor(category: StorageCategory): QueryRequest = when (category) {
        StorageCategory.IMAGES -> mediaRequest(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.MIME_TYPE,
        )

        StorageCategory.VIDEOS -> mediaRequest(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.MIME_TYPE,
        )

        StorageCategory.AUDIO -> mediaRequest(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
        )

        StorageCategory.DOCUMENTS -> filesRequest(
            selection = documentSelection(),
            selectionArgs = null,
        )

        StorageCategory.ARCHIVES -> filesRequest(
            selection = extensionSelection(ARCHIVE_EXTENSIONS),
            selectionArgs = null,
        )

        StorageCategory.APPS -> filesRequest(
            selection = appSelection(),
            selectionArgs = arrayOf(MIME_APK),
        )

        StorageCategory.DOWNLOADS -> filesRequest(
            selection = downloadsSelection(),
            selectionArgs = null,
        )

        StorageCategory.OTHER -> filesRequest(
            selection = otherSelection(),
            selectionArgs = arrayOf(MIME_APK),
        )
    }

    private fun mediaRequest(
        collection: Uri,
        idColumn: String,
        nameColumn: String,
        sizeColumn: String,
        dateColumn: String,
        dataColumn: String,
        mimeColumn: String,
    ) = QueryRequest(
        collection = collection,
        idColumn = idColumn,
        nameColumn = nameColumn,
        sizeColumn = sizeColumn,
        dateColumn = dateColumn,
        dataColumn = dataColumn,
        mimeColumn = mimeColumn,
        selection = null,
        selectionArgs = null,
    )

    private fun filesRequest(
        selection: String?,
        selectionArgs: Array<String>?,
    ) = QueryRequest(
        collection = MediaStore.Files.getContentUri("external"),
        idColumn = MediaStore.Files.FileColumns._ID,
        nameColumn = MediaStore.Files.FileColumns.DISPLAY_NAME,
        sizeColumn = MediaStore.Files.FileColumns.SIZE,
        dateColumn = MediaStore.Files.FileColumns.DATE_MODIFIED,
        dataColumn = MediaStore.Files.FileColumns.DATA,
        mimeColumn = MediaStore.Files.FileColumns.MIME_TYPE,
        selection = selection,
        selectionArgs = selectionArgs,
    )

    // region Selection clauses -------------------------------------------------

    private val nameColumn get() = MediaStore.Files.FileColumns.DISPLAY_NAME
    private val dataColumn get() = MediaStore.Files.FileColumns.DATA
    private val mimeColumn get() = MediaStore.Files.FileColumns.MIME_TYPE

    /** MIME set OR name-extension set that identifies office/text documents. */
    private fun documentSelection(): String {
        val mimeClause = inClause(mimeColumn, DOCUMENT_MIMES.size)
        val extClause = extensionSelection(DOCUMENT_EXTENSIONS)
        // Office XML MIME types share the openxmlformats-officedocument prefix.
        val officePrefix = "$mimeColumn LIKE 'application/vnd.openxmlformats-officedocument%'"
        return "(($mimeClause) OR $officePrefix OR ($extClause))"
    }

    private fun appSelection(): String =
        "($mimeColumn = ? OR ${extensionSelection(APK_EXTENSIONS)})"

    private fun downloadsSelection(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE 'Download/%'"
        } else {
            "$dataColumn LIKE '%/Download/%'"
        }

    /**
     * Best-effort "everything else": exclude known image/video/audio media
     * types, documents, archives, and apps. May legitimately be empty on volumes
     * that only contain classified media.
     */
    private fun otherSelection(): String {
        val notImage = "($mimeColumn IS NULL OR $mimeColumn NOT LIKE 'image/%')"
        val notVideo = "($mimeColumn IS NULL OR $mimeColumn NOT LIKE 'video/%')"
        val notAudio = "($mimeColumn IS NULL OR $mimeColumn NOT LIKE 'audio/%')"
        val notDoc = "NOT (${documentSelection()})"
        val notArchive = "NOT (${extensionSelection(ARCHIVE_EXTENSIONS)})"
        val notApp = "NOT (${appSelection()})"
        // Exclude directory rows (MEDIA_TYPE_NONE with a trailing-slash data path
        // is uncommon, but skip obvious folder entries by requiring a display name).
        return "$notImage AND $notVideo AND $notAudio AND $notDoc AND $notArchive AND $notApp"
    }

    /** Builds `(lower(name) LIKE '%.ext' OR ...)` for the given extensions. */
    private fun extensionSelection(extensions: Set<String>): String =
        extensions.joinToString(prefix = "(", separator = " OR ", postfix = ")") { ext ->
            // Extensions are trusted constants (no user input), safe to inline.
            "LOWER($nameColumn) LIKE '%.$ext'"
        }

    private fun inClause(column: String, count: Int): String =
        (0 until count).joinToString(prefix = "$column IN (", separator = ",", postfix = ")") { "?" }

    // region Execution ---------------------------------------------------------

    private fun runQuery(request: QueryRequest, sort: CategorySort): List<FileItem> {
        val projection = buildList {
            add(request.idColumn)
            add(request.nameColumn)
            add(request.sizeColumn)
            add(request.dateColumn)
            add(request.dataColumn)
            request.mimeColumn?.let { add(it) }
        }.toTypedArray()

        // DOCUMENTS selection embeds its own MIME "IN (?, ?, ...)" placeholders,
        // so prepend the document MIME args ahead of any request-level args.
        val args = when {
            request.selection?.contains(inClause(mimeColumn, DOCUMENT_MIMES.size)) == true -> {
                DOCUMENT_MIMES.toTypedArray() + (request.selectionArgs ?: emptyArray())
            }
            else -> request.selectionArgs
        }

        val sortOrder = sortOrderFor(sort, request)

        val cursor: Cursor = context.contentResolver.query(
            request.collection,
            projection,
            request.selection,
            args,
            sortOrder,
        ) ?: return emptyList()

        return cursor.use { c -> mapCursor(c, request) }
    }

    private fun sortOrderFor(sort: CategorySort, request: QueryRequest): String = when (sort) {
        CategorySort.DATE_DESC -> "${request.dateColumn} DESC"
        CategorySort.NAME_ASC -> "${request.nameColumn} COLLATE NOCASE ASC"
        CategorySort.SIZE_DESC -> "${request.sizeColumn} DESC"
    }

    private fun mapCursor(cursor: Cursor, request: QueryRequest): List<FileItem> {
        val idIndex = cursor.getColumnIndex(request.idColumn)
        val nameIndex = cursor.getColumnIndex(request.nameColumn)
        val sizeIndex = cursor.getColumnIndex(request.sizeColumn)
        val dateIndex = cursor.getColumnIndex(request.dateColumn)
        val dataIndex = cursor.getColumnIndex(request.dataColumn)
        val mimeIndex = request.mimeColumn?.let { cursor.getColumnIndex(it) } ?: -1

        val out = ArrayList<FileItem>(cursor.count.coerceAtLeast(0))
        while (cursor.moveToNext()) {
            val item = try {
                mapRow(cursor, request, idIndex, nameIndex, sizeIndex, dateIndex, dataIndex, mimeIndex)
            } catch (_: Throwable) {
                null
            }
            if (item != null) out.add(item)
        }
        return out
    }

    private fun mapRow(
        cursor: Cursor,
        request: QueryRequest,
        idIndex: Int,
        nameIndex: Int,
        sizeIndex: Int,
        dateIndex: Int,
        dataIndex: Int,
        mimeIndex: Int,
    ): FileItem? {
        val data = if (dataIndex >= 0 && !cursor.isNull(dataIndex)) cursor.getString(dataIndex) else null

        // Resolve a usable path: prefer the real DATA path; fall back to the
        // content URI so newer scoped-storage rows are still openable.
        val path = when {
            !data.isNullOrEmpty() -> data
            idIndex >= 0 -> ContentUris.withAppendedId(request.collection, cursor.getLong(idIndex)).toString()
            else -> return null
        }

        // Never list files inside a vendor recycle bin / app-private / thumbnail dir: MediaStore
        // indexes e.g. Samsung's `Android/.Trash/…`, but those paths open to "Not found".
        if (StorageExclusions.isExcluded(path)) return null

        val rawName = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
        val name = when {
            !rawName.isNullOrEmpty() -> rawName
            !data.isNullOrEmpty() -> data.substringAfterLast('/')
            else -> return null
        }

        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
        // DATE_MODIFIED is stored in seconds since epoch; normalize to millis.
        val dateSeconds = if (dateIndex >= 0 && !cursor.isNull(dateIndex)) cursor.getLong(dateIndex) else 0L
        val lastModified = dateSeconds * 1000L

        val extension = extensionOf(name)
        val cursorMime = if (mimeIndex >= 0 && !cursor.isNull(mimeIndex)) cursor.getString(mimeIndex) else null

        return FileItem(
            path = path,
            name = name,
            isDirectory = false,
            sizeBytes = size,
            lastModified = lastModified,
            type = fileTypeFor(name, isDirectory = false),
            extension = extension,
            mimeType = cursorMime,
        )
    }

    // endregion

    private companion object {
        const val MIME_APK = "application/vnd.android.package-archive"

        val DOCUMENT_EXTENSIONS: Set<String> = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "rtf", "odt", "csv", "epub", "md",
        )

        val DOCUMENT_MIMES: List<String> = listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.oasis.opendocument.text",
            "text/plain",
            "text/rtf",
            "application/rtf",
            "text/csv",
            "text/markdown",
            "application/epub+zip",
        )

        val ARCHIVE_EXTENSIONS: Set<String> = setOf(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
        )

        val APK_EXTENSIONS: Set<String> = setOf("apk")
    }
}
