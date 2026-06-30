package com.jupiter.filemanager.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.domain.model.ThemeMode
import com.jupiter.filemanager.ui.navigation.Destination
import com.jupiter.filemanager.ui.theme.AccentColor
import com.jupiter.filemanager.ui.theme.AccentPalette
import androidx.compose.foundation.clickable as clickableModifier
import androidx.compose.foundation.selection.selectable as selectableModifier

/**
 * Settings screen exposing the user-facing application preferences.
 *
 * Renders a theme-mode selector (System / Light / Dark) plus toggles for
 * showing hidden files, enabling the dual-pane browser and the AI assistant,
 * a masked Claude API-key field, a Personalization section (accent color,
 * AMOLED black, dynamic color), a Privacy opt-in for anonymous analytics, a
 * Jupiter Pro entry point, followed by a static About section. All persistence
 * is delegated to [SettingsViewModel]; this composable contains no file or
 * preference IO.
 *
 * @param onOpenRoute navigates to a named route (e.g. the Pro paywall).
 * @param onBack invoked when the user dismisses the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenRoute: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader(title = "Appearance")
            ThemeModeSelector(
                selected = uiState.themeMode,
                onSelected = viewModel::setThemeMode,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Personalization")
            AccentColorPicker(
                selectedArgb = uiState.accentColorArgb,
                onAccentSelected = viewModel::setAccentColorArgb,
            )
            SettingsSwitchRow(
                icon = Icons.Filled.DarkMode,
                title = "AMOLED black",
                subtitle = "Use pure-black backgrounds in dark theme",
                checked = uiState.amoledBlack,
                onCheckedChange = viewModel::setAmoledBlack,
            )
            SettingsSwitchRow(
                icon = Icons.Filled.Palette,
                title = "Dynamic color",
                subtitle = "Match your wallpaper colors on Android 12+",
                checked = uiState.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Browsing")
            SettingsSwitchRow(
                icon = Icons.Filled.Visibility,
                title = "Show hidden files",
                subtitle = "Display files and folders that start with a dot",
                checked = uiState.showHidden,
                onCheckedChange = viewModel::setShowHidden,
            )
            SettingsSwitchRow(
                icon = Icons.Filled.ViewColumn,
                title = "Dual-pane layout",
                subtitle = "Show two file panes side by side on wide screens",
                checked = uiState.dualPaneEnabled,
                onCheckedChange = viewModel::setDualPane,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Assistant")
            SettingsSwitchRow(
                icon = Icons.Filled.SmartToy,
                title = "Enable AI assistant",
                subtitle = "Smart suggestions for naming, search and cleanup",
                checked = uiState.aiEnabled,
                onCheckedChange = viewModel::setAiEnabled,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "AI assistant")
            AiApiKeyField(
                apiKey = uiState.aiApiKey,
                onApiKeyChange = viewModel::setAiApiKey,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Privacy")
            SettingsSwitchRow(
                icon = Icons.Filled.Insights,
                title = "Help improve Jupiter",
                subtitle = "Share anonymous usage data (opt-in)",
                checked = uiState.analyticsOptIn,
                onCheckedChange = viewModel::setAnalyticsOptIn,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Jupiter Pro")
            SettingsNavigationRow(
                icon = Icons.Filled.Star,
                title = "Jupiter Pro",
                subtitle = "Support development and explore Pro benefits",
                onClick = { onOpenRoute(Destination.Paywall.route) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Storage")
            SettingsNavigationRow(
                icon = Icons.Filled.Storage,
                title = "Storage analysis",
                subtitle = "Review usage and reclaim space",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Transfers")
            SettingsNavigationRow(
                icon = Icons.Filled.SwapHoriz,
                title = "Transfer history",
                subtitle = "View recent copy, move and send tasks",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Security")
            SettingsNavigationRow(
                icon = Icons.Filled.Lock,
                title = "App lock",
                subtitle = "Require authentication to open Jupiter",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Cloud")
            SettingsNavigationRow(
                icon = Icons.Filled.Cloud,
                title = "Connected accounts",
                subtitle = "Manage linked cloud storage providers",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Automation")
            SettingsNavigationRow(
                icon = Icons.Filled.PlayArrow,
                title = "Rules",
                subtitle = "Automate sorting and cleanup tasks",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Advanced")
            SettingsNavigationRow(
                icon = Icons.Filled.DeleteSweep,
                title = "Cache",
                subtitle = "Clear thumbnails and temporary data",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "About")
            AboutSection()
        }
    }
}

/**
 * Small caption-style header that introduces a group of related settings.
 */
@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 8.dp,
        ),
    )
}

/**
 * Radio-style selector for the three [ThemeMode] options.
 */
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        ThemeMode.entries.forEach { mode ->
            ThemeModeRow(
                mode = mode,
                selected = mode == selected,
                onSelect = { onSelected(mode) },
            )
        }
    }
}

/**
 * A single selectable theme-mode row.
 */
@Composable
private fun ThemeModeRow(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableModifier(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = iconForThemeMode(mode),
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
                text = labelForThemeMode(mode),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = descriptionForThemeMode(mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}

/**
 * Horizontal row of selectable accent-color swatches plus a "default" option.
 *
 * The "default" swatch (argb 0L) keeps the dynamic/brand color so the existing
 * look is preserved unless the user explicitly picks an accent. The currently
 * selected swatch is marked with a check.
 */
@Composable
private fun AccentColorPicker(
    selectedArgb: Long,
    onAccentSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Colorize,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Accent color",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentSwatch(
                color = MaterialTheme.colorScheme.primary,
                selected = selectedArgb == 0L,
                contentDescription = "Default accent",
                onClick = { onAccentSelected(0L) },
            )
            AccentPalette.forEach { accent: AccentColor ->
                AccentSwatch(
                    color = accent.color,
                    selected = selectedArgb == accent.argb,
                    contentDescription = accent.name,
                    onClick = { onAccentSelected(accent.argb) },
                )
            }
        }
    }
}

/**
 * A single circular accent swatch. Shows a check overlay when [selected].
 */
@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(40.dp)
            .clickableModifier(onClickLabel = contentDescription) { onClick() },
        shape = CircleShape,
        color = color,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        if (selected) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * A labelled switch row used for boolean preferences. The whole row is
 * clickable and toggles the switch.
 */
@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableModifier { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
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
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * A masked text field for the Claude API key plus an explanatory note.
 *
 * The field keeps a local draft so typing stays responsive while the persisted
 * value is written through [onApiKeyChange]. The draft is re-seeded from
 * [apiKey] whenever the persisted value changes (e.g. on first load), and the
 * contents are obscured by default with a show/hide toggle. No key material is
 * logged or transmitted here; persistence is handled by the view model.
 */
@Composable
private fun AiApiKeyField(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(apiKey) }
    var revealed by rememberSaveable { mutableStateOf(false) }

    // Re-seed the local draft when the persisted key changes (initial load or
    // external update) without clobbering in-progress edits to the same value.
    LaunchedEffect(apiKey) {
        if (apiKey != draft) {
            draft = apiKey
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { value ->
                draft = value
                onApiKeyChange(value)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text = "Claude API key") },
            placeholder = { Text(text = "sk-ant-…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        imageVector = if (revealed) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (revealed) {
                            "Hide API key"
                        } else {
                            "Show API key"
                        },
                    )
                }
            },
            visualTransformation = if (revealed) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
            ),
        )
        Text(
            text = "Your key is stored on-device only and never leaves your " +
                "phone except to call Claude. It enables Claude-powered search, " +
                "smart naming and automation rules.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A labelled row that navigates to a sub-screen. Visually mirrors
 * [SettingsSwitchRow] but uses a trailing chevron instead of a switch.
 * The [onClick] defaults to a no-op so sections can be surfaced before
 * their backing destinations are wired up.
 */
@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableModifier { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
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
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * Static informational block describing the application.
 */
@Composable
private fun AboutSection(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Jupiter File Manager",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Version $APP_VERSION",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "A fast, private, native file manager for Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "All operations run on-device. No data leaves your phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
    }
}

private const val APP_VERSION = "1.0.0"

/** Human-readable label for a [ThemeMode]. */
private fun labelForThemeMode(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

/** Short description shown under each theme-mode option. */
private fun descriptionForThemeMode(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Follow the device theme"
    ThemeMode.LIGHT -> "Always use a light theme"
    ThemeMode.DARK -> "Always use a dark theme"
}

/** Leading icon for each theme-mode option. */
private fun iconForThemeMode(mode: ThemeMode): ImageVector = when (mode) {
    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
    ThemeMode.LIGHT -> Icons.Filled.LightMode
    ThemeMode.DARK -> Icons.Filled.Brightness4
}
