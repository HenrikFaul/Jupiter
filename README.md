# Jupiter

A modern, native **Android file manager** built entirely with Kotlin, Jetpack
Compose and Material 3. Jupiter focuses on a clean, fast browsing experience plus
a set of "power" tools — storage cleanup and duplicate detection, an encrypted
vault, search, and an optional (pluggable) AI assistant.

| | |
|---|---|
| **Language** | Kotlin 2.0 |
| **UI** | Jetpack Compose + Material 3 (dynamic color on Android 12+) |
| **Architecture** | MVVM + Clean Architecture |
| **DI** | Hilt |
| **Async** | Coroutines / `Flow` / `StateFlow` |
| **minSdk / targetSdk / compileSdk** | 26 / 35 / 35 |
| **JDK** | 17 |
| **Build** | Gradle 8.14.3, AGP 8.7.3 |

---

## Features

- **File browser** — directory listing with breadcrumbs, multi-select,
  sort/filter, copy / move (with live progress), rename, delete, create folder,
  bookmarks and recents.
- **Search** — recursive search with an optional natural-language mode that is
  routed through the AI assistant when one is configured.
- **Cleanup** — storage overview by category, large-file finder, and duplicate
  detection (size bucketing followed by content hashing), with one-tap reclaim.
- **Vault** — import files into an encrypted, app-private store backed by
  Jetpack Security (`EncryptedFile` + `MasterKey`); optional biometric unlock.
- **Transfer / Preview** — file preview (image / video / audio / text / PDF) and
  a transfer screen scaffold.
- **Theming** — system / light / dark theme modes, persisted via DataStore, with
  Material You dynamic color where available.

---

## Architecture

Jupiter follows a layered **Clean Architecture** split, with a unidirectional
data flow inside each feature (`UI -> ViewModel -> Repository -> DataSource`).

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
  layer     │  filesystem, storage, prefs, bookmark, vault  │
            └──────────────────────────────────────────────┘
```

Cross-cutting concerns:

- **`core.result`** — an `AppResult<T>` sealed type and a typed `AppError`
  hierarchy used instead of throwing across layer boundaries.
- **`core.util`** — pure helpers for byte/date formatting and MIME / file-type
  resolution.
- **`di`** — Hilt modules. `CoroutineModule` provides qualified dispatchers
  (`@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`); `RepositoryModule`
  and `AiModule` bind interfaces to their implementations.

**Threading rule:** Composables are pure UI and never perform file IO. All disk
access happens off the main thread via repository `suspend` functions or flows
running on the injected `@IoDispatcher`.

---

## Module / package map

Everything lives in the single `:app` Gradle module under the package
`com.jupiter.filemanager`.

| Package | Responsibility |
|---|---|
| `core.result` | `AppResult`, `AppError`, result combinators |
| `core.util` | `Formatters` (bytes/date/count), `MimeUtil` (extension / MIME / `FileType`) |
| `di` | Hilt modules: `CoroutineModule`, `RepositoryModule`, `AiModule` |
| `domain.model` | `FileItem`, `FileType`, `SortOption`, `FilterOption`, `StorageVolumeInfo`, `FileOperationProgress`, storage-analysis & bookmark models, `ThemeMode` |
| `domain.repository` | Repository interfaces: `FileRepository`, `StorageAnalyticsRepository`, `VaultRepository`, `BookmarkRepository` |
| `data.permission` | `StorageAccessManager`, `StorageAccessState` |
| `data.file` | `FileSystemDataSource`, `FileOperationsManager`, `ArchiveManager` (zip), `FileRepositoryImpl` |
| `data.storage` | `StorageVolumeProvider`, `StorageAnalyticsRepositoryImpl` |
| `data.preferences` | `SettingsDataStore` (DataStore-backed settings) |
| `data.bookmark` | `BookmarkRepositoryImpl` (bookmarks + recents) |
| `data.vault` | `VaultRepositoryImpl` (encrypted vault) |
| `feature.ai` | `AiAssistant` contract + `NoOpAiAssistant` default |
| `feature.permission` | Permission onboarding screen + ViewModel |
| `feature.home` | Home dashboard (volumes, categories, recents, bookmarks) |
| `feature.browser` (+ `.components`) | File browser screen, ViewModel and reusable composables (rows, breadcrumbs, sheets, dialogs, progress) |
| `feature.search` | Search screen + ViewModel |
| `feature.cleanup` | Cleanup / analytics screen + ViewModel |
| `feature.vault` | Vault screen + ViewModel |
| `feature.settings` | Settings screen + ViewModel |
| `feature.transfer` | Transfer screen |
| `feature.preview` | File preview screen + ViewModel |
| `ui.theme` | `JupiterTheme`, color schemes, typography |
| `ui.navigation` | `Destination` routes + `JupiterNavHost` |
| `ui.components` | Shared composables (`LoadingView`, `EmptyView`, `ErrorView`, file-type icons) |
| (root) | `JupiterApp` (`@HiltAndroidApp`), `MainActivity`, `MainViewModel` |

---

## Permission strategy

A general-purpose file manager needs to read and write across the whole external
storage volume, so the permission model is tiered by Android version. The state
is surfaced to the UI through `StorageAccessManager` as a `StorageAccessState`
(`FULL_ACCESS`, `SCOPED_ONLY`, `NONE`).

| API level | Mechanism |
|---|---|
| **API 30+ (R and above)** | **All Files Access** via `MANAGE_EXTERNAL_STORAGE`. The app sends the user to the system settings screen (`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`, falling back to `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`) and checks `Environment.isExternalStorageManager()`. |
| **API ≤ 32** | Legacy `READ_EXTERNAL_STORAGE` (capped with `maxSdkVersion="32"`). |
| **API ≤ 29** | Legacy `WRITE_EXTERNAL_STORAGE` (capped with `maxSdkVersion="29"`; superseded by scoped storage afterwards). |

Additional permissions:

- `USE_BIOMETRIC` — optional biometric unlock for the encrypted Vault.
- `POST_NOTIFICATIONS` — operation/foreground progress notifications on API 33+.

Files are shared with other apps through a `FileProvider` (authority
`${applicationId}.fileprovider`) so raw `file://` URIs are never exposed; the
authority is derived from the build's `applicationId` so the `.debug` and
release variants can coexist on one device.

The first launch routes to the `Permission` destination, which explains and
requests the appropriate access before the rest of the app becomes usable.

---

## Building

### Prerequisites

- JDK 17 (Temurin recommended)
- Android SDK with **platform 35** and current build-tools
- A `local.properties` pointing at your SDK (Android Studio creates this
  automatically):

  ```properties
  sdk.dir=/path/to/Android/sdk
  ```

The Gradle wrapper pins the required Gradle version, so no global Gradle install
is needed.

### Common commands

```bash
# Debug APK  -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug

# Release APK -> app/build/outputs/apk/release/app-release.apk
# (signed with the debug keystore for convenience; replace before publishing)
./gradlew assembleRelease

# Unit tests
./gradlew testDebugUnitTest

# Install the debug build on a connected device/emulator
./gradlew installDebug
```

> **Signing note:** the `release` build type is currently wired to the debug
> signing config (see `app/build.gradle.kts`) so CI and local builds produce an
> installable APK out of the box. Swap in a real keystore + `signingConfig`
> before shipping to production.

---

## Continuous integration

CI is defined in [`.github/workflows/android.yml`](.github/workflows/android.yml)
and runs on every push / pull request to `main` (or `master`) and on manual
dispatch. The workflow:

1. Checks out the repository.
2. Sets up **JDK 17 (Temurin)**, the **Android SDK**, and Gradle (with caching).
3. Runs `./gradlew assembleDebug` and `./gradlew assembleRelease`.
4. Runs `./gradlew testDebugUnitTest` in a parallel job.

### Where the APK artifacts are published

The built APKs are uploaded as **GitHub Actions build artifacts** on the
workflow run page (under the run's *Artifacts* section):

| Artifact name | Contents |
|---|---|
| `app-debug-apk` | `app/build/outputs/apk/debug/app-debug.apk` |
| `app-release-apk` | `app/build/outputs/apk/release/app-release.apk` |

Open **Actions → the relevant workflow run → Artifacts** to download them. Unit
test reports are uploaded as `unit-test-reports` when tests run.

---

## License

This project is provided as-is for demonstration purposes. Add a license file to
clarify reuse terms before distributing.
