# Jupiter — Runtime Diagnosis & Fix Plan

> Scope: on-device runtime/logic/UX defects. The app compiles green and unit tests pass — every defect below is a runtime behavior, lifecycle, or UX bug, not a compile error.
> Package `com.jupiter.filemanager` · Kotlin 2.0 · Compose/Material3 · MVVM/Hilt · Coroutines/Flow/StateFlow · minSdk 26 / target 35.
> All paths are repo-relative to `/home/user/Jupiter`. Line numbers reflect the diagnostic snapshot; treat them as anchors, re-confirm on edit.

---

## 1. Summary

There are **two distinct root causes** behind the three "infinite spinner" storage screens, plus two unimplemented features and one inert dual-pane UI.

**Root cause A — the storage scans never produce content in a user-acceptable window because the data layer does the most expensive thing first and emits nothing until it finishes.**
`StorageAnalyticsRepositoryImpl.storageOverview()` does a synchronous, non-incremental, full-volume walk of `/storage/emulated/0` (depth 64) before returning a single terminal result, and it stat-s **every file ~8–9 times** (it builds a whole `FileItem` just to read `.type`). `findDuplicates()` walks the **entire tree** into a size-bucket map with zero emissions, then SHA-1-hashes every same-size candidate before the first `emit`. Every storage ViewModel keeps its full-screen `LoadingView` up until either the first emission or flow completion — both of which arrive only after minutes of IO. So the loading-clear logic is *correct*; the screens are simply waiting on an unbounded, front-loaded scan that streams nothing.

**Root cause B — Smart Merge can hang truly forever (not just "slow") because a blocking JNI media probe runs inside the Flow collector.**
`SmartMergeViewModel.scan()` awaits `MediaQualityProbe.probeAll(...)` **inside** the `collect` lambda before adding the first recommendation or letting the flow complete. `probeAll` probes paths sequentially, and for VIDEO/AUDIO it calls `MediaMetadataRetriever.setDataSource(path)` with **no timeout** — a blocking call that can hang for seconds-to-forever on a malformed/large file. While it's hung: no recommendation exists and `onCompletion` never fires, so `state.recommendations.isEmpty() && state.isScanning` stays permanently true → `LoadingView()` forever.

**Secondary cause C — the entire data layer swallows "permission denied" into "empty result."**
`FileSystemDataSource.walkTopDown` converts a `null`/`SecurityException` from `listFiles()` into `continue`. Without `MANAGE_EXTERNAL_STORAGE` on API30+, the root yields zero children, the scan completes with 0 bytes/0 files, and **no error is raised**. No scan ViewModel consults `StorageAccessManager` after the one-shot splash gate, so a denied/revoked permission is indistinguishable from a genuinely empty volume. This is the mechanism behind Cleanup's "0 B / 0 Large / 0 Duplicate groups" with no prompt. (It does **not** cause the infinite spinners — an empty walk completes and clears `isScanning`.)

**Features D & E — never implemented.** Google Drive "Connect (coming soon)" is hard-coded placeholder UI with no OAuth/Drive client anywhere in the codebase. Dual-pane renders two panes but never wires the *already-existing* `copySelectedTo`/`moveSelectedTo` API, never enters selection mode, and never uses Coil for thumbnails.

**Distinct fixes required: 14** (see §6 roadmap for ownership and ordering).

---

## 2. Hanging screens

### 2.1 Storage Analytics — perceived-infinite spinner (full-disk walk + per-file over-stat)

**Cause:** Root cause A. *Not* `probeAll` (Analytics doesn't probe). *Not* a non-completing flow. It is a single terminal `suspend` over an unbounded, pathologically slow walk; the permission/empty-state issue is a separate, milder symptom on the same screen.

- **`data/storage/StorageAnalyticsRepositoryImpl.kt:60`** — `storageOverview()` blocks on one terminal result:
  ```kotlin
  for (file in dataSource.walkTopDown(volume.rootPath)) {
      currentCoroutineContext().ensureActive()
      if (!isRegularFile(file)) continue
      val size = safeLength(file)
      val category = categoryFor(file)
      ...
  } // runs to completion before AppResult.Success is returned; nothing emitted incrementally
  ```
  `StorageAnalyticsViewModel.analyze()` (~line 41) awaits this single suspend; `StorageAnalyticsScreen.kt:105` gates the full-screen spinner on `uiState.isLoading && uiState.overview == null`, so it stays up for the entire walk.

- **`data/storage/StorageAnalyticsRepositoryImpl.kt:183`** — the dominant cost: classifying a file by name re-stats it ~7 extra times.
  ```kotlin
  private fun fileTypeOf(file: File): FileType =
      runCatching { dataSource.toFileItem(file).type }.getOrDefault(FileType.OTHER)
  // builds a whole FileItem (isDirectory/length/lastModified/isHidden/canRead/canWrite/childCount)
  // just to read .type, which is derived purely from the filename.
  ```
  Combined with `safeLength` (line 65) and `isRegularFile` (line 63) that is ~9 syscalls/file across tens of thousands of files.

**Fix:**
1. Classify by filename only — expose the existing pure `FileSystemDataSource.fileTypeFor(name, isDirectory)` and call `dataSource.fileTypeFor(file.name, isDirectory = false)`. Removes ~6 syscalls/file; this alone makes the walk render fast.
2. Make `storageOverview` incremental: expose it as `Flow<StorageOverview>` (or a progress callback) emitting a partial overview every N files / ~250 ms. Have the VM set `_uiState.overview` early (non-null) so the screen leaves `LoadingView` (the `overview == null` gate at `StorageAnalyticsScreen.kt:105` already supports this) while a lightweight "scanning" chip stays visible.
3. Scope/cap the walk (skip `Android/data`, `.thumbnails`; lower depth for analytics).

### 2.2 Smart Merge — true-infinite spinner (blocking probe inside the collector)

**Cause:** Root cause B — `MediaQualityProbe.probeAll` awaited inside `collect`, plus an untimed `MediaMetadataRetriever.setDataSource`. This is the only screen whose hang can be *literally* forever.

- **`feature/cleanup/SmartMergeViewModel.kt:81`** — terminal state and first content both gated behind the probe:
  ```kotlin
  val groupQualities = qualityProbe.probeAll(group.files.map { it.path })
  qualities.putAll(groupQualities)
  collected.add(buildRecommendation(group, qualities))
  _uiState.value = _uiState.value.copy(recommendations = collected.toList(), ...)
  // only AFTER probeAll returns — collection is sequential & back-pressured
  ```
- **`data/.../MediaQualityProbe.kt:39`** — `probeAll` probes sequentially (`paths.associateWith { probe(it) }`).
- **`data/.../MediaQualityProbe.kt:207`** — `withRetriever` → `MediaMetadataRetriever.setDataSource(path)`, blocking JNI, **no timeout**.
- **`feature/cleanup/SmartMergeScreen.kt:138`** — `state.recommendations.isEmpty() && state.isScanning` → `LoadingView()` stays true forever while the probe is hung.

**Fix (two changes, both required):**
1. **`MediaQualityProbe.kt`** — wrap `setDataSource`/extract in `withTimeoutOrNull(3_000..5_000)`; on timeout fall back to size-only quality. A single bad file then degrades instead of hanging the collector.
2. **`SmartMergeViewModel.scan()`** — do **not** await `probeAll` inside `collect`. Emit each recommendation immediately using a size/`lastModified`-only ranking (empty probe map), let `onCompletion` flip `isScanning=false`, then probe asynchronously (`viewModelScope.launch` on `Dispatchers.IO`, or after collection) and merge qualities into state when ready. Optionally bound `probeAll` concurrency with a semaphore + `awaitAll`.

> Note: A secondary slowness also applies here — `findDuplicates` (see §2.4) front-loads the whole walk + first-bucket hashing before the first emit. Fixing §2.4 removes the residual "slow even on a healthy library" delay; fixing the probe removes the true hang.

### 2.3 Cleanup — stuck on "Scanning storage…" with 0 B / 0 Large / 0 Duplicate groups

**Cause:** Root cause A (non-streaming `findDuplicates`) gating the spinner, compounded by root cause C (denied permission → silent empty). *Not* `probeAll` — Cleanup's probing is off the critical path.

- **`feature/cleanup/CleanupViewModel.kt:102`** — `isScanning` cleared only after **both** jobs join:
  ```kotlin
  val largeFilesJob = launch { collectLargeFiles(rootPath) }
  val duplicatesJob = launch { collectDuplicates(rootPath) }
  largeFilesJob.join()
  duplicatesJob.join()
  _uiState.update { it.copy(isScanning = false) }
  ```
  `duplicatesJob.join()` never returns in a user-acceptable window because `findDuplicates` is non-streaming (§2.4). Large-files threshold is 100 MB, so `largeFiles` is empty for most users → the visible 0/0/0.

**Fix:**
1. Decouple `isScanning` from `duplicatesJob.join()`: flip a coarse `isScanning=false` once overview + large-file pass complete; surface duplicate discovery as its own secondary/incremental indicator (the screen already renders groups as they arrive).
2. Make `findDuplicates` stream (§2.4).
3. Add the permission gate (§2.5) so a denied scan reads "Grant All Files Access," not an endless spinner.

### 2.4 Duplicates — infinite spinner (first emit far too late)

**Cause:** Root cause A in `findDuplicates`. The VM's loading logic is correct; the flow simply emits nothing for minutes.

- **`feature/cleanup/DuplicatesViewModel.kt:51`** — correct `onStart`/`onCompletion` lifecycle:
  ```kotlin
  analyticsRepository.findDuplicates(root)
      .onStart { ... isScanning = true ... }
      .onCompletion { cause -> if (cause == null) { ...copy(isScanning = false) } }
      .collect { group -> if (group.files.size > 1) { collected.add(group); ...; probeGroup(group) } }
  ```
- **`data/storage/StorageAnalyticsRepositoryImpl.kt:124`** — the non-streaming engine:
  ```kotlin
  for (file in dataSource.walkTopDown(rootPath)) { ... bySize.getOrPut(size){...}.add(file) } // entire tree first
  for ((_, candidates) in bySize) { ... val hash = hashFile(file) ... emit(DuplicateGroup(...)) } // emit only after full walk + hashing
  ```
  Until the first emit, `groups.isEmpty() && isScanning` keeps `DuplicatesScreen.kt:147-149` on `LoadingView()`.

- **`feature/cleanup/DuplicatesViewModel.kt:92`** (secondary, not the hang) — `probeGroup` launches `probeAll` per group; off-main and exception-guarded so it does **not** gate `isScanning`, but it floods `IoDispatcher` with blocking JNI work and slows everything. Defer/throttle probing until after the scan (or lazily for visible rows) and bound concurrency.

**Fix:** Make `findDuplicates` emit candidate groups incrementally and bound the work (skip huge buckets; cheap prefix-hash pre-filter before full SHA-1; exclude `Android/data`/`.thumbnails`). The screen already supports incremental rendering ("Still scanning…" SummaryCard), so an early emit resolves the spinner. Add the permission gate (§2.5).

### 2.5 Cross-cutting — permission gating (Cleanup / Analytics / Duplicates / Smart Merge / Recent / Downloads / Search)

**Cause:** Root cause C. The data layer swallows denial into emptiness, and permission is gated **once** at splash.

- **`data/file/FileSystemDataSource.kt:124`** — denial absorbed:
  ```kotlin
  val children: Array<File>? = try { file.listFiles() } catch (_: SecurityException) { null }
  if (children.isNullOrEmpty()) continue
  ```
- **`feature/splash/SplashViewModel.kt:43`** — one-shot gate; nothing re-checks after `Destination.Main`:
  ```kotlin
  !storageAccessManager.hasAllFilesAccess() -> Destination.Permission.route
  ```
- `PermissionScreen.kt:91-101` re-checks on `ON_RESUME`, but the user is never routed there post-onboarding.

**Important — this is NOT the infinite-spinner cause.** A denied/empty `findDuplicates` flow completes normally (`bySize` empty → no emissions → `onCompletion` fires), so `isScanning` is correctly cleared. Confirmed at `SmartMergeViewModel.kt:72`. Fix permission for honest empty-states; fix the probe/streaming for the spinners.

**Fix:**
1. Inject `StorageAccessManager` into `CleanupViewModel`, `StorageAnalyticsViewModel`, `DuplicatesViewModel`, `SmartMergeViewModel`, `RecentViewModel`, `DownloadsViewModel`, `SearchViewModel`. At the top of `scan()`/`analyze()`/`loadDownloads()`, if `!hasAllFilesAccess()` set a distinct `permissionRequired = true` state and skip the walk.
2. Screens render an actionable "Grant All Files Access" empty-state launching `StorageAccessManager.manageAllFilesSettingsIntent()`; re-run the scan on `ON_RESUME` (the `DisposableEffect` pattern from `PermissionScreen`).
3. Optionally distinguish `null` (denied/unreadable) from `emptyArray()` (truly empty) in `FileSystemDataSource.walkTopDown`, and return `AppError.AccessDenied(rootPath)` from `storageOverview`/`findDuplicates`/`findLargeFiles` when the root is unreadable (the `SecurityException` catch at `StorageAnalyticsRepositoryImpl.kt:90` currently never fires because the data source already absorbed it).
4. Stop `DownloadsViewModel` from collapsing `AppResult.Failure` (incl. `AccessDenied`) into `error = null`.

---

## 3. Cloud / Google Drive

### Current state (verified, not a bug — unimplemented)
- **`feature/cloud/CloudHubScreen.kt:256`** — `TextButton(onClick = {}, enabled = false) { Text("Connect (coming soon)") }`. Hard-disabled placeholder.
- **`data/connection/ConnectionRepositoryImpl.kt:116`** (`addCloudAccount`) and `:225-232` (`decodeCloudAccount`) construct `CloudAccount` with `usedBytes=0L, totalBytes=0L, isConnected=false` **unconditionally**. The "Connected" branch at `CloudHubScreen.kt:209` is dead code.
- Persistence format is `id|provider|displayName` (key `cloud_accounts`, store `jupiter_connections`). No token/email/quota field, no per-provider branching.
- No OAuth / GoogleSignIn / Credential Manager / Drive REST anywhere; no `play-services-auth`/`google-api`/`googleid`/`credentials`/`drive` deps in any gradle/toml. `data/remote/` has FTP/SFTP/SMB/WebDAV file sources but **no `DriveFileSource`**.

### Reusable building blocks
- **`data/remote/CredentialStore.kt`** — EncryptedSharedPreferences-backed per-id secret store (used for FTP/SFTP/SMB passwords). Natural home for the Drive refresh/access token, keyed by account id.
- **`data/remote/*FileSource.kt` + `RemoteAccessRepositoryImpl.kt`** — the established remote-browsing extension point; add `DriveFileSource` alongside.
- **`StorageBar`** component already renders used/total quota — once `usedBytes`/`totalBytes` are real, the connected card (`CloudHubScreen.kt:227`) works with no UI change.
- `ConnectionRepository.addCloudAccount`/`removeCloudAccount` + DataStore already survive process death.

### Plan A — real OAuth + Drive REST v3 (account-level access + quota)

**New deps (version catalog + app `build.gradle`):**
- `androidx.credentials:credentials` + `androidx.credentials:credentials-play-services-auth` — Credential Manager (current recommended path; the old `play-services-auth` GoogleSignIn is deprecated).
- `com.google.android.libraries.identity.googleid:googleid` — `GetSignInWithGoogleOption`.
- For Drive REST: prefer raw **OkHttp** against `googleapis.com` (lightweight). Heavier Play-services-free alternative: `com.google.api-client:google-api-client-android` + `com.google.apis:google-api-services-drive`.

**OAuth client-id requirement (must do in Google Cloud Console):**
- Create an **Android** OAuth client (package `com.jupiter.filemanager` + signing SHA-1) **and** a **Web application** client. Credential Manager / `GetSignInWithGoogleOption` requires the **Web** client id as `serverClientId`.
- Enable the **Drive API**; configure + verify the OAuth consent screen for the requested scopes. `drive.readonly`/`drive` are sensitive/restricted and need Google verification for distribution; `drive.file` (app-created files only) needs no verification.

**Files to add/change:**
- ADD `data/cloud/DriveAuthManager.kt` — `signIn(activity)`: Credential Manager `GetCredentialRequest` with `GetSignInWithGoogleOption(serverClientId = WEB_CLIENT_ID)` → then `com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient` `AuthorizationRequest` for `https://www.googleapis.com/auth/drive.readonly` (or `drive.file`) → access token / server auth code. Persist refresh/auth token in `CredentialStore` keyed by `account.id`. Token refresh on 401; never log tokens.
- ADD `data/cloud/DriveFileSource.kt` — mirror `WebDavFileSource`; list via `GET /drive/v3/files?q='<folderId>' in parents&fields=files(id,name,mimeType,size,modifiedTime)`, download via `GET /drive/v3/files/{id}?alt=media`, Bearer token from `DriveAuthManager`.
- ADD a `DriveConnect` use-case — after sign-in, `GET https://www.googleapis.com/drive/v3/about?fields=storageQuota,user` and update `isConnected=true`, `usedBytes=storageQuota.usage`, `totalBytes=storageQuota.limit`, `displayName`/email from `user`.
- CHANGE `domain/model/CloudAccount.kt` — add `accountEmail` and keep `isConnected`/quota as real fields.
- CHANGE `data/connection/ConnectionRepositoryImpl.kt:116, 225-232` — extend the encoded record beyond `id|provider|displayName` to persist email + connected + quota; stop hard-coding `false/0/0`.
- CHANGE `feature/cloud/CloudHubScreen.kt:256` + `CloudHubViewModel` — wire Connect → `viewModel.onConnect(account)` → `DriveAuthManager.signIn(activity)`.
- Manifest: only `INTERNET` (already required for remote sources). Tokens stay in `CredentialStore`.

### Plan B — SAF fallback (no OAuth, zero console work)
Android's Google Drive app exposes a `DocumentsProvider`, so `ACTION_OPEN_DOCUMENT_TREE` / `ACTION_OPEN_DOCUMENT` grants Drive folder access with **no** client-id, scopes, or verification.
- In `CloudHubScreen`/`CloudHubViewModel`: `rememberLauncherForActivityResult` launching `Intent(ACTION_OPEN_DOCUMENT_TREE)`; `takePersistableUriPermission` on the returned tree Uri; persist the Uri string in the cloud-account record; browse via `DocumentFile.fromTreeUri` / `DocumentsContract` (reuse the app's SAF document-browsing path).
- Trade-offs: **no `storageQuota`** (hide the `StorageBar` quota row), requires the Drive app installed, folder-scoped (not account-wide). This is the fastest honest "Connect"; Plan A is needed for account-level sync + quota.

**Recommendation:** ship **Plan B first** (immediate honest connectivity, no console/verification blocker), then layer **Plan A** for quota + account-level browsing.

---

## 4. Dual pane

### Current state
`DualPaneScreen` renders two independent `Pane`s but wires each only to navigation (`onCrumbPath`/`onOpenDirectory` → `openDirectory`, `onOpenFile`, `onRetry` → `refresh`). The transfer plumbing **already exists** in `FileBrowserViewModel` — `copySelectedTo(destinationPath)` (line 238), `moveSelectedTo(destinationPath)` (line 245) backed by `fileRepository.copy/move` with streamed `FileOperationProgress` via `runOperation` (line 366), plus `enterSelection` (121), `toggleSelection` (109), `clearSelection` (129). The dual-pane UI just never calls any of it, never enters selection mode, and reuses the full-width `FileRow` verbatim.

### Feature plan

**4.1 Copy/move between panes — `feature/browser/DualPaneScreen.kt:82, 177`**
- Add an `activePane: Left/Right` concept (`remember`, set on tap/long-press in that pane).
- Route per-row `onLongClick` to `activeViewModel.enterSelection(item)`; `onClick` while `selectionMode` → `toggleSelection(item)`, else keep open/navigate. (Currently `onLongClick` at `:177` just re-opens — wasted.) Pass real `selected = state.selectedPaths.contains(item.path)` and `selectionMode = state.selectionMode` into `FileRow` (currently hard-coded `false/false` at `:168-169`).
- Add an action bar (BottomAppBar / Row above the divider) shown when the active pane has `selectionMode=true`, with **Copy**/**Move** calling `activeViewModel.copySelectedTo(otherState.currentPath)` / `moveSelectedTo(otherState.currentPath)`.
- Render `components/OperationProgressCard.kt` bound to the active pane's `state.operation`; after a successful copy/move call the **destination** pane's `refresh()` (the source pane already reloads inside `runOperation`).

**4.2 Image/video thumbnails (Coil) — `feature/browser/components/FileRow.kt:91`**
- Replace the unconditional `Icon(iconForFile(item), ...)` with a thumbnail-or-icon composable: for `FileType.IMAGE`/`FileType.VIDEO` render
  ```kotlin
  AsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
          .data(File(item.path)).crossfade(true).size(96).build(),
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
  ) // iconForFile vector as placeholder/error
  ```
  `ContentScale.Crop` in a fixed 40.dp square fixes the landscape letter-boxing complaint; Coil decodes video frames too. Benefits single-pane `FileBrowserScreen` (shared row) as well. (Pattern already used at `feature/preview/ImageGalleryScreen.kt:163`.)

**4.3 Better density — `feature/browser/components/FileRow.kt:80`**
- Add `dense: Boolean = false`: when true reduce horizontal padding to ~8.dp, icon to ~32.dp, hide the trailing `MoreVert` overflow (dual-pane actions live in the action bar), collapse the subtitle to one combined line. Pass `dense = true` from `DualPaneScreen`'s `Pane` (`:166`).

**4.4 Total-Commander dual-pane features for phone (pick 2–3):**
- **Swap panes** — toolbar button swapping left/right paths (`F2`-style) for fast "copy here → look there."
- **Sync browse / equalize paths** — "set other pane to this path" button so both panes show the same folder, then diverge.
- **Select-by-pattern / Select all** — wildcard or "select all in pane" to bulk-mark for transfer (feeds `copySelectedTo`/`moveSelectedTo`).
- (Optional 4th) **Tap-the-divider to focus**, with a thin active-pane accent border so the user always knows the transfer source.

---

## 5. Other broken / misleading features

| Feature | File:line | Root cause | Fix |
|---|---|---|---|
| Analytics shows empty donut, not a permission prompt, when access denied | `data/storage/StorageAnalyticsRepositoryImpl.kt:47` | `storageOverview()` never checks `StorageAccessManager`; null `listFiles()` → 0 files → `Success(totalAnalyzedBytes=0)` indistinguishable from "no files" | Gate on `hasAllFilesAccess()`; return `AppResult.Failure(AppError.AccessDenied)` so the screen shows an ErrorView with "Grant All Files Access" retry |
| Cloud "Connect" permanently disabled | `feature/cloud/CloudHubScreen.kt:256` | Hard-coded `enabled = false`, empty `onClick`; `isConnected`/quota hard-coded `false/0/0` (`ConnectionRepositoryImpl.kt:116, 225-232`) | Implement Drive Plan B (SAF) then Plan A (OAuth+REST); §3 |
| Provider model has no auth/token/quota | `data/connection/ConnectionRepositoryImpl.kt:116` | Record is `id\|provider\|displayName`; no per-provider branch, no token field; `CredentialStore` never called for cloud | Extend record with email/connected/quota; store token in `CredentialStore` keyed by account id; §3 |
| No Drive content browsing path | `data/remote/WebDavFileSource.kt:1` | No `DriveFileSource`; `CloudAccount` never handed to a `FileSource` | Add `DriveFileSource` on the existing `FileSource` contract via Drive REST v3 (or SAF `DocumentsContract`); §3 |
| Dual-pane long-press wasted (no select) | `feature/browser/DualPaneScreen.kt:177` | `onLongClick` duplicates `onClick` (open/navigate) | Route `onLongClick` → `enterSelection`; §4.1 |
| Dual-pane rows too tall / overflow eats width | `feature/browser/components/FileRow.kt:80` | Full-width row reused verbatim in half-width pane | Add `dense` param; §4.3 |
| Recent/Downloads/Search silently empty on denied access | `feature/recent/RecentViewModel.kt:107` | Walk swallows `SecurityException`→null; no `StorageAccessManager` check; `DownloadsViewModel` maps `Failure`→`error=null` | Inject `StorageAccessManager`, add `permissionRequired` state + CTA; stop collapsing `AccessDenied` to empty; §2.5 |
| Probe floods IoDispatcher during dup scan (jank) | `feature/cleanup/DuplicatesViewModel.kt:92` | `probeGroup` launches per-group sequential `probeAll` (blocking JNI) competing with hashing | Defer/throttle probing until after scan / for visible rows; bound concurrency; §2.4 |

> Confirmed **NOT** defects (recorded to rule out): Home, Recent, Favorites, Search, Downloads, Details, Tags ViewModels (correct loading lifecycles over finite/DataStore flows), and the entire Transfer/Remote/Sync/Version/Workspace/Automation/Archive/Vault/Preview set (every VM clears `isLoading` in all branches, IO off-main, lifecycle-collected). The transfer/sync/version repos being "empty" is intentional honest-empty-state, not a hang.

---

## 6. Fix / build roadmap

Ordered, additive waves. Each is self-contained and introduces no regression. Grouped by single-owner files to avoid merge conflicts.

### Wave 1 — Stop the data-layer hangs (unblocks Analytics, Cleanup, Duplicates, Smart Merge)
*Owner A — `data/storage/StorageAnalyticsRepositoryImpl.kt`, `data/file/FileSystemDataSource.kt`*
- Replace `fileTypeOf` with name-only `dataSource.fileTypeFor(...)` (`:183`). Expose `fileTypeFor`/`typeForName` publicly.
- Make `storageOverview` incremental (`Flow<StorageOverview>` / progress callback), emit partial early.
- Make `findDuplicates` stream candidate groups incrementally; bound work (skip huge buckets, cheap prefix-hash pre-filter, exclude `Android/data`/`.thumbnails`).
- (Optional) distinguish `null` vs empty `listFiles()` in `walkTopDown` to enable `AccessDenied`.

### Wave 2 — Fix the media probe & VM loading gates
*Owner B — `data/.../MediaQualityProbe.kt`, `feature/cleanup/SmartMergeViewModel.kt`, `CleanupViewModel.kt`, `DuplicatesViewModel.kt`, `StorageAnalyticsViewModel.kt`*
- `MediaQualityProbe`: `withTimeoutOrNull(3–5s)` around `setDataSource`/extract; size-only fallback (`:207`, `:39`).
- `SmartMergeViewModel.scan()`: emit recommendation immediately (size/`lastModified` ranking), probe async, merge later (`:81`).
- `CleanupViewModel`: don't gate `isScanning` on `duplicatesJob.join()` (`:102`).
- VMs collect the now-incremental Analytics/Duplicates flows; show "still scanning" chrome, not bare `LoadingView`.
- `DuplicatesViewModel`: defer/throttle `probeGroup` (`:92`).

### Wave 3 — Permission gating & honest empty-states
*Owner B (or C) — the scan ViewModels + their screens*
- Inject `StorageAccessManager` into Cleanup/Analytics/Duplicates/SmartMerge/Recent/Downloads/Search VMs; add `permissionRequired` state; re-scan on `ON_RESUME`.
- Screens render "Grant All Files Access" CTA via `manageAllFilesSettingsIntent()`.
- `DownloadsViewModel`: stop mapping `AccessDenied`→`error=null`.
- `StorageAnalyticsRepositoryImpl.storageOverview`: return `AppError.AccessDenied` when root unreadable.
- **New dep:** none.

### Wave 4 — Dual pane (independent of storage waves)
*Owner C — `feature/browser/DualPaneScreen.kt`, `feature/browser/components/FileRow.kt` (+ shared with `FileBrowserScreen`)*
- 4.1 active-pane + selection wiring + Copy/Move action bar + `OperationProgressCard` + destination `refresh()`.
- 4.2 Coil `AsyncImage` thumbnails in `FileRow` (also benefits single-pane).
- 4.3 `dense` param.
- 4.4 swap panes + sync/equalize paths + select-all/by-pattern.
- **New dep:** none (Coil already present).
- ⚠️ `FileRow` is shared with single-pane `FileBrowserScreen`; make thumbnail/`dense` changes additive (default `dense=false`) to avoid regressing the single-pane list.

### Wave 5 — Google Drive, SAF first
*Owner D — `feature/cloud/*`, `data/connection/ConnectionRepositoryImpl.kt`, `data/cloud/*` (new), `domain/model/CloudAccount.kt`*
- 5a (Plan B / SAF): `ACTION_OPEN_DOCUMENT_TREE` connect, persistable Uri, `DocumentFile` browse, hide quota row. **No new deps, no console work.**
- 5b (Plan A / OAuth): `DriveAuthManager` + `DriveFileSource` + `DriveConnect` use-case; extend `CloudAccount` + record persistence; store token in `CredentialStore`; wire `CloudHubScreen.kt:256`.
  - **New deps:** `androidx.credentials:credentials`, `androidx.credentials:credentials-play-services-auth`, `com.google.android.libraries.identity.googleid:googleid`, plus OkHttp (likely transitive) **or** `com.google.api-client:google-api-client-android` + `com.google.apis:google-api-services-drive`.
  - **External blocker:** Google Cloud Console — Android + **Web** OAuth client ids, Drive API enabled, consent-screen verification for sensitive scopes (use `drive.file` to avoid verification initially).

### Ownership summary (single-owner file map)
| Owner | Files |
|---|---|
| A (data/storage) | `data/storage/StorageAnalyticsRepositoryImpl.kt`, `data/file/FileSystemDataSource.kt` |
| B (cleanup VMs + probe) | `data/.../MediaQualityProbe.kt`, `feature/cleanup/{SmartMerge,Cleanup,Duplicates}ViewModel.kt`, `feature/analytics/StorageAnalyticsViewModel.kt` |
| C (browser/dual-pane + permission UI) | `feature/browser/DualPaneScreen.kt`, `feature/browser/components/FileRow.kt`, scan screens' permission CTA |
| D (cloud) | `feature/cloud/*`, `data/connection/ConnectionRepositoryImpl.kt`, `data/cloud/*` (new), `domain/model/CloudAccount.kt` |

> Permission-gating VM edits (Wave 3) touch files also owned by B/C — sequence Wave 3 after Waves 1–2 land, and keep the `permissionRequired` additions isolated to the top of `scan()`/`analyze()` to minimize conflicts.
