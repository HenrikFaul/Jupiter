# Jupiter — Smart File Manager

A modern, native **Android file manager** built entirely with Kotlin, Jetpack
Compose and Material 3. Jupiter pairs a clean, fast browsing experience with a
deep set of power tools — storage analytics, duplicate detection & smart merge,
an encrypted vault, tags & workspaces, transfer/cloud/NAS surfaces, rule-based
automation, media viewers, and an optional (pluggable) AI assistant — behind a
simple bottom-navigation shell.

> The full product/architecture rationale (competitive analysis, disruptive
> feature set, roadmap) lives in [`docs/PRODUCT_STRATEGY.md`](docs/PRODUCT_STRATEGY.md).

| | |
|---|---|
| **Language** | Kotlin 2.0 |
| **UI** | Jetpack Compose + Material 3 (dynamic color on Android 12+) |
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

## Feature surface (NEXUS screen set)

| Area | Screens / capability |
|---|---|
| **Onboarding** | Branded splash, 4-page intro carousel, permission rationale + request |
| **Home** | Dashboard: search entry, Quick Access (Downloads/Documents/Images), storage overview bars, tools grid, recents, favorites |
| **Browse** | List browser (breadcrumbs, multi-select, sort/filter, copy/move with live progress, rename, delete, create folder, folder-chooser), **dual-pane** mode |
| **Find** | Recursive search with optional natural-language mode (AI-routed) |
| **Clean** | Storage analytics (category breakdown), large-file finder, duplicate detection (size-bucket → content hash), **smart merge** (keep-best) |
| **Organize** | Recent activity, Downloads, Favorites (folders/files), **Tags & Collections**, **Project Workspaces**, File Details |
| **Transfer** | Transfer Center (history), Nearby (P2P scaffold), Wi-Fi desktop transfer |
| **Remote** | Cloud Hub (Drive/Dropbox/OneDrive/iCloud/Box/WebDAV accounts), NAS/SMB/SFTP/WebDAV connections |
| **Secure** | Encrypted Vault (`EncryptedFile` + `MasterKey`, biometric-ready), **Privacy Dashboard** |
| **Automate** | Rule list with enable toggles, **AI Rule Builder** (natural-language → rule) |
| **Media** | Image gallery (pager), Video player, Music player, Archive manager (zip), PDF/text/preview |
| **History** | Version history, Sync conflict resolution |
| **Settings** | Theme mode, show hidden, dual-pane, AI toggle |

### Honest backend status

Jupiter ships **UI-complete** for every screen above. User-created data — tags,
automation rules, workspaces, bookmarks/recents, and cloud/NAS *connection
entries* — genuinely persists via DataStore. Analytics, duplicates, downloads,
cleanup and the privacy dashboard run on **real on-device data**. Features that
require an external backend or heavyweight protocol stack — **live** cloud/NAS
I/O (OAuth + SMB/SFTP/WebDAV transport), peer-to-peer & Wi-Fi desktop transfer,
file versioning and sync — present clear *empty / connect* states rather than
fabricated data, and are structured so the transport layer can be dropped in
behind the existing repositories without UI changes.

The **AI assistant** is isolated behind the `AiAssistant` interface with a safe
`NoOpAiAssistant` default (returns an honest "not configured" result, never
fabricates file contents). A real implementation can be bound in `AiModule`
without touching the rest of the app.

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

Single `:app` Gradle module under `com.jupiter.filemanager`.

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
| `ui.theme` · `ui.navigation` · `ui.components` | `JupiterTheme`; `Destination` + `JupiterNavHost`; shared composables |
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

JVM unit tests under `app/src/test/` cover the pure domain/core logic (no Android
framework dependencies): `AppResult`/`AppError`, `MimeUtil` extension→`FileType`
mapping, byte/count formatting, `FileItem.parentPath`, storage/operation/
duplicate/merge/transfer/workspace math, `SortOption`, and `FavoritesUiState`.
Run with `./gradlew testDebugUnitTest`.

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
| `jupiter-debug-apk` | `app-debug.apk` (installable) |
| `jupiter-release-apk` | `app-release.apk` (R8-minified) |

Open **Actions → the workflow run → Artifacts** to download. Test reports upload
as `unit-test-reports`.

---

## License

Provided as-is for demonstration. Add a license file to clarify reuse terms
before distributing.
