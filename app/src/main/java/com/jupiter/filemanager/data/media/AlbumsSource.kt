package com.jupiter.filemanager.data.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.jupiter.filemanager.core.util.StorageExclusions
import com.jupiter.filemanager.core.util.extensionOf
import com.jupiter.filemanager.core.util.fileTypeFor
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single image album, mirroring a `MediaStore` image *bucket* (the folder the
 * gallery groups by: Camera, Screenshots, Download, WhatsApp Images, …).
 *
 * @param bucketId stable `MediaStore` bucket id — used to re-query the album's images.
 * @param name human-readable bucket/folder name (BUCKET_DISPLAY_NAME).
 * @param count number of images in the album.
 * @param coverPath path (or content URI string) of the newest image in the album,
 *   used as the grid cover; null when no cover could be resolved.
 */
data class Album(
    val bucketId: String,
    val name: String,
    val count: Int,
    val coverPath: String?,
)

/**
 * Gallery-style image albums backed by [MediaStore].
 *
 * [imageAlbums] runs a single indexed query over `MediaStore.Images` ordered by
 * date (newest first) and folds the rows into one [Album] per `BUCKET_ID`, taking
 * the first (newest) row's path as the album cover. [imagesIn] returns every image
 * in a given bucket as [FileItem]s.
 *
 * Every query runs off the main thread on [dispatcher], guards its cursor with
 * [use], and never throws: any failure — including a missing media permission —
 * yields an empty list.
 */
@Singleton
class AlbumsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    /**
     * Returns the device's image albums (one per `MediaStore` bucket), each with a
     * name, image count and newest-image cover, ordered by album name. Never throws;
     * returns an empty list on any error or when the media permission is missing.
     */
    suspend fun imageAlbums(): List<Album> = withContext(dispatcher) {
        try {
            queryAlbums()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Returns every image in the album identified by [bucketId], newest first, as
     * [FileItem]s. Never throws; returns an empty list on any error or when the
     * media permission is missing.
     */
    suspend fun imagesIn(bucketId: String): List<FileItem> = withContext(dispatcher) {
        try {
            queryImages(bucketId)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    // region Albums query ------------------------------------------------------

    private fun queryAlbums(): List<Album> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
        )
        // Newest first, so the first row seen per bucket is the album cover.
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        val cursor: Cursor = context.contentResolver.query(
            collection,
            projection,
            /* selection = */ null,
            /* selectionArgs = */ null,
            sortOrder,
        ) ?: return emptyList()

        return cursor.use { c -> foldAlbums(c) }
    }

    private fun foldAlbums(cursor: Cursor): List<Album> {
        val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
        val bucketIdIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
        val bucketNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

        if (bucketIdIndex < 0) return emptyList()

        // Preserve first-seen (newest) order for the cover; LinkedHashMap keeps it.
        val builders = LinkedHashMap<String, AlbumBuilder>()
        while (cursor.moveToNext()) {
            try {
                if (cursor.isNull(bucketIdIndex)) continue
                val bucketId = cursor.getString(bucketIdIndex) ?: continue

                // Skip images that live in a recycle-bin/thumbnail dir so a trash bucket never
                // appears as an album (and its trashed image never becomes a cover).
                val rowPath = if (dataIndex >= 0 && !cursor.isNull(dataIndex)) {
                    cursor.getString(dataIndex)
                } else {
                    null
                }
                if (rowPath != null && StorageExclusions.isExcluded(rowPath)) continue

                val builder = builders.getOrPut(bucketId) {
                    val name = bucketNameIndex
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { cursor.getString(it) }
                        ?.takeIf { it.isNotEmpty() }
                        ?: "Unknown"
                    // First row per bucket is the newest -> use it as the cover.
                    val cover = resolvePath(cursor, dataIndex, idIndex)
                    AlbumBuilder(bucketId = bucketId, name = name, coverPath = cover)
                }
                builder.count++
            } catch (_: Throwable) {
                // Skip a malformed row rather than failing the whole listing.
            }
        }

        return builders.values
            .map { Album(it.bucketId, it.name, it.count, it.coverPath) }
            .sortedBy { it.name.lowercase() }
    }

    private class AlbumBuilder(
        val bucketId: String,
        val name: String,
        val coverPath: String?,
        var count: Int = 0,
    )

    // region Images-in-album query --------------------------------------------

    private fun queryImages(bucketId: String): List<FileItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        val cursor: Cursor = context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        ) ?: return emptyList()

        return cursor.use { c -> mapImages(c) }
    }

    private fun mapImages(cursor: Cursor): List<FileItem> {
        val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
        val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
        val dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
        val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
        val mimeIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)

        val out = ArrayList<FileItem>(cursor.count.coerceAtLeast(0))
        while (cursor.moveToNext()) {
            val item = try {
                mapImageRow(cursor, idIndex, nameIndex, sizeIndex, dateIndex, dataIndex, mimeIndex)
            } catch (_: Throwable) {
                null
            }
            if (item != null) out.add(item)
        }
        return out
    }

    private fun mapImageRow(
        cursor: Cursor,
        idIndex: Int,
        nameIndex: Int,
        sizeIndex: Int,
        dateIndex: Int,
        dataIndex: Int,
        mimeIndex: Int,
    ): FileItem? {
        val data = if (dataIndex >= 0 && !cursor.isNull(dataIndex)) cursor.getString(dataIndex) else null

        val path = when {
            !data.isNullOrEmpty() -> data
            idIndex >= 0 -> ContentUris.withAppendedId(collection, cursor.getLong(idIndex)).toString()
            else -> return null
        }
        if (StorageExclusions.isExcluded(path)) return null

        val rawName = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
        val name = when {
            !rawName.isNullOrEmpty() -> rawName
            !data.isNullOrEmpty() -> data.substringAfterLast('/')
            else -> return null
        }

        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
        // DATE_MODIFIED is seconds since epoch; normalize to millis.
        val dateSeconds = if (dateIndex >= 0 && !cursor.isNull(dateIndex)) cursor.getLong(dateIndex) else 0L
        val lastModified = dateSeconds * 1000L

        val extension = extensionOf(name)
        val mime = if (mimeIndex >= 0 && !cursor.isNull(mimeIndex)) cursor.getString(mimeIndex) else null

        return FileItem(
            path = path,
            name = name,
            isDirectory = false,
            sizeBytes = size,
            lastModified = lastModified,
            type = fileTypeFor(name, isDirectory = false),
            extension = extension,
            mimeType = mime,
        )
    }

    // region Shared helpers ----------------------------------------------------

    /** Resolves a usable path: prefer DATA, fall back to the content URI. */
    private fun resolvePath(cursor: Cursor, dataIndex: Int, idIndex: Int): String? {
        val data = if (dataIndex >= 0 && !cursor.isNull(dataIndex)) cursor.getString(dataIndex) else null
        return when {
            !data.isNullOrEmpty() -> data
            idIndex >= 0 && !cursor.isNull(idIndex) ->
                ContentUris.withAppendedId(collection, cursor.getLong(idIndex)).toString()
            else -> null
        }
    }
}

/**
 * Lightweight, pure per-type auto-tag suggestions for [item], derived purely from
 * its [FileType] (with a small extension refinement). Intended to be offered as
 * one-tap tag chips in the UI; this does **not** persist anything to the tag store.
 *
 * Examples: an image -> ["Image", "Photo"], a video -> ["Video"], audio ->
 * ["Audio", "Music"], a document/PDF -> ["Document"], an archive -> ["Archive"],
 * an APK -> ["App"], source code -> ["Code"].
 */
fun suggestTagsFor(item: FileItem): List<String> = when (item.type) {
    FileType.IMAGE -> {
        val base = listOf("Image", "Photo")
        // GIFs are commonly treated as their own thing.
        if (item.extension.equals("gif", ignoreCase = true)) base + "GIF" else base
    }
    FileType.VIDEO -> listOf("Video")
    FileType.AUDIO -> listOf("Audio", "Music")
    FileType.DOCUMENT -> listOf("Document")
    FileType.PDF -> listOf("Document", "PDF")
    FileType.ARCHIVE -> listOf("Archive")
    FileType.APK -> listOf("App")
    FileType.CODE -> listOf("Code")
    FileType.FOLDER -> listOf("Folder")
    FileType.OTHER -> emptyList()
}
