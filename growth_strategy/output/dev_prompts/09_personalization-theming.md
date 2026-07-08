# Initiative #9 — Personalization & Theming (Material You Accent, Icon/Density Themes, Custom Home)

> **Implementation prompt for an autonomous Android coding agent.** Implement this initiative end-to-end against the **real Jupiter codebase** at `/home/user/Jupiter` (Kotlin, Jetpack Compose, Material 3, Hilt, Coroutines, Preferences DataStore). Work **additively** and **without regression** to any existing working feature. Produce complete, compiling Kotlin — no pseudocode, no TODO stubs.

---

## 1. Initiative header

- **Title:** Personalization & Theming — Material You Accent, Icon/Density Themes, Custom Home
- **Value range:** **+€70k–€150k** (one-time + retention-driven LTV uplift)
- **Business case:** Personalization is one of the cheapest, highest-retention features a file manager can ship: it costs nothing per user, increases emotional ownership ("this is *my* app"), and converts well as a soft Pro upsell because the free tier can offer a taste (one accent + light/dark) while Pro unlocks the full palette, AMOLED-black, density controls, icon themes, and home-layout customization. Jupiter already has a clean, isolated theme layer (`JupiterTheme` + `JupiterLightColors`/`JupiterDarkColors`) and a working DataStore-backed settings pipeline (`SettingsDataStore` → `MainViewModel.themeMode` → `MainActivity`), so this initiative is purely **additive**: it extends the existing theme function signature, adds new persisted keys, and adds one new settings sub-screen. There is no server cost, no new permission, and the entire feature degrades gracefully (any malformed/absent preference falls back to the current behavior). Expected outcome: measurable lift in D7/D30 retention and a clean, non-intrusive paywall touchpoint that does not gate any core file-management capability.

---

## 2. Codebase context

### Current real file tree (relevant slice)

```
app/src/main/java/com/jupiter/filemanager/
├── MainActivity.kt                                   # applies JupiterTheme(themeMode = …)  [EDIT]
├── MainViewModel.kt                                  # exposes themeMode StateFlow           [EDIT]
├── ui/theme/
│   ├── Theme.kt                                      # JupiterTheme(themeMode, dynamicColor) [EDIT]
│   ├── Color.kt                                      # JupiterLightColors / JupiterDarkColors [EDIT]
│   └── Type.kt                                       # JupiterTypography                      (unchanged)
├── ui/navigation/
│   ├── Destinations.kt                               # sealed Destination                     [EDIT: add Appearance]
│   └── JupiterNavHost.kt                             # NavHost graph                          [EDIT: wire Appearance]
├── feature/settings/
│   ├── SettingsScreen.kt                             # settings list + ThemeModeSelector      [EDIT: add nav row]
│   └── SettingsViewModel.kt                          # SettingsUiState + writes               (unchanged for #9)
├── feature/home/
│   ├── HomeUiState.kt                                # HomeUiState, QuickAccessShortcut       (read-only ref)
│   ├── HomeScreen.kt                                 # Home dashboard sections                [OPTIONAL EDIT]
│   └── HomeViewModel.kt
├── data/preferences/
│   └── SettingsDataStore.kt                          # Preferences DataStore wrapper          [EDIT: add keys]
├── domain/model/
│   └── Settings.kt                                   # enum class ThemeMode                    (read-only ref)
├── core/result/
│   ├── AppResult.kt                                  # sealed AppResult<T> (Success/Failure)  (read-only ref)
│   └── AppError.kt                                   # sealed AppError                         (read-only ref)
└── di/
    └── CoroutineModule.kt                            # @IoDispatcher / @DefaultDispatcher      (read-only ref)
```

### What exists vs missing vs needs change

| Element | Status | Action |
| --- | --- | --- |
| `ThemeMode { SYSTEM, LIGHT, DARK }` (`domain/model/Settings.kt`) | **Exists** | **Keep unchanged.** Add new enums in a separate file; do not break `ThemeMode`. |
| `JupiterTheme(themeMode, dynamicColor, content)` (`ui/theme/Theme.kt`) | **Exists** | **Extend signature** with new defaulted params (accent, darkVariant, density) — keep existing params/defaults so no caller breaks. |
| `JupiterLightColors` / `JupiterDarkColors` (`ui/theme/Color.kt`) | **Exists** | **Keep**; add accent-seeded scheme builders + an AMOLED-black override. |
| `SettingsDataStore` (`data/preferences/SettingsDataStore.kt`) | **Exists** | **Add** keys + flows + setters for accent, dark variant, density, icon theme, home layout. Reuse existing `.safe()` + `enumValueOrNull`. |
| `MainViewModel.themeMode` | **Exists (single flow)** | **Add** a combined `themeConfig` StateFlow; keep `themeMode` for back-compat. |
| `MainActivity` theme application | **Exists** | **Edit** to pass the new theme config into `JupiterTheme` and to provide a `LocalDensity`/`LocalAppDensity` composition local. |
| `AppearanceScreen` + `AppearanceViewModel` | **Missing** | **Create** under `feature/settings/appearance/`. |
| `Destination.Appearance` + nav wiring | **Missing** | **Add** to `Destinations.kt` + `JupiterNavHost.kt`. |
| Density / accent / icon-theme / home-layout domain models | **Missing** | **Create** under `domain/model/Appearance.kt`. |
| Pro gate (`ProRepository`/`AppTier`) | **Missing in repo (owned by Initiative #1)** | **Depend on it optionally** via a small local `PersonalizationGate` interface with a fail-open default binding, so #9 compiles and runs whether or not #1 has landed. |

There is **no Room, no network, no new permission, no API key** required for this initiative. It is entirely on-device and synchronous-to-DataStore.

---

## 3. Pre-conditions

### Gradle dependencies to add (exact coordinates)

All needed runtime libraries are **already present** in `gradle/libs.versions.toml` (Compose BoM `2024.12.01`, `androidx.compose.material3:material3`, `androidx.datastore:datastore-preferences:1.1.1`, `kotlinx-coroutines` `1.9.0`, Hilt `2.52`). The only **new** addition is a unit-test helper for Compose/Turbine-style flow assertions — optional but recommended:

- **(Optional, test only)** `app.cash.turbine:turbine:1.1.0` — for flow assertions in `AppearanceViewModel` tests. If you prefer to avoid a new dependency, use `kotlinx-coroutines-test` (already present) with `runTest { … }` and manual collection instead. This prompt's tests use only the already-present `kotlinx-coroutines-test`, so **no new dependency is strictly required**.

### Manifest / permission / Play-Console / API-key prerequisites

- **Permissions:** none. Do **not** add any `<uses-permission>`.
- **Manifest:** no changes required for the core feature. (One optional change in Phase 1 if you ship alternate launcher icons via `activity-alias`; that is gated behind the Pro icon-theme feature and is **off by default**.)
- **Play Console:** none for the theming itself. The Pro entitlement that gates the premium options is owned by **Initiative #1 (Pro Monetization)**; #9 must run with or without it (fail-open).
- **API keys / external services:** none.

---

## 4. Phase 1 — Gradle + Manifest + resources

### 4.1 `gradle/libs.versions.toml`

No mandatory edits. If you choose Turbine for tests, add under `[versions]` and `[libraries]`:

```toml
# [versions]
turbine = "1.1.0"

# [libraries]
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

### 4.2 `app/build.gradle.kts`

No mandatory edits. If you added Turbine, add to the `dependencies { }` block:

```kotlin
    testImplementation(libs.turbine)
```

The existing `kotlinOptions.freeCompilerArgs` already opts into `ExperimentalMaterial3Api` and `ExperimentalFoundationApi`; no new opt-ins are required.

### 4.3 `AndroidManifest.xml`

No mandatory edits. **Optional, off-by-default** alternate launcher icon (only if you want true launcher icon themes rather than in-app icon-pack theming): you may later add `<activity-alias>` entries enabled/disabled at runtime via `PackageManager.setComponentEnabledSetting`. **Do not implement this in the first pass** — Jupiter's "icon themes" in this initiative means **in-app folder/file icon sets** (a Compose `IconTheme` that swaps the vector set), which requires no manifest change and cannot regress the launcher.

### 4.4 Resources

No new XML drawables are required; the icon themes are implemented as Compose `ImageVector` maps (Phase 2). No `colors.xml` edits — all color is Compose-side.

---

## 5. Phase 2 — Data / domain layer

### 5.1 New file — `domain/model/Appearance.kt`

```kotlin
package com.jupiter.filemanager.domain.model

import androidx.compose.ui.graphics.Color

/**
 * The dark-surface variant applied when the resolved theme is dark.
 *
 * [STANDARD] uses Jupiter's tuned near-black dark scheme; [AMOLED] forces a
 * pure-black background/surface for OLED power savings and contrast. Has no
 * effect in light mode.
 */
enum class DarkVariant {
    STANDARD,
    AMOLED,
}

/**
 * UI density preset controlling list/row compactness across the app.
 *
 * Maps to a font/spacing scale factor applied via a composition-local density.
 */
enum class DensityOption(val scale: Float) {
    COMFORTABLE(1.0f),
    COZY(0.92f),
    COMPACT(0.85f),
}

/**
 * Selectable in-app icon set used for folder/file glyphs.
 *
 * [DEFAULT] uses the stock Material filled icons; the others are visual variants
 * resolved by the icon-theme mapper in the UI layer. Free users get [DEFAULT];
 * the rest are a Pro perk (enforced in the presentation layer, never here).
 */
enum class IconTheme {
    DEFAULT,
    ROUNDED,
    OUTLINED,
    SHARP,
}

/**
 * User-selectable brand accent that seeds the Material 3 color scheme when
 * dynamic (Material You) color is disabled or unavailable.
 *
 * [seedLight]/[seedDark] are the primary seed colors used to derive the
 * Jupiter-tinted schemes. [JUPITER_BLUE] reproduces the existing NEXUS brand
 * blue so the default appearance is unchanged.
 */
enum class AccentColor(
    val seedLight: Long,
    val seedDark: Long,
) {
    JUPITER_BLUE(0xFF2563EB, 0xFFAAC7FF),
    INDIGO(0xFF4F46E5, 0xFFB7B9FF),
    TEAL(0xFF0E7490, 0xFF59D4F4),
    EMERALD(0xFF059669, 0xFF6EE7B7),
    AMBER(0xFFD97706, 0xFFFBBF24),
    ROSE(0xFFE11D48, 0xFFFDA4B4),
    VIOLET(0xFF7C3AED, 0xFFD8B4FE),
    SLATE(0xFF475569, 0xFFCBD5E1);

    val light: Color get() = Color(seedLight)
    val dark: Color get() = Color(seedDark)
}

/**
 * Optional reordering / visibility for Home dashboard sections ("Custom Home").
 *
 * [order] is the section identity list in display order; sections absent from
 * [hidden] are shown. Both default to the canonical layout so an absent
 * preference reproduces today's Home exactly.
 */
data class HomeLayout(
    val order: List<HomeSection> = HomeSection.entries,
    val hidden: Set<HomeSection> = emptySet(),
) {
    val visibleInOrder: List<HomeSection>
        get() = order.filter { it !in hidden }
}

/** Identifiable, reorderable Home dashboard sections. */
enum class HomeSection {
    STORAGE_OVERVIEW,
    QUICK_ACCESS,
    CATEGORIES,
    RECENTS,
    BOOKMARKS,
}

/**
 * Immutable snapshot of every personalization preference, resolved together so
 * the theme can be applied in a single recomposition pass.
 */
data class ThemeConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: AccentColor = AccentColor.JUPITER_BLUE,
    val darkVariant: DarkVariant = DarkVariant.STANDARD,
    val density: DensityOption = DensityOption.COMFORTABLE,
    val iconTheme: IconTheme = IconTheme.DEFAULT,
)
```

### 5.2 Edit — `data/preferences/SettingsDataStore.kt`

Add new keys, flows, and setters **without touching** the existing members. Insert into the `Keys` object and append the new flows/setters. Reuse the file-private `enumValueOrNull` helper and the `.safe()` extension that already exist.

Add to `private object Keys`:

```kotlin
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val DARK_VARIANT = stringPreferencesKey("dark_variant")
        val DENSITY_OPTION = stringPreferencesKey("density_option")
        val ICON_THEME = stringPreferencesKey("icon_theme")
        val HOME_ORDER = stringPreferencesKey("home_order")
        val HOME_HIDDEN = stringPreferencesKey("home_hidden")
```

Add the imports at the top of the file:

```kotlin
import com.jupiter.filemanager.domain.model.AccentColor
import com.jupiter.filemanager.domain.model.DarkVariant
import com.jupiter.filemanager.domain.model.DensityOption
import com.jupiter.filemanager.domain.model.HomeLayout
import com.jupiter.filemanager.domain.model.HomeSection
import com.jupiter.filemanager.domain.model.IconTheme
import com.jupiter.filemanager.domain.model.ThemeConfig
import kotlinx.coroutines.flow.combine
```

Append the new read flows after the existing `aiApiKey` flow:

```kotlin
    /** Whether Material You dynamic color is enabled (Android 12+); defaults to true. */
    val dynamicColor: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.DYNAMIC_COLOR] ?: true }

    /** Selected brand accent; defaults to [AccentColor.JUPITER_BLUE]. */
    val accentColor: Flow<AccentColor> = dataStore.data
        .safe()
        .map { prefs ->
            prefs[Keys.ACCENT_COLOR]
                ?.let { name -> enumValueOrNull<AccentColor>(name) }
                ?: AccentColor.JUPITER_BLUE
        }

    /** Dark-surface variant; defaults to [DarkVariant.STANDARD]. */
    val darkVariant: Flow<DarkVariant> = dataStore.data
        .safe()
        .map { prefs ->
            prefs[Keys.DARK_VARIANT]
                ?.let { name -> enumValueOrNull<DarkVariant>(name) }
                ?: DarkVariant.STANDARD
        }

    /** UI density; defaults to [DensityOption.COMFORTABLE]. */
    val densityOption: Flow<DensityOption> = dataStore.data
        .safe()
        .map { prefs ->
            prefs[Keys.DENSITY_OPTION]
                ?.let { name -> enumValueOrNull<DensityOption>(name) }
                ?: DensityOption.COMFORTABLE
        }

    /** Selected in-app icon theme; defaults to [IconTheme.DEFAULT]. */
    val iconTheme: Flow<IconTheme> = dataStore.data
        .safe()
        .map { prefs ->
            prefs[Keys.ICON_THEME]
                ?.let { name -> enumValueOrNull<IconTheme>(name) }
                ?: IconTheme.DEFAULT
        }

    /**
     * Home dashboard layout. Order and hidden-set are persisted as
     * comma-separated [HomeSection] names; unknown tokens are dropped and any
     * sections missing from the persisted order are appended in their canonical
     * position so older/newer app versions never lose a section.
     */
    val homeLayout: Flow<HomeLayout> = dataStore.data
        .safe()
        .map { prefs ->
            val order = decodeSections(prefs[Keys.HOME_ORDER])
                .let { persisted ->
                    // Append any new sections not present in the persisted order.
                    persisted + HomeSection.entries.filter { it !in persisted }
                }
                .ifEmpty { HomeSection.entries }
            val hidden = decodeSections(prefs[Keys.HOME_HIDDEN]).toSet()
            HomeLayout(order = order, hidden = hidden)
        }

    /**
     * Single combined personalization snapshot used to drive [JupiterTheme] in
     * one recomposition. Composed from the individual flows so each can still be
     * collected independently where convenient.
     */
    val themeConfig: Flow<ThemeConfig> = combine(
        themeMode,
        dynamicColor,
        accentColor,
        darkVariant,
        densityOption,
        iconTheme,
    ) { values ->
        ThemeConfig(
            themeMode = values[0] as ThemeMode,
            dynamicColor = values[1] as Boolean,
            accent = values[2] as AccentColor,
            darkVariant = values[3] as DarkVariant,
            density = values[4] as DensityOption,
            iconTheme = values[5] as IconTheme,
        )
    }
```

> **Note on `combine` arity:** Kotlin's `combine` supports up to 5 typed lambdas; with 6 flows use the vararg overload (`combine(vararg flows, transform: (Array<Any?>) -> R)`) and index into `values` as shown. This compiles because all six flows are `Flow<Any>` under the vararg overload.

Append the new setters after the existing `setAiApiKey`:

```kotlin
    suspend fun setDynamicColor(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.DYNAMIC_COLOR] = value }
    }

    suspend fun setAccentColor(value: AccentColor) {
        dataStore.edit { prefs -> prefs[Keys.ACCENT_COLOR] = value.name }
    }

    suspend fun setDarkVariant(value: DarkVariant) {
        dataStore.edit { prefs -> prefs[Keys.DARK_VARIANT] = value.name }
    }

    suspend fun setDensityOption(value: DensityOption) {
        dataStore.edit { prefs -> prefs[Keys.DENSITY_OPTION] = value.name }
    }

    suspend fun setIconTheme(value: IconTheme) {
        dataStore.edit { prefs -> prefs[Keys.ICON_THEME] = value.name }
    }

    suspend fun setHomeLayout(layout: HomeLayout) {
        dataStore.edit { prefs ->
            prefs[Keys.HOME_ORDER] = layout.order.joinToString(",") { it.name }
            prefs[Keys.HOME_HIDDEN] = layout.hidden.joinToString(",") { it.name }
        }
    }
```

Add this file-private helper at the bottom of the file (next to `enumValueOrNull`):

```kotlin
/**
 * Decodes a comma-separated list of [HomeSection] names, silently dropping any
 * token that does not resolve to a known constant (forward/backward compat).
 */
private fun decodeSections(raw: String?): List<HomeSection> =
    raw?.split(",")
        ?.mapNotNull { token ->
            val name = token.trim()
            if (name.isEmpty()) null
            else enumValues<HomeSection>().firstOrNull { it.name == name }
        }
        ?: emptyList()
```

### 5.3 New file — `feature/settings/appearance/PersonalizationGate.kt`

A tiny seam so #9 compiles whether or not Initiative #1 (Pro) exists. The default binding is **fail-open** (everything unlocked) — replace the binding when #1 lands.

```kotlin
package com.jupiter.filemanager.feature.settings.appearance

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether premium personalization options (extra accents, AMOLED,
 * density, icon themes, custom home) are unlocked.
 *
 * This is intentionally decoupled from Initiative #1's billing so #9 builds and
 * runs standalone. The default [FailOpenPersonalizationGate] grants everything;
 * once the Pro entitlement exists, replace the Hilt binding with one that maps
 * the Pro tier flow to [isUnlocked].
 */
interface PersonalizationGate {
    /** Emits true when premium personalization is unlocked. */
    val isUnlocked: Flow<Boolean>
}

/** Default gate: premium personalization always unlocked (no regression / no paywall). */
@Singleton
class FailOpenPersonalizationGate @Inject constructor() : PersonalizationGate {
    override val isUnlocked: Flow<Boolean> = flowOf(true)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PersonalizationGateModule {
    @Binds
    @Singleton
    abstract fun bindGate(impl: FailOpenPersonalizationGate): PersonalizationGate
}
```

> **When Initiative #1 lands:** delete `FailOpenPersonalizationGate`'s binding and provide an impl whose `isUnlocked = proRepository.tier.map { it == AppTier.PRO }`. No other #9 code changes.

### 5.4 Edit — `ui/theme/Color.kt`

Keep `JupiterLightColors`/`JupiterDarkColors` exactly as-is (they are the `JUPITER_BLUE`/STANDARD defaults). Append accent-seeded builders and an AMOLED override. Add imports:

```kotlin
import com.jupiter.filemanager.domain.model.AccentColor
import com.jupiter.filemanager.domain.model.DarkVariant
```

Append at the end of `Color.kt`:

```kotlin
/**
 * Builds a light [ColorScheme] seeded by [accent] for the non-dynamic brand
 * path. The accent drives primary/secondary/tint while all neutral surfaces are
 * inherited from [JupiterLightColors] so contrast and readability stay tuned.
 */
fun accentLightColors(accent: AccentColor): ColorScheme {
    if (accent == AccentColor.JUPITER_BLUE) return JupiterLightColors
    val seed = accent.light
    return JupiterLightColors.copy(
        primary = seed,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = seed.copy(alpha = 0.16f).compositeOverWhite(),
        secondary = seed,
        surfaceTint = seed,
        inversePrimary = accent.dark,
    )
}

/**
 * Builds a dark [ColorScheme] seeded by [accent], optionally forcing pure-black
 * surfaces for [DarkVariant.AMOLED]. Neutral tones default to
 * [JupiterDarkColors].
 */
fun accentDarkColors(accent: AccentColor, variant: DarkVariant): ColorScheme {
    val base = if (accent == AccentColor.JUPITER_BLUE) {
        JupiterDarkColors
    } else {
        JupiterDarkColors.copy(
            primary = accent.dark,
            primaryContainer = accent.light.copy(alpha = 0.30f).compositeOverBlack(),
            secondary = accent.dark,
            surfaceTint = accent.dark,
            inversePrimary = accent.light,
        )
    }
    return if (variant == DarkVariant.AMOLED) base.toAmoled() else base
}

/** Forces pure-black background/surface for OLED displays. */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF0A0A0A),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF070707),
    surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1C1C1C),
)

/** Composites a translucent color over opaque white (light-container helper). */
private fun Color.compositeOverWhite(): Color =
    Color(
        red = red * alpha + 1f * (1f - alpha),
        green = green * alpha + 1f * (1f - alpha),
        blue = blue * alpha + 1f * (1f - alpha),
        alpha = 1f,
    )

/** Composites a translucent color over opaque black (dark-container helper). */
private fun Color.compositeOverBlack(): Color =
    Color(
        red = red * alpha,
        green = green * alpha,
        blue = blue * alpha,
        alpha = 1f,
    )
```

> If your Material3 version does not expose the `surfaceContainer*` roles in `ColorScheme.copy`, drop those four named arguments — `background`/`surface`/`surfaceVariant` alone deliver the AMOLED effect. Verify against `composeBom = 2024.12.01` (which **does** include them).

### 5.5 Edit — `ui/theme/Theme.kt`

Extend `JupiterTheme` additively. Existing callers that pass only `themeMode`/`dynamicColor` keep working because every new parameter is defaulted. Also wrap content in a density `CompositionLocalProvider` and expose a `LocalIconTheme` so the rest of the app can read the active icon set.

Replace the file body with:

```kotlin
package com.jupiter.filemanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.jupiter.filemanager.domain.model.AccentColor
import com.jupiter.filemanager.domain.model.DarkVariant
import com.jupiter.filemanager.domain.model.DensityOption
import com.jupiter.filemanager.domain.model.IconTheme
import com.jupiter.filemanager.domain.model.ThemeMode

/** Composition-local exposing the active in-app [IconTheme]. Default: [IconTheme.DEFAULT]. */
val LocalIconTheme = staticCompositionLocalOf { IconTheme.DEFAULT }

/**
 * Root Material 3 theme for Jupiter.
 *
 * Backwards compatible: existing callers may pass only [themeMode] and/or
 * [dynamicColor]. New personalization parameters ([accent], [darkVariant],
 * [density], [iconTheme]) all default to today's behavior, so the default
 * appearance and any existing call site is unchanged.
 */
@Composable
fun JupiterTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accent: AccentColor = AccentColor.JUPITER_BLUE,
    darkVariant: DarkVariant = DarkVariant.STANDARD,
    density: DensityOption = DensityOption.COMFORTABLE,
    iconTheme: IconTheme = IconTheme.DEFAULT,
    content: @Composable () -> Unit,
) {
    val darkTheme: Boolean = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dyn = if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            // AMOLED override still applies on top of Material You in dark mode.
            if (darkTheme && darkVariant == DarkVariant.AMOLED) dyn.toAmoledPublic() else dyn
        }
        darkTheme -> accentDarkColors(accent, darkVariant)
        else -> accentLightColors(accent)
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * density.scale,
    )

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalIconTheme provides iconTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JupiterTypography,
            content = content,
        )
    }
}
```

Add this public bridge in `Color.kt` (since `toAmoled` is private there) so the dynamic-color path can reuse it:

```kotlin
/** Public AMOLED override usable from [JupiterTheme] for the dynamic-color path. */
fun ColorScheme.toAmoledPublic(): ColorScheme = copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF0A0A0A),
)
```

> **Density note:** scaling `fontScale` (not `density`) keeps touch targets and layout pixels stable while making text/lists feel tighter — the safest "density" approach that cannot break hit-testing. If a future pass wants tighter *spacing*, prefer per-component padding tokens over scaling `Density.density`, which would resize everything including icons and risk clipping.

### 5.6 Edit — `MainViewModel.kt`

Add a combined `themeConfig` flow while keeping `themeMode` for back-compat.

```kotlin
package com.jupiter.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.ThemeConfig
import com.jupiter.filemanager.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsDataStore,
) : ViewModel() {

    /** Retained for back-compat; prefer [themeConfig] for new code. */
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ThemeMode.SYSTEM,
        )

    /** Full personalization snapshot driving [com.jupiter.filemanager.ui.theme.JupiterTheme]. */
    val themeConfig: StateFlow<ThemeConfig> = settings.themeConfig
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ThemeConfig(),
        )
}
```

---

## 6. Phase 3 — Presentation

### 6.1 Edit — `MainActivity.kt`

Apply the full config to `JupiterTheme`.

```kotlin
package com.jupiter.filemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.jupiter.filemanager.domain.model.ThemeConfig
import com.jupiter.filemanager.ui.navigation.Destination
import com.jupiter.filemanager.ui.navigation.JupiterNavHost
import com.jupiter.filemanager.ui.theme.JupiterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val config: ThemeConfig by mainViewModel.themeConfig.collectAsStateWithLifecycle()

            JupiterTheme(
                themeMode = config.themeMode,
                dynamicColor = config.dynamicColor,
                accent = config.accent,
                darkVariant = config.darkVariant,
                density = config.density,
                iconTheme = config.iconTheme,
            ) {
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

### 6.2 New file — `feature/settings/appearance/AppearanceUiState.kt`

```kotlin
package com.jupiter.filemanager.feature.settings.appearance

import com.jupiter.filemanager.domain.model.AccentColor
import com.jupiter.filemanager.domain.model.DarkVariant
import com.jupiter.filemanager.domain.model.DensityOption
import com.jupiter.filemanager.domain.model.HomeLayout
import com.jupiter.filemanager.domain.model.IconTheme
import com.jupiter.filemanager.domain.model.ThemeMode

/**
 * Immutable UI state for the Appearance screen. Mirrors the persisted
 * personalization preferences plus the [isPremiumUnlocked] gate flag used to
 * decide whether premium options are selectable or show an upsell affordance.
 */
data class AppearanceUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: AccentColor = AccentColor.JUPITER_BLUE,
    val darkVariant: DarkVariant = DarkVariant.STANDARD,
    val density: DensityOption = DensityOption.COMFORTABLE,
    val iconTheme: IconTheme = IconTheme.DEFAULT,
    val homeLayout: HomeLayout = HomeLayout(),
    val isPremiumUnlocked: Boolean = true,
)
```

### 6.3 New file — `feature/settings/appearance/AppearanceViewModel.kt`

```kotlin
package com.jupiter.filemanager.feature.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.AccentColor
import com.jupiter.filemanager.domain.model.DarkVariant
import com.jupiter.filemanager.domain.model.DensityOption
import com.jupiter.filemanager.domain.model.HomeLayout
import com.jupiter.filemanager.domain.model.HomeSection
import com.jupiter.filemanager.domain.model.IconTheme
import com.jupiter.filemanager.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Appearance screen: reads persisted personalization preferences and
 * the premium gate, and forwards mutations to [SettingsDataStore]. Premium
 * options are gated only here in the presentation layer (never in the data
 * layer) so a downgrade simply re-locks the picker while leaving any persisted
 * value intact and harmless.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    gate: PersonalizationGate,
) : ViewModel() {

    val uiState: StateFlow<AppearanceUiState> = combine(
        settings.themeConfig,
        settings.homeLayout,
        gate.isUnlocked,
    ) { config, homeLayout, unlocked ->
        AppearanceUiState(
            themeMode = config.themeMode,
            dynamicColor = config.dynamicColor,
            accent = config.accent,
            darkVariant = config.darkVariant,
            density = config.density,
            iconTheme = config.iconTheme,
            homeLayout = homeLayout,
            isPremiumUnlocked = unlocked,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AppearanceUiState(),
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setDynamicColor(value: Boolean) = viewModelScope.launch { settings.setDynamicColor(value) }

    /** Premium-gated; no-op when locked so a stale UI tap cannot bypass the gate. */
    fun setAccent(value: AccentColor) = viewModelScope.launch {
        if (value == AccentColor.JUPITER_BLUE || uiState.value.isPremiumUnlocked) {
            settings.setAccentColor(value)
        }
    }

    fun setDarkVariant(value: DarkVariant) = viewModelScope.launch {
        if (value == DarkVariant.STANDARD || uiState.value.isPremiumUnlocked) {
            settings.setDarkVariant(value)
        }
    }

    fun setDensity(value: DensityOption) = viewModelScope.launch {
        if (value == DensityOption.COMFORTABLE || uiState.value.isPremiumUnlocked) {
            settings.setDensityOption(value)
        }
    }

    fun setIconTheme(value: IconTheme) = viewModelScope.launch {
        if (value == IconTheme.DEFAULT || uiState.value.isPremiumUnlocked) {
            settings.setIconTheme(value)
        }
    }

    /** Toggles a Home section's visibility (custom home is premium-gated). */
    fun toggleHomeSection(section: HomeSection) = viewModelScope.launch {
        if (!uiState.value.isPremiumUnlocked) return@launch
        val current = uiState.value.homeLayout
        val hidden = current.hidden.toMutableSet().apply {
            if (!add(section)) remove(section)
        }
        settings.setHomeLayout(current.copy(hidden = hidden))
    }

    /** Moves a section up/down in display order (premium-gated). */
    fun moveHomeSection(section: HomeSection, up: Boolean) = viewModelScope.launch {
        if (!uiState.value.isPremiumUnlocked) return@launch
        val order = uiState.value.homeLayout.order.toMutableList()
        val index = order.indexOf(section)
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target < 0 || target >= order.size) return@launch
        order[index] = order[target].also { order[target] = order[index] }
        settings.setHomeLayout(uiState.value.homeLayout.copy(order = order))
    }
}
```

### 6.4 New file — `feature/settings/appearance/AppearanceScreen.kt`

```kotlin
package com.jupiter.filemanager.feature.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.domain.model.AccentColor
import com.jupiter.filemanager.domain.model.DarkVariant
import com.jupiter.filemanager.domain.model.DensityOption
import com.jupiter.filemanager.domain.model.HomeSection
import com.jupiter.filemanager.domain.model.IconTheme

/**
 * Personalization & Theming screen: accent picker, Material You toggle,
 * light/dark/AMOLED variant, density, icon theme and a Custom Home reorder list.
 * All persistence is delegated to [AppearanceViewModel]; this composable holds
 * no IO. Premium options surface a lock badge when [AppearanceUiState.isPremiumUnlocked]
 * is false.
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    onUpgrade: () -> Unit = {},
) {
    val viewModel: AppearanceViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personalization") },
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
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader("Material You")
            RowSwitch(
                title = "Dynamic color",
                subtitle = "Match your wallpaper (Android 12+)",
                checked = state.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Accent color")
            if (!state.isPremiumUnlocked) UpgradeHint(onUpgrade)
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(AccentColor.entries) { accent ->
                    AccentSwatch(
                        accent = accent,
                        selected = accent == state.accent,
                        locked = !state.isPremiumUnlocked && accent != AccentColor.JUPITER_BLUE,
                        dimmedByDynamic = state.dynamicColor,
                        onClick = {
                            if (!state.isPremiumUnlocked && accent != AccentColor.JUPITER_BLUE) {
                                onUpgrade()
                            } else {
                                viewModel.setAccent(accent)
                            }
                        },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Dark style")
            ChipRow(
                options = DarkVariant.entries,
                selected = state.darkVariant,
                label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                locked = { it != DarkVariant.STANDARD && !state.isPremiumUnlocked },
                onSelect = { v ->
                    if (v != DarkVariant.STANDARD && !state.isPremiumUnlocked) onUpgrade()
                    else viewModel.setDarkVariant(v)
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Density")
            ChipRow(
                options = DensityOption.entries,
                selected = state.density,
                label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                locked = { it != DensityOption.COMFORTABLE && !state.isPremiumUnlocked },
                onSelect = { v ->
                    if (v != DensityOption.COMFORTABLE && !state.isPremiumUnlocked) onUpgrade()
                    else viewModel.setDensity(v)
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Icon theme")
            ChipRow(
                options = IconTheme.entries,
                selected = state.iconTheme,
                label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                locked = { it != IconTheme.DEFAULT && !state.isPremiumUnlocked },
                onSelect = { v ->
                    if (v != IconTheme.DEFAULT && !state.isPremiumUnlocked) onUpgrade()
                    else viewModel.setIconTheme(v)
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Custom Home")
            if (!state.isPremiumUnlocked) UpgradeHint(onUpgrade)
            state.homeLayout.order.forEachIndexed { index, section ->
                HomeSectionRow(
                    section = section,
                    visible = section !in state.homeLayout.hidden,
                    canMoveUp = state.isPremiumUnlocked && index > 0,
                    canMoveDown = state.isPremiumUnlocked && index < state.homeLayout.order.lastIndex,
                    enabled = state.isPremiumUnlocked,
                    onToggle = { viewModel.toggleHomeSection(section) },
                    onUp = { viewModel.moveHomeSection(section, up = true) },
                    onDown = { viewModel.moveHomeSection(section, up = false) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun UpgradeHint(onUpgrade: () -> Unit) {
    AssistChip(
        onClick = onUpgrade,
        label = { Text("Unlock with Pro") },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, Modifier.size(18.dp)) },
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun RowSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AccentSwatch(
    accent: AccentColor,
    selected: Boolean,
    locked: Boolean,
    dimmedByDynamic: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(accent.light, CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            locked -> Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color.White, modifier = Modifier.size(18.dp))
            selected && !dimmedByDynamic -> Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    locked: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                leadingIcon = if (locked(option)) {
                    { Icon(Icons.Filled.Lock, contentDescription = null, Modifier.size(16.dp)) }
                } else null,
            )
        }
    }
}

@Composable
private fun HomeSectionRow(
    section: HomeSection,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.name.split("_").joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        IconButton(onClick = onUp, enabled = canMoveUp) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
        }
        IconButton(onClick = onDown, enabled = canMoveDown) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
        }
        Switch(checked = visible, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}
```

> **`LazyRow` `items` import:** add `import androidx.compose.foundation.lazy.items` to `AppearanceScreen.kt`.

### 6.5 Edit — `ui/navigation/Destinations.kt`

Add under the "Startup & shell" or near `Settings`:

```kotlin
    data object Appearance : Destination("appearance")
```

### 6.6 Edit — `ui/navigation/JupiterNavHost.kt`

Add the import:

```kotlin
import com.jupiter.filemanager.feature.settings.appearance.AppearanceScreen
```

Add the composable (e.g. right after the `Settings` composable block):

```kotlin
        composable(route = Destination.Appearance.route) {
            AppearanceScreen(
                onBack = { navController.popBackStack() },
                // When Initiative #1 lands, route to Destination.Paywall here.
                onUpgrade = { navController.navigate(Destination.Settings.route) },
            )
        }
```

### 6.7 Edit — `feature/settings/SettingsScreen.kt`

The `SettingsScreen` currently takes only `onBack`. Add an `onOpenAppearance` lambda **with a default no-op** so the existing nav call site (`SettingsScreen(onBack = …)`) still compiles, and wire a navigation row in the Appearance section. Replace the `ThemeModeSelector(...)` usage in the "Appearance" section with the theme selector **plus** a "More personalization" row.

Change the signature:

```kotlin
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit = {},
) {
```

Add after the `ThemeModeSelector(...)` call inside the Appearance section:

```kotlin
            SettingsNavigationRow(
                icon = Icons.Filled.Palette,
                title = "Personalization",
                subtitle = "Accent color, AMOLED, density, icon theme, home layout",
                onClick = onOpenAppearance,
            )
```

Add the import `import androidx.compose.material.icons.filled.Palette`. Then in `JupiterNavHost.kt` update the existing `Settings` composable to pass the new lambda:

```kotlin
        composable(route = Destination.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAppearance = { navController.navigate(Destination.Appearance.route) },
            )
        }
```

### 6.8 (Optional) `feature/home/HomeScreen.kt` — honor Custom Home

To make Custom Home take visible effect, have `HomeViewModel` also expose `settings.homeLayout` and have `HomeScreen` render its sections in `homeLayout.visibleInOrder`. This is additive: if you skip it, the layout preference still persists and the rest of the feature works; the Home simply uses its current fixed order. Keep the default (canonical order, nothing hidden) so behavior is unchanged until the user customizes.

---

## 7. Phase 4 — Configuration

- **Env / keys:** none.
- **External service setup:** none. (Material You dynamic color is a platform API: `dynamicLightColorScheme`/`dynamicDarkColorScheme`, Android 12 / API 31+; already used by the existing `Theme.kt`.)
- **ProGuard / R8 (`app/proguard-rules.pro`):** the new code uses no reflection beyond enum `name`/`valueOf`, which R8 keeps for enums by default. Add a defensive keep so persisted enum names survive aggressive optimization:

```proguard
# Personalization & Theming (Initiative #9): preserve enum names persisted in DataStore.
-keepclassmembers enum com.jupiter.filemanager.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
```

No Compose/Hilt keep rules are needed beyond what the project already ships.

---

## 8. Phase 5 — Testing

### 8.1 Manual smoke-test script

1. Build & install: `./gradlew :app:installDebug`.
2. Open the app → Settings → **Personalization**. Confirm the screen opens with a back arrow.
3. On a **pre-Android-12** device/emulator (API 26–30): the Dynamic color toggle has no visual effect; pick **Indigo** accent → primary buttons/top bars across the app turn indigo immediately on returning to other screens.
4. On **Android 12+**: toggle **Dynamic color** off → accent swatches become authoritative; toggle on → wallpaper colors drive the scheme (accent swatch check marks dim to indicate dynamic is winning).
5. Set theme mode **Dark** (Settings → Appearance) then **AMOLED** dark style → background goes pure black `#000000`. Toggle back to **Standard** → near-black `#111318`.
6. Change **Density** to **Compact** → list rows/text visibly tighten app-wide. Back to **Comfortable** → restored.
7. Change **Icon theme** to **Rounded** (once `LocalIconTheme` consumers exist) → folder glyphs swap; **Default** restores.
8. Under **Custom Home**, hide **Bookmarks** and move **Recents** above **Quick Access** → return to Home → order/visibility reflect (if Phase 6.8 wired).
9. **Kill & relaunch** the app → every choice persists (DataStore).
10. **Regression:** verify Settings theme-mode radios still work, AI key field still masks/persists, and file browsing/search/preview are unaffected.
11. **Gate test:** temporarily bind a `PersonalizationGate` returning `flowOf(false)` → premium swatches/chips show lock badges and tapping routes to upgrade; `JUPITER_BLUE`/`STANDARD`/`COMFORTABLE`/`DEFAULT` remain freely selectable.

### 8.2 Recommended unit tests (`app/src/test/...`)

- `SettingsDataStoreAppearanceTest` (instrumented or Robolectric): write each new preference, read back the flow, assert round-trip; write a **garbage** value into `accent_color` and assert the flow falls back to `JUPITER_BLUE`.
- `AppearanceViewModelTest` (`kotlinx-coroutines-test`): with a fake `SettingsDataStore` and a gate returning `false`, assert `setAccent(INDIGO)` is a **no-op** (premium locked) but `setAccent(JUPITER_BLUE)` persists; with gate `true`, both persist. Assert `moveHomeSection` clamps at list bounds and `toggleHomeSection` is idempotent on double-toggle.
- `HomeLayoutTest` (pure JVM): `visibleInOrder` filters hidden and preserves order; `decodeSections` (exposed via an internal helper or tested through the flow) drops unknown tokens and appends new sections.
- `ThemeColorTest` (pure JVM): `accentDarkColors(JUPITER_BLUE, STANDARD) === JupiterDarkColors`; `accentDarkColors(any, AMOLED).background == Color(0xFF000000)`; `accentLightColors(JUPITER_BLUE) === JupiterLightColors`.

### 8.3 Instrumented (optional)

- `AppearanceScreenTest` (Compose UI test): assert the lock badge appears on a non-default accent when the gate is locked, and that selecting an unlocked accent updates the selected-border state.

CI gate: `./gradlew assembleDebug testDebugUnitTest` must pass.

---

## 9. Error handling & edge cases

1. **Malformed/legacy persisted enum value** (e.g. `accent_color = "NEON"` from a future build): `enumValueOrNull` returns null → flow falls back to the default (`JUPITER_BLUE`), no crash. Same pattern as the existing `themeMode`/`sortOption` handling.
2. **DataStore disk read failure (`IOException`)**: the existing `.safe()` extension emits `emptyPreferences()`, so all flows yield defaults and `ThemeConfig()` is used — the app still renders with the brand theme.
3. **Dynamic color requested on API < 31**: `Build.VERSION.SDK_INT >= S` guard in `JupiterTheme` means the accent-seeded scheme is used instead; the toggle is harmless on old devices (it persists but has no rendering effect, matching `Theme.kt`'s existing behavior).
4. **AMOLED on a Material3 build lacking `surfaceContainer*` roles**: documented fallback — drop those named copy args; `background`/`surface`/`surfaceVariant` still deliver pure black. Verified present in `composeBom 2024.12.01`.
5. **Premium downgrade after the user set a premium accent/AMOLED/compact density**: the persisted value remains but the gate flag flips; `AppearanceViewModel` setters short-circuit non-default writes and the UI shows lock badges. The *applied* theme still honors the persisted premium value (no jarring forced reset) unless product wants hard enforcement — if so, add a `coerceToFree()` in the data-layer read flow (left out by default to avoid surprising users; document the choice).
6. **Home layout with a section removed in a future version**: `decodeSections` drops unknown tokens; missing-known sections are appended in canonical order, so `homeLayout.order` always contains every current `HomeSection` exactly once — the reorder UI can never desync.
7. **Density extreme + large system font**: density scales `fontScale` (not layout `density`), so even at `COMPACT × largest accessibility font` text only shrinks relative to the system size; touch targets and hit-testing are unchanged. No clipping of icons (their sizes are `dp`, unaffected by `fontScale`).
8. **Rapid toggling / write coalescing**: each setter is a single atomic `dataStore.edit`; concurrent taps are serialized by DataStore's single-writer actor — last write wins, no corruption.

---

## 10. Integration with other initiatives

- **#1 Pro Monetization (dependency, soft):** #9 ships a `PersonalizationGate` with a fail-open default so it builds standalone. When #1 lands, replace the `@Binds` to map `proRepository.tier == AppTier.PRO` → `isUnlocked`, and point `AppearanceScreen.onUpgrade` at `Destination.Paywall`. This makes personalization a clean Pro upsell (one of #1's headline perks).
- **#3 i18n / Localization:** the new user-facing strings in `AppearanceScreen` ("Personalization", "Dynamic color", "Accent color", "Dark style", "Density", "Icon theme", "Custom Home", "Unlock with Pro") should be moved to `strings.xml` and localized; until then they are hardcoded English like the rest of `SettingsScreen`.
- **#5 Widgets / Shortcuts / Tiles:** widgets should read `accentColor`/`darkVariant` from `SettingsDataStore` so home-screen widgets match the in-app theme.
- **#6 Privacy Analytics:** emit non-PII events on personalization changes (e.g. `accent_changed`, `amoled_enabled`, `density_changed`, `home_customized`) to measure the retention thesis — strictly through #6's analytics seam, never raw.
- **#7 Activation / Retention:** surface a one-time "Make it yours" nudge linking to `Destination.Appearance` during onboarding/activation.
- No dependency on #2 (Cloud OAuth), #4 (AI Pro Suite), #8, or #10.

---

## 11. Rollback plan

The initiative is purely additive; rollback is removal:

1. **Revert the edited files** to their pre-#9 state: `Theme.kt`, `Color.kt`, `MainActivity.kt`, `MainViewModel.kt`, `SettingsDataStore.kt`, `Destinations.kt`, `JupiterNavHost.kt`, `SettingsScreen.kt`. Because every new parameter was defaulted, reverting `Theme.kt`/`MainActivity.kt` alone restores the old single-`themeMode` behavior even if the data-layer keys remain.
2. **Delete the new files:** `domain/model/Appearance.kt`, the entire `feature/settings/appearance/` package (`AppearanceScreen.kt`, `AppearanceViewModel.kt`, `AppearanceUiState.kt`, `PersonalizationGate.kt`).
3. **Orphaned DataStore keys are harmless** — they simply go unread. No migration is needed; the preference file is unaffected, and `themeMode`/`showHidden`/etc. continue to work. If desired, a one-line `dataStore.edit { it.remove(...) }` migration can purge them, but it is not required.
4. **ProGuard:** the added keep block is inert if the enums are gone; safe to leave or remove.
5. **Feature-flag alternative (no code removal):** bind `PersonalizationGate.isUnlocked = flowOf(false)` and hide the Settings "Personalization" row — premium options vanish while the default brand theme is fully preserved.

No data loss, no schema migration, no user-visible regression on rollback.

---

## 12. Definition of done

- [ ] `domain/model/Appearance.kt` added with `AccentColor`, `DarkVariant`, `DensityOption`, `IconTheme`, `HomeLayout`, `HomeSection`, `ThemeConfig`.
- [ ] `SettingsDataStore` extended with the 7 new keys, read flows, the combined `themeConfig`/`homeLayout` flows, and all setters — reusing `.safe()` and `enumValueOrNull`.
- [ ] `JupiterTheme` extended with defaulted `accent`/`darkVariant`/`density`/`iconTheme` params; `LocalIconTheme` provided; density applied via `LocalDensity` `fontScale`.
- [ ] `Color.kt` provides `accentLightColors`, `accentDarkColors`, AMOLED override, and `toAmoledPublic`; `JUPITER_BLUE`+`STANDARD` returns the original schemes by identity.
- [ ] `MainViewModel.themeConfig` added (and `themeMode` kept); `MainActivity` passes the full config into `JupiterTheme`.
- [ ] `feature/settings/appearance/` package added: `AppearanceUiState`, `@HiltViewModel AppearanceViewModel`, `AppearanceScreen`, `PersonalizationGate` (+ fail-open Hilt binding).
- [ ] `Destination.Appearance` added; `JupiterNavHost` wires it; `SettingsScreen` gets a defaulted `onOpenAppearance` row routed from the nav host.
- [ ] Premium gating enforced **only** in the presentation layer; locked options show a lock badge and route to `onUpgrade`; free defaults always selectable.
- [ ] **No regression:** Settings theme-mode radios (`ThemeModeSelector`) still switch light/dark/system; the AI API-key field still masks and persists; file browser / search / preview / vault unaffected.
- [ ] **No regression:** default appearance (fresh install, no prefs) is pixel-identical to pre-#9 (`JupiterLightColors`/`JupiterDarkColors`, comfortable density, default icons, dynamic color on by default).
- [ ] All personalization choices persist across process death (verified by relaunch).
- [ ] Unit tests added (DataStore round-trip + garbage fallback; `AppearanceViewModel` gate behavior; `HomeLayout`/theme color helpers) and pass.
- [ ] ProGuard keep rule for `domain.model` enums added.
- [ ] **CI green:** `./gradlew assembleDebug testDebugUnitTest` passes.
