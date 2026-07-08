# Initiative #4 — AI Pro Suite (Claude): Summarize, Smart-Rename, Auto-Organize, Semantic Search

> Dev-prompt for an autonomous AI coding agent. Implement end-to-end, **additively**, against the **real** Jupiter codebase at `/home/user/Jupiter`. Read every referenced real file before editing. Produce compiling Kotlin only — no pseudocode, no TODO stubs that break the build. Every AI action is **confirm-before-apply** and **no-hallucination**: the model proposes, the user approves, and only then does Jupiter mutate the file system using the existing `FileRepository`.

---

## 1. Initiative header

**Title:** AI Pro Suite (Claude) — Summarize, Smart-Rename, Auto-Organize, Semantic Search
**Value range:** **+€160k – €320k** annual incremental revenue.
**Business case:** Jupiter already ships a key-gated `AnthropicAiAssistant` that quietly powers a chat screen and natural-language search. That investment is under-monetized: most users never discover it, and it does one-shot text replies rather than file actions. This initiative promotes the assistant into the marquee Pro differentiator — four concrete, trust-building actions (document/folder **Summarize**, AI **Smart-Rename**, one-tap **Auto-Organize** suggestions, and natural-language **Semantic Search**) that each preview an exact diff and require explicit confirmation before any file is touched. Because the actions are visible, repeatable, and obviously useful on the user's own files, they convert free users to Pro at a materially higher rate than an invisible chat box, and they raise retention by making Jupiter the place users go to tidy a messy `Download` folder. The "confirm-before-apply, never fabricate" contract is the wedge: it differentiates Jupiter from competitors that auto-rename destructively, and it is cheap to honor because all mutations reuse the already-audited `FileRepository.rename`/`createFolder`/`move`.

---

## 2. Codebase context

Current relevant real file tree (all paths under `/home/user/Jupiter/app/src/main/java/com/jupiter/filemanager`):

```
feature/ai/
  AiAssistant.kt              # interface: isEnabled, suggestName, explainStorage, parseNaturalQuery   [EXTEND]
  AnthropicAiAssistant.kt     # real Anthropic Messages API impl, key-gated, @Singleton @Inject        [EXTEND]
  NoOpAiAssistant.kt          # inert fallback returning Failure                                        [EXTEND]
  AiAssistantUiState.kt       # ChatMessage/ChatRole + AiAssistantUiState                               [UNCHANGED]
  AiAssistantViewModel.kt     # chat VM                                                                 [UNCHANGED]
  AiAssistantScreen.kt        # chat Compose screen                                                     [UNCHANGED]
feature/search/
  SearchViewModel.kt          # already calls aiAssistant.parseNaturalQuery in NL mode                  [UNCHANGED — verify only]
  SearchScreen.kt             # search UI                                                               [UNCHANGED]
feature/cleanup/              # CleanupScreen/ViewModel, Duplicates, SmartMerge — peer "actions" screens [REFERENCE]
core/result/
  AppResult.kt                # sealed Success/Failure + onSuccess/onFailure/map/getOrNull              [REUSE]
  AppError.kt                 # PermissionDenied/NotFound/AccessDenied/AlreadyExists/Io/Unknown          [REUSE]
di/
  CoroutineModule.kt          # @IoDispatcher / @DefaultDispatcher / @MainDispatcher qualifiers          [REUSE]
  AiModule.kt                 # binds AiAssistant -> AnthropicAiAssistant                                [UNCHANGED]
data/preferences/
  SettingsDataStore.kt        # aiApiKey / aiEnabled flows + setters                                    [REUSE]
domain/model/
  FileItem.kt                 # path/name/isDirectory/sizeBytes/type/extension/...                       [REUSE]
  FileType.kt                 # FOLDER/IMAGE/VIDEO/AUDIO/DOCUMENT/PDF/ARCHIVE/APK/CODE/OTHER             [REUSE]
  FilterOption.kt             # query/showHidden/typeFilter                                             [REUSE]
domain/repository/
  FileRepository.kt           # listFiles/getFile/createFolder/rename/move/search/rootDirectory          [REUSE]
ui/navigation/
  Destinations.kt             # sealed Destination(route) — has AiAssistant, Cleanup, Search             [EXTEND]
  JupiterNavHost.kt           # NavHost wiring                                                          [EXTEND]
```

**What exists:** A working, key-gated Anthropic client (`claude-3-5-haiku-20241022`, Messages API, `x-api-key` header, OkHttp, `AppResult` error mapping, `@IoDispatcher`). NL search already wires `parseNaturalQuery`. The chat screen works.

**What is missing:** (a) Action-shaped AI operations on the interface — summarize a file/folder, rename a batch, propose an organize plan; (b) a domain layer for those operations with confirm-before-apply orchestration; (c) Compose screens + ViewModels for Summarize / Smart-Rename / Auto-Organize; (d) a Room-backed history of applied AI actions so users can see/undo what happened; (e) navigation entries; (f) universal-action-sheet hooks so any `FileItem` can launch an AI action.

**What needs change (additively):** `AiAssistant` interface gains three suspend methods (with default-throwing... no — concrete additions to all three impls). `Destinations.kt` and `JupiterNavHost.kt` gain four routes. Nothing existing is removed or rewired; new code is strictly additive.

---

## 3. Pre-conditions

**Gradle deps to add** (all version refs already exist except Room; see Phase 1):
- `androidx.room:room-runtime:2.6.1`
- `androidx.room:room-ktx:2.6.1`
- `androidx.room:room-compiler:2.6.1` (KSP)
- (already present, reused: `com.squareup.okhttp3:okhttp:4.12.0`, `androidx.datastore:datastore-preferences:1.1.1`, Hilt 2.52, coroutines 1.9.0, `org.json` is bundled in Android.)

**Manifest / permissions:** No new runtime permissions. The Anthropic call uses `android.permission.INTERNET`, which the app already declares (verify in `app/src/main/AndroidManifest.xml`; add `<uses-permission android:name="android.permission.INTERNET" />` if and only if it is absent). No `MANAGE_EXTERNAL_STORAGE` change — all file mutations go through the existing `FileRepository`, which already holds the storage contract.

**Play Console:** None required for this initiative directly. (Entitlement gating is owned by Initiative #1; this initiative only checks an `isPro` flag if available, otherwise gates purely on a configured API key — see §10.)

**API key:** Reuses `SettingsDataStore.aiApiKey`. The user obtains a Claude API key at <https://console.anthropic.com/settings/keys> and pastes it in Settings → AI (existing flow). No key is committed to source. When the key is blank, every AI Pro action degrades gracefully to a "configure AI" message (mirroring `NoOpAiAssistant`).

---

## 4. Phase 1 — Gradle + Manifest + resources

### 4.1 `gradle/libs.versions.toml`

Add under `[versions]`:

```toml
room = "2.6.1"
```

Add under `[libraries]`:

```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

If the project does not already apply KSP, add under `[versions]` a `ksp` aligned to the Kotlin version already in the file (e.g. `ksp = "2.0.21-1.0.25"` for Kotlin 2.0.21 — read the existing `kotlin` version ref and match), and under `[plugins]`:

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

> Read `gradle/libs.versions.toml` first. If Hilt is already wired via `kapt` rather than `ksp`, use `kapt` for Room too (`kapt(libs.androidx.room.compiler)`) to avoid mixing processors. Pick whichever processor the project already uses for Hilt.

### 4.2 `app/build.gradle.kts`

In the `plugins { }` block, add (only if KSP is not already applied):

```kotlin
alias(libs.plugins.ksp)
```

In `dependencies { }`, add next to the existing `implementation(libs.androidx.datastore.preferences)` line:

```kotlin
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler) // or kapt(...) if Hilt uses kapt
```

In `android { defaultConfig { } }`, add the Room schema export arg (so migrations are reproducible):

```kotlin
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```

(If using kapt: `kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }`.)

### 4.3 `app/src/main/AndroidManifest.xml`

Verify `<uses-permission android:name="android.permission.INTERNET" />` is present (it is, for the existing cloud/transfer stack). No other manifest change.

### 4.4 Resources

No new strings file is strictly required, but add user-facing copy to `app/src/main/res/values/strings.xml` (additive `<string>` entries — do not edit existing ones):

```xml
<string name="ai_summarize_title">Summarize</string>
<string name="ai_smart_rename_title">Smart Rename</string>
<string name="ai_auto_organize_title">Auto-Organize</string>
<string name="ai_semantic_search_title">Semantic Search</string>
<string name="ai_confirm_apply">Apply changes</string>
<string name="ai_not_configured">Add a Claude API key in Settings to enable AI.</string>
```

---

## 5. Phase 2 — Data / domain layer

All new files live under `feature/ai/`. Every operation returns `AppResult`, runs blocking work on `@IoDispatcher`, and never throws across boundaries.

### 5.1 Extend the `AiAssistant` interface

**File: `feature/ai/AiAssistant.kt`** — append three methods and supporting models. Full replacement of the file:

```kotlin
package com.jupiter.filemanager.feature.ai

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.StorageOverview

/**
 * A single AI-generated suggestion surfaced to the user.
 */
data class AiSuggestion(
    val title: String,
    val detail: String,
    val confidence: Float,
)

/** One proposed rename: original item plus the AI's suggested new name. */
data class RenameProposal(
    val item: FileItem,
    val suggestedName: String,
)

/** One proposed move into a (possibly new) destination folder, relative to the scanned root. */
data class OrganizeMove(
    val item: FileItem,
    val targetFolderName: String,
    val rationale: String,
)

/**
 * Optional, isolated AI assistant abstraction.
 *
 * Implementations must never crash the app: when the assistant is not configured
 * or a request cannot be fulfilled, they return [AppResult.Failure] rather than
 * throwing. Consumers should gate AI-specific UI on [isEnabled].
 *
 * All "action" methods (summarize/rename/organize) are *proposal* generators: they
 * return text or structured suggestions only. They never mutate the file system —
 * mutation is the caller's responsibility, behind explicit user confirmation.
 */
interface AiAssistant {

    val isEnabled: Boolean

    suspend fun suggestName(item: FileItem): AppResult<String>

    suspend fun explainStorage(overview: StorageOverview): AppResult<String>

    suspend fun parseNaturalQuery(query: String): AppResult<FilterOption>

    /**
     * Produces a plain-language summary describing the file or folder [item] from
     * its metadata (name, type, size, child listing). For folders, [children] is a
     * representative sample of names/types. The model is instructed never to invent
     * file contents it has not been given.
     */
    suspend fun summarize(item: FileItem, children: List<FileItem>): AppResult<String>

    /**
     * Proposes cleaner, more descriptive names for a batch of [items], preserving
     * each extension. Returns one [RenameProposal] per input item that the model
     * chose to rename; items the model leaves unchanged are omitted.
     */
    suspend fun proposeRenames(items: List<FileItem>): AppResult<List<RenameProposal>>

    /**
     * Proposes an organization plan that groups [items] into a small set of
     * descriptive destination folders (by topic/type/date). Returns the moves to
     * apply; folders that do not yet exist are created on apply.
     */
    suspend fun proposeOrganize(items: List<FileItem>): AppResult<List<OrganizeMove>>
}
```

### 5.2 Extend `AnthropicAiAssistant`

**File: `feature/ai/AnthropicAiAssistant.kt`** — add the three methods plus JSON parsing helpers. Insert the following methods into the class body (after `parseNaturalQuery`, before `// region Internals`), and add the helpers inside `// region Internals`. Do not change existing members.

```kotlin
    override suspend fun summarize(item: FileItem, children: List<FileItem>): AppResult<String> {
        val key = currentKey()
        if (key.isBlank()) return notConfigured()

        val prompt = buildString {
            if (item.isDirectory) {
                append("Summarize this folder for the user in plain language: what it appears to contain ")
                append("and how it is organized. Base your answer ONLY on the metadata below. ")
                append("Do NOT invent file contents you cannot see. Be concise (a short paragraph).\n\n")
                append("Folder: ").append(item.name).append('\n')
                append("Direct children (sample, up to ").append(MAX_CHILDREN).append("):\n")
                children.take(MAX_CHILDREN).forEach { child ->
                    append("- ").append(child.name)
                        .append(if (child.isDirectory) " (folder)" else " (" + child.type.name.lowercase() + ")")
                        .append('\n')
                }
            } else {
                append("Summarize what this file most likely is, from its metadata only. ")
                append("Do NOT fabricate its contents. Be concise (one or two sentences).\n\n")
                append("Name: ").append(item.name).append('\n')
                append("Type: ").append(item.type.name).append('\n')
                append("Extension: ").append(item.extension.ifBlank { "(none)" }).append('\n')
                append("Size (bytes): ").append(item.sizeBytes)
            }
        }
        return requestText(key, prompt)
    }

    override suspend fun proposeRenames(items: List<FileItem>): AppResult<List<RenameProposal>> {
        val key = currentKey()
        if (key.isBlank()) return notConfigured()
        if (items.isEmpty()) return AppResult.Success(emptyList())

        val batch = items.take(MAX_BATCH)
        val prompt = buildString {
            append("Suggest cleaner, more descriptive file names for the files below. ")
            append("Preserve each file's original extension exactly. Keep names filesystem-safe ")
            append("(no slashes, no leading dots). If a name is already good, omit it. ")
            append("Reply with ONLY a JSON array of objects like ")
            append("[{\"original\":\"IMG_0001.jpg\",\"suggested\":\"sunset-beach-2024.jpg\"}]. ")
            append("No prose, no code fences.\n\nFiles:\n")
            batch.forEach { item ->
                append("- ").append(item.name)
                    .append(" [").append(item.type.name.lowercase()).append(", ")
                    .append(item.sizeBytes).append(" bytes]\n")
            }
        }

        return when (val result = requestText(key, prompt)) {
            is AppResult.Success -> AppResult.Success(parseRenameArray(result.data, batch))
            is AppResult.Failure -> result
        }
    }

    override suspend fun proposeOrganize(items: List<FileItem>): AppResult<List<OrganizeMove>> {
        val key = currentKey()
        if (key.isBlank()) return notConfigured()
        if (items.isEmpty()) return AppResult.Success(emptyList())

        val batch = items.take(MAX_BATCH)
        val prompt = buildString {
            append("Propose how to organize these files into a SMALL set of descriptive ")
            append("destination subfolders (group by topic, type, or date). Use 2-6 folders max. ")
            append("Folder names must be filesystem-safe (letters, numbers, spaces, hyphens). ")
            append("Reply with ONLY a JSON array like ")
            append("[{\"file\":\"a.pdf\",\"folder\":\"Invoices\",\"why\":\"PDF invoice\"}]. ")
            append("Every input file must appear exactly once. No prose, no code fences.\n\nFiles:\n")
            batch.forEach { item ->
                append("- ").append(item.name)
                    .append(" [").append(item.type.name.lowercase()).append("]\n")
            }
        }

        return when (val result = requestText(key, prompt)) {
            is AppResult.Success -> AppResult.Success(parseOrganizeArray(result.data, batch))
            is AppResult.Failure -> result
        }
    }
```

Helpers (inside `// region Internals`):

```kotlin
    /** Strips ```json fences and leading prose so the JSON array can be parsed. */
    private fun stripToJsonArray(raw: String): String {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        return if (start in 0 until end) raw.substring(start, end + 1) else raw.trim()
    }

    private fun parseRenameArray(raw: String, batch: List<FileItem>): List<RenameProposal> = try {
        val byName = batch.associateBy { it.name }
        val arr = JSONArray(stripToJsonArray(raw))
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val original = obj.optString("original")
                val suggested = sanitizeFileName(obj.optString("suggested"))
                val item = byName[original] ?: continue
                if (suggested.isNotBlank() && suggested != item.name) {
                    add(RenameProposal(item = item, suggestedName = suggested))
                }
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun parseOrganizeArray(raw: String, batch: List<FileItem>): List<OrganizeMove> = try {
        val byName = batch.associateBy { it.name }
        val arr = JSONArray(stripToJsonArray(raw))
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val item = byName[obj.optString("file")] ?: continue
                val folder = sanitizeFolderName(obj.optString("folder"))
                if (folder.isNotBlank()) {
                    add(OrganizeMove(item = item, targetFolderName = folder, rationale = obj.optString("why")))
                }
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }

    /** Keeps only safe filename characters and never returns a path. */
    private fun sanitizeFileName(raw: String): String =
        raw.trim().trim('"').substringAfterLast('/').substringAfterLast('\\')
            .filter { it.isLetterOrDigit() || it in ALLOWED_NAME_CHARS }
            .trimStart('.')

    private fun sanitizeFolderName(raw: String): String =
        raw.trim().trim('"').replace('/', ' ').replace('\\', ' ')
            .filter { it.isLetterOrDigit() || it in ALLOWED_FOLDER_CHARS }
            .trim()
```

Add to the `companion object`:

```kotlin
        const val MAX_CHILDREN: Int = 40
        const val MAX_BATCH: Int = 30
        const val MAX_TOKENS_ACTION: Int = 1024
        val ALLOWED_NAME_CHARS: Set<Char> = setOf(' ', '-', '_', '.', '(', ')')
        val ALLOWED_FOLDER_CHARS: Set<Char> = setOf(' ', '-', '_')
```

> Note: `requestText` already uses `MAX_TOKENS = 512`. For the batch operations, prefer raising `max_tokens` to `MAX_TOKENS_ACTION`. The cleanest additive change: overload `requestText` with an optional `maxTokens: Int = MAX_TOKENS` parameter and pass `MAX_TOKENS_ACTION` from the new methods. Change the single line `put("max_tokens", MAX_TOKENS)` to `put("max_tokens", maxTokens)` and the signature to `private suspend fun requestText(apiKey: String, userPrompt: String, maxTokens: Int = MAX_TOKENS)`. This does not alter existing call sites (default preserves behavior).

### 5.3 Extend `NoOpAiAssistant`

**File: `feature/ai/NoOpAiAssistant.kt`** — add the three overrides so the interface stays satisfiable for the inert fallback:

```kotlin
    override suspend fun summarize(item: FileItem, children: List<FileItem>): AppResult<String> = notConfigured()

    override suspend fun proposeRenames(items: List<FileItem>): AppResult<List<RenameProposal>> = notConfigured()

    override suspend fun proposeOrganize(items: List<FileItem>): AppResult<List<OrganizeMove>> = notConfigured()
```

### 5.4 Room: AI action history

**File: `feature/ai/data/AiActionEntity.kt`**

```kotlin
package com.jupiter.filemanager.feature.ai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One applied AI Pro action, persisted so the user can review (and, for renames,
 * undo) what the assistant changed. Append-only history; never auto-mutated.
 */
@Entity(tableName = "ai_action_history")
data class AiActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,            // "RENAME" | "ORGANIZE" | "SUMMARIZE"
    val originalPath: String,    // path before the action
    val resultPath: String,      // path after the action (== originalPath for SUMMARIZE)
    val detail: String,          // summary text, or "oldName -> newName"
    val appliedAt: Long,         // epoch millis
)
```

**File: `feature/ai/data/AiActionDao.kt`**

```kotlin
package com.jupiter.filemanager.feature.ai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiActionDao {

    @Insert
    suspend fun insert(entity: AiActionEntity): Long

    @Query("SELECT * FROM ai_action_history ORDER BY appliedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AiActionEntity>>

    @Query("DELETE FROM ai_action_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

**File: `feature/ai/data/AiDatabase.kt`**

```kotlin
package com.jupiter.filemanager.feature.ai.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AiActionEntity::class], version = 1, exportSchema = true)
abstract class AiDatabase : RoomDatabase() {
    abstract fun aiActionDao(): AiActionDao
}
```

### 5.5 Hilt module for Room

**File: `feature/ai/data/AiDataModule.kt`**

```kotlin
package com.jupiter.filemanager.feature.ai.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiDataModule {

    @Provides
    @Singleton
    fun provideAiDatabase(@ApplicationContext context: Context): AiDatabase =
        Room.databaseBuilder(context, AiDatabase::class.java, "jupiter_ai.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAiActionDao(db: AiDatabase): AiActionDao = db.aiActionDao()
}
```

### 5.6 Domain orchestrator: confirm-before-apply

**File: `feature/ai/domain/AiActionsRepository.kt`** — the single place that turns approved proposals into file-system mutations via `FileRepository`, records history, and maps every failure to `AppResult`/`AppError`.

```kotlin
package com.jupiter.filemanager.feature.ai.domain

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.feature.ai.OrganizeMove
import com.jupiter.filemanager.feature.ai.RenameProposal
import com.jupiter.filemanager.feature.ai.data.AiActionDao
import com.jupiter.filemanager.feature.ai.data.AiActionEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of applying a batch of proposals: per-item success/failure detail. */
data class ApplyOutcome(
    val applied: Int,
    val failed: Int,
    val failures: List<String>,
)

/**
 * Applies user-confirmed AI proposals to the real file system using [FileRepository],
 * recording each successful mutation in [AiActionDao]. Nothing here calls the model;
 * proposals arrive already generated and approved.
 */
@Singleton
class AiActionsRepository @Inject constructor(
    private val fileRepository: FileRepository,
    private val dao: AiActionDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** Lists direct children of [path] for summarize/organize scanning. */
    suspend fun children(path: String): AppResult<List<FileItem>> = withContext(io) {
        fileRepository.listFiles(path, SortOption(), FilterOption())
    }

    /** Applies confirmed renames one-by-one; partial success is reported, not fatal. */
    suspend fun applyRenames(proposals: List<RenameProposal>): AppResult<ApplyOutcome> =
        withContext(io) {
            var applied = 0
            val failures = mutableListOf<String>()
            for (p in proposals) {
                when (val r = fileRepository.rename(p.item, p.suggestedName)) {
                    is AppResult.Success -> {
                        applied++
                        runCatching {
                            dao.insert(
                                AiActionEntity(
                                    kind = "RENAME",
                                    originalPath = p.item.path,
                                    resultPath = r.data.path,
                                    detail = p.item.name + " -> " + p.suggestedName,
                                    appliedAt = System.currentTimeMillis(),
                                ),
                            )
                        }
                    }
                    is AppResult.Failure -> failures.add(p.item.name + ": " + r.error.displayMessage)
                }
            }
            AppResult.Success(ApplyOutcome(applied, failures.size, failures))
        }

    /**
     * Applies a confirmed organize plan: creates each destination folder under
     * [rootPath] if missing, then moves each file into it. Move uses the existing
     * [FileRepository.move] flow and waits for completion.
     */
    suspend fun applyOrganize(rootPath: String, moves: List<OrganizeMove>): AppResult<ApplyOutcome> =
        withContext(io) {
            var applied = 0
            val failures = mutableListOf<String>()
            val createdFolders = HashMap<String, String>() // folderName -> absolute path

            for (move in moves) {
                val targetPath = createdFolders.getOrElse(move.targetFolderName) {
                    val resolved = ensureFolder(rootPath, move.targetFolderName)
                    if (resolved == null) {
                        failures.add(move.item.name + ": could not create folder " + move.targetFolderName)
                        continue
                    }
                    createdFolders[move.targetFolderName] = resolved
                    resolved
                }
                val ok = runCatching {
                    fileRepository.move(listOf(move.item), targetPath).toList()
                }.isSuccess
                if (ok) {
                    applied++
                    runCatching {
                        dao.insert(
                            AiActionEntity(
                                kind = "ORGANIZE",
                                originalPath = move.item.path,
                                resultPath = targetPath + "/" + move.item.name,
                                detail = move.item.name + " -> " + move.targetFolderName,
                                appliedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                } else {
                    failures.add(move.item.name + ": move failed")
                }
            }
            AppResult.Success(ApplyOutcome(applied, failures.size, failures))
        }

    /** Records a generated summary so the user can find it later. */
    suspend fun recordSummary(item: FileItem, summary: String) = withContext(io) {
        runCatching {
            dao.insert(
                AiActionEntity(
                    kind = "SUMMARIZE",
                    originalPath = item.path,
                    resultPath = item.path,
                    detail = summary.take(500),
                    appliedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Creates [name] under [parent] (idempotent: returns existing folder if present). */
    private suspend fun ensureFolder(parent: String, name: String): String? {
        val target = parent.trimEnd('/') + "/" + name
        return when (val existing = fileRepository.getFile(target)) {
            is AppResult.Success -> if (existing.data.isDirectory) target else null
            is AppResult.Failure -> when (val created = fileRepository.createFolder(parent, name)) {
                is AppResult.Success -> created.data.path
                is AppResult.Failure ->
                    if (created.error is AppError.AlreadyExists) target else null
            }
        }
    }
}
```

---

## 6. Phase 3 — Presentation

Three screens, each a confirm-before-apply flow. Semantic Search needs no new screen — it is already wired into `SearchViewModel.parseNaturalQuery`; just verify and add an entry point (§6.5). All screens use `@HiltViewModel`, immutable `UiState`, `collectAsStateWithLifecycle`, Material3.

### 6.1 Summarize

**File: `feature/ai/SummarizeViewModel.kt`**

```kotlin
package com.jupiter.filemanager.feature.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.feature.ai.domain.AiActionsRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummarizeUiState(
    val path: String = "",
    val title: String = "",
    val isEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val summary: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SummarizeViewModel @Inject constructor(
    private val aiAssistant: AiAssistant,
    private val fileRepository: FileRepository,
    private val actions: AiActionsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val path: String =
        savedStateHandle.get<String>(Destination.AiSummarize.ARG_PATH).orEmpty()

    private val _uiState = MutableStateFlow(
        SummarizeUiState(path = path, isEnabled = aiAssistant.isEnabled),
    )
    val uiState: StateFlow<SummarizeUiState> = _uiState.asStateFlow()

    init {
        if (path.isNotBlank()) generate()
    }

    fun retry() = generate()

    private fun generate() {
        _uiState.update { it.copy(isLoading = true, error = null, summary = null) }
        viewModelScope.launch {
            val fileResult = fileRepository.getFile(path)
            val item = (fileResult as? AppResult.Success)?.data
            if (item == null) {
                _uiState.update { it.copy(isLoading = false, error = "File not found.") }
                return@launch
            }
            val children = if (item.isDirectory) {
                (actions.children(path) as? AppResult.Success)?.data.orEmpty()
            } else {
                emptyList()
            }
            when (val r = aiAssistant.summarize(item, children)) {
                is AppResult.Success -> {
                    actions.recordSummary(item, r.data)
                    _uiState.update {
                        it.copy(isLoading = false, title = item.name, summary = r.data)
                    }
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = r.error.displayMessage)
                }
            }
        }
    }
}
```

**File: `feature/ai/SummarizeScreen.kt`**

```kotlin
package com.jupiter.filemanager.feature.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarizeScreen(
    onBack: () -> Unit,
    viewModel: SummarizeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { "Summarize" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.error != null -> {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::retry, enabled = state.isEnabled) { Text("Retry") }
                }
                state.summary != null -> Text(
                    state.summary!!,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
```

### 6.2 Smart-Rename

**File: `feature/ai/SmartRenameViewModel.kt`**

```kotlin
package com.jupiter.filemanager.feature.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.feature.ai.domain.AiActionsRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A single row the user can toggle on/off before applying. */
data class RenameRow(
    val proposal: RenameProposal,
    val selected: Boolean = true,
)

data class SmartRenameUiState(
    val folderPath: String = "",
    val isEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val rows: List<RenameRow> = emptyList(),
    val error: String? = null,
    val resultMessage: String? = null,
) {
    val canApply: Boolean get() = !isApplying && rows.any { it.selected }
}

@HiltViewModel
class SmartRenameViewModel @Inject constructor(
    private val aiAssistant: AiAssistant,
    private val actions: AiActionsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val folderPath: String =
        savedStateHandle.get<String>(Destination.AiSmartRename.ARG_PATH).orEmpty()

    private val _uiState = MutableStateFlow(
        SmartRenameUiState(folderPath = folderPath, isEnabled = aiAssistant.isEnabled),
    )
    val uiState: StateFlow<SmartRenameUiState> = _uiState.asStateFlow()

    init {
        if (folderPath.isNotBlank()) scan()
    }

    fun scan() {
        _uiState.update { it.copy(isLoading = true, error = null, rows = emptyList(), resultMessage = null) }
        viewModelScope.launch {
            val children = when (val c = actions.children(folderPath)) {
                is AppResult.Success -> c.data.filter { !it.isDirectory }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = c.error.displayMessage) }
                    return@launch
                }
            }
            when (val r = aiAssistant.proposeRenames(children)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = r.data.map { p -> RenameRow(p) },
                        error = if (r.data.isEmpty()) "No rename suggestions — names already look good." else null,
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = r.error.displayMessage)
                }
            }
        }
    }

    fun toggle(index: Int) {
        _uiState.update { s ->
            s.copy(rows = s.rows.mapIndexed { i, row ->
                if (i == index) row.copy(selected = !row.selected) else row
            })
        }
    }

    fun apply() {
        val selected = _uiState.value.rows.filter { it.selected }.map { it.proposal }
        if (selected.isEmpty()) return
        _uiState.update { it.copy(isApplying = true, error = null) }
        viewModelScope.launch {
            when (val r = actions.applyRenames(selected)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isApplying = false,
                        rows = emptyList(),
                        resultMessage = "Renamed ${r.data.applied} file(s)." +
                            if (r.data.failed > 0) " ${r.data.failed} failed." else "",
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isApplying = false, error = r.error.displayMessage)
                }
            }
        }
    }
}
```

**File: `feature/ai/SmartRenameScreen.kt`**

```kotlin
package com.jupiter.filemanager.feature.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartRenameScreen(
    onBack: () -> Unit,
    viewModel: SmartRenameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Rename") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = viewModel::apply, enabled = state.canApply) {
                    Text("Apply changes")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            state.resultMessage?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.isLoading || state.isApplying) {
                CircularProgressIndicator()
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.rows) { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = row.selected, onCheckedChange = { viewModel.toggle(index) })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                row.proposal.item.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "→ " + row.proposal.suggestedName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
```

### 6.3 Auto-Organize

**File: `feature/ai/AutoOrganizeViewModel.kt`**

```kotlin
package com.jupiter.filemanager.feature.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.feature.ai.domain.AiActionsRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrganizeRow(val move: OrganizeMove, val selected: Boolean = true)

data class AutoOrganizeUiState(
    val folderPath: String = "",
    val isEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val rows: List<OrganizeRow> = emptyList(),
    val error: String? = null,
    val resultMessage: String? = null,
) {
    val canApply: Boolean get() = !isApplying && rows.any { it.selected }
    val folderCount: Int get() = rows.filter { it.selected }.map { it.move.targetFolderName }.distinct().size
}

@HiltViewModel
class AutoOrganizeViewModel @Inject constructor(
    private val aiAssistant: AiAssistant,
    private val actions: AiActionsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val folderPath: String =
        savedStateHandle.get<String>(Destination.AiAutoOrganize.ARG_PATH).orEmpty()

    private val _uiState = MutableStateFlow(
        AutoOrganizeUiState(folderPath = folderPath, isEnabled = aiAssistant.isEnabled),
    )
    val uiState: StateFlow<AutoOrganizeUiState> = _uiState.asStateFlow()

    init {
        if (folderPath.isNotBlank()) scan()
    }

    fun scan() {
        _uiState.update { it.copy(isLoading = true, error = null, rows = emptyList(), resultMessage = null) }
        viewModelScope.launch {
            val children = when (val c = actions.children(folderPath)) {
                is AppResult.Success -> c.data.filter { !it.isDirectory }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = c.error.displayMessage) }
                    return@launch
                }
            }
            when (val r = aiAssistant.proposeOrganize(children)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = r.data.map { m -> OrganizeRow(m) },
                        error = if (r.data.isEmpty()) "Nothing to organize here." else null,
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = r.error.displayMessage)
                }
            }
        }
    }

    fun toggle(index: Int) {
        _uiState.update { s ->
            s.copy(rows = s.rows.mapIndexed { i, row ->
                if (i == index) row.copy(selected = !row.selected) else row
            })
        }
    }

    fun apply() {
        val moves = _uiState.value.rows.filter { it.selected }.map { it.move }
        if (moves.isEmpty()) return
        _uiState.update { it.copy(isApplying = true, error = null) }
        viewModelScope.launch {
            when (val r = actions.applyOrganize(folderPath, moves)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isApplying = false,
                        rows = emptyList(),
                        resultMessage = "Moved ${r.data.applied} file(s)." +
                            if (r.data.failed > 0) " ${r.data.failed} failed." else "",
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isApplying = false, error = r.error.displayMessage)
                }
            }
        }
    }
}
```

**File: `feature/ai/AutoOrganizeScreen.kt`** — mirror `SmartRenameScreen` structure: a `LazyColumn` of `OrganizeRow`s showing `move.item.name → move.targetFolderName` with `move.rationale` as `bodySmall`, a header `Text("${state.folderCount} folder(s) proposed")`, a checkbox per row calling `viewModel.toggle(index)`, and a bottom `Button("Apply changes", enabled = state.canApply, onClick = viewModel::apply)`. Same imports as `SmartRenameScreen.kt`; substitute `AutoOrganizeViewModel`/`AutoOrganizeUiState`/`OrganizeRow`. Show `CircularProgressIndicator()` while `isLoading || isApplying`, the `error` in `colorScheme.error`, and `resultMessage` as `titleMedium`.

### 6.4 Navigation wiring

**File: `ui/navigation/Destinations.kt`** — add under the `// ---- AI & analytics ----` section (additive, do not touch existing `AiAssistant` object):

```kotlin
    data object AiSummarize : Destination("ai_summarize?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "ai_summarize?path=" + android.net.Uri.encode(path)
    }

    data object AiSmartRename : Destination("ai_smart_rename?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "ai_smart_rename?path=" + android.net.Uri.encode(path)
    }

    data object AiAutoOrganize : Destination("ai_auto_organize?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "ai_auto_organize?path=" + android.net.Uri.encode(path)
    }
```

**File: `ui/navigation/JupiterNavHost.kt`** — add imports:

```kotlin
import com.jupiter.filemanager.feature.ai.AutoOrganizeScreen
import com.jupiter.filemanager.feature.ai.SmartRenameScreen
import com.jupiter.filemanager.feature.ai.SummarizeScreen
```

Add inside the `// AI & analytics` block of the `NavHost`, after the existing `Destination.AiAssistant.route` composable:

```kotlin
        composable(
            route = Destination.AiSummarize.route,
            arguments = listOf(
                navArgument(Destination.AiSummarize.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            SummarizeScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Destination.AiSmartRename.route,
            arguments = listOf(
                navArgument(Destination.AiSmartRename.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            SmartRenameScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Destination.AiAutoOrganize.route,
            arguments = listOf(
                navArgument(Destination.AiAutoOrganize.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            AutoOrganizeScreen(onBack = { navController.popBackStack() })
        }
```

### 6.5 Universal-action-sheet hooks + Semantic Search

- **Semantic Search:** No code change needed — `SearchViewModel.resolveFilter` already calls `aiAssistant.parseNaturalQuery` when `naturalLanguage` is on and `aiAssistant.isEnabled`. Verify the toggle is surfaced in `SearchScreen.kt`; if not present, add a `FilterChip("AI", selected = state.naturalLanguage, onClick = viewModel::toggleNaturalLanguage)` in the top bar. This is the fourth pillar, already mostly built.
- **Entry points from a `FileItem`:** Wherever the app shows a per-file overflow/long-press action sheet (search `Grep` for the existing bottom-sheet that lists Rename/Delete/Share — likely in `feature/browser` or a shared `ui/components` sheet), add three new items gated on an injected `AiAssistant.isEnabled`:
  - "Summarize" → `navController.navigate(Destination.AiSummarize.create(item.path))`
  - For a directory: "Smart Rename contents" → `Destination.AiSmartRename.create(item.path)` and "Auto-Organize" → `Destination.AiAutoOrganize.create(item.path)`.
  These call sites only need the existing `onNavigateRoute: (String) -> Unit` lambda already threaded through `FileBrowserScreen`. Do not duplicate the navigation lambda; reuse it.

---

## 7. Phase 4 — Configuration

- **Keys / env:** No build-time secret. The Claude API key is user-supplied at runtime via `SettingsDataStore.aiApiKey` (Settings → AI). Document in the Settings screen help text the key URL: <https://console.anthropic.com/settings/keys>.
- **External service:** Anthropic Messages API, `POST https://api.anthropic.com/v1/messages`, headers `x-api-key`, `anthropic-version: 2023-06-01`. Model `claude-3-5-haiku-20241022` (already used). Pricing/quota is the user's own account; no Jupiter-side billing. Docs: <https://docs.anthropic.com/en/api/messages>.
- **ProGuard / R8** — append to `app/proguard-rules.pro`:

```pro
# Room: keep entities and generated DAO impls
-keep class com.jupiter.filemanager.feature.ai.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(...); }
# OkHttp / org.json already used by AnthropicAiAssistant — no new rules needed.
```

- **Schema dir:** Create `app/schemas/` (Room exports `AiDatabase` schema JSON there). Add it to version control so migrations are reproducible.

---

## 8. Phase 5 — Testing

### 8.1 Manual smoke-test script

1. Build & install: `./gradlew :app:installDebug`.
2. Settings → AI: leave key **blank**. Open any folder → overflow → "Smart Rename". Expect the screen to show "Add a Claude API key in Settings to enable AI." and **no crash**.
3. Paste a valid key (Settings → AI). Re-open "Smart Rename" on a folder containing files like `IMG_0001.jpg`, `doc1.pdf`. Expect a list of `old → new` rows, all checked.
4. Uncheck one row. Tap **Apply changes**. Verify only checked files were renamed on disk (re-open the folder in the browser). Verify the unchecked file is untouched.
5. Overflow → "Auto-Organize" on a messy `Download` folder. Expect 2–6 proposed folders. Apply; verify folders were created and files moved. Re-run — already-organized files should now produce "Nothing to organize here."
6. Overflow → "Summarize" on a folder and on a single PDF. Expect concise text, no fabricated contents.
7. Search screen: enable the **AI** toggle, type "large videos from last month", run. Confirm interpretation feeds the filter (no crash if AI fails — falls back to plain substring).
8. Airplane mode on → any AI action → expect a network error message, no crash.

### 8.2 Unit tests (`app/src/test/.../feature/ai/`)

- `AnthropicAiAssistantParsingTest`: feed `parseRenameArray`/`parseOrganizeArray` raw strings with code fences, extra prose, path-injected `"suggested":"../../etc/passwd"`, and malformed JSON; assert sanitization strips paths and bad input yields `emptyList()`. (Extract the parse helpers to `internal` visibility for testability, or test via a fake HTTP layer.)
- `AiActionsRepositoryTest`: with a fake `FileRepository` and in-memory Room (`Room.inMemoryDatabaseBuilder`), assert `applyRenames` reports partial success when one rename fails, and `applyOrganize` creates each folder once (idempotent `ensureFolder`).
- `SmartRenameViewModelTest` / `AutoOrganizeViewModelTest`: with a fake `AiAssistant`, assert `canApply` toggling, empty-proposal message, and `resultMessage` after apply (use `kotlinx-coroutines-test` already in `libs`).

### 8.3 Instrumented (`app/src/androidTest/`)

- Room DAO round-trip on `AiDatabase` (insert → `observeRecent` emits).
- Navigation: assert `Destination.AiSummarize.create("/x")` round-trips through `SavedStateHandle` decoding.

---

## 9. Error handling & edge cases

1. **Blank/invalid API key** → `currentKey()` returns `""`, every action returns `notConfigured()` → screens show "Add a Claude API key in Settings"; no throw. Invalid key → HTTP 401 → `extractError` surfaces the Anthropic message via `AppError.Unknown`.
2. **Network failure / airplane mode** → `IOException` caught in `requestText` → `AppError.Io` with the message; ViewModels render it in `colorScheme.error` with a Retry.
3. **Model returns prose instead of JSON** → `stripToJsonArray` salvages the `[...]` slice; if still unparseable, `parseRenameArray`/`parseOrganizeArray` catch and return `emptyList()` → UI shows "No rename suggestions" rather than crashing.
4. **Prompt-injection / path traversal in a suggested name** (`"../secret"`, `"a/b.jpg"`) → `sanitizeFileName`/`sanitizeFolderName` take `substringAfterLast('/')`, drop `\\` and `/`, strip leading dots, and filter to an allow-list → the mutation can never escape the target folder.
5. **Rename collides with an existing file** → `FileRepository.rename` returns `AppResult.Failure(AppError.AlreadyExists)` → counted in `ApplyOutcome.failed` and surfaced ("N failed"); other renames still apply (partial success, not abort).
6. **Destination folder already exists during organize** → `ensureFolder` returns the existing path if it is a directory, or treats `AppError.AlreadyExists` from `createFolder` as success → no duplicate folders, move proceeds.
7. **Empty folder / folder with only subdirectories** → `proposeRenames`/`proposeOrganize` receive an empty file list, short-circuit to `AppResult.Success(emptyList())` (no API call, no spend) → UI shows the "nothing to do" message.
8. **Large folder (hundreds of files)** → batches are capped at `MAX_BATCH = 30` and children sampled at `MAX_CHILDREN = 40`, bounding token cost and latency; the user can re-run to process more.
9. **Concurrent/duplicate apply taps** → `isApplying` guards `apply()` and disables the button (`canApply`); a second tap is a no-op.

---

## 10. Integration with other initiatives

- **#1 Pro monetization / entitlement:** This suite is the headline Pro benefit. If #1 exposes an `EntitlementRepository`/`isPro` flow, gate the three new action entry points on `isPro && aiAssistant.isEnabled`; until #1 lands, gate purely on `aiAssistant.isEnabled`. Inject the entitlement flow additively — do not hard-depend on it.
- **#2 Cloud OAuth:** Organize/rename operate through `FileRepository`; when a cloud-backed `FileRepository` exists, these actions transparently work on cloud folders too — no change here.
- **Cleanup / Duplicates (existing `feature/cleanup`):** Auto-Organize is complementary, not overlapping — it groups, it does not delete. Surface a cross-link from `CleanupScreen` to Auto-Organize.
- **Search (existing):** Semantic Search is the already-wired fourth pillar; no new dependency.
- **AI action history (Room) is self-contained** and can later feed an "Undo last AI change" feature or an analytics initiative.

---

## 11. Rollback plan

The initiative is strictly additive; revert by removal:
1. **Navigation:** delete the three `composable(...)` blocks and three imports added to `JupiterNavHost.kt`; delete the three `Destination.AiSummarize/AiSmartRename/AiAutoOrganize` objects from `Destinations.kt`.
2. **Action-sheet hooks:** remove the three menu items added to the per-file sheet.
3. **Screens/VMs:** delete `SummarizeScreen/ViewModel`, `SmartRenameScreen/ViewModel`, `AutoOrganizeScreen/ViewModel`.
4. **Domain/data:** delete `feature/ai/domain/AiActionsRepository.kt` and the `feature/ai/data/` package (`AiActionEntity`, `AiActionDao`, `AiDatabase`, `AiDataModule`); drop the `jupiter_ai.db` (uninstall clears it).
5. **Interface:** remove `summarize`/`proposeRenames`/`proposeOrganize` from `AiAssistant.kt`, `AnthropicAiAssistant.kt`, and `NoOpAiAssistant.kt`, plus the new model classes `RenameProposal`/`OrganizeMove`.
6. **Gradle:** remove the Room version/libraries and the `ksp(libs.androidx.room.compiler)` line + schema arg.
No existing feature touches the new code paths, so removal cannot regress chat, search, or browsing. The chat screen and NL search were untouched and continue to function with only the original three interface methods if you revert the additions.

---

## 12. Definition of done

- [ ] `AiAssistant`, `AnthropicAiAssistant`, and `NoOpAiAssistant` all compile with the three new methods; `RenameProposal`/`OrganizeMove` defined once.
- [ ] Room `AiDatabase`/`AiActionDao`/`AiActionEntity` build; `AiDataModule` provides them; schema JSON is exported to `app/schemas/`.
- [ ] `AiActionsRepository` applies renames and organize moves only through `FileRepository`, records history, and reports partial success via `ApplyOutcome`.
- [ ] Summarize / Smart-Rename / Auto-Organize screens render, are confirm-before-apply (preview + checkboxes + explicit "Apply changes"), and never mutate without confirmation.
- [ ] Three routes wired in `Destinations.kt` + `JupiterNavHost.kt`; per-file action sheet launches each, gated on `aiAssistant.isEnabled` (and `isPro` if available).
- [ ] Every AI path degrades gracefully with a blank key and on network failure — verified, no crash (manual steps 2 and 8).
- [ ] Name/folder sanitization prevents path traversal; malformed model output yields empty proposals, not crashes (unit tests pass).
- [ ] **No regression:** the existing **AI chat screen (`AiAssistantScreen`/`AiAssistantViewModel`)** still opens and replies, and **natural-language Search (`SearchViewModel.parseNaturalQuery`)** still interprets queries and falls back to substring on failure.
- [ ] **No regression:** existing **file browse / rename / move / create-folder** behavior is unchanged (the new repository only *calls* `FileRepository`, never modifies it).
- [ ] **No regression:** app launches to the existing start flow (Splash → Onboarding/Permission/Main); no new mandatory permission prompt.
- [ ] Unit tests for parsing/sanitization, `AiActionsRepository`, and the three ViewModels pass; instrumented Room round-trip passes.
- [ ] **CI green:** `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` both succeed.
- [ ] ProGuard rules added; release build (`assembleRelease`) does not strip Room generated classes.
