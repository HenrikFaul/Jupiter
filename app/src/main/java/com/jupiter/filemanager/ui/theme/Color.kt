package com.jupiter.filemanager.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Jupiter palette — dark, technical and calm.
//
// The supplied product references use a near-black blue graphite canvas with a
// luminous teal action color. These palette roles are intentionally fixed as the
// branded default; users can still opt into dynamic color or another accent from
// Settings. All component code consumes Material semantic colors rather than the
// raw values below.
// ---------------------------------------------------------------------------

// --- Brand seeds ---
private val TealPrimaryLight = Color(0xFF006D65)
private val TealPrimaryDark = Color(0xFF3DE5D5)
private val SlateSecondaryLight = Color(0xFF465B74)
private val SlateSecondaryDark = Color(0xFFB9C8D8)

// --- Light scheme colors ---
private val md_light_primary = TealPrimaryLight
private val md_light_onPrimary = Color(0xFFFFFFFF)
private val md_light_primaryContainer = Color(0xFF9AF7EC)
private val md_light_onPrimaryContainer = Color(0xFF00201D)

private val md_light_secondary = SlateSecondaryLight
private val md_light_onSecondary = Color(0xFFFFFFFF)
private val md_light_secondaryContainer = Color(0xFFD9E5F2)
private val md_light_onSecondaryContainer = Color(0xFF031D31)

private val md_light_tertiary = Color(0xFF6B4FA0)
private val md_light_onTertiary = Color(0xFFFFFFFF)
private val md_light_tertiaryContainer = Color(0xFFEBDDFF)
private val md_light_onTertiaryContainer = Color(0xFF25113E)

private val md_light_error = Color(0xFFBA1A1A)
private val md_light_onError = Color(0xFFFFFFFF)
private val md_light_errorContainer = Color(0xFFFFDAD6)
private val md_light_onErrorContainer = Color(0xFF410002)

private val md_light_background = Color(0xFFF6FAFB)
private val md_light_onBackground = Color(0xFF111D22)
private val md_light_surface = Color(0xFFF6FAFB)
private val md_light_onSurface = Color(0xFF111D22)
private val md_light_surfaceVariant = Color(0xFFDDE8EA)
private val md_light_onSurfaceVariant = Color(0xFF3E4A4E)
private val md_light_outline = Color(0xFF6E7A7D)
private val md_light_outlineVariant = Color(0xFFBECCCE)
private val md_light_scrim = Color(0xFF000000)
private val md_light_inverseSurface = Color(0xFF1A272C)
private val md_light_inverseOnSurface = Color(0xFFEAF2F3)
private val md_light_inversePrimary = TealPrimaryDark
private val md_light_surfaceTint = TealPrimaryLight

// --- Dark scheme colors ---
private val md_dark_primary = TealPrimaryDark
private val md_dark_onPrimary = Color(0xFF003732)
private val md_dark_primaryContainer = Color(0xFF005149)
private val md_dark_onPrimaryContainer = Color(0xFF9BFFF2)

private val md_dark_secondary = SlateSecondaryDark
private val md_dark_onSecondary = Color(0xFF233542)
private val md_dark_secondaryContainer = Color(0xFF344856)
private val md_dark_onSecondaryContainer = Color(0xFFD7E7F5)

private val md_dark_tertiary = Color(0xFFCAB2FF)
private val md_dark_onTertiary = Color(0xFF382059)
private val md_dark_tertiaryContainer = Color(0xFF513B75)
private val md_dark_onTertiaryContainer = Color(0xFFEBDDFF)

private val md_dark_error = Color(0xFFFFB4AB)
private val md_dark_onError = Color(0xFF690005)
private val md_dark_errorContainer = Color(0xFF93000A)
private val md_dark_onErrorContainer = Color(0xFFFFDAD6)

private val md_dark_background = Color(0xFF071019)
private val md_dark_onBackground = Color(0xFFE7F0F3)
private val md_dark_surface = Color(0xFF0B151F)
private val md_dark_onSurface = Color(0xFFE7F0F3)
private val md_dark_surfaceVariant = Color(0xFF1B2834)
private val md_dark_onSurfaceVariant = Color(0xFFB1C0CB)
private val md_dark_outline = Color(0xFF7B8B96)
private val md_dark_outlineVariant = Color(0xFF2B3A46)
private val md_dark_scrim = Color(0xFF000000)
private val md_dark_inverseSurface = Color(0xFFE7F0F3)
private val md_dark_inverseOnSurface = Color(0xFF16232D)
private val md_dark_inversePrimary = TealPrimaryLight
private val md_dark_surfaceTint = TealPrimaryDark

/** Light Material 3 color scheme for Jupiter. */
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

/** Dark Material 3 color scheme for Jupiter. */
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
 * The first entry ("Jupiter Teal") matches the brand primary so the default
 * selection preserves the current look. These are vivid, high-chroma seeds;
 * the theme derives a full tonal [ColorScheme] from the chosen seed.
 */
val AccentPalette: List<AccentColor> = listOf(
    accent("Jupiter Teal", 0xFF006D65),
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
