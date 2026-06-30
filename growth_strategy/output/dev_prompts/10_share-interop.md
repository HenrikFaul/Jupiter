# Initiative #10 — Share & Interop Hub (Receive "Save to Jupiter", Quick-Share, SAF DocumentsProvider)

> **AI coding-agent implementation prompt.** Implement this end-to-end, additively, with zero regression to existing working features. Use Kotlin / Jetpack Compose / Material3 / Hilt / Coroutines against the **real** Jupiter codebase at `/home/user/Jupiter/app/src/main/java/com/jupiter/filemanager`. Read every "real file" referenced before editing. Do not invent APIs that the codebase does not already expose — the contracts (`AppResult`, `AppError`, `@IoDispatcher`, `Destination`, `JupiterNavHost`, `FileRepository`, `FileItem`) shown here are copied verbatim from the live source.

---

## 1. Initiative header

- **Title:** Share & Interop Hub — Receive "Save to Jupiter", Quick-Share, SAF `DocumentsProvider`.
- **Value range:** **+€100k–€200k**.
- **Business case:** Jupiter today is a *destination* app — the user must open it to do anything. This initiative turns Jupiter into a **hub other apps route through**, capturing intent flow that competitors miss. By registering as an `ACTION_SEND` / `ACTION_SEND_MULTIPLE` receive target, every share sheet on the device gains a **"Save to Jupiter"** entry, so photos, PDFs and downloads land in Jupiter without the user ever opening it first. By publishing a Storage Access Framework (`DocumentsProvider`), Jupiter appears inside **every other app's file picker** (Gmail attachments, Chrome uploads, WhatsApp send) — making Jupiter the canonical place files live. A built-in Quick-Share path lets users push received content straight back out to another app or device, closing the loop. The result is viral surface area (Jupiter shows up in flows the user didn't seek out) plus deep stickiness (other apps depend on Jupiter as their storage browser). The work is purely **additive** — new manifest entries plus new components — so it cannot regress the existing browser, vault, transfer or cleanup features.

---

## 2. Codebase context

### Relevant real file tree (verified to exist)

```
app/
  src/main/AndroidManifest.xml                                  # EXISTS — add intent-filters + provider
  src/main/res/xml/file_paths.xml                               # EXISTS — FileProvider paths (reuse)
  src/main/res/values/strings.xml                               # EXISTS — add strings
  src/main/java/com/jupiter/filemanager/
    JupiterApp.kt                                               # EXISTS — @HiltAndroidApp (no change)
    MainActivity.kt                                             # EXISTS — single-activity host (no change)
    core/result/AppResult.kt                                    # EXISTS — Success/Failure + onSuccess/map
    core/result/AppError.kt                                     # EXISTS — sealed AppError (PermissionDenied, Io, …)
    core/util/MimeUtil.kt                                       # EXISTS — mimeTypeFor / extensionOf / fileTypeFor
    di/CoroutineModule.kt                                       # EXISTS — @IoDispatcher / @DefaultDispatcher / @MainDispatcher
    di/RepositoryModule.kt                                      # EXISTS — @Binds for repositories (add binding here)
    domain/model/FileItem.kt                                    # EXISTS — immutable FileItem
    domain/repository/FileRepository.kt                         # EXISTS — file system abstraction
    data/file/FileSystemDataSource.kt                           # EXISTS — File -> FileItem mapper (reuse)
    data/file/FileRepositoryImpl.kt                             # EXISTS — @Singleton FileRepository impl
    data/file/FileOperationsManager.kt                          # EXISTS — copy/move helpers (reuse)
    ui/navigation/Destinations.kt                               # EXISTS — sealed Destination
    ui/navigation/JupiterNavHost.kt                             # EXISTS — central NavHost
```

### What exists vs missing vs needs change

| Concern | Status | Action |
|---|---|---|
| `ACTION_SEND` receive target | **MISSING** | Add `<intent-filter>` to a **new** `ReceiveShareActivity` |
| SAF `DocumentsProvider` | **MISSING** | Add **new** `JupiterDocumentsProvider` + `<provider>` |
| Quick-Share out | **MISSING** | New share repository + UI; reuse existing `FileProvider` |
| `FileProvider` (share out) | **EXISTS** | Reuse authority `${applicationId}.fileprovider` + `file_paths.xml` |
| Copy bytes into a folder | **EXISTS (partial)** | Reuse `FileSystemDataSource` / add focused copy helper for `Uri` → `File` |
| DI wiring | **EXISTS** | Add `@Binds` in `RepositoryModule.kt`; new `ShareModule` for the receive activity's deps via constructor injection |
| Navigation | **EXISTS** | `ReceiveShareActivity` is a **separate** activity (NOT in `JupiterNavHost`) so the receive flow never disturbs the main back stack; add an internal `ReceiveShareScreen` composable |

**Design rule:** `ReceiveShareActivity` is intentionally a *second* `@AndroidEntryPoint` activity, exported and `excludeFromRecents`-friendly, so the "Save to Jupiter" sheet does **not** hijack `MainActivity`'s navigation graph. The SAF provider is a `DocumentsProvider`, which the platform instantiates outside the Hilt graph — so it uses an `EntryPointAccessors` bridge to obtain repository dependencies.

---

## 3. Pre-conditions

### Gradle dependencies to add (exact coordinates)

The receive/share/SAF work needs **no new third-party libraries** — it uses only the Android framework (`DocumentsProvider`, `ContentResolver`, `MatrixCursor`) plus libraries already present (`androidx.documentfile`, Coroutines, Hilt, Compose BOM). The only additions are **test** helpers already partially present, plus Robolectric for JVM provider tests (optional but recommended):

```
robolectric = { group = "org.robolectric", name = "robolectric", version = "4.13" }
androidx-test-core = { group = "androidx.test", name = "core", version = "1.6.1" }
mockk = { group = "io.mockk", name = "mockk", version = "1.13.13" }
```

Version refs to add to `[versions]`: `robolectric = "4.13"`, `androidxTestCore = "1.6.1"`, `mockk = "1.13.13"`.

### Manifest / permission / Play-Console prerequisites

- **No new runtime permissions.** Receiving shares uses temporary URI grants the sender attaches; SAF read access is mediated by the framework. The existing `MANAGE_EXTERNAL_STORAGE` already covers writing received bytes into shared storage.
- **No API keys, no external service, no Play Console toggle** is required. SAF `DocumentsProvider` registration is purely a manifest `<provider>` with `android.permission.MANAGE_DOCUMENTS` declared by the **framework caller**, plus the `android.content.action.DOCUMENTS_PROVIDER` intent filter — the agent only declares it; no signing or console step.
- **Authority uniqueness:** the SAF provider authority must be `${applicationId}.documents` so debug (`.debug` suffix) and release variants do not collide on a device that has both installed — mirrors the existing `.fileprovider` pattern.

---

## 4. Phase 1 — Gradle + Manifest + resources

### 4.1 `gradle/libs.versions.toml`

Add under `[versions]`:

```toml
robolectric = "4.13"
androidxTestCore = "1.6.1"
mockk = "1.13.13"
```

Add under `[libraries]`:

```toml
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
```

### 4.2 `app/build.gradle.kts`

Append to the `dependencies { }` block (only test deps — no production deps needed):

```kotlin
    // Share & Interop Hub — JVM tests for the DocumentsProvider / repositories.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockk)
```

If you enable Robolectric, also add inside `android { }`:

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
```

### 4.3 `app/src/main/AndroidManifest.xml`

Add the following **inside** the existing `<application>` element, after the existing `FileProvider` `<provider>` block. Do **not** modify the existing `MainActivity` or `FileProvider` entries.

```xml
        <!--
            Receive target for "Save to Jupiter". A dedicated, exported activity so
            the share sheet does not hijack MainActivity's navigation graph. It is
            transparent/dialog-styled and finishes immediately after the user picks
            a destination, returning the caller to its previous task.
        -->
        <activity
            android:name=".feature.share.ReceiveShareActivity"
            android:exported="true"
            android:excludeFromRecents="true"
            android:label="@string/share_receive_title"
            android:noHistory="true"
            android:taskAffinity=""
            android:theme="@style/Theme.Jupiter">
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="*/*" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND_MULTIPLE" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="*/*" />
            </intent-filter>
        </activity>

        <!--
            Storage Access Framework DocumentsProvider. Makes Jupiter's storage
            browsable inside any other app's file picker. The authority is derived
            from applicationId so debug/release variants do not collide. The
            framework enforces MANAGE_DOCUMENTS on callers; we only export it and
            declare the DOCUMENTS_PROVIDER intent filter.
        -->
        <provider
            android:name=".data.saf.JupiterDocumentsProvider"
            android:authorities="${applicationId}.documents"
            android:exported="true"
            android:grantUriPermissions="true"
            android:permission="android.permission.MANAGE_DOCUMENTS">
            <intent-filter>
                <action android:name="android.content.action.DOCUMENTS_PROVIDER" />
            </intent-filter>
        </provider>
```

### 4.4 `app/src/main/res/values/strings.xml`

Add these strings (the file already opens with `<resources>` — append before `</resources>`):

```xml
    <!-- Share & Interop Hub -->
    <string name="share_receive_title">Save to Jupiter</string>
    <string name="share_receive_destination">Choose a destination folder</string>
    <string name="share_receive_save">Save here</string>
    <string name="share_receive_saving">Saving…</string>
    <string name="share_receive_saved">Saved %1$d item(s) to %2$s</string>
    <string name="share_receive_empty">Nothing to save</string>
    <string name="share_receive_error">Could not save: %1$s</string>
    <string name="share_quick_send">Quick-share</string>
    <string name="share_quick_send_failed">Could not share the selected file(s)</string>
    <string name="saf_root_title">Jupiter</string>
    <string name="saf_root_summary">Device storage via Jupiter</string>
```

No new `xml/file_paths.xml` entries are required — the existing `external-path name="external_files" path="."` already exposes the whole volume for share-out.

---

## 5. Phase 2 — Data / domain layer

All new files live under the existing package roots. Every cross-boundary call returns `AppResult<…>` and uses `@IoDispatcher`, exactly like the existing code.

### 5.1 Domain model — `domain/model/ShareDestination.kt` (NEW)

```kotlin
package com.jupiter.filemanager.domain.model

/**
 * A candidate destination folder offered to the user on the "Save to Jupiter"
 * screen. Immutable; produced by the share repository, rendered by the UI.
 *
 * @property displayName short label shown in the list (e.g. "Downloads").
 * @property absolutePath absolute filesystem path of the folder.
 */
data class ShareDestination(
    val displayName: String,
    val absolutePath: String,
)
```

### 5.2 Domain model — `domain/model/SharedPayload.kt` (NEW)

```kotlin
package com.jupiter.filemanager.domain.model

import android.net.Uri

/**
 * A single inbound item from an ACTION_SEND / ACTION_SEND_MULTIPLE intent.
 *
 * @property uri content/file Uri granted by the sending app.
 * @property displayName best-effort file name resolved from the resolver, or a
 *   synthesised name when the sender did not provide one.
 * @property mimeType MIME type reported by the sender or resolver, or null.
 * @property sizeBytes size in bytes when known, or null.
 */
data class SharedPayload(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
)
```

### 5.3 Domain repository — `domain/repository/ShareRepository.kt` (NEW)

```kotlin
package com.jupiter.filemanager.domain.repository

import android.net.Uri
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.ShareDestination
import com.jupiter.filemanager.domain.model.SharedPayload

/**
 * Abstraction over the "Save to Jupiter" receive flow and Quick-Share out flow.
 *
 * Implementations perform all blocking IO on a background dispatcher; callers
 * (ViewModels) may invoke these from `viewModelScope` without thread management.
 */
interface ShareRepository {

    /** Resolves inbound [uris] into [SharedPayload]s with names/sizes/MIME. */
    suspend fun resolvePayloads(uris: List<Uri>): AppResult<List<SharedPayload>>

    /** Returns the user-facing list of candidate destination folders. */
    suspend fun destinations(): AppResult<List<ShareDestination>>

    /**
     * Copies the bytes behind each [payload] into [destinationPath], skipping
     * name collisions by suffixing " (n)". Returns the list of absolute paths
     * actually written.
     */
    suspend fun saveToFolder(
        payloads: List<SharedPayload>,
        destinationPath: String,
    ): AppResult<List<String>>

    /**
     * Builds shareable content:// Uris (via FileProvider) for [absolutePaths] so
     * the caller can fire an ACTION_SEND chooser. Returns the Uris in order.
     */
    suspend fun shareableUris(absolutePaths: List<String>): AppResult<List<Uri>>
}
```

### 5.4 Data impl — `data/share/ShareRepositoryImpl.kt` (NEW)

```kotlin
package com.jupiter.filemanager.data.share

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.core.util.mimeTypeFor
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.ShareDestination
import com.jupiter.filemanager.domain.model.SharedPayload
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.ShareRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [ShareRepository] backed by the local filesystem and the app's
 * FileProvider. Mirrors the codebase convention: never throws across the
 * boundary, returns [AppResult], and runs blocking IO on [IoDispatcher].
 */
@Singleton
class ShareRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileRepository: FileRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ShareRepository {

    private val authority: String get() = context.packageName + ".fileprovider"

    override suspend fun resolvePayloads(uris: List<Uri>): AppResult<List<SharedPayload>> =
        withContext(ioDispatcher) {
            try {
                val payloads = uris.mapNotNull { uri -> resolveSingle(uri) }
                AppResult.Success(payloads)
            } catch (e: SecurityException) {
                AppResult.Failure(AppError.AccessDenied(uris.firstOrNull()?.toString().orEmpty()))
            } catch (e: Exception) {
                AppResult.Failure(AppError.Io("Failed to read shared content", e))
            }
        }

    private fun resolveSingle(uri: Uri): SharedPayload? {
        val resolver = context.contentResolver
        var name: String? = null
        var size: Long? = null
        val cursor: Cursor? = runCatching {
            resolver.query(uri, null, null, null, null)
        }.getOrNull()
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        val mime = resolver.getType(uri) ?: name?.let { mimeTypeFor(it) }
        val safeName = name?.takeIf { it.isNotBlank() }
            ?: (uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() })
            ?: ("shared_" + System.currentTimeMillis())
        return SharedPayload(uri = uri, displayName = safeName, mimeType = mime, sizeBytes = size)
    }

    override suspend fun destinations(): AppResult<List<ShareDestination>> =
        withContext(ioDispatcher) {
            val root = fileRepository.rootDirectory()
            val candidates = buildList {
                add(ShareDestination("Downloads", publicDir(Environment.DIRECTORY_DOWNLOADS, root)))
                add(ShareDestination("Documents", publicDir(Environment.DIRECTORY_DOCUMENTS, root)))
                add(ShareDestination("Pictures", publicDir(Environment.DIRECTORY_PICTURES, root)))
                add(ShareDestination("Internal storage", root))
            }.filter { File(it.absolutePath).let { f -> f.exists() || f.mkdirs() } }
            AppResult.Success(candidates)
        }

    private fun publicDir(type: String, fallbackRoot: String): String {
        val dir = runCatching { Environment.getExternalStoragePublicDirectory(type) }.getOrNull()
        return if (dir != null && dir.absolutePath.isNotBlank()) dir.absolutePath
        else File(fallbackRoot, type).absolutePath
    }

    override suspend fun saveToFolder(
        payloads: List<SharedPayload>,
        destinationPath: String,
    ): AppResult<List<String>> = withContext(ioDispatcher) {
        if (payloads.isEmpty()) return@withContext AppResult.Success(emptyList())
        val destination = File(destinationPath)
        if (!destination.exists() && !destination.mkdirs()) {
            return@withContext AppResult.Failure(AppError.AccessDenied(destinationPath))
        }
        if (!destination.isDirectory || !destination.canWrite()) {
            return@withContext AppResult.Failure(AppError.AccessDenied(destinationPath))
        }
        val written = mutableListOf<String>()
        try {
            for (payload in payloads) {
                val target = uniqueTarget(destination, payload.displayName)
                context.contentResolver.openInputStream(payload.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER) }
                } ?: return@withContext AppResult.Failure(
                    AppError.Io("Could not open source for " + payload.displayName),
                )
                written += target.absolutePath
            }
            AppResult.Success(written)
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.AccessDenied(destinationPath))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Io(e.message ?: "Write failed", e))
        }
    }

    private fun uniqueTarget(dir: File, requestedName: String): File {
        val sanitized = requestedName.replace('/', '_').replace(' ', '_').ifBlank { "file" }
        var candidate = File(dir, sanitized)
        if (!candidate.exists()) return candidate
        val dot = sanitized.lastIndexOf('.')
        val base = if (dot > 0) sanitized.substring(0, dot) else sanitized
        val ext = if (dot > 0) sanitized.substring(dot) else ""
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, base + " (" + n + ")" + ext)
            n++
        }
        return candidate
    }

    override suspend fun shareableUris(absolutePaths: List<String>): AppResult<List<Uri>> =
        withContext(ioDispatcher) {
            try {
                val uris = absolutePaths.map { path ->
                    FileProvider.getUriForFile(context, authority, File(path))
                }
                AppResult.Success(uris)
            } catch (e: IllegalArgumentException) {
                AppResult.Failure(AppError.Io("File is outside shareable paths", e))
            }
        }

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024
    }
}
```

### 5.5 DI binding — edit `di/RepositoryModule.kt`

Add the import and one `@Binds` method. **Full replacement** of the file:

```kotlin
package com.jupiter.filemanager.di

import com.jupiter.filemanager.data.bookmark.BookmarkRepositoryImpl
import com.jupiter.filemanager.data.file.FileRepositoryImpl
import com.jupiter.filemanager.data.share.ShareRepositoryImpl
import com.jupiter.filemanager.data.storage.StorageAnalyticsRepositoryImpl
import com.jupiter.filemanager.data.vault.VaultRepositoryImpl
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.ShareRepository
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import com.jupiter.filemanager.domain.repository.VaultRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the concrete data-layer repository implementations to their domain-layer
 * interfaces so they can be injected throughout the app.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds
    abstract fun bindStorageAnalyticsRepository(impl: StorageAnalyticsRepositoryImpl): StorageAnalyticsRepository

    @Binds
    abstract fun bindVaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository

    @Binds
    abstract fun bindShareRepository(impl: ShareRepositoryImpl): ShareRepository
}
```

### 5.6 SAF — `data/saf/JupiterDocumentsProvider.kt` (NEW)

The framework instantiates this **outside** Hilt, so it bridges in `FileRepository` via an `@EntryPoint`. It exposes the device root read-only by default (Jupiter already holds All-Files-Access).

```kotlin
package com.jupiter.filemanager.data.saf

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import com.jupiter.filemanager.R
import com.jupiter.filemanager.core.util.mimeTypeFor
import com.jupiter.filemanager.domain.repository.FileRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileNotFoundException

/**
 * Storage Access Framework provider that lets any other app browse Jupiter's
 * storage from its own file picker. Document IDs are absolute file paths; the
 * root maps to [FileRepository.rootDirectory].
 *
 * Read access only by default — openDocument rejects write modes — so a stray
 * picker cannot mutate user storage through Jupiter.
 */
class JupiterDocumentsProvider : DocumentsProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun fileRepository(): FileRepository
    }

    private val rootPath: String by lazy {
        val ctx = requireContext()
        val entry = EntryPointAccessors.fromApplication(
            ctx.applicationContext,
            ProviderEntryPoint::class.java,
        )
        entry.fileRepository().rootDirectory()
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val ctx = requireContext()
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT_ID)
            add(Root.COLUMN_DOCUMENT_ID, rootPath)
            add(Root.COLUMN_TITLE, ctx.getString(R.string.saf_root_title))
            add(Root.COLUMN_SUMMARY, ctx.getString(R.string.saf_root_summary))
            add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_IS_CHILD)
            add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            add(Root.COLUMN_MIME_TYPES, "*/*")
        }
        return cursor
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val file = File(documentId ?: throw FileNotFoundException("null id"))
        if (!file.exists()) throw FileNotFoundException("not found: " + documentId)
        addFileRow(cursor, file)
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = File(parentDocumentId ?: throw FileNotFoundException("null parent"))
        val children = runCatching { parent.listFiles() }.getOrNull().orEmpty()
        for (child in children) {
            if (child.isHidden) continue
            runCatching { addFileRow(cursor, child) }
        }
        return cursor
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = File(documentId ?: throw FileNotFoundException("null id"))
        if (!file.exists() || !file.isFile) throw FileNotFoundException("not a file: " + documentId)
        // Read-only by design; reject any write/append/truncate request.
        val accessMode = ParcelFileDescriptor.parseMode("r")
        if (mode != null && mode.any { it == 'w' || it == 'a' || it == 't' }) {
            throw FileNotFoundException("write not supported")
        }
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        if (parentDocumentId == null || documentId == null) return false
        val parent = File(parentDocumentId).canonicalPath + File.separator
        val child = File(documentId).canonicalPath
        return child.startsWith(parent)
    }

    private fun addFileRow(cursor: MatrixCursor, file: File) {
        val mime = if (file.isDirectory) Document.MIME_TYPE_DIR
        else (mimeTypeFor(file.name) ?: "application/octet-stream")
        var flags = 0
        if (file.isDirectory && file.canWrite()) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        if (mime.startsWith("image/") || mime.startsWith("video/")) {
            flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        }
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, file.absolutePath)
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_SIZE, file.length())
            add(Document.COLUMN_MIME_TYPE, mime)
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
        }
    }

    override fun openDocumentThumbnail(
        documentId: String?,
        sizeHint: Point?,
        signal: CancellationSignal?,
    ): android.content.res.AssetFileDescriptor? {
        val file = File(documentId ?: return null)
        if (!file.isFile) return null
        return android.content.res.AssetFileDescriptor(
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode("r")),
            0,
            file.length(),
        )
    }

    private companion object {
        const val ROOT_ID = "jupiter_root"

        val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_MIME_TYPES,
        )

        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
    }
}
```

---

## 6. Phase 3 — Presentation

The receive flow runs in its **own** activity (`ReceiveShareActivity`), *not* in `JupiterNavHost`. A small immutable `UiState` + `@HiltViewModel` drive a single Compose screen.

### 6.1 `feature/share/ReceiveShareUiState.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.share

import com.jupiter.filemanager.domain.model.ShareDestination
import com.jupiter.filemanager.domain.model.SharedPayload

/**
 * Immutable UI state for the "Save to Jupiter" screen. Pure snapshot; all IO is
 * in the ViewModel/repository.
 */
data class ReceiveShareUiState(
    val payloads: List<SharedPayload> = emptyList(),
    val destinations: List<ShareDestination> = emptyList(),
    val selectedDestination: ShareDestination? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedPaths: List<String> = emptyList(),
    val finished: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = !isSaving && !isLoading && payloads.isNotEmpty() && selectedDestination != null
}
```

### 6.2 `feature/share/ReceiveShareViewModel.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.share

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.ShareDestination
import com.jupiter.filemanager.domain.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the "Save to Jupiter" screen. Receives the inbound Uris from the
 * activity, resolves them, lists destinations, and writes bytes on confirm.
 */
@HiltViewModel
class ReceiveShareViewModel @Inject constructor(
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiveShareUiState())
    val uiState: StateFlow<ReceiveShareUiState> = _uiState.asStateFlow()

    private var initialized = false

    /** Called once by the activity with the inbound Uris. Idempotent. */
    fun onIntentReceived(uris: List<Uri>) {
        if (initialized) return
        initialized = true
        if (uris.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, error = EMPTY) }
            return
        }
        viewModelScope.launch {
            val payloadsResult = shareRepository.resolvePayloads(uris)
            val destResult = shareRepository.destinations()
            val payloads = (payloadsResult as? AppResult.Success)?.data.orEmpty()
            val dests = (destResult as? AppResult.Success)?.data.orEmpty()
            if (payloads.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, error = EMPTY) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    payloads = payloads,
                    destinations = dests,
                    selectedDestination = dests.firstOrNull(),
                    isLoading = false,
                    error = null,
                )
            }
        }
    }

    fun selectDestination(destination: ShareDestination) {
        _uiState.update { it.copy(selectedDestination = destination) }
    }

    fun save() {
        val state = _uiState.value
        val destination = state.selectedDestination ?: return
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = shareRepository.saveToFolder(state.payloads, destination.absolutePath)) {
                is AppResult.Success ->
                    _uiState.update {
                        it.copy(isSaving = false, savedPaths = result.data, finished = true)
                    }
                is AppResult.Failure ->
                    _uiState.update {
                        it.copy(isSaving = false, error = result.error.displayMessage)
                    }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private companion object {
        const val EMPTY = "Nothing to save"
    }
}
```

### 6.3 `feature/share/ReceiveShareScreen.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.R
import com.jupiter.filemanager.domain.model.ShareDestination
import com.jupiter.filemanager.domain.model.SharedPayload

/**
 * "Save to Jupiter" UI. Lists the inbound items, a radio list of destination
 * folders, and a Save button. Pure: state in, callbacks out.
 */
@Composable
fun ReceiveShareScreen(
    state: ReceiveShareUiState,
    onSelectDestination: (ShareDestination) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.share_receive_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            state.payloads.isEmpty() ->
                Text(stringResource(R.string.share_receive_empty))
            else -> {
                Text(
                    text = state.payloads.joinToString(limit = 5) { it.displayName },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.share_receive_destination),
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.destinations, key = { it.absolutePath }) { dest ->
                        DestinationRow(
                            destination = dest,
                            selected = dest == state.selectedDestination,
                            onClick = { onSelectDestination(dest) },
                        )
                    }
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSaving) stringResource(R.string.share_receive_saving)
                    else stringResource(R.string.share_receive_save),
                )
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun DestinationRow(
    destination: ShareDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(destination.displayName, style = MaterialTheme.typography.bodyLarge)
    }
}
```

### 6.4 `feature/share/ReceiveShareActivity.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.R
import com.jupiter.filemanager.ui.theme.JupiterTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Exported receive target for "Save to Jupiter". Parses inbound
 * ACTION_SEND / ACTION_SEND_MULTIPLE Uris, renders [ReceiveShareScreen], and
 * finishes when the user saves or cancels. Kept separate from MainActivity so
 * the main navigation back stack is never disturbed.
 */
@AndroidEntryPoint
class ReceiveShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = extractUris(intent)
        setContent {
            JupiterTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel: ReceiveShareViewModel = hiltViewModel()
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    viewModel.onIntentReceived(uris)

                    if (state.finished) {
                        val folder = state.selectedDestination?.displayName.orEmpty()
                        Toast.makeText(
                            this,
                            getString(R.string.share_receive_saved, state.savedPaths.size, folder),
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    } else {
                        ReceiveShareScreen(
                            state = state,
                            onSelectDestination = viewModel::selectDestination,
                            onSave = viewModel::save,
                            onCancel = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun extractUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(getStreamUri(intent))
            Intent.ACTION_SEND_MULTIPLE -> getStreamUris(intent)
            else -> emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun getStreamUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun getStreamUris(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
}
```

### 6.5 Quick-Share out — `feature/share/QuickShare.kt` (NEW, reusable helper)

This is the "send-to-device / send-to-app" path, callable from existing browser selection. It does **not** alter `JupiterNavHost`; it is a stateless helper any screen can call.

```kotlin
package com.jupiter.filemanager.feature.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.jupiter.filemanager.R
import com.jupiter.filemanager.core.util.mimeTypeFor
import java.io.File

/**
 * Stateless Quick-Share helper. Builds an ACTION_SEND / ACTION_SEND_MULTIPLE
 * chooser for one or more absolute file paths, using the existing FileProvider
 * authority so no raw file:// Uris leak.
 */
object QuickShare {

    fun share(context: Context, absolutePaths: List<String>) {
        val files = absolutePaths.map(::File).filter { it.exists() && it.isFile }
        if (files.isEmpty()) return
        val authority = context.packageName + ".fileprovider"
        val uris = ArrayList<Uri>(files.size)
        for (f in files) {
            runCatching { FileProvider.getUriForFile(context, authority, f) }
                .getOrNull()?.let { uris.add(it) }
        }
        if (uris.isEmpty()) return

        val mime = files.map { mimeTypeFor(it.name) ?: "*/*" }.distinct()
            .singleOrNull() ?: "*/*"

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }.apply {
            type = mime
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, context.getString(R.string.share_quick_send))
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(chooser)
    }
}
```

### 6.6 Navigation wiring

`ReceiveShareActivity` is a **separate exported activity**, so it is *not* added to `Destinations.kt` or `JupiterNavHost.kt`. **Leave both files unchanged.** This is the deliberate isolation that guarantees zero regression to the main navigation graph. (If you later want a Quick-Share entry inside the in-app browser, call `QuickShare.share(context, paths)` from the existing browser's overflow action — no new destination needed.)

---

## 7. Phase 4 — Configuration

- **Env / keys:** none. No `BuildConfig` fields, no secrets, no `.properties` entries.
- **External service setup:** none — entirely on-device framework integration. For manual verification of the SAF provider, the Android system file picker (Files by Google, or any `ACTION_OPEN_DOCUMENT` caller) is the "external service"; documentation: <https://developer.android.com/guide/topics/providers/create-document-provider> and the share intent docs at <https://developer.android.com/training/sharing/receive>.
- **ProGuard / R8** (`app/proguard-rules.pro`): the release build has `isMinifyEnabled = true`. The `DocumentsProvider` is referenced only from the manifest, so keep it and its EntryPoint, and keep Hilt-generated members:

```proguard
# Share & Interop Hub — keep manifest-referenced provider/activity from being stripped/renamed.
-keep class com.jupiter.filemanager.data.saf.JupiterDocumentsProvider { *; }
-keep class com.jupiter.filemanager.feature.share.ReceiveShareActivity { *; }
-keep class com.jupiter.filemanager.data.saf.JupiterDocumentsProvider$ProviderEntryPoint { *; }
```

(Hilt already ships consumer rules for `@EntryPoint`; these are belt-and-braces for the manifest-only class.)

---

## 8. Phase 5 — Testing

### 8.1 Manual smoke-test script

1. `./gradlew assembleDebug` → installs `com.jupiter.filemanager.debug`.
2. Open **Google Photos** (or Gallery) → select a photo → **Share** → confirm **"Save to Jupiter"** appears → tap it → ReceiveShareScreen opens → pick **Downloads** → **Save here** → toast "Saved 1 item(s) to Downloads".
3. Open Jupiter → browse to Downloads → confirm the photo is present and opens.
4. Repeat with **multi-select** in Photos (3 images) via Share → confirm `ACTION_SEND_MULTIPLE` saves all 3; if a name collides, confirm the second is suffixed " (1)".
5. Open **Gmail** → Compose → Attach → **browse** → in the picker's hamburger, confirm **"Jupiter"** root appears → drill into folders → attach a file → confirm it attaches.
6. In Jupiter's browser, select a file → overflow → **Quick-share** → confirm the system chooser opens and sending to another app works.
7. **Regression:** confirm normal app launch still routes Splash → Main; open the file browser, vault, transfer, cleanup — all behave exactly as before.

### 8.2 Recommended unit / instrumented tests

`app/src/test/java/com/jupiter/filemanager/data/share/ShareRepositoryImplTest.kt`:

```kotlin
package com.jupiter.filemanager.data.share

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.SharedPayload
import com.jupiter.filemanager.domain.repository.FileRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShareRepositoryImplTest {

    @Test
    fun saveToFolder_emptyPayloads_returnsEmptySuccess() = runTest {
        val context = mockk<android.content.Context>(relaxed = true)
        val fileRepo = mockk<FileRepository>(relaxed = true)
        val repo = ShareRepositoryImpl(context, fileRepo, UnconfinedTestDispatcher())
        val result = repo.saveToFolder(emptyList(), "/tmp")
        assertTrue(result is AppResult.Success)
        assertEquals(0, (result as AppResult.Success).data.size)
    }

    @Test
    fun saveToFolder_nonWritableDestination_returnsAccessDenied() = runTest {
        val context = mockk<android.content.Context>(relaxed = true)
        val fileRepo = mockk<FileRepository>(relaxed = true)
        val repo = ShareRepositoryImpl(context, fileRepo, UnconfinedTestDispatcher())
        val payload = SharedPayload(mockk(relaxed = true), "x.txt", "text/plain", 1L)
        // A path that cannot be created (a file masquerading as a dir) yields AccessDenied.
        val bogus = File.createTempFile("blocker", ".tmp").absolutePath
        val result = repo.saveToFolder(listOf(payload), bogus)
        assertTrue(result is AppResult.Failure)
    }
}
```

Instrumented (`androidTest`) for the SAF provider — `JupiterDocumentsProviderTest.kt`: resolve the provider via `context.contentResolver`, call `queryRoots` through a `DocumentsContract.buildRootsUri(authority)` and assert exactly one root with `COLUMN_TITLE == "Jupiter"`; then `queryChildDocuments` on the root document id and assert it returns a non-throwing cursor.

---

## 9. Error handling & edge cases (≥6 concrete scenarios)

1. **Sender grants no readable Uri / revoked grant.** `ShareRepositoryImpl.resolveSingle` wraps `resolver.query` in `runCatching` and `openInputStream` returns null → `saveToFolder` returns `AppError.Io("Could not open source …")`, surfaced as a non-fatal error string; the activity stays open so the user can retry or cancel.
2. **`SecurityException` reading a Uri** (cross-user / expired permission). Caught explicitly in `resolvePayloads` and `saveToFolder` → mapped to `AppError.AccessDenied`, displayed via `state.error`; no crash.
3. **Destination folder not writable / cannot be created.** `saveToFolder` checks `mkdirs()`, `isDirectory`, and `canWrite()` up-front → returns `AppError.AccessDenied(destinationPath)` before any byte is written (no partial-write corruption of a half-created file).
4. **Filename collision in destination.** `uniqueTarget` suffixes " (1)", " (2)", … preserving the extension, so an existing `report.pdf` becomes `report (1).pdf` — never overwrites user data.
5. **Empty / malformed share intent** (no `EXTRA_STREAM`). `extractUris` returns an empty list; `onIntentReceived` short-circuits to `error = "Nothing to save"` and shows the empty state; Save button is disabled (`canSave == false`).
6. **SAF caller requests a write mode** (`"w"`, `"wa"`, `"rwt"`). `openDocument` rejects any mode containing `w`/`a`/`t` with `FileNotFoundException("write not supported")`, keeping the provider read-only and preventing arbitrary apps from mutating storage through Jupiter.
7. **SAF `queryChildDocuments` on an unreadable directory.** `parent.listFiles()` is wrapped in `runCatching`, returns empty, and each row mapping is itself `runCatching`-guarded, so one bad child never aborts the whole listing (mirrors `FileSystemDataSource` behaviour).
8. **`FileProvider` asked to share a path outside `file_paths.xml`.** `shareableUris` / `QuickShare` catch `IllegalArgumentException` → `AppError.Io("File is outside shareable paths")` / silently skip, no crash.

---

## 10. Integration with other initiatives

- **Reuses (no new infra):** the existing `FileRepository` (`rootDirectory()`), `FileSystemDataSource`, `core/util/MimeUtil.kt` (`mimeTypeFor`), `core/result` (`AppResult`/`AppError`), `di/CoroutineModule` (`@IoDispatcher`), and the existing `FileProvider` authority + `file_paths.xml`. No other initiative is a hard prerequisite — this ships standalone.
- **Synergy with Transfer / Quick-Share-to-device (Nearby/Wifi transfer features):** `QuickShare` can be extended to add the existing `NearbyTransfer` / `WifiTransfer` destinations as chooser targets later; the helper is intentionally decoupled so that initiative can plug in without touching the receive flow.
- **Synergy with Workspaces / Tags:** `ReceiveShareViewModel.destinations()` is the natural extension point to offer "save into Workspace X" — additive, since destinations are just `ShareDestination(displayName, absolutePath)`.
- **Shared DI surface:** the new `@Binds bindShareRepository` lives alongside the existing repository bindings in `RepositoryModule` — adding it does not change any other binding.
- **No conflicting manifest edits:** other initiatives that touch the manifest (e.g. background workers) edit different elements; the two new `<activity>`/`<provider>` blocks are independent.

---

## 11. Rollback plan

The change is purely additive; revert in this order (each step is independent and safe):

1. **Manifest:** delete the `ReceiveShareActivity` `<activity>` block and the `JupiterDocumentsProvider` `<provider>` block from `AndroidManifest.xml`. The app immediately stops appearing in share sheets and pickers; nothing else changes.
2. **Code:** delete the new packages/files: `feature/share/` (Activity, Screen, ViewModel, UiState, QuickShare), `data/share/ShareRepositoryImpl.kt`, `data/saf/JupiterDocumentsProvider.kt`, `domain/repository/ShareRepository.kt`, `domain/model/ShareDestination.kt`, `domain/model/SharedPayload.kt`.
3. **DI:** remove the `bindShareRepository` `@Binds` and its two imports from `RepositoryModule.kt` (restore the verbatim original shown in §5.5 *minus* the share binding).
4. **Resources:** remove the added strings from `strings.xml` and the three ProGuard rules.
5. **Gradle:** remove the three test dependency lines and their `libs.versions.toml` entries (and the `testOptions` block if added).
6. `./gradlew clean assembleDebug testDebugUnitTest` to confirm the tree is back to baseline. Because no existing file's behaviour was altered (only `RepositoryModule` and the manifest/strings gained additive entries), reverting cannot leave the app in a broken intermediate state.

---

## 12. Definition of done (≥10 items)

- [ ] `AndroidManifest.xml` contains the new `ReceiveShareActivity` (both `ACTION_SEND` and `ACTION_SEND_MULTIPLE` filters) and the `JupiterDocumentsProvider` with authority `${applicationId}.documents`.
- [ ] Sharing a photo from another app shows **"Save to Jupiter"**, and saving lands the real bytes in the chosen folder (verified by browsing to it in Jupiter).
- [ ] Multi-select share (`ACTION_SEND_MULTIPLE`) saves all items; colliding names are suffixed " (n)" and never overwrite existing files.
- [ ] Jupiter appears as a root inside another app's system file picker (e.g. Gmail attach) and files can be browsed/attached from it.
- [ ] `JupiterDocumentsProvider.openDocument` is read-only and rejects write modes.
- [ ] `QuickShare.share(...)` opens a working system chooser using the existing FileProvider authority (no `file://` leak; `FileUriExposedException` cannot occur).
- [ ] All new cross-boundary calls return `AppResult`/`AppError` and run on `@IoDispatcher`; no `Dispatchers.IO` hard-coding, no thrown exceptions across boundaries.
- [ ] `ReceiveShareViewModel` exposes an **immutable** `ReceiveShareUiState` via `StateFlow`; the screen is a pure state-in/callbacks-out composable.
- [ ] `RepositoryModule` binds `ShareRepository`; the app builds and injects without Hilt errors.
- [ ] ProGuard rules keep the manifest-referenced provider/activity/EntryPoint; **release** build (`assembleRelease`) does not strip them.
- [ ] **No regression:** existing app launch flow (Splash → Onboarding/Permission → Main) still works; the file **Browser** and **Vault** features still open and function unchanged.
- [ ] **No regression:** `Destinations.kt` and `JupiterNavHost.kt` are byte-for-byte unchanged (the receive flow is a separate activity), so the main navigation back stack is unaffected.
- [ ] **CI green:** `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` both pass; the new `ShareRepositoryImplTest` runs and passes.
```
