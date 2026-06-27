package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * Persists user-defined [Tag]s and the associations between files and tags.
 */
interface TagRepository {

    /**
     * Streams the current set of [Tag]s, emitting on every change.
     */
    fun observeTags(): Flow<List<Tag>>

    /**
     * Creates a new tag with the given display [name] and packed ARGB color.
     */
    suspend fun addTag(name: String, colorArgb: Long)

    /**
     * Removes the tag identified by [id], dropping all of its file associations.
     */
    suspend fun removeTag(id: String)

    /**
     * Associates the file at [path] with the tag identified by [tagId].
     */
    suspend fun tagFile(path: String, tagId: String)

    /**
     * Removes the association between the file at [path] and the tag [tagId].
     */
    suspend fun untagFile(path: String, tagId: String)

    /**
     * Streams the absolute file paths currently associated with [tagId].
     */
    fun filesForTag(tagId: String): Flow<List<String>>
}
