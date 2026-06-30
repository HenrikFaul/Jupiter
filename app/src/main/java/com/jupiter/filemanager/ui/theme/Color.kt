package com.jupiter.filemanager.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Jupiter palette — "NEXUS" brand refresh.
// A vivid blue primary (blue 600 ≈ 0xFF2563EB) paired with a slate/indigo
// secondary accent, clean near-white light surfaces and near-black dark
// surfaces. Tuned for Material 3 tonal-contrast expectations.
// ---------------------------------------------------------------------------

// --- Brand seeds ---
private val BluePrimaryLight = Color(0xFF2563EB) // blue 600
private val BluePrimaryDark = Color(0xFFAAC7FF)
private val SlateSecondaryLight = Color(0xFF4F5B92) // indigo/slate accent
private val SlateSecondaryDark = Color(0xFFBBC4FF)

// --- Light scheme colors ---
private val md_light_primary = BluePrimaryLight
private val md_light_onPrimary = Color(0xFFFFFFFF)
private val md_light_primaryContainer = Color(0xFFDBE6FF)
private val md_light_onPrimaryContainer = Color(0xFF001A41)

private val md_light_secondary = SlateSecondaryLight
private val md_light_onSecondary = Color(0xFFFFFFFF)
private val md_light_secondaryContainer = Color(0xFFDFE0FF)
private val md_light_onSecondaryContainer = Color(0xFF09164B)

private val md_light_tertiary = Color(0xFF0E7490) // cyan/teal accent
private val md_light_onTertiary = Color(0xFFFFFFFF)
private val md_light_tertiaryContainer = Color(0xFFB5EBFF)
private val md_light_onTertiaryContainer = Color(0xFF001F2A)

private val md_light_error = Color(0xFFBA1A1A)
private val md_light_onError = Color(0xFFFFFFFF)
private val md_light_errorContainer = Color(0xFFFFDAD6)
private val md_light_onErrorContainer = Color(0xFF410002)

private val md_light_background = Color(0xFFFBFCFF)
private val md_light_onBackground = Color(0xFF1A1C1E)
private val md_light_surface = Color(0xFFFBFCFF)
private val md_light_onSurface = Color(0xFF1A1C1E)
private val md_light_surfaceVariant = Color(0xFFE0E2EC)
private val md_light_onSurfaceVariant = Color(0xFF43474E)
private val md_light_outline = Color(0xFF73777F)
private val md_light_outlineVariant = Color(0xFFC3C6CF)
private val md_light_scrim = Color(0xFF000000)
private val md_light_inverseSurface = Color(0xFF2F3033)
private val md_light_inverseOnSurface = Color(0xFFF1F0F4)
private val md_light_inversePrimary = BluePrimaryDark
private val md_light_surfaceTint = BluePrimaryLight

// --- Dark scheme colors ---
private val md_dark_primary = BluePrimaryDark
private val md_dark_onPrimary = Color(0xFF002E69)
private val md_dark_primaryContainer = Color(0xFF004494)
private val md_dark_onPrimaryContainer = Color(0xFFDBE6FF)

private val md_dark_secondary = SlateSecondaryDark
private val md_dark_onSecondary = Color(0xFF202C61)
private val md_dark_secondaryContainer = Color(0xFF374379)
private val md_dark_onSecondaryContainer = Color(0xFFDFE0FF)

private val md_dark_tertiary = Color(0xFF59D4F4)
private val md_dark_onTertiary = Color(0xFF003545)
private val md_dark_tertiaryContainer = Color(0xFF004D62)
private val md_dark_onTertiaryContainer = Color(0xFFB5EBFF)

private val md_dark_error = Color(0xFFFFB4AB)
private val md_dark_onError = Color(0xFF690005)
private val md_dark_errorContainer = Color(0xFF93000A)
private val md_dark_onErrorContainer = Color(0xFFFFDAD6)

private val md_dark_background = Color(0xFF111318)
private val md_dark_onBackground = Color(0xFFE2E2E6)
private val md_dark_surface = Color(0xFF111318)
private val md_dark_onSurface = Color(0xFFE2E2E6)
private val md_dark_surfaceVariant = Color(0xFF43474E)
private val md_dark_onSurfaceVariant = Color(0xFFC3C6CF)
private val md_dark_outline = Color(0xFF8D9199)
private val md_dark_outlineVariant = Color(0xFF43474E)
private val md_dark_scrim = Color(0xFF000000)
private val md_dark_inverseSurface = Color(0xFFE2E2E6)
private val md_dark_inverseOnSurface = Color(0xFF2F3033)
private val md_dark_inversePrimary = BluePrimaryLight
private val md_dark_surfaceTint = BluePrimaryDark

/** Light Material 3 color scheme for Jupiter (NEXUS vivid-blue brand). */
val JupiterLightColors: ColorScheme = lightColorScheme(
    primary = md_light_primary,
    onPrimary = md_light_onPrimary,
    primaryContainer = md_light_primaryContainer,
    onPrimaryContainer = md_light_onPrimaryContainer,
    secondary = md_light_secondary,
    onSecondary = md_light_onSecondary,
    secondaryContainer = md_light_secondaryContainer,
    onSecondaryContainer = md_light_onSecondaryContainer,
    tertiary = md_light_tertiary,
    onTertiary = md_light_onTertiary,
    tertiaryContainer = md_light_tertiaryContainer,
    onTertiaryContainer = md_light_onTertiaryContainer,
    error = md_light_error,
    onError = md_light_onError,
    errorContainer = md_light_errorContainer,
    onErrorContainer = md_light_onErrorContainer,
    background = md_light_background,
    onBackground = md_light_onBackground,
    surface = md_light_surface,
    onSurface = md_light_onSurface,
    surfaceVariant = md_light_surfaceVariant,
    onSurfaceVariant = md_light_onSurfaceVariant,
    outline = md_light_outline,
    outlineVariant = md_light_outlineVariant,
    scrim = md_light_scrim,
    inverseSurface = md_light_inverseSurface,
    inverseOnSurface = md_light_inverseOnSurface,
    inversePrimary = md_light_inversePrimary,
    surfaceTint = md_light_surfaceTint,
)

/** Dark Material 3 color scheme for Jupiter (NEXUS vivid-blue brand). */
val JupiterDarkColors: ColorScheme = darkColorScheme(
    primary = md_dark_primary,
    onPrimary = md_dark_onPrimary,
    primaryContainer = md_dark_primaryContainer,
    onPrimaryContainer = md_dark_onPrimaryContainer,
    secondary = md_dark_secondary,
    onSecondary = md_dark_onSecondary,
    secondaryContainer = md_dark_secondaryContainer,
    onSecondaryContainer = md_dark_onSecondaryContainer,
    tertiary = md_dark_tertiary,
    onTertiary = md_dark_onTertiary,
    tertiaryContainer = md_dark_tertiaryContainer,
    onTertiaryContainer = md_dark_onTertiaryContainer,
    error = md_dark_error,
    onError = md_dark_onError,
    errorContainer = md_dark_errorContainer,
    onErrorContainer = md_dark_onErrorContainer,
    background = md_dark_background,
    onBackground = md_dark_onBackground,
    surface = md_dark_surface,
    onSurface = md_dark_onSurface,
    surfaceVariant = md_dark_surfaceVariant,
    onSurfaceVariant = md_dark_onSurfaceVariant,
    outline = md_dark_outline,
    outlineVariant = md_dark_outlineVariant,
    scrim = md_dark_scrim,
    inverseSurface = md_dark_inverseSurface,
    inverseOnSurface = md_dark_inverseOnSurface,
    inversePrimary = md_dark_inversePrimary,
    surfaceTint = md_dark_surfaceTint,
)

// ---------------------------------------------------------------------------
// Accent palette (Personalization).
//
// A small curated set of selectable accent colors users can apply on top of
// the brand scheme. Each entry carries a stable ARGB [Long] suitable for
// persistence via SettingsDataStore.accentColorArgb. An argb of 0L means
// "no override" — use the dynamic/brand default (preserving current look).
// ---------------------------------------------------------------------------

/**
 * A selectable accent color option.
 *
 * @param name a short human-readable label for UI (e.g. settings color picker).
 * @param color the [Color] swatch to render.
 * @param argb the persisted ARGB value (as [Long]); pair with
 *   [SettingsDataStore.setAccentColorArgb]. Never 0L for real entries (0L is
 *   reserved to mean "use the dynamic/brand default").
 */
data class AccentColor(
    val name: String,
    val color: Color,
    val argb: Long,
)

private fun accent(name: String, argb: Long): AccentColor =
    AccentColor(name = name, color = Color(argb), argb = argb)

/**
 * Curated list of selectable accent colors for the Personalization settings.
 *
 * The first entry ("Jupiter Blue") matches the brand primary so the default
 * selection preserves the current look. These are vivid, high-chroma seeds;
 * the theme derives a full tonal [ColorScheme] from the chosen seed.
 */
val AccentPalette: List<AccentColor> = listOf(
    accent("Jupiter Blue", 0xFF2563EB),
    accent("Indigo", 0xFF4F46E5),
    accent("Violet", 0xFF7C3AED),
    accent("Magenta", 0xFFC026D3),
    accent("Rose", 0xFFE11D48),
    accent("Orange", 0xFFEA580C),
    accent("Amber", 0xFFD97706),
    accent("Emerald", 0xFF059669),
    accent("Teal", 0xFF0D9488),
    accent("Cyan", 0xFF0891B2),
)
