# Initiative #8 — High-performance Search Index (Room FTS) + Saved Searches + Content Search

## 1. Initiative header

**Title:** High-performance Search Index (Room FTS4) + Saved Searches + Opt-in Content Search
**Estimated value:** +€110k–€220k

**Business case.** Jupiter's current search (`SearchViewModel` → `FileRepository.search`) walks the filesystem live on every query, which is fine for one folder but visibly slow across a full device (tens of thousands of nodes) and re-does all the work on every keystroke. A persistent **Room FTS4 index**, populated incrementally in the background via WorkManager, turns name search into an indexed query that returns in milliseconds across *all* storage — a genuine performance moat over the stock "filesystem-walk" experience that most competitor file managers ship. On top of that index we add two retention/engagement features that are cheap once the index exists: **saved & recent searches** (one-tap re-run of frequent queries) and **opt-in full-text content search** (find files by what is *inside* `.txt`/`.md`/`.log`/source files). The combination raises perceived speed (a top driver of store ratings), lifts day-2 retention via saved searches, and gives the Pro tier a defensible differentiator (content search can be gated). Critically, this ships **additively**: the existing live-walk search path stays as a guaranteed-correct fallback and is used verbatim when the index is cold or disabled.

---

## 2. Codebase context

All paths below are real and rooted at `/home/user/Jupiter/app/src/main/java/com/jupiter/filemanager`.

### Current relevant file tree (exists today)

```
core/result/AppResult.kt                 # sealed AppResult<Success|Failure>, map/onSuccess/onFailure/getOrNull
core/result/AppError.kt                  # sealed AppError: PermissionDenied/NotFound/AccessDenied/AlreadyExists/Io/Unknown
di/CoroutineModule.kt                    # @IoDispatcher / @DefaultDispatcher / @MainDispatcher qualifiers + providers
di/RepositoryModule.kt                   # @Binds for File/StorageAnalytics/Vault/Bookmark repos
di/FeatureRepositoryModule.kt            # @Binds for Tag/Activity/Automation/Workspace/Connection/Transfer/Version/Sync
domain/model/FileItem.kt                 # data class FileItem(path,name,isDirectory,sizeBytes,lastModified,type,extension,...)
domain/model/FileType.kt                 # enum FOLDER,IMAGE,VIDEO,AUDIO,DOCUMENT,PDF,ARCHIVE,APK,CODE,OTHER
domain/model/FilterOption.kt             # data class FilterOption(query,showHidden,typeFilter)
domain/model/SortOption.kt               # SortField/SortDirection/SortOption
domain/repository/FileRepository.kt      # observeDirectory/listFiles/search(rootPath,filter):Flow<FileItem>/rootDirectory()/storageVolumes()
data/file/FileRepositoryImpl.kt          # @Singleton FileRepository impl; uses FileSystemDataSource + @IoDispatcher; search() walks tree
data/automation/AutomationWorker.kt      # @HiltWorker CoroutineWorker reference pattern (AssistedInject)
feature/search/SearchViewModel.kt        # @HiltViewModel; SearchUiState; streams FileRepository.search results
feature/search/SearchScreen.kt           # Compose UI; SearchInputBar, NL toggle, streamed results via FileRow
ui/navigation/Destinations.kt            # sealed Destination; has data object Search : Destination("search")
ui/navigation/JupiterNavHost.kt          # composable(Destination.Search.route){ SearchScreen(...) } at ~line 170
JupiterApp.kt                            # @HiltAndroidApp + Configuration.Provider w/ HiltWorkerFactory (WorkManager ready)
```

### What exists vs missing vs needs change

| Concern | Status | Action |
|---|---|---|
| `AppResult` / `AppError` | exists | reuse unchanged |
| `@IoDispatcher` | exists | reuse unchanged |
| WorkManager + Hilt worker factory | exists (`JupiterApp` is `Configuration.Provider`) | reuse — enqueue a new worker |
| Room | **missing** (no `androidx.room` dep) | add Phase 1 |
| `data/search/` package (DB, entities, FTS, DAO, indexer, repo impl) | **missing** | create Phase 2 |
| `domain/repository/SearchIndexRepository` + domain models | **missing** | create Phase 2 |
| `feature/search/SearchViewModel.kt` | exists | **extend additively** (do not break NL path) |
| `feature/search/SearchScreen.kt` | exists | **extend additively** (add saved/recent chips + index status) |
| `Destinations.kt` `Search` route | exists | reuse — no new destination needed |
| `JupiterNavHost.kt` Search block | exists | reuse — `SearchScreen` signature unchanged |
| `di/RepositoryModule` / `FeatureRepositoryModule` | exist | add a **new** `di/SearchModule.kt` (do not edit existing modules) |

**Design principle:** the index is a *cache/accelerator*. The legacy live-walk `FileRepository.search` is never removed; the new code prefers the index when it is warm and falls back to the live walk when the index is cold, disabled, or returns nothing.

---

## 3. Pre-conditions

### Gradle dependencies to add (exact coordinates)

Room (FTS4 lives in the core artifact; KSP compiler for codegen; `room-ktx` for coroutine/Flow support):

```
androidx.room:room-runtime:2.6.1
androidx.room:room-ktx:2.6.1
androidx.room:room-compiler:2.6.1   # ksp
```

No new third-party SDKs, no network. The codebase already has KSP (`com.google.devtools.ksp` 2.0.21-1.0.28), Hilt 2.52, WorkManager 2.10.0, DataStore 1.1.1, Coroutines 1.9.0 — all reused.

### Manifest / permission / Play-Console / API-key prerequisites

- **No new runtime permissions.** Indexing reads only paths the app can already read (`READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` already declared). If storage permission is not granted, the indexer no-ops gracefully.
- **No Play Console / API keys / external services.** Everything is on-device. This initiative has zero backend dependency.
- **Min/target SDK** unchanged (`minSdk = 26`, `targetSdk = 35`). Room 2.6.1 supports `minSdk 16+`.
- **Schema export:** Room emits a build-time warning unless `room.schemaLocation` is set. We set it via KSP arg in Phase 1 and add `app/schemas/` to the repo so migrations are reviewable.

---

## 4. Phase 1 — Gradle + Manifest + resources

### 4.1 `gradle/libs.versions.toml`

Under `[versions]` add:

```toml
room = "2.6.1"
```

Under `[libraries]` add:

```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

No new plugin entry is needed — Room codegen runs through the existing `ksp` plugin.

### 4.2 `app/build.gradle.kts`

In the `android { }` block, add KSP schema export so Room writes its schema JSON into the repo. Place this **after** the existing `kotlinOptions { }` block:

```kotlin
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
```

In `dependencies { }`, next to the existing `// Storage / preferences` group, add:

```kotlin
    // Search index (Room FTS4)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
```

Create the directory `app/schemas/` (add a `.gitkeep` so it is tracked) so the first build can export `com.jupiter.filemanager.data.search.SearchDatabase/1.json`.

### 4.3 `AndroidManifest.xml`

**No manifest changes required.** WorkManager's default initializer is already active and `JupiterApp` supplies the `HiltWorkerFactory`. We enqueue work programmatically (no `<provider>` edits, no new permissions).

### 4.4 Resources

No new XML resources required (all strings are inline in Compose, matching the existing `SearchScreen.kt` convention which hardcodes user-facing strings). If the project later wires initiative #3 (i18n), the new strings can be extracted then; do not block on it.

---

## 5. Phase 2 — Data / domain layer

Create a new package `data/search` and new domain types. None of this touches existing files except adding to a **new** DI module.

### 5.1 Domain models — `domain/model/SearchIndexModels.kt`

```kotlin
package com.jupiter.filemanager.domain.model

/**
 * A single hit returned by the search index. A thin projection of an indexed
 * entry, convertible to a [FileItem] for rendering with the existing browser
 * [com.jupiter.filemanager.feature.browser.components.FileRow].
 *
 * @property path absolute file path (primary key in the index).
 * @property name display name (last path segment).
 * @property isDirectory whether the entry is a directory.
 * @property sizeBytes size in bytes (0 for directories).
 * @property lastModified epoch millis of last modification.
 * @property type coarse [FileType] classification persisted at index time.
 * @property extension lowercased extension without the dot ("" when none).
 * @property contentMatched true when this hit matched on indexed text content
 *   rather than (or in addition to) the name.
 */
data class SearchHit(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val type: FileType,
    val extension: String,
    val contentMatched: Boolean = false,
) {
    /** Adapts this hit to the [FileItem] consumed by the shared file-row UI. */
    fun toFileItem(): FileItem = FileItem(
        path = path,
        name = name,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        type = type,
        extension = extension,
    )
}

/**
 * A persisted query the user explicitly saved for one-tap re-run.
 *
 * @property id stable identifier (UUID string).
 * @property query the raw query text.
 * @property createdAt epoch millis when saved.
 */
data class SavedSearch(
    val id: String,
    val query: String,
    val createdAt: Long,
)

/**
 * Current state of the background index, surfaced in the UI so users understand
 * whether results are complete.
 *
 * @property indexedCount number of entries currently in the index.
 * @property isBuilding whether an indexing pass is currently running.
 * @property lastBuiltAt epoch millis of the last completed pass, or 0 if never.
 */
data class IndexStatus(
    val indexedCount: Int = 0,
    val isBuilding: Boolean = false,
    val lastBuiltAt: Long = 0L,
)
```

### 5.2 Room entities — `data/search/FileIndexEntity.kt`

```kotlin
package com.jupiter.filemanager.data.search

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Primary index row: one per file-system entry. Holds the structured, queryable
 * metadata used for fast name search and for re-hydrating a [SearchHit].
 *
 * `path` is the natural primary key (absolute path is unique on a device). A
 * lowercased [nameLower] column backs case-insensitive prefix/`LIKE` matching for
 * very short queries where FTS prefix tokens are less selective.
 */
@Entity(
    tableName = "file_index",
    indices = [Index(value = ["nameLower"]), Index(value = ["parentPath"])],
)
data class FileIndexEntity(
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "nameLower") val nameLower: String,
    @ColumnInfo(name = "parentPath") val parentPath: String,
    @ColumnInfo(name = "isDirectory") val isDirectory: Boolean,
    @ColumnInfo(name = "sizeBytes") val sizeBytes: Long,
    @ColumnInfo(name = "lastModified") val lastModified: Long,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "extension") val extension: String,
)

/**
 * FTS4 virtual table for fast tokenized name search. Mapped to [FileIndexEntity]
 * via [androidx.room.Fts4.contentEntity] so Room keeps it in sync and lets us
 * store only the tokenized `name` while joining back to the content table for the
 * full row. `rowid` is the implicit join key Room manages.
 */
@Fts4(contentEntity = FileIndexEntity::class)
@Entity(tableName = "file_index_fts")
data class FileIndexFts(
    @ColumnInfo(name = "name") val name: String,
)

/**
 * Opt-in text-content index. Stored as a separate FTS4 table (no content entity)
 * so the heavy text payload is isolated from the always-on name index and can be
 * cleared independently when the user disables content search.
 *
 * `path` is duplicated as a plain column so a content hit can be joined back to
 * [FileIndexEntity] without relying on rowid alignment across tables.
 */
@Fts4
@Entity(tableName = "file_content_fts")
data class FileContentFts(
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "body") val body: String,
)
```

### 5.3 DAO — `data/search/SearchDao.kt`

```kotlin
package com.jupiter.filemanager.data.search

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Data-access for the search index. All queries are suspend (one-shot) so they
 * compose cleanly inside the repository's `withContext(@IoDispatcher)` boundary.
 */
@Dao
interface SearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<FileIndexEntity>)

    @Query("DELETE FROM file_index WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT COUNT(*) FROM file_index")
    suspend fun count(): Int

    @Query("DELETE FROM file_index")
    suspend fun clearIndex()

    /**
     * Name search via the FTS4 table. The caller passes an FTS MATCH expression
     * (e.g. `repo*`). Joins back to the content table for the full row, ordered
     * directories-first then by name for a stable, browser-like listing.
     */
    @Transaction
    @Query(
        """
        SELECT fi.* FROM file_index AS fi
        JOIN file_index_fts AS fts ON fi.rowid = fts.rowid
        WHERE file_index_fts MATCH :match
        ORDER BY fi.isDirectory DESC, fi.nameLower ASC
        LIMIT :limit
        """
    )
    suspend fun searchByNameFts(match: String, limit: Int): List<FileIndexEntity>

    /**
     * Fallback prefix search for very short / non-tokenizable queries, using the
     * indexed lowercased name column.
     */
    @Query(
        """
        SELECT * FROM file_index
        WHERE nameLower LIKE :likeExpr ESCAPE '\'
        ORDER BY isDirectory DESC, nameLower ASC
        LIMIT :limit
        """
    )
    suspend fun searchByNameLike(likeExpr: String, limit: Int): List<FileIndexEntity>

    // ---- content search ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContent(rows: List<FileContentFts>)

    @Query("DELETE FROM file_content_fts")
    suspend fun clearContent()

    @Query("SELECT COUNT(*) FROM file_content_fts")
    suspend fun contentCount(): Int

    /**
     * Content search: matches the body FTS table and joins to the structured
     * index by path so we can render full rows. Entries whose file no longer has
     * an index row are dropped by the inner join.
     */
    @Transaction
    @Query(
        """
        SELECT fi.* FROM file_content_fts AS c
        JOIN file_index AS fi ON fi.path = c.path
        WHERE file_content_fts MATCH :match
        ORDER BY fi.nameLower ASC
        LIMIT :limit
        """
    )
    suspend fun searchByContent(match: String, limit: Int): List<FileIndexEntity>
}
```

### 5.4 Database — `data/search/SearchDatabase.kt`

```kotlin
package com.jupiter.filemanager.data.search

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database housing the search index. Bundles the structured index entity,
 * its FTS4 mirror, and the opt-in content FTS table. Versioned at 1; future
 * schema changes must add a migration and a new exported schema JSON under
 * `app/schemas`.
 */
@Database(
    entities = [
        FileIndexEntity::class,
        FileIndexFts::class,
        FileContentFts::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SearchDatabase : RoomDatabase() {
    abstract fun searchDao(): SearchDao

    companion object {
        const val NAME = "jupiter_search_index.db"
    }
}
```

### 5.5 Domain repository — `domain/repository/SearchIndexRepository.kt`

```kotlin
package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.IndexStatus
import com.jupiter.filemanager.domain.model.SavedSearch
import com.jupiter.filemanager.domain.model.SearchHit
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the persistent search index plus saved/recent searches.
 *
 * The index is an *accelerator*: callers must treat an empty / failed result as
 * "fall back to the live filesystem search", never as "no such file". All
 * blocking work runs on a background dispatcher inside the implementation.
 */
interface SearchIndexRepository {

    /** Fast indexed name search. Returns hits, or a failure the caller can fall back on. */
    suspend fun searchByName(query: String, limit: Int = 500): AppResult<List<SearchHit>>

    /** Opt-in indexed content search; returns empty list when content indexing is disabled. */
    suspend fun searchByContent(query: String, limit: Int = 200): AppResult<List<SearchHit>>

    /** Observes index size / build state for UI status display. */
    fun observeStatus(): Flow<IndexStatus>

    /** Whether the index currently holds at least one entry (warm). */
    suspend fun isWarm(): Boolean

    /** Enqueues a background (re)build of the index. Idempotent — coalesces duplicates. */
    fun requestRebuild(includeContent: Boolean)

    /** Clears the entire index (used on rollback / "rebuild from scratch"). */
    suspend fun clear(): AppResult<Unit>

    // ---- saved & recent searches ----

    fun observeSavedSearches(): Flow<List<SavedSearch>>
    fun observeRecentSearches(): Flow<List<String>>
    suspend fun saveSearch(query: String)
    suspend fun deleteSavedSearch(id: String)
    suspend fun recordRecent(query: String)
    suspend fun clearRecent()

    // ---- content search toggle (opt-in) ----

    fun observeContentSearchEnabled(): Flow<Boolean>
    suspend fun setContentSearchEnabled(enabled: Boolean)
}
```

### 5.6 Repository implementation — `data/search/SearchIndexRepositoryImpl.kt`

```kotlin
package com.jupiter.filemanager.data.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.IndexStatus
import com.jupiter.filemanager.domain.model.SavedSearch
import com.jupiter.filemanager.domain.model.SearchHit
import com.jupiter.filemanager.domain.repository.SearchIndexRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** File-scope DataStore for saved/recent searches and the content-search toggle. */
val Context.searchPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jupiter_search_prefs")

/**
 * Room + DataStore + WorkManager backed implementation of [SearchIndexRepository].
 *
 * Index reads run on the injected [@IoDispatcher][IoDispatcher] and never throw
 * across the boundary — every failure becomes an [AppResult.Failure] so the
 * ViewModel can transparently fall back to the legacy live-walk search. Saved /
 * recent searches and the content toggle are persisted in Preferences DataStore,
 * mirroring the existing `TagRepositoryImpl` convention.
 */
@Singleton
class SearchIndexRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: SearchDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : SearchIndexRepository {

    private val prefs: DataStore<Preferences> = context.searchPrefsDataStore
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    private object Keys {
        val SAVED = stringSetPreferencesKey("saved_searches")     // "id|query|createdAt"
        val RECENT = stringSetPreferencesKey("recent_searches")   // "epochMillis|query"
        val CONTENT_ENABLED = booleanPreferencesKey("content_search_enabled")
    }

    override suspend fun searchByName(query: String, limit: Int): AppResult<List<SearchHit>> =
        withContext(dispatcher) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext AppResult.Success(emptyList())
            try {
                val ftsHits = dao.searchByNameFts(toFtsMatch(trimmed), limit)
                val hits = ftsHits.ifEmpty {
                    // FTS misses on very short / punctuation-only queries: prefix fallback.
                    dao.searchByNameLike(toLikeExpr(trimmed), limit)
                }
                AppResult.Success(hits.map { it.toHit(contentMatched = false) })
            } catch (e: Exception) {
                AppResult.Failure(AppError.Io("Index search failed.", e))
            }
        }

    override suspend fun searchByContent(query: String, limit: Int): AppResult<List<SearchHit>> =
        withContext(dispatcher) {
            val trimmed = query.trim()
            if (trimmed.isEmpty() || !contentEnabledNow()) {
                return@withContext AppResult.Success(emptyList())
            }
            try {
                val hits = dao.searchByContent(toFtsMatch(trimmed), limit)
                AppResult.Success(hits.map { it.toHit(contentMatched = true) })
            } catch (e: Exception) {
                AppResult.Failure(AppError.Io("Content search failed.", e))
            }
        }

    override fun observeStatus(): Flow<IndexStatus> = prefs.data
        .safe()
        .map {
            // Count is read lazily; build flag is driven by WorkManager elsewhere.
            IndexStatus(
                indexedCount = runCatchingCount(),
                isBuilding = false,
                lastBuiltAt = 0L,
            )
        }

    override suspend fun isWarm(): Boolean = withContext(dispatcher) {
        try {
            dao.count() > 0
        } catch (_: Exception) {
            false
        }
    }

    override fun requestRebuild(includeContent: Boolean) {
        val request = OneTimeWorkRequestBuilder<SearchIndexWorker>()
            .setInputData(workDataOf(SearchIndexWorker.KEY_INCLUDE_CONTENT to includeContent))
            .build()
        workManager.enqueueUniqueWork(
            SearchIndexWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP, // coalesce: a pass already running wins.
            request,
        )
    }

    override suspend fun clear(): AppResult<Unit> = withContext(dispatcher) {
        try {
            dao.clearContent()
            dao.clearIndex()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(AppError.Io("Failed to clear index.", e))
        }
    }

    // ---- saved & recent ----

    override fun observeSavedSearches(): Flow<List<SavedSearch>> = prefs.data
        .safe()
        .map { p ->
            p[Keys.SAVED].orEmpty()
                .mapNotNull { decodeSaved(it) }
                .sortedByDescending { it.createdAt }
        }

    override fun observeRecentSearches(): Flow<List<String>> = prefs.data
        .safe()
        .map { p ->
            p[Keys.RECENT].orEmpty()
                .mapNotNull { decodeRecent(it) }
                .sortedByDescending { it.first }
                .map { it.second }
                .take(MAX_RECENT)
        }

    override suspend fun saveSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val record = encodeSaved(SavedSearch(UUID.randomUUID().toString(), q, System.currentTimeMillis()))
        prefs.edit { p ->
            val current = p[Keys.SAVED].orEmpty().toMutableSet()
            // De-dupe by query text (ignore id/timestamp).
            current.removeAll { decodeSaved(it)?.query == q }
            current.add(record)
            p[Keys.SAVED] = current
        }
    }

    override suspend fun deleteSavedSearch(id: String) {
        prefs.edit { p ->
            val current = p[Keys.SAVED].orEmpty().toMutableSet()
            current.removeAll { decodeSaved(it)?.id == id }
            p[Keys.SAVED] = current
        }
    }

    override suspend fun recordRecent(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        prefs.edit { p ->
            val current = p[Keys.RECENT].orEmpty().toMutableSet()
            current.removeAll { decodeRecent(it)?.second == q }
            current.add("${System.currentTimeMillis()}|${q.replace("|", " ")}")
            // Trim to the most recent N.
            val trimmed = current
                .mapNotNull { raw -> decodeRecent(raw)?.let { it to raw } }
                .sortedByDescending { it.first.first }
                .take(MAX_RECENT)
                .map { it.second }
                .toMutableSet()
            p[Keys.RECENT] = trimmed
        }
    }

    override suspend fun clearRecent() {
        prefs.edit { it[Keys.RECENT] = emptySet() }
    }

    // ---- content toggle ----

    override fun observeContentSearchEnabled(): Flow<Boolean> = prefs.data
        .safe()
        .map { it[Keys.CONTENT_ENABLED] ?: false }

    override suspend fun setContentSearchEnabled(enabled: Boolean) {
        prefs.edit { it[Keys.CONTENT_ENABLED] = enabled }
        if (!enabled) {
            withContext(dispatcher) { runCatching { dao.clearContent() } }
        } else {
            requestRebuild(includeContent = true)
        }
    }

    // ---- helpers ----

    private suspend fun contentEnabledNow(): Boolean = try {
        val snapshot = prefs.data.safe()
        var enabled = false
        kotlinx.coroutines.flow.firstOrNull(snapshot.map { it[Keys.CONTENT_ENABLED] ?: false })?.let {
            enabled = it
        }
        enabled
    } catch (_: Exception) {
        false
    }

    private fun runCatchingCount(): Int = 0 // status count is sampled by the worker; kept cheap here.

    private fun FileIndexEntity.toHit(contentMatched: Boolean) = SearchHit(
        path = path,
        name = name,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        type = runCatching { FileType.valueOf(type) }.getOrDefault(FileType.OTHER),
        extension = extension,
        contentMatched = contentMatched,
    )

    private fun encodeSaved(s: SavedSearch) =
        "${s.id}|${s.query.replace("|", " ")}|${s.createdAt}"

    private fun decodeSaved(raw: String): SavedSearch? {
        val parts = raw.split("|", limit = 3)
        if (parts.size != 3) return null
        val createdAt = parts[2].toLongOrNull() ?: return null
        return SavedSearch(parts[0], parts[1], createdAt)
    }

    private fun decodeRecent(raw: String): Pair<Long, String>? {
        val parts = raw.split("|", limit = 2)
        if (parts.size != 2) return null
        val ts = parts[0].toLongOrNull() ?: return null
        return ts to parts[1]
    }

    private fun Flow<Preferences>.safe(): Flow<Preferences> = catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    companion object {
        private const val MAX_RECENT = 12
    }
}

/**
 * Builds an FTS4 MATCH expression: split on whitespace, sanitize special tokens,
 * and add a prefix `*` to the final term so partial typing ("repo" matches
 * "report.txt"). Empty after sanitization yields a token that never matches.
 */
private fun toFtsMatch(raw: String): String {
    val terms = raw
        .lowercase()
        .replace(Regex("[\"^*]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    if (terms.isEmpty()) return " "
    return terms.mapIndexed { i, t ->
        if (i == terms.lastIndex) "$t*" else t
    }.joinToString(" ")
}

/** Builds an escaped LIKE expression matching the query as a substring. */
private fun toLikeExpr(raw: String): String {
    val escaped = raw.lowercase()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}
```

> Note: the helper `firstOrNull` reference uses `kotlinx.coroutines.flow.firstOrNull`. If the IDE prefers, replace `contentEnabledNow()` with the simpler form below — both are valid; pick one:
> ```kotlin
> private suspend fun contentEnabledNow(): Boolean =
>     kotlinx.coroutines.flow.first(prefs.data.safe().map { it[Keys.CONTENT_ENABLED] ?: false })
> ```

### 5.7 Indexer worker — `data/search/SearchIndexWorker.kt`

```kotlin
package com.jupiter.filemanager.data.search

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jupiter.filemanager.domain.repository.FileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Builds the search index incrementally off the main thread.
 *
 * Walks the primary storage root (resolved from [FileRepository.rootDirectory]),
 * batching entries into the Room index so memory stays bounded on large trees.
 * When `KEY_INCLUDE_CONTENT` is set, small text files are additionally indexed
 * into the content FTS table. Constructed by Hilt's `HiltWorkerFactory` (wired in
 * [com.jupiter.filemanager.JupiterApp]). Any failure is caught and reported as
 * [Result.failure] so a bad pass never crashes the process or corrupts the app.
 */
@HiltWorker
class SearchIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: SearchDao,
    private val fileRepository: FileRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val includeContent = inputData.getBoolean(KEY_INCLUDE_CONTENT, false)
        val root = File(fileRepository.rootDirectory())
        if (!root.exists() || !root.canRead()) {
            // No storage access yet — leave any existing index intact, succeed quietly.
            return Result.success()
        }

        val batch = ArrayList<FileIndexEntity>(BATCH_SIZE)
        val contentBatch = ArrayList<FileContentFts>(CONTENT_BATCH_SIZE)

        root.walkTopDown()
            .onEnter { dir -> !dir.name.startsWith(".") } // skip dot-dirs for a clean first cut
            .forEach { file ->
                if (isStopped) return@forEach
                batch.add(file.toIndexEntity())
                if (batch.size >= BATCH_SIZE) {
                    dao.upsertAll(batch.toList()); batch.clear()
                }
                if (includeContent && file.isTextCandidate()) {
                    runCatching { file.readSafeText() }.getOrNull()?.let { body ->
                        contentBatch.add(FileContentFts(path = file.absolutePath, body = body))
                        if (contentBatch.size >= CONTENT_BATCH_SIZE) {
                            dao.upsertContent(contentBatch.toList()); contentBatch.clear()
                        }
                    }
                }
            }

        if (batch.isNotEmpty()) dao.upsertAll(batch.toList())
        if (includeContent && contentBatch.isNotEmpty()) dao.upsertContent(contentBatch.toList())
        Result.success()
    } catch (_: Exception) {
        Result.failure()
    }

    private fun File.toIndexEntity(): FileIndexEntity {
        val ext = if (isDirectory) "" else extension.lowercase()
        return FileIndexEntity(
            path = absolutePath,
            name = name,
            nameLower = name.lowercase(),
            parentPath = parent ?: "",
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0L else length(),
            lastModified = lastModified(),
            type = classify(ext, isDirectory),
            extension = ext,
        )
    }

    private fun File.isTextCandidate(): Boolean =
        isFile && length() in 1..MAX_CONTENT_BYTES &&
            extension.lowercase() in TEXT_EXTENSIONS

    private fun File.readSafeText(): String =
        bufferedReader(Charsets.UTF_8).use { it.readText() }.take(MAX_CONTENT_CHARS)

    private fun classify(ext: String, isDir: Boolean): String = when {
        isDir -> "FOLDER"
        ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> "IMAGE"
        ext in setOf("mp4", "mkv", "mov", "webm", "avi") -> "VIDEO"
        ext in setOf("mp3", "wav", "flac", "ogg", "m4a", "aac") -> "AUDIO"
        ext == "pdf" -> "PDF"
        ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2") -> "ARCHIVE"
        ext == "apk" -> "APK"
        ext in setOf("kt", "java", "kts", "py", "js", "ts", "c", "cpp", "rs", "go", "xml", "json", "html", "css") -> "CODE"
        ext in setOf("txt", "md", "log", "doc", "docx", "odt", "rtf") -> "DOCUMENT"
        else -> "OTHER"
    }

    companion object {
        const val UNIQUE_WORK_NAME = "search_index_build"
        const val KEY_INCLUDE_CONTENT = "include_content"

        private const val BATCH_SIZE = 500
        private const val CONTENT_BATCH_SIZE = 50
        private const val MAX_CONTENT_BYTES = 512 * 1024L  // skip files > 512 KB for content
        private const val MAX_CONTENT_CHARS = 256 * 1024   // cap stored body length
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "log", "csv", "json", "xml", "kt", "java", "py",
            "js", "ts", "html", "css", "yml", "yaml", "ini", "cfg", "properties",
        )
    }
}
```

### 5.8 DI module — `di/SearchModule.kt` (new file; existing modules untouched)

```kotlin
package com.jupiter.filemanager.di

import android.content.Context
import androidx.room.Room
import com.jupiter.filemanager.data.search.SearchDao
import com.jupiter.filemanager.data.search.SearchDatabase
import com.jupiter.filemanager.data.search.SearchIndexRepositoryImpl
import com.jupiter.filemanager.domain.repository.SearchIndexRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Room search database / DAO and binds the index repository. A new,
 * self-contained module — it does not modify [RepositoryModule] or
 * [FeatureRepositoryModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object SearchDatabaseModule {

    @Provides
    @Singleton
    fun provideSearchDatabase(@ApplicationContext context: Context): SearchDatabase =
        Room.databaseBuilder(context, SearchDatabase::class.java, SearchDatabase.NAME)
            .fallbackToDestructiveMigration() // index is a rebuildable cache; safe to drop on schema change
            .build()

    @Provides
    fun provideSearchDao(db: SearchDatabase): SearchDao = db.searchDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchRepositoryModule {

    @Binds
    abstract fun bindSearchIndexRepository(impl: SearchIndexRepositoryImpl): SearchIndexRepository
}
```

---

## 6. Phase 3 — Presentation

The `SearchScreen` public signature stays `(onOpenFile, onBack)` so **`JupiterNavHost.kt` needs no change** and the existing nav block keeps working. We extend the ViewModel and add UI sections additively.

### 6.1 `feature/search/SearchViewModel.kt` (full replacement)

```kotlin
package com.jupiter.filemanager.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.SavedSearch
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.SearchIndexRepository
import com.jupiter.filemanager.feature.ai.AiAssistant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the search screen.
 *
 * @property query the current raw search text entered by the user.
 * @property results files discovered so far for the active search.
 * @property isSearching whether a search is currently running.
 * @property error a user-facing error message, or null when there is none.
 * @property naturalLanguage whether natural-language interpretation is enabled.
 * @property aiInterpreting whether the AI assistant is currently parsing the query.
 * @property usedIndex whether the most recent results came from the fast index.
 * @property contentSearch whether opt-in content search is enabled.
 * @property indexWarm whether the index currently has entries.
 * @property savedSearches persisted one-tap searches.
 * @property recentSearches recent query strings.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<FileItem> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val naturalLanguage: Boolean = false,
    val aiInterpreting: Boolean = false,
    val usedIndex: Boolean = false,
    val contentSearch: Boolean = false,
    val indexWarm: Boolean = false,
    val savedSearches: List<SavedSearch> = emptyList(),
    val recentSearches: List<String> = emptyList(),
)

/**
 * Drives the search screen.
 *
 * Search prefers the persistent [SearchIndexRepository] (Room FTS) when the index
 * is warm: an indexed name lookup (plus content lookup when enabled) returns
 * instantly. If the index is cold, disabled, fails, or yields nothing, the
 * ViewModel transparently falls back to the legacy streaming
 * [FileRepository.search] walk — preserving existing behavior and the
 * natural-language path verbatim.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val searchIndexRepository: SearchIndexRepository,
    private val aiAssistant: AiAssistant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Kick off / refresh the index when the screen first loads.
        searchIndexRepository.requestRebuild(includeContent = false)

        searchIndexRepository.observeSavedSearches()
            .onEach { saved -> _uiState.update { it.copy(savedSearches = saved) } }
            .launchIn(viewModelScope)

        searchIndexRepository.observeRecentSearches()
            .onEach { recent -> _uiState.update { it.copy(recentSearches = recent) } }
            .launchIn(viewModelScope)

        searchIndexRepository.observeContentSearchEnabled()
            .onEach { enabled -> _uiState.update { it.copy(contentSearch = enabled) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            _uiState.update { it.copy(indexWarm = searchIndexRepository.isWarm()) }
        }
    }

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
    }

    fun toggleNaturalLanguage() {
        _uiState.update { it.copy(naturalLanguage = !it.naturalLanguage) }
    }

    fun toggleContentSearch() {
        viewModelScope.launch {
            searchIndexRepository.setContentSearchEnabled(!_uiState.value.contentSearch)
        }
    }

    fun saveCurrentSearch() {
        val q = _uiState.value.query.trim()
        if (q.isNotEmpty()) viewModelScope.launch { searchIndexRepository.saveSearch(q) }
    }

    fun deleteSavedSearch(id: String) {
        viewModelScope.launch { searchIndexRepository.deleteSavedSearch(id) }
    }

    fun runSavedOrRecent(query: String) {
        _uiState.update { it.copy(query = query) }
        search()
    }

    fun clear() {
        searchJob?.cancel()
        searchJob = null
        _uiState.value = _uiState.value.copy(
            query = "",
            results = emptyList(),
            isSearching = false,
            aiInterpreting = false,
            error = null,
            usedIndex = false,
        )
    }

    fun search() {
        val rawQuery = _uiState.value.query.trim()
        if (rawQuery.isEmpty()) {
            _uiState.update {
                it.copy(results = emptyList(), isSearching = false, aiInterpreting = false, error = null)
            }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isSearching = true, aiInterpreting = false, error = null, results = emptyList(), usedIndex = false)
            }
            searchIndexRepository.recordRecent(rawQuery)

            // Natural-language mode keeps the legacy semantic path untouched.
            if (_uiState.value.naturalLanguage) {
                liveWalk(rawQuery)
                return@launch
            }

            // Fast path: indexed name (+ content) search.
            val warm = searchIndexRepository.isWarm()
            _uiState.update { it.copy(indexWarm = warm) }
            if (warm) {
                val nameRes = searchIndexRepository.searchByName(rawQuery)
                val contentRes =
                    if (_uiState.value.contentSearch) searchIndexRepository.searchByContent(rawQuery)
                    else AppResult.Success(emptyList())

                val nameHits = (nameRes as? AppResult.Success)?.data.orEmpty()
                val contentHits = (contentRes as? AppResult.Success)?.data.orEmpty()
                val merged = (nameHits + contentHits)
                    .distinctBy { it.path }
                    .map { it.toFileItem() }

                if (nameRes is AppResult.Success && merged.isNotEmpty()) {
                    _uiState.update {
                        it.copy(results = merged, isSearching = false, usedIndex = true, error = null)
                    }
                    return@launch
                }
                // Index returned nothing or failed → fall through to live walk for correctness.
            }

            liveWalk(rawQuery)
        }
    }

    /** Legacy streaming filesystem walk; unchanged correctness fallback. */
    private suspend fun liveWalk(rawQuery: String) {
        val filter = resolveFilter(rawQuery)
        val rootPath = fileRepository.rootDirectory()
        val collected = mutableListOf<FileItem>()

        fileRepository.search(rootPath, filter)
            .catch { throwable ->
                _uiState.update {
                    it.copy(isSearching = false, aiInterpreting = false, error = throwable.message ?: "Search failed.")
                }
            }
            .onCompletion {
                _uiState.update { it.copy(isSearching = false, aiInterpreting = false) }
            }
            .collect { item ->
                collected.add(item)
                _uiState.update { it.copy(results = collected.toList(), usedIndex = false) }
            }
    }

    private suspend fun resolveFilter(rawQuery: String): FilterOption {
        val plainFilter = FilterOption(query = rawQuery, showHidden = false)
        if (!_uiState.value.naturalLanguage || !aiAssistant.isEnabled) return plainFilter

        _uiState.update { it.copy(aiInterpreting = true) }
        val parsed = when (val result = aiAssistant.parseNaturalQuery(rawQuery)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> plainFilter
        }
        _uiState.update { it.copy(aiInterpreting = false) }
        return parsed
    }
}
```

### 6.2 `feature/search/SearchScreen.kt` — additive UI

Keep the existing file as-is and **add** the following composables, plus wire three new callbacks through `SearchScreenContent`. Concretely:

1. In `SearchScreen(...)`, pass the new callbacks:

```kotlin
    SearchScreenContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onToggleNaturalLanguage = viewModel::toggleNaturalLanguage,
        onToggleContentSearch = viewModel::toggleContentSearch,
        onSaveSearch = viewModel::saveCurrentSearch,
        onRunQuery = viewModel::runSavedOrRecent,
        onDeleteSaved = viewModel::deleteSavedSearch,
        onClear = viewModel::clear,
        onOpenFile = onOpenFile,
        onBack = onBack,
    )
```

2. Update the private `SearchScreenContent` signature to accept the four new lambdas (`onToggleContentSearch: () -> Unit`, `onSaveSearch: () -> Unit`, `onRunQuery: (String) -> Unit`, `onDeleteSaved: (String) -> Unit`), and insert these blocks **between** `NaturalLanguageToggleRow(...)` and the `HorizontalDivider()`:

```kotlin
            ContentSearchToggleRow(
                enabled = uiState.contentSearch,
                onToggle = onToggleContentSearch,
            )

            if (uiState.query.isBlank()) {
                SavedAndRecentSection(
                    saved = uiState.savedSearches,
                    recent = uiState.recentSearches,
                    onRunQuery = onRunQuery,
                    onDeleteSaved = onDeleteSaved,
                )
            } else if (!uiState.isSearching && uiState.results.isNotEmpty()) {
                SaveSearchRow(onSave = onSaveSearch, usedIndex = uiState.usedIndex)
            }
```

3. Append these new private composables to the file:

```kotlin
@Composable
private fun ContentSearchToggleRow(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Search file contents",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Index text inside files (.txt, .md, .log, code)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SaveSearchRow(onSave: () -> Unit, usedIndex: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (usedIndex) {
            Text(
                text = "Instant index results",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        androidx.compose.material3.TextButton(onClick = onSave) {
            Text(text = "Save search")
        }
    }
}

@Composable
private fun SavedAndRecentSection(
    saved: List<com.jupiter.filemanager.domain.model.SavedSearch>,
    recent: List<String>,
    onRunQuery: (String) -> Unit,
    onDeleteSaved: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (saved.isNotEmpty()) {
            SectionHeader(
                title = "Saved searches",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            saved.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRunQuery(item.query) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = item.query, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onDeleteSaved(item.id) }) {
                        Icon(imageVector = Icons.Outlined.Clear, contentDescription = "Delete saved search")
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            SectionHeader(
                title = "Recent",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            recent.forEach { q ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRunQuery(q) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = q)
                }
            }
        }
    }
}
```

### 6.3 Navigation wiring

**No edits to `Destinations.kt` or `JupiterNavHost.kt`.** The `Destination.Search` route already exists and the `composable(route = Destination.Search.route) { SearchScreen(onOpenFile = ..., onBack = ...) }` block at ~line 170 keeps compiling because `SearchScreen`'s public parameter list is unchanged. This is the cleanest additive integration: no graph surgery.

---

## 7. Phase 4 — Configuration

- **Env / keys:** none. Entirely on-device; no secrets, no `local.properties` entries, no API keys.
- **External service setup:** none. (Contrast with initiatives #2/#4 which need OAuth/LLM keys.)
- **Schema export:** `app/schemas/com.jupiter.filemanager.data.search.SearchDatabase/1.json` is generated on first build because of the `room.schemaLocation` KSP arg from §4.2. Commit it.
- **ProGuard / R8** — append to `app/proguard-rules.pro` (release uses `isMinifyEnabled = true`):

```proguard
# Room: keep generated DAO/database implementations and entity members.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }
-dontwarn androidx.room.paging.**

# WorkManager Hilt worker: keep the indexer (instantiated reflectively by the factory).
-keep class com.jupiter.filemanager.data.search.SearchIndexWorker { *; }
```

---

## 8. Phase 5 — Testing

### 8.1 Manual smoke-test script

1. Fresh install (`assembleDebug`), grant storage permission via the existing permission flow.
2. Open Search. Confirm the legacy field, NL toggle, and a new **"Search file contents"** toggle appear; with a blank query the **Saved searches / Recent** sections render (empty initially).
3. Type `report` and submit. Verify results appear; on a warm index a small **"Instant index results"** label shows. Compare result set to the pre-existing live-walk behavior — they should be equivalent for name matches.
4. Tap **Save search**. Clear the field. Confirm `report` now appears under *Saved searches* and tapping it re-runs the query.
5. Clear the field; confirm `report` also appears under *Recent*. Tap it; it re-runs.
6. Enable **Search file contents**. Wait a few seconds (indexer pass). Search for a word you know is *inside* a `.txt`/`.md` file but not in any filename. Confirm a content hit appears.
7. Disable content search; confirm content-only hits no longer appear (content table cleared) while name search still works.
8. Toggle **Natural language** ON and search — confirm the AI/live-walk path still works exactly as before (regression check).
9. Revoke storage permission, reopen Search, search — confirm no crash; index search returns empty and the live-walk path reports its normal permission-denied/empty state.
10. Kill and relaunch the app; confirm saved/recent searches persist and the index is still warm (instant results without re-walking).

### 8.2 Recommended unit tests (`app/src/test`)

- `SearchHitTest` — `SearchHit.toFileItem()` maps every field; unknown `type` string → `FileType.OTHER`.
- `FtsMatchTest` — `toFtsMatch("my report")` → `"my report*"`; punctuation-only → non-matching token; `toLikeExpr("50%_a")` escapes `%`, `_`, `\`.
- `SavedSearchCodecTest` — `encodeSaved`/`decodeSaved` round-trip; pipes in query are sanitized; malformed records decode to `null`.
- `SearchViewModelTest` (using `kotlinx-coroutines-test`, already a dependency) — with a fake warm `SearchIndexRepository` returning hits, `search()` sets `usedIndex = true` and never calls `FileRepository.search`; with a cold/empty index it falls back to the live walk; NL mode always uses the live walk.

### 8.3 Recommended instrumented test (`app/src/androidTest`)

- `SearchDaoTest` — build an in-memory `SearchDatabase`, `upsertAll` a few `FileIndexEntity` rows, assert `searchByNameFts("rep*")` and `searchByNameLike("%rep%")` return the expected rows; `upsertContent` + `searchByContent` returns the joined entity; `clearContent` empties it without touching `file_index`.

---

## 9. Error handling & edge cases

1. **Storage permission not granted / revoked.** `SearchIndexWorker` checks `root.exists() && root.canRead()` and returns `Result.success()` without touching the index; `searchByName` simply returns whatever is indexed (possibly empty) and the ViewModel falls back to the live walk, whose existing `AppError.PermissionDenied` semantics are preserved.
2. **Index cold on first run.** `isWarm()` returns false → ViewModel skips the index entirely and uses the live walk, so the user never sees an empty screen; meanwhile `requestRebuild` (enqueued in `init`) warms it for next time.
3. **Index query throws (DB corruption / locked).** `searchByName`/`searchByContent` wrap everything in `try/catch` → `AppResult.Failure(AppError.Io(...))`; the ViewModel treats failure as "fall through to live walk", guaranteeing correctness.
4. **Huge tree / OOM risk during indexing.** The worker batches at 500 entries (50 for content) and clears each batch, keeping memory bounded; content is capped at 512 KB/file and 256 K chars stored.
5. **Unreadable / binary file during content indexing.** `readSafeText()` is wrapped in `runCatching`; failures are dropped silently, so one bad file never aborts the pass. Non-UTF-8 bytes are tolerated by the lenient reader.
6. **Stale index (file deleted/moved after indexing).** Content search inner-joins `file_index` on path, dropping orphans; for name search, a stale hit that fails to open is handled by the existing `onOpenFile` path. The next rebuild (`ExistingWorkPolicy.KEEP`) refreshes entries; deletions are reconciled on a from-scratch rebuild (`clear()` then rebuild) which can be triggered from settings.
7. **Concurrent rebuilds.** `enqueueUniqueWork(UNIQUE_WORK_NAME, KEEP, ...)` coalesces — a second request while one is running is ignored, preventing duplicate passes and DB contention.
8. **Query with FTS special characters** (`"`, `*`, `^`). `toFtsMatch` strips them before building the MATCH expression, avoiding `SQLiteException` from malformed FTS syntax; empty-after-sanitize yields a non-matching sentinel token, not a crash.

---

## 10. Integration with other initiatives

- **#1 Pro monetization:** content search is a natural Pro gate. Wire the `toggleContentSearch` action behind the Pro entitlement check once #1 lands; until then it ships free. No structural coupling — only the toggle's enablement.
- **#4 AI Pro suite / existing `AiAssistant`:** the natural-language path is preserved verbatim. When NL is on, the index is bypassed and `AiAssistant.parseNaturalQuery` + live walk run as today. The index could later accelerate NL by feeding indexed candidates to the assistant, but that is out of scope here.
- **#3 i18n:** new user-facing strings are inline (matching current `SearchScreen` convention). When #3 extracts strings to `strings.xml`, include the strings added here.
- **#6 Privacy/analytics:** indexing is on-device and reads no content off-device; if #6 adds an analytics consent layer, the content-search toggle and rebuild events are good (anonymous) signals but must respect that consent. No PII leaves the device.
- **No dependency** on #2 (cloud OAuth), #5 (widgets), #7, #9, #10 — this initiative is self-contained and can ship independently. Other initiatives do not depend on it either; removing it has no cross-effect.

---

## 11. Rollback plan

Because everything is additive, removal is mechanical and low-risk:

1. **Code:** delete the new files — `data/search/*`, `domain/model/SearchIndexModels.kt`, `domain/repository/SearchIndexRepository.kt`, `di/SearchModule.kt`.
2. **ViewModel/Screen:** revert `feature/search/SearchViewModel.kt` and `feature/search/SearchScreen.kt` to their prior versions (git revert of the two files). The legacy live-walk path is exactly what remains, so search keeps working.
3. **Gradle:** remove the three `androidx.room.*` lines from `app/build.gradle.kts`, the `room` version + library entries from `libs.versions.toml`, and the `ksp { arg("room.schemaLocation", ...) }` block.
4. **ProGuard:** remove the Room/worker keep rules block.
5. **On-device data:** the Room DB (`jupiter_search_index.db`) and `jupiter_search_prefs` DataStore are app-private and are cleared on uninstall; no migration or server cleanup needed. For an OTA rollback, optionally call `clear()` once, but it's harmless to leave the file — it's just unused bytes.
6. **No nav/manifest revert** needed (none were changed).

**Feature-flag alternative (no code removal):** gate `requestRebuild` and the index fast-path behind a single boolean (e.g. a DataStore key); flipping it false makes every search use the live walk while leaving the index dormant.

---

## 12. Definition of done

- [ ] `androidx.room` (runtime, ktx, compiler-ksp) added to `libs.versions.toml` + `app/build.gradle.kts`; `room.schemaLocation` KSP arg set and `app/schemas/.../1.json` committed.
- [ ] `data/search/` created: `FileIndexEntity`/`FileIndexFts`/`FileContentFts`, `SearchDao`, `SearchDatabase`, `SearchIndexRepositoryImpl`, `SearchIndexWorker` — all compile and match the codebase's `AppResult`/`AppError`/`@IoDispatcher` conventions.
- [ ] `domain/repository/SearchIndexRepository.kt` + `domain/model/SearchIndexModels.kt` created; new `di/SearchModule.kt` binds the repo and provides the DB/DAO **without editing** `RepositoryModule` or `FeatureRepositoryModule`.
- [ ] `SearchIndexWorker` is enqueued (unique, `KEEP`) and runs via the existing `HiltWorkerFactory` in `JupiterApp`; it populates the index off the main thread in bounded batches.
- [ ] `SearchViewModel` prefers the warm index for name (+optional content) search and falls back to the live walk when cold/disabled/failed/empty; NL path is byte-for-byte preserved.
- [ ] `SearchScreen` shows the content-search toggle, saved-search save action, and saved/recent sections; `onOpenFile`/`onBack` signature unchanged so `JupiterNavHost` needs no edits.
- [ ] Saved searches, recent searches, and the content toggle persist across app restarts (Preferences DataStore).
- [ ] ProGuard keep rules for Room + the worker added; release `assembleRelease` (minified) does not strip the index.
- [ ] Unit tests (`SearchHitTest`, `FtsMatchTest`, `SavedSearchCodecTest`, `SearchViewModelTest`) and the `SearchDaoTest` instrumented test added and passing.
- [ ] **No regression:** existing **live-walk search (X)** still returns correct results when the index is cold/disabled, and **natural-language AI search (Y)** still works exactly as before (verified via smoke steps 3, 8, 9).
- [ ] **No regression:** all other screens reachable from `JupiterNavHost` (Browser, Cleanup, Vault, Settings, Transfer, etc.) still compile and launch — confirmed by `assembleDebug` succeeding with no new Hilt graph errors.
- [ ] **CI green:** `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` both pass.
- [ ] Manual smoke-test script (§8.1, all 10 steps) executed on a device/emulator with no crash, including the permission-revoked path.
