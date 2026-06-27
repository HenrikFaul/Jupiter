package com.jupiter.filemanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.jupiter.filemanager.domain.model.ThemeMode

/**
 * Root Material 3 theme for Jupiter.
 *
 * @param themeMode resolves whether the dark scheme is used: [ThemeMode.LIGHT] forces light,
 *   [ThemeMode.DARK] forces dark, and [ThemeMode.SYSTEM] follows the device setting.
 * @param dynamicColor when true and running on Android 12 (S) or newer, uses the wallpaper-based
 *   dynamic color scheme; otherwise the NEXUS vivid-blue brand schemes are applied.
 */
@Composable
fun JupiterTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
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
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> JupiterDarkColors
        else -> JupiterLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JupiterTypography,
        content = content,
    )
}
