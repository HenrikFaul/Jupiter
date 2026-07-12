# Jupiter — Android File Manager

Jupiter is a native **Android file manager** built with Kotlin, Jetpack Compose
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
| **Home and design** | Dark teal dashboard with a live used/available storage ring, Quick Access, category cards, recent items, and routes to the existing power tools. The default is the branded dark theme; users can still select another theme, accent, or dynamic color in Settings. |
| **Browse and file operations** | Breadcrumbs, sort/filter, multi-select, rename, folder creation, folder chooser, live copy/move progress, Recycle Bin deletion, and optional dual-pane browsing. A complete transfer plan is checked before any write; existing destination files or folders are rejected rather than silently overwritten. |
| **Search** | Indexed search when the index is available, reconciled with a live walk; scope chips for All, Files, Folders, PDFs, Images, and AI search. The last eight submitted queries are stored only on-device, can be selected again or cleared, and never persist paths, result snippets, or AI output. |
| **Storage and cleanup** | Real on-device storage analysis, large-file review, and exact/similar duplicate groups. Exact selection keeps the quality-ranked candidate and sends selected extras to the Recycle Bin; quality selection is not presented as a separate merge feature. |
| **Photos and media** | MediaStore-backed category browsing. The Photos view has real Camera, Screenshots, and Downloads refinements, date-grouped image grids, and routes items to the existing viewers. |
| **App storage** | A streaming per-app storage scan after Android Usage Access is granted. Results stay useful while scanning and can be viewed as all apps, largest apps, or cache-heavy apps; Android system settings handle app-level cleanup actions. |
| **Recycle Bin** | Deletions first go to the app-managed Recycle Bin. Items can be restored individually or together; permanent deletion is confirmed. Automatic retention is off by default and requires an explicit user setting. |
| **Secure vault** | Biometric/device-credential-gated `EncryptedFile` storage. Import uses Android's Storage Access Framework document picker and streams the selected `content://` URI into the vault; the source is not removed. Export and permanent vault deletion remain explicit actions. |
| **Organization and automation** | Favorites, tags, workspaces, activity, and locally stored automation rules remain available through their existing routes. |
| **Remote and transfer** | Wi-Fi desktop sharing and configured remote-connection surfaces are available. Google Drive sign-in is enabled only when a valid OAuth client ID is supplied; other cloud providers are explicitly marked as unavailable until their backend exists. |

### Boundaries and truthful states

Jupiter does not substitute decorative data for a missing service. Cloud accounts,
remote sources, and network transfer only operate when their required connection
or configuration is present. The optional AI path is isolated behind
`AiAssistant`; its default implementation reports that it is not configured
instead of inventing file contents or answers. Likewise, empty, permission, and
error states are intentional parts of the UI rather than hidden behind sample
content.

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

JVM unit tests under `app/src/test/` cover the core/domain rules plus the
high-risk storage safeguards: index and deduplication behavior, storage
exclusions, archive safety, Recycle Bin retention, transfer collision rejection,
search-filter/history policy, vault ViewModel state, and streaming app-storage
reconciliation. Run them with `./gradlew testDebugUnitTest`.

Device-dependent flows — storage permissions, the document picker, biometric
authentication, real app-size measurements, remote connections, and media
playback — still need verification on a device or emulator with suitable test
data and permissions.

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
