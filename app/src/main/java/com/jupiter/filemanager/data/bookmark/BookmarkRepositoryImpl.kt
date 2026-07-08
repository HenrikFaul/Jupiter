package com.jupiter.filemanager.data.bookmark

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jupiter.filemanager.domain.model.Bookmark
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Top-level DataStore for bookmark/recents persistence. Kept separate from the
 * settings DataStore so the two never share a backing file.
 */
private val Context.bookmarkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jupiter_bookmarks",
)

/**
 * [BookmarkRepository] backed by Jetpack DataStore preferences.
 *
 * Bookmarks are persisted as an unordered string set where each entry encodes
 * `path|label` (the path may not contain the `|` separator in practice, but the
 * encoding splits on the first separator to remain robust). Recents are stored
 * as a single ordered, newline-delimited string (most recent first) capped at
 * [MAX_RECENTS] entries.
 */
@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BookmarkRepository {

    private val dataStore: DataStore<Preferences> = context.bookmarkDataStore

    override fun observeBookmarks(): Flow<List<Bookmark>> =
        dataStore.data.map { prefs ->
            val encoded = prefs[BOOKMARKS_KEY].orEmpty()
            encoded
                .mapNotNull { decodeBookmark(it) }
                .sortedBy { it.label.lowercase() }
        }

    override suspend fun addBookmark(path: String, label: String) {
        val normalizedPath = path.trimEnd('/').ifEmpty { "/" }
        dataStore.edit { prefs ->
            val current = prefs[BOOKMARKS_KEY].orEmpty()
            // Drop any existing entry for this path so we update rather than duplicate.
            val withoutPath = current.filterNot { decodeBookmark(it)?.path == normalizedPath }
            val updated = withoutPath.toMutableSet().apply {
                add(encodeBookmark(normalizedPath, label))
            }
            prefs[BOOKMARKS_KEY] = updated
        }
    }

    override suspend fun removeBookmark(path: String) {
        val normalizedPath = path.trimEnd('/').ifEmpty { "/" }
        dataStore.edit { prefs ->
            val current = prefs[BOOKMARKS_KEY].orEmpty()
            val updated = current.filterNot { decodeBookmark(it)?.path == normalizedPath }.toSet()
            prefs[BOOKMARKS_KEY] = updated
        }
    }

    override fun observeRecents(): Flow<List<String>> =
        dataStore.data.map { prefs ->
            decodeRecents(prefs[RECENTS_KEY])
        }

    override suspend fun addRecent(path: String) {
        val normalizedPath = path.trimEnd('/').ifEmpty { "/" }
        dataStore.edit { prefs ->
            val current = decodeRecents(prefs[RECENTS_KEY])
            val updated = buildList {
                add(normalizedPath)
                // Preserve previous order, excluding the path we just moved to the front.
                addAll(current.filterNot { it == normalizedPath })
            }.take(MAX_RECENTS)
            prefs[RECENTS_KEY] = encodeRecents(updated)
        }
    }

    private fun encodeBookmark(path: String, label: String): String = "$path$SEPARATOR$label"

    private fun decodeBookmark(encoded: String): Bookmark? {
        val index = encoded.indexOf(SEPARATOR)
        if (index < 0) return null
        val path = encoded.substring(0, index)
        val label = encoded.substring(index + 1)
        if (path.isEmpty()) return null
        return Bookmark(path = path, label = label)
    }

    private fun encodeRecents(paths: List<String>): String =
        paths.joinToString(RECENTS_SEPARATOR)

    private fun decodeRecents(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(RECENTS_SEPARATOR).filter { it.isNotEmpty() }
    }

    private companion object {
        val BOOKMARKS_KEY = stringSetPreferencesKey("bookmarks")
        val RECENTS_KEY = stringPreferencesKey("recents")
        const val SEPARATOR = "|"
        const val RECENTS_SEPARATOR = "\n"
        const val MAX_RECENTS = 20
    }
}
