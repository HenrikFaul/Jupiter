package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

/**
 * Persists user bookmarks (pinned locations) and recently visited paths.
 */
interface BookmarkRepository {

    /**
     * Streams the current set of saved [Bookmark]s, emitting on every change.
     */
    fun observeBookmarks(): Flow<List<Bookmark>>

    /**
     * Adds a bookmark for [path] with the given display [label]. If a bookmark
     * for [path] already exists it should be updated rather than duplicated.
     */
    suspend fun addBookmark(path: String, label: String)

    /**
     * Removes the bookmark associated with [path], if present.
     */
    suspend fun removeBookmark(path: String)

    /**
     * Streams the list of recently visited paths, most recent first.
     */
    fun observeRecents(): Flow<List<String>>

    /**
     * Records [path] as recently visited, moving it to the front of the recents list.
     */
    suspend fun addRecent(path: String)
}
