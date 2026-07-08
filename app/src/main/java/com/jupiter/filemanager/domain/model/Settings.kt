package com.jupiter.filemanager.domain.model

/**
 * User-selectable theme mode for the application.
 *
 * [SYSTEM] follows the device's light/dark setting, while [LIGHT] and [DARK]
 * force the respective appearance.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
