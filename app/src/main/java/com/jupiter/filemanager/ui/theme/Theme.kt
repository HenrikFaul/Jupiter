package com.jupiter.filemanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.jupiter.filemanager.domain.model.ThemeMode

/** Pure black used for AMOLED-black backgrounds/surfaces in dark mode. */
private val AmoledBlack = Color(0xFF000000)

/**
 * Root Material 3 theme for Jupiter.
 *
 * @param themeMode resolves whether the dark scheme is used: [ThemeMode.LIGHT] forces light,
 *   [ThemeMode.DARK] forces dark, and [ThemeMode.SYSTEM] follows the device setting.
 * @param dynamicColor when true and running on Android 12 (S) or newer, uses the wallpaper-based
 *   dynamic color scheme; otherwise the Jupiter midnight/teal brand schemes are applied. An explicit
 *   [accentColorArgb] override takes precedence over dynamic color.
 * @param accentColorArgb optional ARGB accent override (as [Long]). When non-zero, the scheme's
 *   primary/related roles are derived from this color. `0L` (default) means "no override" —
 *   the dynamic/brand default is used, preserving the current look.
 * @param amoledBlack when true and the resolved scheme is dark, forces background/surface roles
 *   to pure black (0xFF000000) for AMOLED displays. No effect in light mode.
 */
@Composable
fun JupiterTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accentColorArgb: Long = 0L,
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme: Boolean = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val baseScheme: ColorScheme = when {
        // An explicit accent override always wins over dynamic color.
        accentColorArgb != 0L -> {
            val base = if (darkTheme) JupiterDarkColors else JupiterLightColors
            base.withAccent(Color(accentColorArgb), darkTheme)
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> JupiterDarkColors
        else -> JupiterLightColors
    }

    val colorScheme: ColorScheme = if (amoledBlack && darkTheme) {
        baseScheme.toAmoledBlack()
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JupiterTypography,
        shapes = JupiterShapes,
        content = content,
    )
}

/**
 * Derives a [ColorScheme] whose primary roles come from the given [accent] seed, keeping the
 * remaining roles from the receiver scheme. This is a lightweight derivation (not a full HCT
 * tonal palette) that picks a readable on-primary based on luminance and a tinted container.
 */
private fun ColorScheme.withAccent(accent: Color, darkTheme: Boolean): ColorScheme {
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
    val container = if (darkTheme) accent.darken(0.55f) else accent.lighten(0.78f)
    val onContainer = if (darkTheme) accent.lighten(0.82f) else accent.darken(0.65f)
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        inversePrimary = if (darkTheme) accent.darken(0.4f) else accent.lighten(0.4f),
        surfaceTint = accent,
    )
}

/** Forces background/surface roles of a dark scheme to pure black for AMOLED displays. */
private fun ColorScheme.toAmoledBlack(): ColorScheme = copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledBlack,
    surfaceContainer = AmoledBlack,
    surfaceDim = AmoledBlack,
)

/** Blends [this] color toward black by [fraction] (0f = unchanged, 1f = black). */
private fun Color.darken(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = red * (1f - f),
        green = green * (1f - f),
        blue = blue * (1f - f),
        alpha = alpha,
    )
}

/** Blends [this] color toward white by [fraction] (0f = unchanged, 1f = white). */
private fun Color.lighten(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (1f - red) * f,
        green = green + (1f - green) * f,
        blue = blue + (1f - blue) * f,
        alpha = alpha,
    )
}
