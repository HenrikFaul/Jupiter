package com.jupiter.filemanager.data.activity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jupiter.filemanager.domain.model.ActivityEntry
import com.jupiter.filemanager.domain.model.ActivityType
import com.jupiter.filemanager.domain.repository.ActivityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level [DataStore] delegate backing the persisted activity history.
 *
 * Declared at file scope so the single instance is shared across the process,
 * as required by the Preferences DataStore contract. Uses a unique store name
 * so it never collides with the other feature stores.
 */
val Context.activityDataStore: DataStore<Preferences> by preferencesDataStore(name = "jupiter_activity")

/**
 * Preferences-DataStore-backed implementation of [ActivityRepository].
 *
 * The activity log is serialized as a single JSON array string under one key.
 * Entries are kept newest-first and capped to the most recent [MAX_ENTRIES].
 */
@Singleton
class ActivityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActivityRepository {

    private val dataStore: DataStore<Preferences> = context.activityDataStore

    private object Keys {
        val ENTRIES = stringPreferencesKey("activity_entries")
    }

    override fun observeActivity(): Flow<List<ActivityEntry>> = dataStore.data
        .safe()
        .map { prefs -> decode(prefs[Keys.ENTRIES]) }

    override suspend fun record(
        type: ActivityType,
        description: String,
        affectedPaths: List<String>,
    ) {
        val entry = ActivityEntry(
            id = UUID.randomUUID().toString(),
            type = type,
            description = description,
            timestamp = System.currentTimeMillis(),
            affectedPaths = affectedPaths,
        )
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES])
            val updated = (listOf(entry) + current)
                .sortedByDescending { it.timestamp }
                .take(MAX_ENTRIES)
            prefs[Keys.ENTRIES] = encode(updated)
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(Keys.ENTRIES) }
    }

    private fun encode(entries: List<ActivityEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            val pathsArray = JSONArray()
            entry.affectedPaths.forEach { pathsArray.put(it) }
            val obj = JSONObject().apply {
                put(FIELD_ID, entry.id)
                put(FIELD_TYPE, entry.type.name)
                put(FIELD_DESCRIPTION, entry.description)
                put(FIELD_TIMESTAMP, entry.timestamp)
                put(FIELD_PATHS, pathsArray)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<ActivityEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<ActivityEntry>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val type = runCatching {
                    ActivityType.valueOf(obj.optString(FIELD_TYPE))
                }.getOrNull() ?: continue
                val pathsArray = obj.optJSONArray(FIELD_PATHS)
                val paths = if (pathsArray == null) {
                    emptyList()
                } else {
                    buildList {
                        for (p in 0 until pathsArray.length()) {
                            add(pathsArray.optString(p))
                        }
                    }
                }
                result.add(
                    ActivityEntry(
                        id = obj.optString(FIELD_ID),
                        type = type,
                        description = obj.optString(FIELD_DESCRIPTION),
                        timestamp = obj.optLong(FIELD_TIMESTAMP),
                        affectedPaths = paths,
                    ),
                )
            }
            result.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Swallows [IOException]s raised while reading the persisted file by emitting
     * empty preferences, so collectors fall back to an empty log instead of
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
        const val MAX_ENTRIES = 100

        const val FIELD_ID = "id"
        const val FIELD_TYPE = "type"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_PATHS = "paths"
    }
}
