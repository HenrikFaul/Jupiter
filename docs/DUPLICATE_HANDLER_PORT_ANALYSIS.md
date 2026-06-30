# Sift → Jupiter: Duplicate-Handler Port Analysis

> Principal-engineer port map from the **Sift** Windows desktop duplicate handler
> (Tauri/Rust engine + React/TS UI, SQLite content index, two-stage BLAKE3 hashing,
> media-quality probing) onto the **Jupiter** Android file manager
> (Kotlin · Coroutines/Flow · Hilt/MVVM · Compose · WorkManager · Coil).
>
> Source of truth for the current Jupiter baseline:
> - `app/src/main/java/com/jupiter/filemanager/data/storage/StorageAnalyticsRepositoryImpl.kt`
> - `app/src/main/java/com/jupiter/filemanager/domain/model/StorageAnalysis.kt` (`DuplicateGroup`, `wastedBytes`)
> - `app/src/main/java/com/jupiter/filemanager/domain/model/MergeRecommendation.kt`
> - `app/src/main/java/com/jupiter/filemanager/feature/cleanup/*` (`DuplicatesScreen`, `DuplicatesViewModel`, `SmartMergeScreen`, `SmartMergeViewModel`, `CleanupScreen`)
> - `app/src/main/java/com/jupiter/filemanager/feature/analytics/*`

---

## 1. Summary

**Sift** is a Windows desktop duplicate handler built on a Tauri (Rust) engine with a
React/TypeScript front end. Its defining idea is a **durable SQLite content index** treated
as "recovery truth": every file's content hash is remembered forever, keyed by volume
identity, so a drive reconnected months later is matched against everything ever seen — even
copies since deleted. Around that index it layers a **two-stage hashing cascade**
(size → partial head/tail fingerprint → full BLAKE3), **incremental re-hash skipping**
(unchanged `(size, mtime, volume, id)` is never re-hashed), engine-enforced **safety
invariants** (never delete the last surviving copy; never auto-delete), a **quarantine/Recycle
+ restore** deletion model with an **append-only audit log**, a **configurable Scan Builder**,
a **smart-selection rule engine**, and **media-quality/near-duplicate** detection via bundled
`ffprobe`/`ffmpeg`.

Jupiter already has the *core detection and review loop* (walk → size-bucket → streamed full
SHA-1 → `DuplicateGroup` → multi-select delete / keep-best smart-merge, all streaming via Flow
with coroutine cancellation). What it lacks is everything that makes repeat scans cheap
(persistent index, incremental reuse, partial pre-hash), everything that makes deletion *safe
and reversible* (engine-level keep-one invariant, trash/restore, audit log, structured
partial-failure results), and the *power features* (configurable scan scope, auto-select rules,
media-quality keeper ranking, perceptual near-dup).

**Headline counts (127 capabilities in, deduplicated to the distinct buckets below):**

| Bucket | Count |
|---|---:|
| **Already in the mobile app** (present or trivially met by the platform) | **9** |
| **Portable to mobile** (clear wins worth building) | **~14 distinct** (from 81 raw, heavily overlapping) |
| **Questionable** (keep-or-skip, mostly desktop-leaning) | **~9 distinct** (from 37 raw) |
| **Explicitly skip** (desktop-only) | **~6 families** |

The single biggest win is the **persistent Room content index + incremental re-hash skip**:
today Jupiter re-walks and re-hashes *from scratch every scan* — the most expensive thing a
phone can do for storage (slow I/O, battery, thermal). The cheapest high-value wins are the
**partial head/tail pre-hash stage** and the **engine-level "keep at least one" invariant**,
both small and both directly improving the code in `StorageAnalyticsRepositoryImpl` and the
delete paths in `DuplicatesViewModel`/`SmartMergeViewModel`.

---

## 2. Already in the mobile app

| Capability | What Sift does | Jupiter equivalent |
|---|---|---|
| Content-hash duplicate detection | Confirms equality by full content hash (BLAKE3) within size buckets | `StorageAnalyticsRepositoryImpl.findDuplicates` — size bucket → streamed full **SHA-1** → `DuplicateGroup(hash, files)` |
| Memory-bounded, cancellable streaming scan | Bounded hashing, cooperative `AtomicBool` cancel, live progress, ETA | Chunked 64 KB streamed hash (`hashFile`), `currentCoroutineContext().ensureActive()` at every stage, cold `Flow` emitting groups incrementally (`DuplicatesViewModel.scan`) |
| Skip empty / unreadable files | Records/handles fs errors; never crashes on a bad file | `isRegularFile` / `safeLength` swallow `SecurityException`; empties (`size <= 0`) skipped; unreadable returns `null` hash |
| Reclaimable-bytes display | `reclaimableBytes` per cluster = sum of all-but-one | `DuplicateGroup.wastedBytes` (= `files.drop(1).sumOf { sizeBytes }`); surfaced in `DuplicatesScreen`/`CleanupScreen` |
| Keep-one "representative" suggestion | `rep_idx`, keep-best `SelectionDecision{keep, reason}` | `MergeRecommendation(recommendedKeepPath, removablePaths)`; `SmartMergeViewModel.buildRecommendation` (keeps newest, falls back to first) |
| Preview-then-commit deletion UX | propose (selection) → review (UI) → confirm | `SmartMergeScreen` previews keep-best, `DuplicatesScreen` multi-select + delete confirmation before any IO |
| Visual preview before deleting a near-dup | `imagehash::thumbnail_png` / `videohash::video_thumbnail_png` | Native: **Coil** (`coil.compose`, `coil.video` already deps) + MediaStore/`ThumbnailUtils` thumbnails — no PNG-pipe port needed |
| Versioned, reversible schema migrations | `migrations/`, idempotent versioned runner, no `CREATE TABLE IF NOT EXISTS` for evolution | **Room** enforces versioned `Migration` objects as a first-class concept *(applies once the index lands — use real `Migration`s, never `fallbackToDestructiveMigration`)* |
| Live scan monitor (progress + Stop) | Stage pipeline, Stat tiles, bounded log, `cancelScan` | Flow-driven `isScanning` state + restartable `scan()`; cancellation via coroutine scope. (Stage labels / candidate counter / ETA are cheap UX deltas, not new capability.) |

---

## 3. Portable to mobile (the wins)

Sorted by **value** (high → medium) then **effort** (S → L). Effort: S ≈ ≤1 day, M ≈ a few days,
L ≈ a week+. "New dep" flagged where it matters.

| Capability | Why it matters on Android | Value | Effort | How to implement in Jupiter |
|---|---|:--:|:--:|---|
| **Partial head/tail pre-hash stage** (size → head+tail fingerprint → full hash) | Avoids fully reading large files that merely share a size. Phone I/O is the bottleneck; reading two 4 GB videos end-to-end just to discover they differ is the worst case Jupiter hits today | **High** | **S** | In `StorageAnalyticsRepositoryImpl.findDuplicates`, between the size-bucket loop and the full-hash loop, add a `partialHash(file)` that reads first+last N KB via `RandomAccessFile`/`FileChannel` and re-buckets the size group by that fingerprint before paying for `hashFile`. Pure Kotlin, reuses `MessageDigest`. No new dep. |
| **Engine-level "keep at least one" invariant** | Jupiter only has UI confirmation (prompt lists this as NOT present). A bug, a bad auto-select rule, or a race must never be able to delete *every* copy of a group | **High** | **S** | Add a pure guard in a delete use-case (new `domain/usecase/DeleteDuplicatesUseCase` or a guard in `DuplicatesViewModel.deleteSelected` / `SmartMergeViewModel.mergeAll`): reject any plan where, for some `group.hash`, the kept set is empty — *before* any `File.delete()`. Unit-testable; mirror Sift's `safety.rs` CI gate with a JVM test. No new dep. |
| **Structured partial-failure result** (`success | partial | failed` + per-item reason) | Android deletes fail per-file (SAF perms, `RecoverableSecurityException`, locked, MediaStore). Today the VMs only count `failures` and lose *which* and *why* | **High** | **S** | Replace the `deletedPaths to failures` pairs in `DuplicatesViewModel`/`SmartMergeViewModel` with a sealed `BatchDeleteResult(succeeded: List<String>, failed: Map<String, FailReason>)`. Surface failed paths+reasons in `DuplicatesUiState`. Pure Kotlin. No new dep. |
| **Configurable Scan Builder** (folder include/exclude, min-size, type/date filters) | Marquee file-manager feature: "scan only DCIM, ignore `Android/data`". Cuts scan cost and false positives. Natural on Android scoped storage | **High** | **M** | New `domain/model/ScanConfig` (roots, excludes, `minSizeBytes`, type/date predicates) threaded into `findDuplicates(rootPath)` → `findDuplicates(config)`. Apply predicates inside the `walkTopDown` loop. Compose config screen in `feature/cleanup/`. No new dep. |
| **Smart-selection rule engine** (keep newest / oldest / largest / shortest-path / in-folder-X, auto-select the rest) | Turns tedious manual multi-select into one tap — the headline dedupe UX. Jupiter has only single keep-best (`buildRecommendation`) | **High** | **M** | A `KeepRule` strategy (sealed) → `Comparator<FileItem>` chain over `DuplicateGroup.files`; auto-populate `selectedPaths`/`keepSelections`. Generalize `SmartMergeViewModel.buildRecommendation`. Pure Kotlin predicate/strategy. No new dep. |
| **Media-quality keeper ranking** (integrity → resolution → bitrate → size) | Concrete ordered keeper algorithm Jupiter lacks; for near-dup video/photo it picks the *better* copy, not just the newest | **High** | **M** | One concrete `KeepRule` implementation reading resolution/bitrate/duration via `MediaMetadataRetriever`/`MediaExtractor` (and MediaStore where indexed). Comparator: healthiest → highest res → highest bitrate → largest. No new dep (platform APIs). |
| **App-managed quarantine `.trash` + restore/undo** | Prompt explicitly: no OS Recycle Bin but a `.trash` folder is feasible; trash/restore is NOT present. A file-deleting app without undo is dangerous | **High** | **M** | New `data/storage/TrashRepository`: move (not delete) into an app-owned `.trash` dir; on API 30+ prefer `MediaStore` `IS_TRASHED`, else a real move on managed storage; record original path for restore + a purge policy (TTL). Wire into the two delete paths. No new dep (Room recommended for trash records — see below). |
| **Never-auto-delete policy: default to quarantine, permanent is a separate gated action** | Pairs with `.trash`: ordinary "delete" should quarantine; permanent purge needs a second explicit confirm and is logged | **High** | **M** | Policy layer over the delete use-case: `delete()` → trash; `purge()` → second confirmation + audit row. Reuses the quarantine + audit capabilities. No new dep. |
| **Persistent SQLite (Room) content index** (`content(hash)` + `file(path,size,mtime,volumeId,hash)`) | **The single biggest win.** Jupiter re-walks + re-hashes from scratch every scan. A durable index lets repeat scans skip unchanged files and reuse prior hashes — huge speed/battery/thermal savings | **High** | **L** | **New dep: Room** (`androidx.room` — not yet in `gradle/libs.versions.toml`). New `data/storage/index/` package: `@Entity` `FileRecord`/`ContentRecord`, DAO, `JupiterDatabase`, Hilt module. `findDuplicates` consults the index first. Use real versioned `Migration`s (see §2 migration note). |
| **Incremental re-hash skip by `(path, size, mtime)`** | The concrete payoff of the index: unchanged files are never re-hashed, making repeat scans near-instant | **High** | **M** | Once the index exists, in `findDuplicates` look up `(path,size,mtime)`; if matched, reuse stored hash and skip `hashFile`. `File.lastModified()`/`length()` (or `DocumentFile`/MediaStore) supply the keys. **Drop Sift's Windows `volume id`** — `(path,size,mtime)` suffices on a phone. Depends on the Room index. |
| **Append-only audit log** (row written *before* the destructive op, same transaction) | Crash-recoverable record of intent; underpins undo, reporting, and user trust. Jupiter has none | **Medium** | **S** | New Room `audit_log` entity (`action, path, hash, outcome, timestamp, detail`), written inside the same transaction as the trash/delete. SELECT-only read path. Depends on Room being present. |
| **Symlink-cycle guard in the walk** | Android filesystems have symlinks; `walkTopDown` can loop infinitely or double-count, inflating `wastedBytes`. Sift's reparse/junction logic distills to "don't recurse symlinked dirs by default" | **Medium** | **S** | In `FileSystemDataSource.walkTopDown` (or the repo loop), skip symlinked directories by default (`Files.isSymbolicLink` / `FileVisitOption`-aware walk), with an opt-in. Drop the NTFS reparse/junction/ADS specifics. No new dep. |
| **Bounded parallel hashing worker pool** (≈cores, capped) feeding a single writer | Multi-core phones can hash several files concurrently for real throughput, while a single Room writer avoids contention. Must stay memory-bounded (Jupiter already chunk-hashes) and respect battery/thermal | **Medium** | **M** | Hash size/partial-survivor buckets on `Dispatchers.IO.limitedParallelism(n)` with `n = cores` capped; collect into a single index-writer coroutine. Guard with thermal/battery checks. No new dep. |
| **Checkpointed / resumable scans + background indexing** | Mobile scans get killed (backgrounded, Doze, low memory). Persisting a scan cursor + a periodic idle/charging index refresh keeps detection instant | **Medium** | **M** | Persist a scan cursor + hash queue to Room; resume on relaunch. Schedule a periodic `WorkManager` job (Jupiter already uses WorkManager — see `data/automation/AutomationWorker.kt`, `JupiterApp.kt`) to refresh the index on idle+charging. Depends on the Room index. **Reuses existing WorkManager dep.** |

> **Cross-time "you already have/had this" signal** (a deduplicated survivor of Sift's
> cross-drive/cross-time hooks): once the index exists, flag a newly added file that duplicates
> content from a *prior* scan even if the old copy was since deleted. Medium value, contingent on
> the index — fold into a later wave. The pure **cross-drive removable-volume** logic is low value
> on a 1–2 volume phone (see §4).

---

## 4. Questionable — keep or skip

| Capability | Argument FOR porting | Argument AGAINST / desktop reason | Verdict |
|---|---|---|:--:|
| **Media integrity verdicts** (Healthy/Suspicious/Partial/Corrupted) | The integrity *signal* feeds the keeper ranking and finds truncated videos | Sift's impl bundles `ffprobe`/`ffmpeg`; per-frame deep decode is too battery-heavy on a phone. On-device you'd approximate with `MediaMetadataRetriever` (missing duration/streams ⇒ suspicious) | **Later** (lightweight signal only; no ffmpeg) |
| **Cross-time duplicate detection** (matches content from prior scans) | Genuinely useful once the index exists ("you already had this") | No value without the persistent index; the cross-*drive* motivation barely applies to phones | **Later** (after index) |
| **Coarse volume identity** (internal / SD-uuid / SAF-tree) | Lets the index distinguish storage locations | The rich NTFS GUID/offline-drive-history motivation barely applies; phones have ~1–2 volumes | **Port (minimal)** — store a coarse `volumeId` only; skip the GUID machinery |
| **User-defined protected paths / retention policies** (e.g. never delete from DCIM/Camera) | Real safety value on a phone where Camera is precious | NOT actually implemented in Sift's docs — only keep-one + keeper hints — so this is net-new design, not a lift | **Later** (net-new, build after trash) |
| **Empty-result vs call-failed distinction; record failed files with error status** | Jupiter silently *skips* unreadable files; recording + surfacing a "N files couldn't be scanned" count is honest | It's a discipline, not a module | **Port** (fold into the §3 partial-failure / scan-result model) |
| **Lossless path storage + separate NFC-normalized compare key** | Transferable kernel: Unicode NFC normalization when grouping by *name* | NTFS case-insensitivity, `MAX_PATH`/`\\?\`, reserved names (CON/PRN) are Windows-only; ext4/F2FS are case-sensitive. Irrelevant to content-hash dedup | **Skip** (until/unless Jupiter adds name-based grouping) |
| **Debounced (~250 ms) progress events** | Avoids overdrawing Compose during a million-file scan | Likely already mitigated by Compose recomposition + Flow; `Flow.conflate()`/`sample()` is a one-liner if needed | **Later** (trivial tweak if profiling shows it) |
| **Reclaimed-bytes captured at deletion time** (lifetime running total) | A lifetime "space reclaimed" stat is a nice trust/engagement metric | Jupiter already shows the pre-deletion estimate (`wastedBytes`); the lifetime total is a small delta riding on the audit log | **Port** (cheap once audit log exists) |
| **Smart-select / bulk-actions as a paid "Pro" gate** | Mirrors Sift's `license.rs` PRO_FEATURES | Licensing/monetization is a product decision, not an engine port; the *capability* (rule engine) is already in §3 | **Skip** (the feature ports; the paywall is out of scope) |

---

## 5. Recommended port roadmap

Ranked top-8 build order, in **additive, no-regression waves**, each mapped to Jupiter packages.
Earlier waves are pure improvements to existing code; the index waves are the foundation the
power features build on.

**Wave 1 — Safety & cheap wins (no new deps, all in `feature/cleanup` + a new `domain/usecase`)**
1. **Engine-level keep-at-least-one invariant** — guard in a new `DeleteDuplicatesUseCase`, enforced before any `File.delete()` in `DuplicatesViewModel.deleteSelected` / `SmartMergeViewModel.mergeAll`. *(S)*
2. **Structured partial-failure result** — `BatchDeleteResult(succeeded, failed: Map<path,reason>)` replacing the `pair to failures` shape in both delete VMs; surface in `DuplicatesUiState`. Folds in the "record failed/unreadable files" discipline from §4. *(S)*
3. **Partial head/tail pre-hash stage** — extra bucketing pass in `StorageAnalyticsRepositoryImpl.findDuplicates` before full `hashFile`. *(S)*
4. **Symlink-cycle guard** — skip symlinked dirs by default in `FileSystemDataSource.walkTopDown`. *(S)*

**Wave 2 — Reversible deletion (data/storage; Room recommended here)**
5. **App-managed `.trash` + restore/undo** and the **never-auto-delete → quarantine-by-default, permanent-is-gated** policy — new `data/storage/TrashRepository` (+ `MediaStore IS_TRASHED` on API 30+), wired into the Wave-1 delete use-case. Introduce **Room** here for trash + **audit log** records (audit row written before the move, same transaction). **New dep: `androidx.room`.** *(M)*

**Wave 3 — Power features (feature/cleanup; pure Kotlin over existing models)**
6. **Smart-selection rule engine** (`KeepRule` strategy → comparator chain) + **media-quality keeper ranking** (`MediaMetadataRetriever`) — generalize `SmartMergeViewModel.buildRecommendation`; auto-populate selection. *(M)*
7. **Configurable Scan Builder** — `ScanConfig` threaded into `findDuplicates(config)` + Compose config screen. *(M)*

**Wave 4 — The index foundation (data/storage/index; Room + existing WorkManager)**
8. **Persistent Room content index + incremental re-hash skip by `(path,size,mtime)`** — the biggest perf/battery win; then layer **checkpointed/resumable scans**, **background indexing** via a periodic `WorkManager` job (reuse the pattern in `data/automation/AutomationWorker.kt` + `JupiterApp.kt`), and the **cross-time "already had this" signal**. **Uses Room (added in Wave 2) + existing WorkManager dep.** *(L)*

**New dependencies required:** only **Room** (`androidx.room` — runtime/ktx + KSP compiler; not currently in `gradle/libs.versions.toml`), introduced in Wave 2. WorkManager (`androidx.work:work-runtime-ktx`) and Coil are **already** present. Everything in Waves 1 and 3 is pure Kotlin + platform APIs (`RandomAccessFile`, `MediaMetadataRetriever`, `MediaStore`) — **no new deps**.

---

## 6. Explicitly skip (desktop-only)

These have no meaningful Android analog and should not be ported:

- **NTFS MFT / USN journal scanning** — Sift can read the Master File Table / change journal for fast enumeration. Android has no MFT/USN; use `walkTopDown` / `MediaStore` / `DocumentFile`.
- **Drive-letter & volume-GUID specifics** (`\\?\Volume{...}`, `E:\`, `GetVolumeNameForVolumeMountPoint`, serial/label `stable_key`) — no drive letters on Android. At most keep a *coarse* volume id (internal / SD-uuid / SAF tree) per §4; skip the GUID machinery.
- **Admin/UAC elevation** — Android uses the permission model (`MANAGE_EXTERNAL_STORAGE`, SAF), not elevation.
- **NTFS hardlink / reparse-point / junction / ADS tricks** — distill only to the generic "don't recurse symlinks" guard in §3; the Windows-specific reparse handling is irrelevant.
- **Long-path / reserved-name / case-insensitivity handling** (`MAX_PATH`, `\\?\` prefix, CON/PRN, case-fold) — ext4/F2FS are case-sensitive with no `MAX_PATH`; skip `pathkey.rs`-style logic.
- **Bundled `ffprobe`/`ffmpeg` per-frame deep decode** — replace with lightweight `MediaMetadataRetriever`-based integrity signals (§4 "Later"); do not bundle native media binaries.

### Repository hygiene (act on this)

The repo currently carries the entire **`ultimate-duplicate-handler/`** Sift source tree —
**~127 MB**, **167 files tracked in git**, including Windows binaries that should never be in a
git repo:

- `ultimate-duplicate-handler/app/Sift-Portable/Singula.exe` (and `_archive/.../Singula.exe`, `Sift.exe`)
- `ultimate-duplicate-handler/Everything-1.4.1.1032.x64-Setup.exe`
- `ultimate-duplicate-handler/cisdem-duplicatefinder.exe`

**Recommendation:** `git rm -r --cached ultimate-duplicate-handler/` (or at minimum the `*.exe`
binaries and the `Sift-Portable/` build outputs), add `ultimate-duplicate-handler/` and
`*.exe`/`*.dll` to `.gitignore` (no such entries exist today), and keep the *reference docs*
(README/CODING_RULES/MEDIA_QUALITY/migrations) — which is all this port needs — somewhere
lightweight (e.g. `docs/reference/sift/`) rather than shipping the Windows app inside an Android
repo. This removes the bulk of the repo's weight (`.git` is only 55 MB vs the 127 MB working tree).
