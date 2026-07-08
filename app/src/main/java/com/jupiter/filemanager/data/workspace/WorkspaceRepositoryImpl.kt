package com.jupiter.filemanager.data.workspace

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jupiter.filemanager.core.result.getOrNull
import com.jupiter.filemanager.domain.model.Workspace
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.WorkspaceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level [DataStore] delegate backing persisted [Workspace]s.
 *
 * Declared at file scope (distinct preferences name "jupiter_workspaces") so the
 * single instance is shared across the process, as required by the Preferences
 * DataStore contract.
 */
val Context.workspacesDataStore: DataStore<Preferences> by preferencesDataStore(name = "jupiter_workspaces")

/**
 * [WorkspaceRepository] implementation persisting user-curated workspaces via
 * Jetpack Preferences DataStore.
 *
 * Each workspace is serialized as a JSON object holding its identifier, name and
 * member paths. Aggregate metadata ([Workspace.totalBytes] / [Workspace.lastModified])
 * is computed on demand on a best-effort basis from [fileRepository], so that the
 * persisted payload remains small and never drifts from the real file system.
 */
@Singleton
class WorkspaceRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val fileRepository: FileRepository,
) : WorkspaceRepository {

    private val dataStore: DataStore<Preferences> = context.workspacesDataStore

    private object Keys {
        val WORKSPACES = stringPreferencesKey("workspaces_json")
    }

    /**
     * Streams stored workspaces enriched with best-effort aggregate size and
     * last-modified metadata resolved from the current file system state.
     */
    override fun observeWorkspaces(): Flow<List<Workspace>> = dataStore.data
        .safe()
        .map { prefs ->
            decode(prefs[Keys.WORKSPACES]).map { enrich(it) }
        }

    override suspend fun createWorkspace(name: String, itemPaths: List<String>): String {
        val id = UUID.randomUUID().toString()
        val workspace = Workspace(
            id = id,
            name = name,
            itemPaths = itemPaths.distinct(),
        )
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.WORKSPACES])
            prefs[Keys.WORKSPACES] = encode(current + workspace)
        }
        return id
    }

    override suspend fun getWorkspace(id: String): Workspace? {
        val prefs = dataStore.data.safe().first()
        val workspace = decode(prefs[Keys.WORKSPACES]).firstOrNull { it.id == id } ?: return null
        return enrich(workspace)
    }

    override suspend fun addItem(workspaceId: String, path: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.WORKSPACES])
            val updated = current.map { workspace ->
                if (workspace.id == workspaceId && path !in workspace.itemPaths) {
                    workspace.copy(itemPaths = workspace.itemPaths + path)
                } else {
                    workspace
                }
            }
            prefs[Keys.WORKSPACES] = encode(updated)
        }
    }

    override suspend fun deleteWorkspace(id: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.WORKSPACES])
            prefs[Keys.WORKSPACES] = encode(current.filterNot { it.id == id })
        }
    }

    /**
     * Resolves aggregate size and most-recent modification time for [workspace]
     * by querying [fileRepository] for each member path. Paths that cannot be
     * resolved are skipped silently so a stale entry never breaks the listing.
     */
    private suspend fun enrich(workspace: Workspace): Workspace {
        var totalBytes = 0L
        var lastModified = 0L
        for (path in workspace.itemPaths) {
            val item = fileRepository.getFile(path).getOrNull() ?: continue
            totalBytes += item.sizeBytes
            if (item.lastModified > lastModified) {
                lastModified = item.lastModified
            }
        }
        return workspace.copy(totalBytes = totalBytes, lastModified = lastModified)
    }

    /** Serializes [workspaces] to a compact JSON array string for persistence. */
    private fun encode(workspaces: List<Workspace>): String {
        val array = JSONArray()
        workspaces.forEach { workspace ->
            val paths = JSONArray().apply {
                workspace.itemPaths.forEach { put(it) }
            }
            val obj = JSONObject().apply {
                put(FIELD_ID, workspace.id)
                put(FIELD_NAME, workspace.name)
                put(FIELD_PATHS, paths)
            }
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Parses persisted workspaces from [raw], returning an empty list when the
     * value is absent or malformed. Aggregate metadata is intentionally not
     * persisted and is recomputed in [enrich].
     */
    private fun decode(raw: String?): List<Workspace> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString(FIELD_ID).takeIf { it.isNotEmpty() } ?: continue
                    val name = obj.optString(FIELD_NAME)
                    val pathsArray = obj.optJSONArray(FIELD_PATHS)
                    val paths = buildList {
                        if (pathsArray != null) {
                            for (j in 0 until pathsArray.length()) {
                                val path = pathsArray.optString(j)
                                if (path.isNotEmpty()) add(path)
                            }
                        }
                    }
                    add(Workspace(id = id, name = name, itemPaths = paths))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Swallows [IOException]s raised while reading the persisted file by emitting
     * empty preferences, so collectors fall back to an empty list instead of
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
        const val FIELD_ID = "id"
        const val FIELD_NAME = "name"
        const val FIELD_PATHS = "paths"
    }
}
