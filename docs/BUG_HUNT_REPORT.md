# Jupiter — Bug Hunt Report

Package: `com.jupiter.filemanager` · minSdk 26 / target+compile 35 · Kotlin 2.0 / Compose / Material3 / Hilt / Coroutines · Status: compiles green on CI.

All findings below were adversarially verified against the real source. This report covers **confirmed runtime defects only** (wrong result, crash, hang, leak, data loss, security hole, broken UX). Style/naming issues are excluded.

---

## 1. Summary

**Total confirmed defects: 20**

### By severity

| Severity | Count |
|----------|-------|
| Blocker  | 2  |
| High     | 10 |
| Medium   | 5  |
| Low      | 3  |

### By category

| Category   | Count |
|------------|-------|
| Data loss  | 6  |
| Security   | 5  |
| Logic      | 5  |
| Crash      | 3  |
| Threading  | 1  |

### Single most important fix

**Add a self-overwrite guard to `FileOperationsManager.copyFileStreaming` (FileOperationsManager.kt:262).** Copying or moving a file into the directory it already lives in — trivially reachable from the action-mode Copy/Move buttons, which open the folder chooser rooted at the current directory — opens the same path for read and write. `FileOutputStream` truncates the file to 0 bytes before any bytes are read, and for MOVE the now-empty source is then deleted. The user's file is **silently and irreversibly destroyed** with two taps. This is pure data loss in the app's most-used code path and must be fixed first.

---

## 2. Blockers & High

Ordered by severity, then by blast radius.

---

#### Copy/Move into the file's own directory truncates and destroys the source

`app/src/main/java/com/jupiter/filemanager/data/file/FileOperationsManager.kt:262`

**Badges:** `BLOCKER` · `DATA LOSS`

**Failure scenario.** The action-mode Copy/Move buttons open `FolderChooserDialog` with `startPath = uiState.currentPath` (FileBrowserScreen.kt:230/236) — the directory the selected items already live in. The user taps **Select** to confirm, so `destinationDir == source.parent` and `target = File(destinationDir, source.name) == source`. `copyFileStreaming` opens `source.inputStream()` then `target.outputStream()`; `FileOutputStream` opens with `O_TRUNC`, zeroing the inode the input FD points at. `input.read()` immediately returns `-1`, the loop writes 0 bytes, and the file is left empty. For COPY the file is truncated to 0 bytes; for MOVE the post-copy cleanup (lines 217–221) then `deleteRecursively` the source, destroying it entirely. No self-overwrite guard exists anywhere in the chain (`planEntry`, `FileRepositoryImpl.copy/move`, `FileBrowserViewModel.copySelectedTo/moveSelectedTo`).

```kotlin
source.inputStream().buffered(BUFFER_SIZE).use { input ->
    target.outputStream().buffered(BUFFER_SIZE).use { output ->  // if target==source, truncates before any read
```

**Fix.** Before copying, compare canonical paths. If `source.canonicalFile == target.canonicalFile` (or, for a directory move, `target` is inside `source`), reject with a `FAILED` progress or auto-rename the destination (e.g. `name (copy)`). Never open the same path for read and write.

---

#### Wi-Fi transfer server can never start — INTERNET permission missing

`app/src/main/AndroidManifest.xml:30`

**Badges:** `BLOCKER` · `CRASH`

**Failure scenario.** The manifest declares only READ/WRITE_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE, USE_BIOMETRIC, POST_NOTIFICATIONS — **no `android.permission.INTERNET`** (project-wide grep returns zero hits; the NanoHTTPD JAR contributes none). Android maps INTERNET to the `inet` GID (AID_INET 3003) and the kernel restricts the `socket(AF_INET/AF_INET6)` syscall to that group — this blocks `bind`/`listen`, not just outbound connects. When the user taps **Start server**, `WifiTransferViewModel.start()` → `WifiTransferServer.startServer()` → NanoHTTPD `start(SOCKET_READ_TIMEOUT, false)` creates a `ServerSocket`, whose `socket()` syscall throws `SocketException: EACCES`. That is caught at WifiTransferServer.kt:38, which returns `null`, and the ViewModel publishes the misleading error *"Couldn't start the server. Make sure you're connected to Wi-Fi."* This fires deterministically on every device and every network — the entire feature is permanently non-functional.

```xml
<!-- AndroidManifest.xml permissions list: no INTERNET declared -->
```

**Fix.** Add `<uses-permission android:name="android.permission.INTERNET"/>` to AndroidManifest.xml.

---

#### Biometric vault unlock is dead code — vault always unlocks with no authentication

`app/src/main/java/com/jupiter/filemanager/feature/vault/VaultScreen.kt:311`

**Badges:** `HIGH` · `SECURITY`

**Failure scenario.** `authenticateThenUnlock()` calls `context.findFragmentActivity()`, which walks the `ContextWrapper` chain for a `FragmentActivity`. The only host activity is `class MainActivity : ComponentActivity()` (MainActivity.kt:37) — **not** a FragmentActivity/AppCompatActivity — so `findFragmentActivity()` returns `null` on every device. The `if (activity == null)` branch then calls `onAuthenticated()` and returns, so `viewModel.unlock()` runs immediately and `BiometricPrompt` (which requires a FragmentActivity to attach) is never constructed. The encrypted vault opens with a single tap and zero authentication on 100% of installs; anyone holding an unlocked phone reaches all vault contents.

```kotlin
val activity = context.findFragmentActivity()
if (activity == null) {
    // No FragmentActivity host to attach a BiometricPrompt to; proceed.
    onAuthenticated()
    return
}
```

**Fix.** Make the host a `FragmentActivity`/`AppCompatActivity` so `BiometricPrompt` can attach, **and** change the fallback so a missing host keeps the vault locked (surface an error) instead of calling `onAuthenticated()`. The "degrade to unlock" path must never apply to the no-host case.

---

#### WebDAV directory navigation duplicates the base path, breaking subfolder browsing

`app/src/main/java/com/jupiter/filemanager/data/remote/WebDavFileSource.kt:82`

**Badges:** `HIGH` · `LOGIC`

**Failure scenario.** With a non-empty `basePath` (e.g. Nextcloud `"/remote.php/dav/files/user"`), `list()` builds the request as `joinPaths(basePathRelative(credentials), path)`. But `buildEntry()` sets each `RemoteEntry.path = normalized` — the **full** server-absolute decoded href, which already includes the base (e.g. `"/remote.php/dav/files/user/Photos"`). The `basePrefix` is computed but only used by `isSelf()` for self-filtering, never to strip the prefix. Tapping a folder calls `list(connectionId, entry.path)`, so `joinPaths("/remote.php/dav/files/user", "/remote.php/dav/files/user/Photos")` → `"/remote.php/dav/files/user/remote.php/dav/files/user/Photos"`, which 404s. The same double-prepend afflicts `download()` (line 110). Subfolder navigation is impossible whenever a base path is set.

```kotlin
val requestPath = joinPaths(basePathRelative(credentials), path)   // list()
// buildEntry returns RemoteEntry(path = normalized, ...) — full server-absolute href, base included
```

**Fix.** Make entry paths relative to the base (strip `basePrefix` in `buildEntry`/`parseMultiStatus`), **or** have `list()`/`download()` detect an already-absolute path and not re-prepend the base. Keep the path representation `list()` returns consistent with what `list()` expects as input.

---

#### WebDAV over HTTPS on a non-443 port is forced to plaintext http

`app/src/main/java/com/jupiter/filemanager/data/remote/WebDavFileSource.kt:169`

**Badges:** `HIGH` · `LOGIC`

**Failure scenario.** `scheme()` returns `"https"` only when `port == 443`, else `"http"`. `RemoteCredentials`/`ConnectionType` have no TLS flag and no WEBDAVS variant, so the port is the sole signal. For an HTTPS server on a custom port (e.g. Nextcloud at `https://host:8443/remote.php/dav`), `scheme()` returns `"http"` and `buildUrl()` produces `http://host:8443/...`. Every PROPFIND/GET hits a TLS-only port over plaintext and fails — `testConnection`/`list`/`download` all break for any HTTPS WebDAV server not on 443 (the common 8443/custom-port case).

```kotlin
private fun scheme(credentials: RemoteCredentials): String =
    if (credentials.port == 443) "https" else "http"
```

**Fix.** Derive the scheme from connection type / an explicit stored TLS flag (e.g. a WEBDAV-S variant or `https` boolean) rather than guessing from the port, and default WebDAV to https. At minimum, also treat any user-marked secure port as https — the `port==443`-only check cannot represent HTTPS on custom ports.

---

#### FTPS data channel never protected (no PBSZ/PROT) — transfers in cleartext

`app/src/main/java/com/jupiter/filemanager/data/remote/FtpFileSource.kt:91`

**Badges:** `HIGH` · `SECURITY`

**Failure scenario.** For `ConnectionType.FTPS` an `FTPSClient` (explicit/FTPES) is created. Its data-channel protection defaults to `"C"` (Clear). After login the code calls `enterLocalPassiveMode()` and `setFileType()` but **never** `execPBSZ(0)`/`execPROT("P")` (grep confirms these are absent). The control channel is TLS-encrypted, but directory listings (`listFiles`) and file bytes (`retrieveFile`) travel in plaintext — defeating the confidentiality the user chose FTPS for. On strict servers requiring PROT P, transfers also fail outright.

```kotlin
val client: FTPClient = if (credentials.type == ConnectionType.FTPS) { FTPSClient() } else { FTPClient() }
...
client.enterLocalPassiveMode()
client.setFileType(FTP.BINARY_FILE_TYPE)
// no execPBSZ(0) / execPROT("P")
```

**Fix.** After successful login, when the client is an `FTPSClient`, call `execPBSZ(0)` then `execPROT("P")` before any transfer, and fail the connection if the server rejects PROT P.

---

#### createZip crashes the entire archive on any empty subdirectory

`app/src/main/java/com/jupiter/filemanager/data/file/ArchiveManager.kt:98`

**Badges:** `HIGH` · `CRASH`

**Failure scenario.** `collectZipSources()` (lines 781–783) emits a `ZipSource` whose `file` is the **directory itself** with `entryName` ending in `/` for any empty subdirectory. The `createZip` loop has no directory branch: for every source it unconditionally runs `BufferedInputStream(FileInputStream(source.file))`. `FileInputStream` on a directory throws `FileNotFoundException` (an `IOException`), caught at line 149 and turned into a terminal `FAILED` snapshot; the half-written `.zip` is then deleted (lines 162–164). Compressing **any** folder tree containing even one empty directory fails entirely and produces nothing.

```kotlin
val entry = ZipEntry(source.entryName).apply { time = source.file.lastModified() }
zip.putNextEntry(entry)
BufferedInputStream(FileInputStream(source.file)).use { input ->  // throws FNFE when source.file is a directory
```

**Fix.** Detect directory entries in the loop (`if (source.entryName.endsWith("/")) { zip.putNextEntry(entry); zip.closeEntry(); processedItems += 1; continue }` or check `source.file.isDirectory`) and skip the `FileInputStream` read, writing only the empty directory entry. Stream content only for regular files.

---

#### Accent color built with the wrong Compose `Color(ULong)` overload — crash / wrong color

`app/src/main/java/com/jupiter/filemanager/ui/theme/Theme.kt:51`

**Badges:** `HIGH` · `CRASH`

**Failure scenario.** Picking a non-default accent (e.g. Indigo `0xFF4F46E5L`) runs `base.withAccent(Color(accentColorArgb.toULong()), darkTheme)`. The `Color(value: ULong)` overload does **not** interpret its argument as ARGB — it treats it as the internal packed `Color.value` whose low 6 bits encode a color-space id (`getColorSpace(value and 0x3f)`). For Indigo the low bits decode to id 37, out of range → `getColorSpace(37)` throws `IllegalArgumentException`. The throw fires during composition when `withAccent` calls `accent.luminance()` (Theme.kt:79), crashing the whole app (JupiterTheme wraps the root). Several palette entries crash; others decode to a wrong-but-valid color space and render garbage colors. Swatches in the picker look correct only because they use the ARGB-aware `Color(argb: Long)` overload (Color.kt:182).

```kotlin
base.withAccent(Color(accentColorArgb.toULong()), darkTheme)
```

**Fix.** Use the ARGB overload: `Color(accentColorArgb)` (Long) or `Color(accentColorArgb.toInt())`, matching the swatch construction. Do not call `.toULong()`.

---

#### Single-item Delete from the actions sheet deletes permanently with no confirmation

`app/src/main/java/com/jupiter/filemanager/feature/browser/FileBrowserScreen.kt:574`

**Badges:** `HIGH` · `DATA LOSS`

**Failure scenario.** Long-press → `FileActionsSheet` → **Delete** immediately runs `viewModel.enterSelection(item)` then `viewModel.deleteSelected()`, which flows to `FileOperationsManager.delete` → `deleteRecursively` — a permanent, non-trash `File.delete()` over the whole tree. No confirmation dialog, no undo. One accidental tap irreversibly destroys a file or an entire folder. (The Cleanup and Duplicates screens do gate delete with `DeleteConfirmDialog`, confirming this omission is unintended.)

```kotlin
FileAction.DELETE -> {
    viewModel.enterSelection(item)
    viewModel.deleteSelected()
}
```

**Fix.** Route delete through a confirmation `AlertDialog` (like `RenameDialog`) before `deleteSelected()`, or move to a trash/restore flow. Apply the same gate to `SelectionTopBar.onDelete`.

---

#### Bulk Delete from the selection bar also deletes permanently with no confirmation

`app/src/main/java/com/jupiter/filemanager/feature/browser/FileBrowserScreen.kt:224`

**Badges:** `HIGH` · `DATA LOSS`

**Failure scenario.** In multi-select mode the `SelectionTopBar` trash icon wires `onDelete = { viewModel.deleteSelected() }` directly. One tap permanently and irreversibly deletes every selected item (potentially many files/folders) — `deleteSelected()` → `FileOperationsManager.delete` → `deleteRecursively` — with no confirmation and no undo. The only `AlertDialog` in the screen is the copy/move folder chooser.

```kotlin
onDelete = { viewModel.deleteSelected() },
```

**Fix.** Show a confirmation dialog summarizing the selected count before invoking `viewModel.deleteSelected()` (shares the fix with the single-item delete above).

---

#### SmartMerge can keep the worse copy and delete the better — MediaQuality.score mixes units

`app/src/main/java/com/jupiter/filemanager/data/media/MediaQualityProbe.kt:99`

**Badges:** `HIGH` · `DATA LOSS`

**Failure scenario.** `probeImage` returns `score = width*height` (pixels) when decode succeeds, but `score = sizeBytes` (bytes) when `BitmapFactory` can't read the bounds (corrupt/unusual header, HEIC variant). The same collision exists for VIDEO (`pixels + bitrate/1000` vs `sizeBytes`) and AUDIO (`bitrate` vs `sizeBytes`). `SmartMergeViewModel` ranks by `score` descending (`maxWithOrNull`, qualityComparator line 201) and keeps the highest. For a group of a genuine 4000×3000 photo (`score = 12,000,000` px) and a corrupt 15 MB copy (`score = 15,000,000` bytes), it recommends **keeping the unreadable 15 MB copy and deleting the real 12 MP photo**. Bytes and pixel counts share the same numeric range, so the ranking routinely flips. Accepting the merge deletes the better original.

```kotlin
// valid image: score = pixels
score = width.toLong() * height.toLong(),
...
// fallback when dims unknown: score = bytes
return MediaQuality(kind = QualityKind.IMAGE, sizeBytes = sizeBytes, label = "", score = sizeBytes)
```

**Fix.** Make `score` a single consistent magnitude. Never fall back to `sizeBytes` for `score` — use `0L` when the real quality metric is unknown so any probed copy outranks an unprobeable one, letting `sizeBytes`/`lastModified` tie-breakers decide. `score` should encode only the intrinsic metric (pixels / bitrate).

---

#### `isEnabled` getter does a `runBlocking` DataStore read on the main thread (ANR risk)

`app/src/main/java/com/jupiter/filemanager/feature/ai/AnthropicAiAssistant.kt:57`

**Badges:** `HIGH` · `THREADING`

**Failure scenario.** `AnthropicAiAssistant` is the production `AiAssistant` binding (AiModule.kt). `AiAssistantViewModel` initializes its state field with `isEnabled = aiAssistant.isEnabled` (line 35) — executed synchronously on the main thread when `hiltViewModel()` constructs the ViewModel. The getter calls `currentKey()` → `runBlocking { settings.aiApiKey.first() }`, blocking the UI thread on a cold Preferences DataStore disk read. Cold start is exactly when the ViewModel is first created, so on a cold/contended DataStore this blocks long enough to drop frames or ANR. `SearchViewModel.resolveFilter` (line 143) hits the same blocking read on the Main dispatcher when natural-language mode is on.

```kotlin
override val isEnabled: Boolean
    get() = currentKey().isNotBlank()
...
private fun currentKey(): String = try {
    runBlocking { settings.aiApiKey.first() }
} catch (_: Throwable) { "" }
```

**Fix.** Don't expose `isEnabled` as a blocking property on Main. Make it `suspend`, or back it with a cached value updated by collecting `settings.aiApiKey` on a background scope, or have callers read inside `withContext(IO)`. Initialize `AiAssistantUiState.isEnabled = false` and update it from a coroutine after an async read instead of in the field initializer.

---

## 3. Medium & Low

| Bug | File:line | Category | Fix |
|-----|-----------|----------|-----|
| Tar/compressed-tar extract byte progress reset every entry (`=` instead of `+=`); progress jumps backward and COMPLETED reports too-small total | ArchiveManager.kt:400 | Medium / Logic | Make `copyEntry`/`writeArchiveEntry` return the cumulative total (`startBytes + written`) **or** accumulate at the call site (`processedBytes += ...`); also make the directory branch consistent. |
| SFTP `StrictHostKeyChecking="no"` with no `known_hosts` — any host key accepted, full MITM exposure (password + files to attacker) | SftpFileSource.kt:109 | Medium / Security | Load a persisted `known_hosts` (`JSch.setKnownHosts`) with `StrictHostKeyChecking="ask"/"yes"` and trust-on-first-use, or pin the host key after first connect. |
| Directory re-listed from disk and DataStore written on every search keystroke (`setFilter` unconditionally persists `showHidden` + calls `loadDirectory()`) — input lag/flicker in large folders | FileBrowserViewModel.kt:131 | Medium / Logic | Persist `showHidden` only when it changed; debounce query reloads or filter loaded items in memory for query-only changes. |
| Video position ticker overwrites in-progress user seek with stale `currentPosition` — slider thumb snaps backward after seek | VideoPlayerScreen.kt:97 | Medium / Logic | Add an `isSeeking` flag set on `onSeek`, cleared once `currentPosition` converges within tolerance of `positionMs`; suppress ticker updates meanwhile (or ignore updates whose delta exceeds the poll step). |
| Anthropic/Claude API key persisted in **plaintext** `jupiter_settings` DataStore, while remote passwords use EncryptedSharedPreferences — readable verbatim on rooted device / file exfiltration | SettingsDataStore.kt:56 | Medium / Security | Store the API key through the existing encrypted layer (`EncryptedSharedPreferences`/`CredentialStore`), consistent with remote passwords. |
| Cancelled copy/move leaves a partial (corrupt) destination file on disk — comment says "clean up the partial target" but no `delete()` runs | FileOperationsManager.kt:266 | Low / Data loss | On `CancellationException` (and failure), delete the partial target before propagating: `catch (ce: CancellationException) { runCatching { target.delete() }; throw ce }`. |
| Copy/Move from actions sheet leaves screen stuck in selection mode when folder chooser is cancelled (`enterSelection` runs before chooser; `onDismiss` clears only `pendingTransfer`) | FileBrowserScreen.kt:578 | Low / Logic | Call `viewModel.clearSelection()` in the chooser's `onDismiss` for sheet-initiated transfers, or defer `enterSelection` until the destination is confirmed. |
| Truncated large UTF-8 text files mislabeled as binary — 512 KB read cuts a multi-byte char, strict decode fails, `!isText` checked before `tooLarge` so wrong notice shown | TextEditorViewModel.kt:254 | Low / Logic | When `tooLarge`, trim the buffer back to the last complete UTF-8 sequence before strict-decoding, or check `tooLarge` before `isText` in `buildNotice`. |

---

## 4. Recommended fix order

1. **Self-overwrite guard in `copyFileStreaming`** (FileOperationsManager.kt:262) — blocker, silent irreversible data loss in the most-used path. Fix first.
2. **Add INTERNET permission** (AndroidManifest.xml) — one-line blocker that unbreaks the entire Wi-Fi transfer feature.
3. **Delete-confirmation dialog** — covers both data-loss highs at once (FileBrowserScreen.kt:224 selection bar and :574 actions sheet) with a single shared confirmation/trash flow.
4. **Vault auth bypass** (VaultScreen.kt:311 + MainActivity → FragmentActivity) — security blocker-grade: make the host a FragmentActivity and never unlock on a missing host.
5. **Accent color crash** (Theme.kt:51) — drop `.toULong()`; one-character-class fix that stops a full-app crash on a common settings action.
6. **SmartMerge unit collision** (MediaQualityProbe.kt:99) — return `0L` on probe failure to stop deleting the better copy.
7. **createZip empty-dir crash** (ArchiveManager.kt:98) — add the directory branch; also fold in the tar progress accumulation fix (ArchiveManager.kt:400) since both live in the same file.
8. **WebDAV path/scheme pair** (WebDavFileSource.kt:82 base-path double-prepend and :169 scheme) — fix together; they jointly determine whether WebDAV works at all.
9. **Transport security: FTPS PROT** (FtpFileSource.kt:91) and **SFTP host-key checking** (SftpFileSource.kt:109) — group as one "remote transport hardening" pass; add **API-key encryption** (SettingsDataStore.kt:56) alongside.
10. **AI `isEnabled` blocking read** (AnthropicAiAssistant.kt:57) — move off the main thread to remove ANR risk.
11. **Remaining UX polish:** search-keystroke reload debounce (FileBrowserViewModel.kt:131), video seek snap-back (VideoPlayerScreen.kt:97), cancelled-copy orphan cleanup (FileOperationsManager.kt:266 — naturally folds into fix #1's file), stuck selection mode on cancel (FileBrowserScreen.kt:578), and truncated-UTF-8 notice (TextEditorViewModel.kt:254).
