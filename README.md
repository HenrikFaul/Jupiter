# Jupiscan — Android File Manager

Jupiscan is a native **Android file manager** built with Kotlin, Jetpack Compose
and Material 3. Its core is real device storage work: browsing, indexed search,
storage analysis, duplicate review, safe file operations, and an encrypted
vault. The app uses a dark midnight-and-teal visual system by default, with
layered cards, compact controls, storage rings, and an elevated bottom
navigation that remain tied to real device data rather than mock values.

> The full product/architecture rationale (competitive analysis, disruptive
> feature set, roadmap) lives in [`docs/PRODUCT_STRATEGY.md`](docs/PRODUCT_STRATEGY.md).

| | |
|---|---|
| **Language** | Kotlin 2.0 |
| **UI** | Jetpack Compose + Material 3; branded dark/teal default, optional dynamic color on Android 12+ |
| **Architecture** | MVVM + Clean Architecture, unidirectional data flow |
| **DI** | Hilt |
| **Async** | Coroutines / `Flow` / `StateFlow` |
| **Persistence** | Jetpack DataStore (Preferences), Jetpack Security (`EncryptedFile`) |
| **Images/Media** | Coil (image + video frames), `MediaPlayer` / `VideoView` |
| **minSdk / targetSdk / compileSdk** | 26 / 35 / 35 |
| **JDK** | 17 · **Build:** Gradle 8.14.3, AGP 8.7.3 |

---

## Navigation flow

```
Splash ──▶ Onboarding (first run only) ──▶ Permission gate ──▶ Main shell
                                                                 │
   ┌─────────────────────────────────────────────────────────────┘
   ▼  bottom navigation (inner NavHost)
 [ Home ] [ Files ] [ Recent ] [ Favorites ] [ More ]
   │         │                                  └─ launches advanced tools (outer routes)
   └─ open file ─▶ type-routed viewer: Image Gallery / Video / Music / Archive / Preview
```

`MainActivity` hosts a single outer `NavHost` starting at **Splash**.
`SplashViewModel` resolves the next route from persisted onboarding state +
`StorageAccessManager`. The **Main** destination is a `Scaffold` with a
`NavigationBar` over its own inner `NavHost` (the five primary tabs); advanced
tools (analytics, vault, automation, cloud, …) are full-screen outer routes.
Opening a file is routed by `FileType` (`openByType`) to the matching viewer.

---

## Current feature set

| Area | Current behavior |
|---|---|
| **Home and design** | The shared midnight/teal reference system is applied across all 49 existing Compose screens: semantic surfaces, compact cards and controls, file/category badges, state views, typography, and navigation. The eight `Generatedscreens/` references define the detailed Home, Files, Search, Photos, Duplicates, App storage, Recycle Bin, and Settings compositions. Live device state remains the content source. |
| **Browse and file operations** | Breadcrumbs, sort/filter, optional type grouping, multi-select, rename, folder creation/chooser, live copy/move progress, Recycle Bin deletion, and optional dual-pane browsing. Share uses a `FileProvider` URI, Details opens the real metadata route, and Compress is offered for supported image/video media and opens the existing compressor with that file preselected. A complete transfer plan rejects collisions before any write; successful copy and restore operations index the full created/restored subtree immediately, while move/rename keeps using the atomic subtree path rewrite. |
| **Search** | Indexed search when available, reconciled with a live walk; history plus Recent and Suggested result sections; scopes for All, Files, Folders, PDFs, Images, and AI search. The last eight submitted queries are stored only on-device, can be reused or cleared, and never persist paths, result snippets, or AI output. |
| **Storage and cleanup** | The primary-volume total/free figures use Android's `StorageStatsManager` source and decimal display units, with a safe `StatFs` fallback. The Storage Truth card explicitly separates platform capacity/free space, shared files Jupiscan can analyse, and used space outside that scan; it never pretends the remainder is all app data. Real on-device analysis, large-file review, and separate exact/similar duplicate presentations remain data-backed. Exact copies require a full content hash. The v10 index stores full/quick SHA-1 evidence as 20-byte BLOBs (instead of 40-character TEXT), ordered media signatures as raw 64-bit vectors, and image/video geometry in one packed integer; a lossless migration plus deferred SQLite compaction avoids a file rescan and returns retired TEXT/index pages to the phone. The v11 image lifecycle adds one small producer-version integer: only current, complete dHash+pHash+aHash+geometry evidence can participate, old/incomplete rows are requeued without losing exact hashes, readiness updates live during backfill, and the exported Room schema is tracked for future migration validation. Image decisions fuse all three hash distances, retain the aspect-ratio veto and complete-link confirmation, and use threshold+1 LSH bands whose candidate partition covers the inclusive distance-8 boundary; final verification remains budgeted and fail-closed under pathological skew. Videos use five timeline samples plus duration/geometry, PDFs use first/middle/last page plus page count, audio uses an acoustic envelope plus duration, and every near group is complete-link confirmed so a transitive bridge cannot merge unrelated endpoints. Similar results are labelled review-only and do not masquerade as exact duplicates. The keeper is protected; size thresholds, largest/smallest ordering, Select all, and Deselect all operate only on the visible scope; reviewed cleanup goes to Recycle Bin. Android 11+ arrival catch-up uses MediaStore version + `GENERATION_MODIFIED` and excludes pending rows; a bounded observer and durable WorkManager backup cover OEM/provider timing gaps. A transient unreadable file never advances the checkpoint. Canonical decision keys and a persistent notification outbox keep alerts idempotent and retry Android-blocked delivery. |
| **Photos and media** | MediaStore-backed category browsing with All, Camera, Screenshots, and Downloads refinements, date-grouped image grids, direct gallery opening, and a direct Similar-photos route. The visible filtered/sorted set can also run as a real 3-second autoplay slideshow with pause/resume, previous/next, and wrap-around; the handoff stays in memory instead of expanding navigation arguments. Selected photos can be moved through a real folder picker; target validation and no-overwrite repository semantics protect the source and destination. |
| **App storage** | A streaming per-app storage scan after Android Usage Access is granted. Results stay useful while scanning and can be viewed as all apps, largest apps, or cache-heavy apps; Android system settings handle app-level cleanup actions. |
| **Recycle Bin** | Deletions first go to the app-managed Recycle Bin. Items can be restored individually or together; permanent deletion is confirmed. Automatic retention is off by default and requires an explicit user setting. |
| **Settings** | Theme/accent/dynamic color, default sort, type grouping, confirm-before-trash, Recycle Bin retention, indexing, live AI enablement, app locale, Vault biometric/PIN policy, and 1/5/15/30-minute auto-lock are persisted preferences rather than decorative rows. The current APK ships English resources, so locale choices are honestly limited to System default or English. Existing advanced-tool routes remain available. |
| **Secure vault** | Fail-closed `EncryptedFile` storage unlocked by a real device-auth result or a salted PBKDF2-protected local PIN. The verifier is stored only in Android-Keystore-backed encrypted preferences; storage failure denies access and never falls back to plaintext. Biometric disablement requires a configured PIN; background/navigation and the selected auto-lock interval close the runtime session. SAF import deliberately clears authorization while the picker is external: only a non-secret pending marker survives recreation, while the returned URI remains memory-only, requires fresh authentication, and is consumed once. The source is not removed; export and permanent deletion remain explicit. |
| **Organization and automation** | Favorites, tags, workspaces, and activity remain available. Automation now opens with five upgrade-safe, suspended examples; rules can be previewed without mutation, edited/renamed, activated/suspended, removed, and explicitly run after confirmation. File deletion is rejected in the authoring UI and again in the execution engine; moves use the normal repository gateway so collision and index-consistency safeguards remain active. |
| **Remote and transfer** | **Jupiscan Relay** is a user-started, 10-minute, token-paired local Downloads sharing session with a locally generated QR code. The link and token are memory-only and every request is gated; it is labelled as trusted-LAN sharing, not end-to-end encrypted transport. Configured remote-connection surfaces are available. Google Drive sign-in is enabled only when a valid OAuth client ID is supplied; other cloud providers are explicitly marked as unavailable until their backend exists. |
| **Privacy & Recovery** | A single Privacy & Recovery Center routes to the encrypted Vault, configured cloud/NAS connections, Recycle Bin restore, Jupiscan Relay session, and data/permission transparency pages. It does not invent a sharing/third-party-permission audit where Android or a provider does not expose that data. |

### Boundaries and truthful states

Jupiscan does not substitute decorative data for a missing service. Cloud accounts,
remote sources, and network transfer only operate when their required connection
or configuration is present. The optional AI path is isolated behind
`AiAssistant`; disabling AI prevents the Anthropic implementation from running,
and an unconfigured backend reports that state instead of inventing file contents
or answers. Empty, permission, and error states are intentional UI states rather
than sample content.

The shared design implementation covers the existing 49-screen source inventory,
but that code-level coverage is not a claim that every density, font scale, OEM
permission sheet, media codec, or foldable posture has been visually certified.
The eight reference screens still require captured device/emulator comparison in
each release gate. The v0.51 round performed that comparison on a Pixel 8 Pro
emulator and also exercised live app-size data, Recycle Bin delete/restore, Vault
PIN unlock, and slideshow timing. Android authentication, full SAF recreation,
locale application, external storage, media codecs, and network behavior still
depend on platform services and suitable real test data.

---

## Architecture

Layered **Clean Architecture** with unidirectional data flow inside each feature
(`UI → ViewModel → Repository → DataSource`); immutable `StateFlow<XUiState>`.

```
            ┌──────────────────────────────────────────────┐
   UI       │  Compose screens + components (feature.*)     │
  layer     │  observe StateFlow<XUiState> from ViewModels  │
            └───────────────▲──────────────────────────────┘
                            │  events ▼   state ▲
            ┌───────────────┴──────────────────────────────┐
  Presenta- │  @HiltViewModel ViewModels (feature.*)        │
  tion      │  expose val uiState: StateFlow<XUiState>      │
            └───────────────▲──────────────────────────────┘
                            │
            ┌───────────────┴──────────────────────────────┐
  Domain    │  Models (domain.model) + Repository           │
  layer     │  interfaces (domain.repository)               │
            └───────────────▲──────────────────────────────┘
                            │  bound via Hilt @Binds
            ┌───────────────┴──────────────────────────────┐
  Data      │  Repository impls + data sources (data.*)     │
  layer     │  filesystem, storage, prefs, vault, tags,     │
            │  activity, automation, workspaces, …          │
            └──────────────────────────────────────────────┘
```

**Cross-cutting:** `core.result` (`AppResult<T>` + typed `AppError` instead of
throwing across layers), `core.util` (pure byte/date formatting + MIME/file-type
resolution), `di` (Hilt modules — `CoroutineModule` provides `@IoDispatcher` /
`@DefaultDispatcher` / `@MainDispatcher`; `RepositoryModule`, `AiModule` and
`FeatureRepositoryModule` bind interfaces to impls).

**Threading rule:** Composables are pure UI and never perform file IO. All disk
access runs off the main thread via repository `suspend` functions / flows on the
injected `@IoDispatcher`.

---

## Module / package map

Single `:app` Gradle module under the legacy, update-stable Android package
`com.jupiter.filemanager`. The user-visible product name is **Jupiscan**; the package,
database and preference identifiers deliberately remain stable so existing installations keep
their data and receive the update instead of becoming a second application.

| Package | Responsibility |
|---|---|
| `core.result` · `core.util` | `AppResult`/`AppError`; `Formatters`, `MimeUtil` |
| `di` | `CoroutineModule`, `RepositoryModule`, `AiModule`, `FeatureRepositoryModule` |
| `domain.model` | `FileItem`, `FileType`, `SortOption`, `FilterOption`, `StorageVolumeInfo`, `FileOperationProgress`, storage-analysis models, `Bookmark`, `Tag`, `ActivityEntry`, `AutomationRule`, `Workspace`, `RemoteConnection`, `CloudAccount`, `TransferTask`, `FileVersion`, `SyncConflict`, `PrivacyReport`, `MergeRecommendation`, `ThemeMode` |
| `domain.repository` | `FileRepository`, `StorageAnalyticsRepository`, `VaultRepository`, `BookmarkRepository`, `TagRepository`, `ActivityRepository`, `AutomationRepository`, `WorkspaceRepository`, `ConnectionRepository`, `TransferRepository`, `VersionRepository`, `SyncRepository` |
| `data.permission` | `StorageAccessManager` (+ `StorageAccessState`) |
| `data.file` | `FileSystemDataSource`, `FileOperationsManager`, `ArchiveManager`, `FileRepositoryImpl` |
| `data.storage` | `StorageVolumeProvider`, `StorageAnalyticsRepositoryImpl` |
| `data.preferences` | `SettingsDataStore`, `AppStateDataStore` |
| `data.bookmark` · `data.vault` | bookmarks/recents; encrypted vault |
| `data.tag` · `data.activity` · `data.automation` · `data.workspace` · `data.connection` · `data.transfer` · `data.version` · `data.sync` | DataStore-backed feature repositories |
| `feature.ai` | `AiAssistant` contract + `NoOpAiAssistant` |
| `feature.*` | One package per screen area (splash, onboarding, main, home, browser, recent, favorites, more, search, analytics, cleanup, downloads, tags, workspace, details, transfer, cloud, privacy, automation, archive, preview, version, sync, vault, settings, permission) |
| `ui.theme` · `ui.navigation` · `ui.components` | legacy code symbols `JupiterTheme` / `JupiterNavHost`; shared Jupiscan composables |
| (root) | `JupiterApp` (`@HiltAndroidApp`), `MainActivity`, `MainViewModel` |

---

## Permission strategy

A general-purpose file manager needs broad read/write across external storage,
so access is tiered by Android version and surfaced as `StorageAccessState`
(`FULL_ACCESS`, `SCOPED_ONLY`, `NONE`).

| API level | Mechanism |
|---|---|
| **30+ (R+)** | **All Files Access** via `MANAGE_EXTERNAL_STORAGE` — routes to system settings (`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`, falling back to `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`) and checks `Environment.isExternalStorageManager()`. |
| **≤ 32** | `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`). |
| **≤ 29** | `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="29"`). |

Also: `USE_BIOMETRIC` (vault unlock), `POST_NOTIFICATIONS` (operation progress on
API 33+). Files are shared via a `FileProvider` (authority
`${applicationId}.fileprovider`) — raw `file://` URIs are never exposed. First
launch routes to the Permission gate, which explains and requests access with a
graceful scoped fallback when All-Files-Access is denied.

---

## Building

**Prerequisites:** JDK 17 (Temurin), Android SDK **platform 35** + build-tools,
and a `local.properties` with `sdk.dir=/path/to/Android/sdk` (Android Studio
creates this). The Gradle wrapper pins the Gradle version.

```bash
./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # -> app/build/outputs/apk/release/app-release.apk (R8 minified)
./gradlew testDebugUnitTest  # JVM unit tests
./gradlew installDebug       # install on a connected device/emulator
```

> **Signing note:** `release` is wired to the debug signing config so CI and
> local builds produce an installable APK out of the box. Swap in a real keystore
> + `signingConfig` before publishing.

---

## Testing

JVM unit tests under `app/src/test/` cover the core/domain rules plus the
high-risk storage safeguards: index and deduplication behavior, visible-only
duplicate selection and keeper protection, storage exclusions, archive safety,
Recycle Bin retention/filtering, transfer collision rejection, photo move-policy,
search history/section policy, slideshow timing/wrap policy, Settings state
ownership, Vault PIN persistence and fail-closed ViewModel state, and streaming
app-storage reconciliation. Run them with `./gradlew testDebugUnitTest`.

For v0.56, a clean cache-disabled `assembleDebug` / `testDebugUnitTest` /
`lintDebug` gate produced 60 suites / 360 JVM tests with zero failures, errors,
or skips. The generated lint report has 215 pre-existing, non-blocking project
findings; the touched storage and perceptual-backfill paths add none.

Release gates should repeat device-dependent checks with representative data and
permissions. The v0.56 API 34 emulator upgrade smoke verified that the explicit
photo-descriptor worker is enqueued and completes, while rows incorrectly marked
as permanently unhashable by the legacy code are retried. The v0.55 API 34
emulator smoke verified a byte-identical new Download copy produces
`Duplicate detected — you already have 1 copy`; it also verified that a persisted undelivered
decision is collapsed into a `Duplicate files detected` summary after notification permission is
available. Earlier v0.54 runtime checks covered the advertised-capacity storage card, all five
suspended Automation examples, mutation-free preview, and restored duplicate controls. Earlier v0.51 runtime checks covered the eight reference
screens, real app-size measurements, Recycle Bin delete/restore, PIN-based Vault unlock, and
slideshow autoplay/pause. A real biometric/device credential, full SAF
configuration-recreation return, app-locale recreation, remote connections, and
device-specific media playback remain device-bound verification boundaries.

---

## Continuous integration

CI ([`.github/workflows/android.yml`](.github/workflows/android.yml)) runs on
push/PR (including `claude/**` branches) and manual dispatch. It sets up JDK 17 +
the Android SDK (`platform-tools`, `platforms;android-35`, `build-tools;35.0.0`),
caches Gradle, then:

1. **Build APKs** — `assembleDebug` (primary) + `assembleRelease` (R8, best-effort).
2. **Unit tests** — `testDebugUnitTest`.

Built APKs are uploaded as **GitHub Actions build artifacts** on the run page:

| Artifact | Contents |
|---|---|
| `jupiscan-debug-apk` | `app-debug.apk` (installable) |
| `jupiscan-release-apk` | `app-release.apk` (R8-minified) |

Open **Actions → the workflow run → Artifacts** to download. Test reports upload
as `unit-test-reports`.

---

## License

Provided as-is for demonstration. Add a license file to clarify reuse terms
before distributing.
