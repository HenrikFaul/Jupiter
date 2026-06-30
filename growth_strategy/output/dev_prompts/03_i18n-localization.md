# Initiative #3 — Localization & Global Reach (12+ languages)

> Dev-prompt for an autonomous AI coding agent. Implement end-to-end, **additively**, against the **real** Jupiter codebase at `/home/user/Jupiter`. Read every referenced real file before editing. Produce compiling Kotlin/XML only — no pseudocode, no TODO stubs that break the build. This initiative is **strings-only and regression-safe**: it externalizes hardcoded UI text, ships translated `res/values-<lang>/strings.xml`, and adds an in-app language picker. It must not alter any file-management behavior.

---

## 1. Initiative header

- **Title:** Localization & Global Reach — 12+ languages, localized UI + listing strings.
- **Estimated valuation impact:** **+€120k – €260k**.
- **Business case:** Jupiter is a general-purpose file-manager utility — the single most universally-needed app category on Android — yet every user-facing string is currently hardcoded English inside Compose `Text("…")` calls. A file manager has no cultural or content barrier to adoption, so its only ceiling on install base is language. Localizing the UI into the 12 highest-volume Play Store languages plus Hungarian (the maintainer's home market) is the highest-ROI install-base multiplier available: it is additive, carries near-zero regression risk (strings only, no logic), and directly unlocks organic installs in DE/ES/FR/PT/IT/BR/RU/TR/ID/JP/KR markets where localized listings rank materially higher. The work also establishes the per-app-language infrastructure (`AppCompat` locale APIs + `res/xml/locales_config.xml`) that future initiatives reuse, and converts "an English app" into "a global utility," which is exactly the re-rating acquirers price in.

---

## 2. Codebase context

Single app module `:app`, package root `com.jupiter.filemanager`. Kotlin 2.0.21, Compose BOM 2024.12.01, Material3, Hilt 2.52, KSP, Coroutines 1.9.0, DataStore Preferences 1.1.1, Navigation Compose 2.8.5. `minSdk 26 / targetSdk 35 / compileSdk 35`. `buildConfig = true` is already enabled.

### Relevant real files that already exist (read these first)

```
/home/user/Jupiter
├── gradle/libs.versions.toml                                   # version catalog (EDIT: add appcompat)
├── app/build.gradle.kts                                        # module deps + resourceConfigurations (EDIT)
├── app/proguard-rules.pro                                      # R8 keep rules (EDIT: tiny, optional)
└── app/src/main
    ├── AndroidManifest.xml                                     # EDIT: add android:localeConfig
    ├── res/values/strings.xml                                  # EXISTS, ~30 strings (EXTEND to full catalog)
    ├── res/xml/                                                # backup_rules / data_extraction_rules / file_paths
    └── java/com/jupiter/filemanager
        ├── JupiterApp.kt                                       # @HiltAndroidApp (no change)
        ├── MainActivity.kt                                     # ComponentActivity (EDIT: AppCompat base + attachBaseContext)
        ├── MainViewModel.kt                                    # @HiltViewModel theme owner (mirror for locale)
        ├── core/result/AppResult.kt                           # AppResult.Success/Failure (reuse)
        ├── core/result/AppError.kt                            # AppError sealed class (reuse, no edit needed)
        ├── di/CoroutineModule.kt                              # @IoDispatcher/@DefaultDispatcher (reuse)
        ├── data/preferences/SettingsDataStore.kt             # DataStore pattern + Keys (EDIT: add app_language)
        ├── feature/settings/SettingsViewModel.kt             # @HiltViewModel + combine() (EDIT: add language)
        ├── feature/settings/SettingsScreen.kt                # Compose settings list (EDIT: add Language section + stringResource)
        ├── ui/navigation/Destinations.kt                      # sealed Destination (no change required)
        ├── ui/navigation/JupiterNavHost.kt                    # NavHost graph (no change required)
        └── feature/**/*.kt                                     # ~40 screens with hardcoded Text("…") (REFACTOR to stringResource)
```

### What exists vs. missing vs. needs change

| Concern | State | Action |
|---|---|---|
| `res/values/strings.xml` | **Exists** (~30 keys: `action_*`, `label_*`, `empty_*`, `error_*`, `permission_*`) | **Extend** into the full default catalog (the source of truth for all translations). |
| Hardcoded `Text("…")` in `feature/**` | **Exists** (10 in `FileBrowserScreen`, 14 in `CleanupScreen`, 8 in `SettingsScreen`/`HomeScreen`/`StorageAnalyticsScreen`, etc.) | **Refactor** to `stringResource(R.string.…)`. Behavior unchanged. |
| `res/values-<lang>/strings.xml` translations | **Missing** | **Create** for 13 locales (hu, de, es, fr, pt, it, ru, tr, id, ja, ko, pt-rBR, nl). |
| `res/xml/locales_config.xml` | **Missing** | **Create**; wire via `android:localeConfig`. |
| Per-app language persistence | **Missing** | **Add** `app_language` to `SettingsDataStore`; bridge to `AppCompatDelegate.setApplicationLocales`. |
| In-app language picker | **Missing** | **Add** `LanguageSelector` section to `SettingsScreen` + `LanguageOption` enum + VM plumbing. |
| `appcompat` dependency | **Missing** | **Add** to catalog + module (needed for `AppCompatDelegate` per-app locales on API < 33 with auto-store backport). |
| `MainActivity` base class | `ComponentActivity` | **Change** to `AppCompatActivity` (a subclass; fully compatible with `setContent`/Hilt) so `AppCompatDelegate` locale APIs apply. |
| `android:supportsRtl="true"` | **Already present** in manifest | Reuse — Arabic/Hebrew not in scope but layout is RTL-ready. |

**Non-negotiable invariant:** The **default** locale (`res/values/strings.xml`) is the existing English copy; every refactored screen renders byte-identical English when the device locale is English/unset. A missing translation in any `values-<lang>` file **falls back automatically** to the default resource (Android resource resolution) — never a crash, never an empty string. The language picker defaults to **"System default,"** so existing behavior is preserved until a user explicitly overrides.

---

## 3. Pre-conditions

### Gradle dependencies to add (exact coordinates)

- `androidx.appcompat:appcompat:1.7.0` — provides `AppCompatActivity` and `AppCompatDelegate.setApplicationLocales(...)`, which on API 24–32 backs per-app languages with an auto-stored `LocaleManager` service; on API 33+ it delegates to the platform `LocaleManager`. This is the single new runtime dependency.

No new permissions. No API keys. No network. No Play Console server-side config is required for the **app** half. The **listing** half (localized store listing text, screenshots) is configured in Play Console → *Store presence → Main store listing → Manage translations* and is documented in Phase 4; it requires no code.

### Manifest / platform prerequisites

- `android:localeConfig="@xml/locales_config"` on `<application>` (declares supported locales to the system so Android 13+ shows Jupiter in *Settings → System → Languages → App languages*).
- Keep `android:supportsRtl="true"` (already set).
- `compileSdk 35` already supports `localeConfig` and `LocaleManager`.

---

## 4. Phase 1 — Gradle + Manifest + resources scaffolding

### 4.1 `gradle/libs.versions.toml`

Add one version and one library. Add under `[versions]`:

```toml
appcompat = "1.7.0"
```

Add under `[libraries]`:

```toml
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
```

### 4.2 `app/build.gradle.kts`

Add the dependency next to the other AndroidX `implementation` lines (e.g. just after `androidx.activity.compose`):

```kotlin
    implementation(libs.androidx.appcompat)
```

Inside `android { defaultConfig { … } }`, pin the shipped locale set so unused translations from transitive libraries are stripped and the APK only carries Jupiter's languages. Add:

```kotlin
        // Keep only locales Jupiter actually ships; strips stray library translations.
        androidResources {
            localeFilters += listOf(
                "en", "hu", "de", "es", "fr", "pt", "pt-rBR",
                "it", "ru", "tr", "id", "ja", "ko", "nl",
            )
        }
```

> Note: `androidResources.localeFilters` is the AGP 8.7 replacement for the deprecated `resourceConfigurations`. If the AGP version in use rejects `localeFilters`, fall back to `resourceConfigurations += listOf(...)` with the same values (use `pt-rBR` → `b+pt+BR` is **not** needed; `resourceConfigurations` accepts `pt-rBR`). Verify with `./gradlew :app:assembleDebug`.

### 4.3 `app/src/main/AndroidManifest.xml`

Add the `localeConfig` attribute to the existing `<application>` element. Locate:

```xml
    <application
        android:name=".JupiterApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Jupiter"
        tools:targetApi="31">
```

Add `android:localeConfig="@xml/locales_config"`:

```xml
    <application
        android:name=".JupiterApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:localeConfig="@xml/locales_config"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Jupiter"
        tools:targetApi="31">
```

### 4.4 NEW `app/src/main/res/xml/locales_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Declares every locale Jupiter ships UI translations for. Read by Android 13+
    (API 33) to populate the per-app language entry under
    Settings → System → Languages → App languages, and by AppCompat's
    auto-stored locale backport on API 24–32.
-->
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="hu" />
    <locale android:name="de" />
    <locale android:name="es" />
    <locale android:name="fr" />
    <locale android:name="pt" />
    <locale android:name="pt-BR" />
    <locale android:name="it" />
    <locale android:name="ru" />
    <locale android:name="tr" />
    <locale android:name="id" />
    <locale android:name="ja" />
    <locale android:name="ko" />
    <locale android:name="nl" />
</locale-config>
```

### 4.5 EXTEND `app/src/main/res/values/strings.xml` (default English — source of truth)

Keep every existing key untouched (other code already references `app_name`, `action_*`, `label_*`, `empty_*`, `error_*`, `permission_*`). **Append** the new keys below into the same `<resources>` block. These cover the screens refactored in Phase 6 and the language picker. (This list is the canonical key set; each `values-<lang>` file mirrors exactly these names.)

```xml
    <!-- Settings: section headers -->
    <string name="settings_title">Settings</string>
    <string name="settings_section_appearance">Appearance</string>
    <string name="settings_section_language">Language</string>
    <string name="settings_section_browsing">Browsing</string>
    <string name="settings_section_assistant">Assistant</string>
    <string name="settings_section_storage">Storage</string>
    <string name="settings_section_transfers">Transfers</string>
    <string name="settings_section_security">Security</string>
    <string name="settings_section_cloud">Cloud</string>
    <string name="settings_section_automation">Automation</string>
    <string name="settings_section_advanced">Advanced</string>
    <string name="settings_section_about">About</string>

    <!-- Settings: theme -->
    <string name="theme_system">System default</string>
    <string name="theme_light">Light</string>
    <string name="theme_dark">Dark</string>
    <string name="theme_system_desc">Follow the device theme</string>
    <string name="theme_light_desc">Always use a light theme</string>
    <string name="theme_dark_desc">Always use a dark theme</string>

    <!-- Settings: language picker -->
    <string name="language_system">System default</string>
    <string name="language_picker_subtitle">Choose the language used across Jupiter</string>
    <string name="language_english">English</string>
    <string name="language_hungarian">Magyar</string>
    <string name="language_german">Deutsch</string>
    <string name="language_spanish">Español</string>
    <string name="language_french">Français</string>
    <string name="language_portuguese">Português</string>
    <string name="language_portuguese_br">Português (Brasil)</string>
    <string name="language_italian">Italiano</string>
    <string name="language_russian">Русский</string>
    <string name="language_turkish">Türkçe</string>
    <string name="language_indonesian">Indonesia</string>
    <string name="language_japanese">日本語</string>
    <string name="language_korean">한국어</string>
    <string name="language_dutch">Nederlands</string>

    <!-- Settings: browsing rows -->
    <string name="settings_show_hidden">Show hidden files</string>
    <string name="settings_show_hidden_desc">Display files and folders that start with a dot</string>
    <string name="settings_dual_pane">Dual-pane layout</string>
    <string name="settings_dual_pane_desc">Show two file panes side by side on wide screens</string>
    <string name="settings_ai_enable">Enable AI assistant</string>
    <string name="settings_ai_enable_desc">Smart suggestions for naming, search and cleanup</string>
    <string name="settings_ai_key_label">Claude API key</string>
    <string name="settings_ai_key_hint">Your key is stored on-device only and never leaves your phone except to call Claude.</string>
    <string name="settings_about_app">Jupiter File Manager</string>
    <string name="settings_about_version">Version %1$s</string>
    <string name="settings_about_tagline">A fast, private, native file manager for Android.</string>
    <string name="settings_about_privacy">All operations run on-device. No data leaves your phone.</string>

    <!-- Main shell tabs / More -->
    <string name="tab_home">Home</string>
    <string name="tab_browse">Browse</string>
    <string name="tab_more">More</string>
    <string name="more_title">More</string>

    <!-- Browser -->
    <string name="browser_new_folder_title">New folder</string>
    <string name="browser_rename_title">Rename</string>
    <string name="browser_name_hint">Name</string>
    <string name="browser_items_selected">%1$d selected</string>
    <string name="browser_sort_by">Sort by</string>
    <string name="browser_sort_name">Name</string>
    <string name="browser_sort_size">Size</string>
    <string name="browser_sort_date">Date modified</string>
    <string name="browser_sort_type">Type</string>
    <string name="browser_folders_first">Folders first</string>

    <!-- Cleanup -->
    <string name="cleanup_title">Cleanup</string>
    <string name="cleanup_scanning">Scanning…</string>
    <string name="cleanup_reclaimable">%1$s reclaimable</string>
    <string name="cleanup_cache">Cache</string>
    <string name="cleanup_duplicates">Duplicates</string>
    <string name="cleanup_large_files">Large files</string>
    <string name="cleanup_empty_folders">Empty folders</string>
    <string name="cleanup_clean_now">Clean now</string>

    <!-- Permission flow (extends existing permission_* keys) -->
    <string name="permission_browse_title">Browse everything</string>
    <string name="permission_browse_desc">See and manage files across internal storage and SD cards.</string>
    <string name="permission_search_title">Search &amp; clean up</string>
    <string name="permission_search_desc">Find large files and duplicates to reclaim space.</string>
    <string name="permission_ondevice_title">Stays on device</string>
    <string name="permission_ondevice_desc">Your files are never uploaded. Everything happens locally.</string>
    <string name="permission_grant_all_files">Grant all files access</string>
    <string name="permission_revoke_note">You can revoke this anytime in system settings.</string>

    <!-- Generic plurals -->
    <plurals name="file_count">
        <item quantity="one">%1$d file</item>
        <item quantity="other">%1$d files</item>
    </plurals>
    <plurals name="folder_count">
        <item quantity="one">%1$d folder</item>
        <item quantity="other">%1$d folders</item>
    </plurals>
```

> The catalog is intentionally complete for the screens touched in Phase 6. When refactoring additional screens, add keys here **first**, then translate. Every `%1$s` / `%1$d` / `%2$d` positional argument must be preserved identically in all translations, and apostrophes in non-English copy must be escaped (`\'`) or the string wrapped in `"…"`.

---

## 5. Phase 2 — Data / domain layer

The "data layer" for localization is a single persisted preference plus a typed `LanguageOption`. There is no network, no Room, no new Hilt module — we reuse the existing `SettingsDataStore` (already `@Singleton @Inject`) and `@IoDispatcher`. Error handling reuses `AppResult`/`AppError`, though the only realistic failure (a DataStore `IOException`) is already swallowed by the existing `.safe()` operator that falls back to empty preferences (→ "System default").

### 5.1 NEW `app/src/main/java/com/jupiter/filemanager/domain/model/LanguageOption.kt`

A pure-domain enum mapping a stable persisted tag to a BCP-47 tag and a display-name resource. `SYSTEM` means "follow the device / let `AppCompatDelegate` use an empty locale list."

```kotlin
package com.jupiter.filemanager.domain.model

import androidx.annotation.StringRes
import com.jupiter.filemanager.R

/**
 * The set of UI languages Jupiter ships, plus a [SYSTEM] sentinel meaning
 * "follow the device locale."
 *
 * [tag] is the persisted, build-stable identifier written to DataStore and is
 * also the BCP-47 language tag handed to AppCompat ([SYSTEM] maps to an empty
 * tag, i.e. no override). [labelRes] is the self-endonym shown in the picker
 * (e.g. "Deutsch", "Magyar") so users recognise their language regardless of
 * the current UI locale.
 *
 * Adding a language is a three-step change: add a [values-xx] resource folder,
 * add the `<locale>` to res/xml/locales_config.xml, and add an entry here.
 */
enum class LanguageOption(
    val tag: String,
    @StringRes val labelRes: Int,
) {
    SYSTEM(tag = "", labelRes = R.string.language_system),
    ENGLISH(tag = "en", labelRes = R.string.language_english),
    HUNGARIAN(tag = "hu", labelRes = R.string.language_hungarian),
    GERMAN(tag = "de", labelRes = R.string.language_german),
    SPANISH(tag = "es", labelRes = R.string.language_spanish),
    FRENCH(tag = "fr", labelRes = R.string.language_french),
    PORTUGUESE(tag = "pt", labelRes = R.string.language_portuguese),
    PORTUGUESE_BR(tag = "pt-BR", labelRes = R.string.language_portuguese_br),
    ITALIAN(tag = "it", labelRes = R.string.language_italian),
    RUSSIAN(tag = "ru", labelRes = R.string.language_russian),
    TURKISH(tag = "tr", labelRes = R.string.language_turkish),
    INDONESIAN(tag = "id", labelRes = R.string.language_indonesian),
    JAPANESE(tag = "ja", labelRes = R.string.language_japanese),
    KOREAN(tag = "ko", labelRes = R.string.language_korean),
    DUTCH(tag = "nl", labelRes = R.string.language_dutch),
    ;

    companion object {
        /**
         * Resolves a persisted [tag] back to a [LanguageOption], defaulting to
         * [SYSTEM] when the tag is null, blank, or unknown (e.g. a language
         * removed in a later build). Never throws.
         */
        fun fromTag(tag: String?): LanguageOption =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}
```

### 5.2 EDIT `app/src/main/java/com/jupiter/filemanager/data/preferences/SettingsDataStore.kt`

Add an `app_language` key, a read `Flow`, and a writer. Mirror the existing style exactly (the `.safe()` operator and `enumValueOrNull` helper already exist in this file).

Add to the `Keys` object (after `AI_API_KEY`):

```kotlin
        val APP_LANGUAGE = stringPreferencesKey("app_language")
```

Add the read flow (after the `aiApiKey` flow). It maps the persisted tag through `LanguageOption.fromTag`, so an unknown/removed tag degrades to `SYSTEM` rather than crashing:

```kotlin
    /**
     * The persisted UI language as a [LanguageOption]; defaults to
     * [LanguageOption.SYSTEM] (follow the device locale) when unset or unknown.
     */
    val appLanguage: Flow<LanguageOption> = dataStore.data
        .safe()
        .map { prefs -> LanguageOption.fromTag(prefs[Keys.APP_LANGUAGE]) }
```

Add the writer (after `setAiApiKey`):

```kotlin
    suspend fun setAppLanguage(option: LanguageOption) {
        dataStore.edit { prefs -> prefs[Keys.APP_LANGUAGE] = option.tag }
    }
```

Add the import at the top of the file (next to the other `domain.model` imports):

```kotlin
import com.jupiter.filemanager.domain.model.LanguageOption
```

### 5.3 NEW `app/src/main/java/com/jupiter/filemanager/core/locale/LocaleController.kt`

A thin, framework-facing controller that translates a `LanguageOption` into an `AppCompatDelegate` application-locale change. It is the **only** place that touches the Android locale framework, keeping the rest of the app testable. It is a `@Singleton` so it can be injected anywhere; it holds no mutable state.

```kotlin
package com.jupiter.filemanager.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.jupiter.filemanager.domain.model.LanguageOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies a [LanguageOption] to the running app via AppCompat's per-app locale
 * APIs.
 *
 * On API 33+ this delegates to the platform `LocaleManager`; on API 24–32
 * AppCompat backs it with an auto-stored service (enabled because Jupiter
 * declares `android:localeConfig` and depends on `androidx.appcompat`).
 * Applying an empty [LocaleListCompat] clears any override and returns the app
 * to following the device locale — this is what [LanguageOption.SYSTEM] does.
 *
 * Safe to call from the main thread; AppCompat marshals the change internally
 * and recreates affected activities. Idempotent: re-applying the current locale
 * is a no-op.
 */
@Singleton
class LocaleController @Inject constructor() {

    fun apply(option: LanguageOption) {
        val locales = if (option == LanguageOption.SYSTEM || option.tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(option.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** The locale currently applied, resolved back to a [LanguageOption]. */
    fun current(): LanguageOption {
        val tag = AppCompatDelegate.getApplicationLocales()
            .toLanguageTags()
            .substringBefore(',') // first tag only
        return LanguageOption.fromTag(tag.ifBlank { null })
    }
}
```

No new Hilt module is required: `LocaleController` is constructor-injectable via its `@Inject` constructor, and `SettingsDataStore` is already provided. `@IoDispatcher` from `di/CoroutineModule.kt` is reused implicitly because all DataStore writes already run on DataStore's own IO dispatcher inside `viewModelScope.launch`.

---

## 6. Phase 3 — Presentation

### 6.1 EDIT `app/src/main/java/com/jupiter/filemanager/MainActivity.kt`

Two changes, both additive and behavior-preserving:

1. Change the base class `ComponentActivity` → `AppCompatActivity` (a `ComponentActivity` subclass; `setContent`, `enableEdgeToEdge`, Hilt `@AndroidEntryPoint`, and `hiltViewModel()` all continue to work unchanged).
2. Apply the persisted locale **before** the first frame so the very first composition is already localized. Because the locale must be read synchronously and DataStore is async, we apply it from a `LaunchedEffect` collecting `MainViewModel.appLanguage` (AppCompat recreates the activity if the applied locale differs — a one-time, invisible recreation on cold start only when an override is set).

Full replacement file:

```kotlin
package com.jupiter.filemanager

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.jupiter.filemanager.domain.model.ThemeMode
import com.jupiter.filemanager.ui.navigation.Destination
import com.jupiter.filemanager.ui.navigation.JupiterNavHost
import com.jupiter.filemanager.ui.theme.JupiterTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for Jupiter.
 *
 * Extends [AppCompatActivity] (still a ComponentActivity) so AppCompat's per-app
 * locale APIs apply; the persisted [com.jupiter.filemanager.domain.model.LanguageOption]
 * is applied via the view model so the UI follows the user's chosen language.
 * Theme handling is unchanged.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val themeMode: ThemeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()

            // Apply the persisted UI language. AppCompat handles recreation if the
            // applied locale differs from what is currently active; on the common
            // path (System default) this is a no-op.
            LaunchedEffect(Unit) {
                mainViewModel.applyPersistedLocale()
            }

            JupiterTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    JupiterNavHost(
                        navController = navController,
                        startDestination = Destination.Splash.route,
                    )
                }
            }
        }
    }
}
```

### 6.2 EDIT `app/src/main/java/com/jupiter/filemanager/MainViewModel.kt`

Inject `SettingsDataStore` (already injected) + `LocaleController`, and expose a one-shot `applyPersistedLocale()` that reads the first persisted value and applies it. Full replacement file:

```kotlin
package com.jupiter.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.locale.LocaleController
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Process-wide view model owning state that must outlive individual feature
 * screens — the resolved [ThemeMode] driving the app theme, plus one-shot
 * application of the persisted UI language on startup.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val localeController: LocaleController,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ThemeMode.SYSTEM,
        )

    /**
     * Applies the persisted language once on cold start. Reads the first
     * persisted [com.jupiter.filemanager.domain.model.LanguageOption] and hands
     * it to [LocaleController]; if it matches the active locale this is a no-op,
     * otherwise AppCompat recreates the activity with the correct locale.
     */
    fun applyPersistedLocale() {
        viewModelScope.launch {
            val option = settings.appLanguage.first()
            localeController.apply(option)
        }
    }
}
```

### 6.3 EDIT `app/src/main/java/com/jupiter/filemanager/feature/settings/SettingsViewModel.kt`

Extend the immutable `SettingsUiState` with `language`, add `LocaleController`, recombine the flows (note `combine` of 6 flows uses the vararg overload), and add a `setLanguage` action that **persists then applies immediately** (live language switch without leaving Settings). Full replacement file:

```kotlin
package com.jupiter.filemanager.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.locale.LocaleController
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.LanguageOption
import com.jupiter.filemanager.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable UI state for the settings screen. Mirrors the preferences persisted
 * by [SettingsDataStore], including the selected UI [language].
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showHidden: Boolean = false,
    val dualPaneEnabled: Boolean = false,
    val aiEnabled: Boolean = false,
    val aiApiKey: String = "",
    val language: LanguageOption = LanguageOption.SYSTEM,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val localeController: LocaleController,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.themeMode,
        settings.showHidden,
        settings.dualPaneEnabled,
        settings.aiEnabled,
        settings.aiApiKey,
        settings.appLanguage,
    ) { values ->
        SettingsUiState(
            themeMode = values[0] as ThemeMode,
            showHidden = values[1] as Boolean,
            dualPaneEnabled = values[2] as Boolean,
            aiEnabled = values[3] as Boolean,
            aiApiKey = values[4] as String,
            language = values[5] as LanguageOption,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setShowHidden(value: Boolean) {
        viewModelScope.launch { settings.setShowHidden(value) }
    }

    fun setDualPane(value: Boolean) {
        viewModelScope.launch { settings.setDualPaneEnabled(value) }
    }

    fun setAiEnabled(value: Boolean) {
        viewModelScope.launch { settings.setAiEnabled(value) }
    }

    fun setAiApiKey(value: String) {
        viewModelScope.launch { settings.setAiApiKey(value.trim()) }
    }

    /**
     * Persists the chosen UI [option] and applies it immediately so the screen
     * re-renders in the new language without a restart. Persisting first
     * guarantees the choice survives the activity recreation AppCompat performs.
     */
    fun setLanguage(option: LanguageOption) {
        viewModelScope.launch {
            settings.setAppLanguage(option)
            localeController.apply(option)
        }
    }
}
```

> The 6-arg `combine` uses the `vararg` overload, which yields an `Array<*>`; the casts are safe because each source flow's element type is fixed. If you prefer type safety, nest two `combine` calls instead — but the vararg form keeps the single-`stateIn` shape the existing code uses.

### 6.4 EDIT `app/src/main/java/com/jupiter/filemanager/feature/settings/SettingsScreen.kt`

Two categories of change:

**(a) Replace hardcoded literals with `stringResource`.** Add imports:

```kotlin
import androidx.compose.ui.res.stringResource
import com.jupiter.filemanager.R
import com.jupiter.filemanager.domain.model.LanguageOption
```

Then swap the literals, e.g.:

- `title = { Text(text = "Settings") }` → `title = { Text(text = stringResource(R.string.settings_title)) }`
- `contentDescription = "Back"` → `contentDescription = stringResource(R.string.action_back)`
- `SettingsSectionHeader(title = "Appearance")` → `SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))`
- `SettingsSwitchRow(title = "Show hidden files", subtitle = "Display files and folders that start with a dot", …)` → `title = stringResource(R.string.settings_show_hidden), subtitle = stringResource(R.string.settings_show_hidden_desc)`
- `Text(text = "Version $APP_VERSION")` → `Text(text = stringResource(R.string.settings_about_version, APP_VERSION))`
- The `labelForThemeMode`/`descriptionForThemeMode` helpers currently return literals. Make them `@Composable` and return `stringResource`, e.g. `ThemeMode.SYSTEM -> stringResource(R.string.theme_system)`.

**(b) Add a Language section.** Insert a new section directly after the "Appearance" divider, before "Browsing":

```kotlin
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(
                title = stringResource(R.string.settings_section_language),
            )
            LanguageSelector(
                selected = uiState.language,
                onSelected = viewModel::setLanguage,
            )
```

Add the `LanguageSelector` composable (place it near `ThemeModeSelector`). It opens a `DropdownMenu` of every `LanguageOption`, showing each language's endonym so users find their own language regardless of the active UI locale:

```kotlin
@Composable
private fun LanguageSelector(
    selected: LanguageOption,
    onSelected: (LanguageOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableModifier { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(selected.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.language_picker_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LanguageOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(option.labelRes)) },
                    onClick = {
                        expanded = false
                        if (option != selected) onSelected(option)
                    },
                    trailingIcon = {
                        if (option == selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        }
    }
}
```

Add the imports the new composable needs:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
```

### 6.5 Navigation wiring

**No `Destinations.kt` or `JupiterNavHost.kt` change is required.** The language picker lives inside the already-wired `Destination.Settings` screen, reachable from `MoreScreen`/`SettingsScreen` exactly as today. This keeps the initiative additive and avoids touching the navigation graph.

### 6.6 Bulk screen refactor (the bulk of the value)

Refactor every hardcoded `Text("…")` and `contentDescription = "…"` in `feature/**` to `stringResource(R.string.…)`, adding keys to the **default** `strings.xml` as you go. Prioritize by visibility and string count (from the real grep): `feature/browser/FileBrowserScreen.kt` (10), `feature/cleanup/CleanupScreen.kt` (14), `feature/home/HomeScreen.kt` (8), `feature/settings/SettingsScreen.kt` (8), `feature/analytics/StorageAnalyticsScreen.kt` (7), `feature/vault/VaultScreen.kt` (7), `feature/cloud/CloudHubScreen.kt` (7), `feature/transfer/*` (5–6 each), `feature/permission/PermissionScreen.kt` (3), `feature/more/MoreScreen.kt` (1, the "More" title + tool titles/subtitles in `rememberMoreSections()`).

Mechanical rules:

- Compose: `Text(text = "Cleanup")` → `Text(text = stringResource(R.string.cleanup_title))`.
- Plurals: `Text("$n files")` → `Text(pluralStringResource(R.plurals.file_count, n, n))`.
- Format args: `Text("$size reclaimable")` → `Text(stringResource(R.string.cleanup_reclaimable, size))`.
- Non-composable data classes (e.g. `MoreTool(title: String, …)` in `MoreScreen.kt`): change the field type to `@StringRes val titleRes: Int` and resolve with `stringResource` at the call site, **or** resolve inside a `@Composable rememberMoreSections()` using `stringResource`. Do not call `stringResource` outside a composable.
- Never concatenate translated fragments; use a single positional-argument string per sentence.

---

## 7. Phase 4 — Configuration: keys, external services, ProGuard

### 7.1 Environment / keys

None. This initiative ships no secrets, no network calls, no `BuildConfig` fields.

### 7.2 External service setup — localized Play Store listing (the "Global Reach" half)

This is configured in the Google Play Console (no code). For each shipped locale:

1. **Play Console → your app → Grow → Store presence → Main store listing** (`https://play.google.com/console`).
2. Click **Manage translations → Add your own translation text** and add each locale: `hu-HU, de-DE, es-ES, fr-FR, pt-PT, pt-BR, it-IT, ru-RU, tr-TR, id, ja-JP, ko-KR, nl-NL`.
3. For each, translate: **app title** (≤30 chars), **short description** (≤80 chars), **full description** (≤4000 chars). Provide localized **screenshots** where feasible (reuse the in-app localized screens captured in Phase 8).
4. **Settings → Localization** can enable Google's auto-translate as a baseline, but human review of the title/short description is recommended for ranking.
5. Save and publish the listing as a *store-listing-only* change (no new APK needed for listing text).

### 7.3 ProGuard / R8

R8 does not strip resources referenced from XML/code, and `AppCompatDelegate`/`LocaleManagerCompat` ship with consumer rules, so **no new keep rules are strictly required**. The `androidResources.localeFilters` set in Phase 1 already restricts shipped locales. Optionally, to be explicit, append to `app/proguard-rules.pro`:

```proguard
# Localization: keep the locale enum referenced reflectively only by name in DataStore.
-keepclassmembers enum com.jupiter.filemanager.domain.model.LanguageOption { *; }
```

`isShrinkResources = true` (already on for release) is compatible: translated `strings.xml` entries are kept because they share keys with the referenced default resources.

---

## 8. Phase 5 — Testing

### 8.1 Manual smoke-test script

1. `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. Install on an API 26 emulator and an API 34 emulator.
2. Cold launch with device locale = English → entire UI renders identical English to the pre-change build (spot-check Browser, Cleanup, Settings, Permission, More).
3. Settings → **Language** → pick **Magyar** → UI re-renders in Hungarian *without leaving Settings*; section headers, toggles, About line all Hungarian.
4. Kill and relaunch the app → still Hungarian (persisted). Confirm DataStore survived process death.
5. Switch to **Deutsch**, then **System default** → System default returns to the device locale immediately.
6. API 34: Android **Settings → System → Languages → App languages → Jupiter** lists Jupiter and offers all 13 languages (proves `locales_config.xml` is wired). Change it there → app follows.
7. Set device to a language Jupiter does **not** ship (e.g. Swedish) → app falls back to default English (no crash, no blank strings).
8. Set device to Arabic → layout mirrors (RTL) thanks to `supportsRtl="true"`; no clipped text.
9. Rotate device in each language → no crash, text persists.
10. Verify a screen with format args (Cleanup "X reclaimable") shows the localized template with the number substituted correctly.

### 8.2 Recommended unit tests (`app/src/test/java/...`)

`LanguageOptionTest.kt`:

```kotlin
package com.jupiter.filemanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageOptionTest {

    @Test
    fun fromTag_knownTag_resolves() {
        assertEquals(LanguageOption.HUNGARIAN, LanguageOption.fromTag("hu"))
        assertEquals(LanguageOption.PORTUGUESE_BR, LanguageOption.fromTag("pt-BR"))
    }

    @Test
    fun fromTag_unknownOrBlank_defaultsToSystem() {
        assertEquals(LanguageOption.SYSTEM, LanguageOption.fromTag("xx"))
        assertEquals(LanguageOption.SYSTEM, LanguageOption.fromTag(""))
        assertEquals(LanguageOption.SYSTEM, LanguageOption.fromTag(null))
    }

    @Test
    fun systemHasBlankTag_othersDistinct() {
        assertEquals("", LanguageOption.SYSTEM.tag)
        val tags = LanguageOption.entries.map { it.tag }
        assertEquals(tags.size, tags.distinct().size)
    }
}
```

`SettingsViewModelLanguageTest.kt` (with a fake `SettingsDataStore` + relaxed/no-op `LocaleController`, using `kotlinx-coroutines-test` already in the catalog) asserting that `setLanguage(HUNGARIAN)` persists `"hu"` and the `uiState.language` flow emits `HUNGARIAN`.

### 8.3 Recommended instrumented test (`app/src/androidTest/java/...`)

A resource-parity test that loads `R.string.settings_title` (and a handful of representative keys) under each in-scope locale via `createConfigurationContext` and asserts the resolved string is non-blank — catches a translation file that is missing a key or has a malformed `%` placeholder.

---

## 9. Error handling & edge cases

1. **Missing translation key in a `values-<lang>` file.** Android resource resolution falls back to `res/values/strings.xml` (default English) automatically. Result: that one label shows English, the rest stay localized. No crash. Mitigation: the `SettingsViewModelLanguageTest`/instrumented parity test flags incomplete files in CI.
2. **Unknown / removed persisted language tag** (user downgrades, or a language is dropped in a future build). `LanguageOption.fromTag` returns `SYSTEM`; `SettingsDataStore.appLanguage` therefore emits `SYSTEM` and the UI follows the device locale. No exception.
3. **DataStore read IOException** (corrupt prefs file / disk error). The existing `.safe()` operator in `SettingsDataStore` emits `emptyPreferences()`, so `appLanguage` resolves to `SYSTEM`. App opens in the device language rather than crashing.
4. **Malformed format string in a translation** (e.g. translator drops `%1$s`). `Resources.getString(..., args)` throws `IllegalFormatException` only when args are passed and the placeholder count mismatches. Mitigation: the default catalog uses positional args (`%1$s`, `%1$d`) which the Android lint `StringFormatInvalid`/`StringFormatMatches` checks validate at build time; lint runs in CI. Keep `abortOnError = false` (already set) but treat these specific lint findings as blocking in review.
5. **Apostrophe / unescaped quote in non-English copy** (common in FR/IT/PT). An unescaped `'` makes AAPT2 fail the build. Mitigation: every translated value with an apostrophe is wrapped in `"…"` or escaped `\'`; `./gradlew :app:assembleDebug` is the gate — a malformed value fails compilation immediately, never ships.
6. **Locale not in `localeFilters`** but present as a `values-xx` folder. AGP strips it from the APK, so it silently won't appear. Mitigation: the `localeFilters` list in Phase 1 and the `locales_config.xml` list must stay in sync with the `values-*` folders and the `LanguageOption` enum — documented as the "three-step" rule in `LanguageOption`'s KDoc.
7. **AppCompat activity recreation loop.** If `applyPersistedLocale()` applied a *different* locale than the resources just loaded, AppCompat recreates the activity; on the next pass the locale matches and `setApplicationLocales` is a no-op, so it converges in exactly one recreation. `LocaleController.apply` is idempotent, preventing loops.
8. **RTL device locale (Arabic/Hebrew) while Jupiter ships only LTR translations.** `supportsRtl="true"` mirrors layouts; untranslated strings show in English but laid out RTL. No crash; acceptable until RTL languages are added.

---

## 10. Integration with other initiatives

- **#1 Jupiter Pro (Monetization):** the Paywall/Upgrade screen introduced there must use `stringResource` from day one; add its keys (`paywall_*`) to this catalog so Pro is localized in every market. This initiative provides the `R.string` discipline #1 depends on.
- **#2 (AI Suite / cloud, etc.):** any new user-facing screen authored by another initiative must follow the "keys in `values/strings.xml` first, then `stringResource`" rule established here — making localization a cross-cutting prerequisite, not a one-off.
- **Onboarding / Permission initiatives:** localized onboarding copy materially lifts grant rates in non-English markets; those screens are already in the Phase 6 refactor list.
- **Shared infrastructure:** `LanguageOption`, `LocaleController`, and the `app_language` DataStore key are the canonical locale primitives every other initiative reuses; no other initiative should call `AppCompatDelegate` directly.
- **No hard ordering dependency:** this initiative is independent and can ship before or after any other; it only *constrains* their string-handling style.

---

## 11. Rollback plan

The change is purely additive (new resources + a persisted preference + `stringResource` swaps), so rollback is removal:

1. **Disable in-app picker (fastest, partial):** remove the `LanguageSelector` section from `SettingsScreen.kt` and the `setLanguage`/`language` additions from `SettingsViewModel`/`SettingsUiState`. The app reverts to system-locale-only behavior; shipped translations still apply when the device locale matches.
2. **Full revert:** `git revert` the feature commit(s). This restores `MainActivity` to `ComponentActivity`, removes `core/locale/LocaleController.kt`, `domain/model/LanguageOption.kt`, the `app_language` key, the `androidx.appcompat` dependency, `androidResources.localeFilters`, `android:localeConfig`, `res/xml/locales_config.xml`, and every `values-<lang>` folder.
3. **Persisted-data safety:** an orphaned `app_language` value left in DataStore after a downgrade is harmless — no code reads it once the flow/key is removed, and DataStore tolerates unknown keys.
4. **Drop a single bad language without a full revert:** delete its `res/values-<lang>/` folder, remove its `<locale>` from `locales_config.xml`, remove its entry from `LanguageOption` and from `localeFilters`. The picker no longer offers it; any user who had selected it falls back to `SYSTEM` via `fromTag`.
5. Because the default `res/values/strings.xml` is unchanged English, **the worst-case rollback state is identical to today's English-only app.**

---

## 12. Definition of done

- [ ] `androidx.appcompat:appcompat:1.7.0` added to `libs.versions.toml` + `app/build.gradle.kts`; `androidResources.localeFilters` lists exactly the 13 shipped locales.
- [ ] `AndroidManifest.xml` has `android:localeConfig="@xml/locales_config"`; `res/xml/locales_config.xml` lists all shipped `<locale>`s.
- [ ] `res/values/strings.xml` extended with the full key catalog (settings, language, browser, cleanup, permission, plurals); all existing keys untouched.
- [ ] `res/values-{hu,de,es,fr,pt,pt-rBR,it,ru,tr,id,ja,ko,nl}/strings.xml` created, each mirroring the default key set with positional args preserved and apostrophes escaped.
- [ ] `LanguageOption.kt`, `LocaleController.kt` created; `SettingsDataStore` has `app_language` key + `appLanguage` flow + `setAppLanguage`.
- [ ] `MainActivity` extends `AppCompatActivity` and applies the persisted locale on cold start; `MainViewModel.applyPersistedLocale()` and `SettingsViewModel.setLanguage()` wired; live in-app language switch works without an app restart and persists across process death.
- [ ] Hardcoded `Text("…")`/`contentDescription` across `feature/**` refactored to `stringResource`/`pluralStringResource` (Browser, Cleanup, Home, Settings, Analytics, Vault, Cloud, Transfer, Permission, More at minimum).
- [ ] **No regression:** the existing **File Browser** (list/open/sort/rename/new-folder) and **Cleanup** (scan/select/clean) flows behave identically in English to the pre-change build — only the *source* of the displayed text changed.
- [ ] **No regression:** **Settings** theme selector, hidden-files / dual-pane / AI toggles, and the Claude API-key field all still persist and function unchanged; Vault unlock, Permission grant flow, and navigation graph are untouched and still work.
- [ ] Device set to an unshipped locale (e.g. Swedish) falls back to English with no crash and no blank strings; RTL device mirrors layout via `supportsRtl`.
- [ ] Unit tests (`LanguageOptionTest`, `SettingsViewModel` language test) and the resource-parity instrumented test pass.
- [ ] **CI green:** `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` both succeed; no AAPT2 string-escaping failures; lint `StringFormat*` findings reviewed clean.
- [ ] Play Console localized listings (title / short / full description) added for each shipped locale.
```
