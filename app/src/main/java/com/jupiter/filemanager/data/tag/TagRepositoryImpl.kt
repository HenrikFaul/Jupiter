package com.jupiter.filemanager.data.tag

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jupiter.filemanager.domain.model.Tag
import com.jupiter.filemanager.domain.repository.TagRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level [DataStore] delegate backing persisted [Tag]s and their file
 * associations. Declared at file scope with a unique store name so the single
 * process-wide instance is honored, per the Preferences DataStore contract.
 */
val Context.tagsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jupiter_tags")

/**
 * Preferences DataStore backed implementation of [TagRepository].
 *
 * Tags are persisted as a [Set] of records, one entry per tag, encoded as
 * `id|name|colorArgb`. The `name` is sanitized of the record delimiters on
 * write so it round-trips safely. File-to-tag associations are stored per tag
 * under a derived key holding the set of associated absolute file paths.
 *
 * The exposed [Tag.fileCount] is computed on read from the size of each tag's
 * associated path set, so it always reflects the current persisted state.
 */
@Singleton
class TagRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : TagRepository {

    private val dataStore: DataStore<Preferences> = context.tagsDataStore

    private object Keys {
        /** Set of `id|name|colorArgb` records, one per defined tag. */
        val TAGS = stringSetPreferencesKey("tags")

        /** Key holding the set of file paths associated with a given tag id. */
        fun filesForTag(tagId: String) = stringSetPreferencesKey("tag_files_$tagId")
    }

    override fun observeTags(): Flow<List<Tag>> = dataStore.data
        .safe()
        .map { prefs ->
            val records = prefs[Keys.TAGS].orEmpty()
            records
                .mapNotNull { decodeTag(it) }
                .map { tag ->
                    val count = prefs[Keys.filesForTag(tag.id)]?.size ?: 0
                    tag.copy(fileCount = count)
                }
                .sortedBy { it.name.lowercase() }
        }

    override suspend fun addTag(name: String, colorArgb: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val tag = Tag(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            colorArgb = colorArgb,
        )
        dataStore.edit { prefs ->
            val current = prefs[Keys.TAGS].orEmpty().toMutableSet()
            current.add(encodeTag(tag))
            prefs[Keys.TAGS] = current
        }
    }

    override suspend fun removeTag(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.TAGS].orEmpty()
            val remaining = current.filterNot { decodeTag(it)?.id == id }.toSet()
            prefs[Keys.TAGS] = remaining
            // Drop the tag's file associations entirely.
            prefs.remove(Keys.filesForTag(id))
        }
    }

    override suspend fun tagFile(path: String, tagId: String) {
        if (path.isBlank() || tagId.isBlank()) return
        dataStore.edit { prefs ->
            val key = Keys.filesForTag(tagId)
            val current = prefs[key].orEmpty().toMutableSet()
            current.add(path)
            prefs[key] = current
        }
    }

    override suspend fun untagFile(path: String, tagId: String) {
        dataStore.edit { prefs ->
            val key = Keys.filesForTag(tagId)
            val current = prefs[key].orEmpty().toMutableSet()
            current.remove(path)
            prefs[key] = current
        }
    }

    override fun filesForTag(tagId: String): Flow<List<String>> = dataStore.data
        .safe()
        .map { prefs ->
            prefs[Keys.filesForTag(tagId)].orEmpty().sorted()
        }

    /**
     * Swallows [IOException]s raised while reading the persisted file by emitting
     * empty preferences, so collectors fall back to empty state instead of
     * crashing.
     */
    private fun Flow<Preferences>.safe(): Flow<Preferences> = catch { throwable ->
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    private companion object {
        private const val FIELD_DELIMITER = '|'

        /** Encodes a [Tag] as `id|name|colorArgb`, sanitizing the name. */
        fun encodeTag(tag: Tag): String {
            val safeName = tag.name.replace(FIELD_DELIMITER, ' ').replace('\n', ' ')
            return buildString {
                append(tag.id)
                append(FIELD_DELIMITER)
                append(safeName)
                append(FIELD_DELIMITER)
                append(tag.colorArgb)
            }
        }

        /** Decodes a `id|name|colorArgb` record, returning null when malformed. */
        fun decodeTag(record: String): Tag? {
            val parts = record.split(FIELD_DELIMITER)
            if (parts.size < 3) return null
            val id = parts[0]
            val name = parts[1]
            val color = parts[2].toLongOrNull() ?: return null
            if (id.isEmpty()) return null
            return Tag(id = id, name = name, colorArgb = color)
        }
    }
}
