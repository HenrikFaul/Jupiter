package com.jupiter.filemanager.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable as clickableModifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable as selectableModifier
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable as toggleableModifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.domain.model.ThemeMode
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.navigation.Destination
import com.jupiter.filemanager.ui.theme.AccentColor
import com.jupiter.filemanager.ui.theme.AccentPalette

/**
 * The user-facing settings hub.
 *
 * The screen intentionally contains only preferences and destinations that are
 * backed by the application today. Each interaction continues to delegate to
 * [SettingsViewModel] or to the already-wired navigation route; this file only
 * provides the grouped-card presentation used across the Jupiter design system.
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            SettingsGroup(title = "Appearance") {
                ThemeModeSelector(
                    selected = uiState.themeMode,
                    onSelected = viewModel::setThemeMode,
                )
            }

            SettingsGroup(title = "Personalization") {
                AccentColorPicker(
                    selectedArgb = uiState.accentColorArgb,
                    onAccentSelected = viewModel::setAccentColorArgb,
                )
                SettingsGroupDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.DarkMode,
                    title = "AMOLED black",
                    subtitle = "Use pure-black backgrounds in dark theme",
                    checked = uiState.amoledBlack,
                    onCheckedChange = viewModel::setAmoledBlack,
                )
                SettingsGroupDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Palette,
                    title = "Dynamic color",
                    subtitle = "Match your wallpaper colors on Android 12+",
                    checked = uiState.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            SettingsGroup(title = "Browsing") {
                SettingsSwitchRow(
                    icon = Icons.Filled.Visibility,
                    title = "Show hidden files",
                    subtitle = "Display files and folders that start with a dot",
                    checked = uiState.showHidden,
                    onCheckedChange = viewModel::setShowHidden,
                )
                SettingsGroupDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.ViewColumn,
                    title = "Dual-pane layout",
                    subtitle = "Show two file panes side by side on wide screens",
                    checked = uiState.dualPaneEnabled,
                    onCheckedChange = viewModel::setDualPane,
                )
            }

            SettingsGroup(title = "AI assistant") {
                SettingsSwitchRow(
                    icon = Icons.Filled.SmartToy,
                    title = "Enable AI assistant",
                    subtitle = "Smart suggestions for naming, search and cleanup",
                    checked = uiState.aiEnabled,
                    onCheckedChange = viewModel::setAiEnabled,
                )
                SettingsGroupDivider()
                AiApiKeyField(
                    apiKey = uiState.aiApiKey,
                    onApiKeyChange = viewModel::setAiApiKey,
                )
            }

            SettingsGroup(title = "Privacy") {
                SettingsSwitchRow(
                    icon = Icons.Filled.Insights,
                    title = "Help improve Jupiter",
                    subtitle = "Share anonymous usage data (opt-in)",
                    checked = uiState.analyticsOptIn,
                    onCheckedChange = viewModel::setAnalyticsOptIn,
                )
                SettingsGroupDivider()
                SettingsNavigationRow(
                    icon = Icons.Filled.Shield,
                    title = "Your data & privacy",
                    subtitle = "See what Jupiter accesses, where your data lives, and how it's protected",
                    onClick = { onOpenRoute(Destination.DataTransparency.route) },
                )
            }

            SettingsGroup(title = "Jupiter Pro") {
                SettingsNavigationRow(
                    icon = Icons.Filled.Star,
                    title = "Jupiter Pro",
                    subtitle = "Support development and explore Pro benefits",
                    onClick = { onOpenRoute(Destination.Paywall.route) },
                )
            }

            SettingsGroup(title = "Storage") {
                SettingsNavigationRow(
                    icon = Icons.Filled.Storage,
                    title = "Storage analysis",
                    subtitle = "Review usage and reclaim space",
                    onClick = { onOpenRoute(Destination.StorageAnalytics.route) },
                )
            }

            SettingsGroup(title = "Recycle Bin") {
                SettingsNavigationRow(
                    icon = Icons.Filled.DeleteSweep,
                    title = "Open Recycle Bin",
                    subtitle = "Restore or permanently remove trashed files",
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onClick = { onOpenRoute(Destination.Trash.route) },
                )
                SettingsGroupDivider()
                TrashAutoDeleteSelector(
                    selectedDays = uiState.trashAutoDeleteDays,
                    onSelected = viewModel::setTrashAutoDeleteDays,
                )
            }

            SettingsGroup(title = "Indexing") {
                SettingsSwitchRow(
                    icon = Icons.Filled.Bolt,
                    title = "Index files for fast search",
                    subtitle = "Cache file metadata so search is instant instead of re-scanning storage every time",
                    checked = uiState.indexingEnabled,
                    onCheckedChange = viewModel::setIndexingEnabled,
                )
                SettingsGroupDivider()
                IndexStatusRow(
                    indexedCount = uiState.indexedCount,
                    indexing = uiState.indexing,
                    progressPercent = uiState.indexProgressPercent,
                    progressCurrent = uiState.indexProgressCurrent,
                    progressTotal = uiState.indexProgressTotal,
                    enabled = uiState.indexingEnabled,
                    onRebuild = viewModel::rebuildIndex,
                )
            }

            SettingsGroup(title = "Transfers") {
                SettingsNavigationRow(
                    icon = Icons.Filled.SwapHoriz,
                    title = "Transfer history",
                    subtitle = "View recent copy, move and send tasks",
                    onClick = { onOpenRoute(Destination.TransferCenter.route) },
                )
            }

            SettingsGroup(title = "Security") {
                SettingsNavigationRow(
                    icon = Icons.Filled.Lock,
                    title = "Vault security",
                    subtitle = "Unlock encrypted files with biometric or device credential",
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onClick = { onOpenRoute(Destination.Vault.route) },
                )
            }

            SettingsGroup(title = "Cloud") {
                SettingsNavigationRow(
                    icon = Icons.Filled.Cloud,
                    title = "Connected accounts",
                    subtitle = "Manage linked cloud storage providers",
                    onClick = { onOpenRoute(Destination.CloudHub.route) },
                )
            }

            SettingsGroup(title = "Automation") {
                SettingsNavigationRow(
                    icon = Icons.Filled.PlayArrow,
                    title = "Rules",
                    subtitle = "Automate sorting and cleanup tasks",
                    onClick = { onOpenRoute(Destination.Automation.route) },
                )
            }

            SettingsGroup(title = "About") {
                AboutSection()
            }
        }
    }
}

/** A titled, rounded settings card matching the supplied dark Jupiter references. */
@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    ) {
        SettingsSectionHeader(title = title)
        JupiterCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp),
            content = content,
        )
    }
}

/** A quiet group label; the card remains the visual focus. */
@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 12.dp, bottom = 8.dp),
    )
}

/** Divider alignment leaves a clear leading-icon column, like the reference design. */
@Composable
private fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 80.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
    )
}

/** Radio-style selector for the three [ThemeMode] options. */
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            ThemeModeRow(
                mode = mode,
                selected = mode == selected,
                onSelect = { onSelected(mode) },
            )
            if (index < ThemeMode.entries.lastIndex) {
                SettingsGroupDivider()
            }
        }
    }
}

/** One accessible theme choice with an unmistakable selected state. */
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
            .defaultMinSize(minHeight = 72.dp)
            .selectableModifier(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(
            icon = iconForThemeMode(mode),
            contentDescription = null,
            size = 48.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
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

/** The Recycle-Bin retention windows offered in Settings (label, days; 0 = never auto-delete). */
private val TRASH_AUTO_DELETE_OPTIONS: List<Pair<String, Int>> = listOf(
    "Never" to 0,
    "After 7 days" to 7,
    "After 15 days" to 15,
    "After 30 days" to 30,
    "After 60 days" to 60,
)

/**
 * Radio selector for the functional Recycle Bin retention policy. A selection is persisted through
 * the view model; it is not merely visual state.
 */
@Composable
private fun TrashAutoDeleteSelector(
    selectedDays: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterIconBadge(
                icon = Icons.Filled.DeleteSweep,
                tint = MaterialTheme.colorScheme.tertiary,
                contentDescription = null,
                size = 48.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Auto-delete trashed items",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Keep items for the selected retention period",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier
                .selectableGroup()
                .padding(start = 62.dp),
        ) {
            TRASH_AUTO_DELETE_OPTIONS.forEach { (label, days) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .selectableModifier(
                            selected = days == selectedDays,
                            onClick = { onSelected(days) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    RadioButton(selected = days == selectedDays, onClick = null)
                }
            }
        }
    }
}

/** Accent swatches for the persisted accent preference. */
@Composable
private fun AccentColorPicker(
    selectedArgb: Long,
    onAccentSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterIconBadge(
                icon = Icons.Filled.Colorize,
                contentDescription = null,
                size = 48.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Accent color",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = selectedAccentName(selectedArgb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 62.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentSwatch(
                color = MaterialTheme.colorScheme.primary,
                selected = selectedArgb == 0L,
                contentDescription = "Jupiter default accent",
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

/** A 48 dp accent selector with radio semantics and a clear selected mark. */
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
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .selectableModifier(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
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
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** A full-row, accessible switch preference with the shared badge treatment. */
@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .toggleableModifier(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(
            icon = icon,
            tint = iconTint,
            contentDescription = null,
            size = 48.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
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
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

/**
 * Live status for the persistent file index. Rebuild remains disabled while indexing is off or
 * already running, so this card cannot queue redundant work.
 */
@Composable
private fun IndexStatusRow(
    indexedCount: Int,
    indexing: Boolean,
    progressPercent: Int?,
    progressCurrent: Int,
    progressTotal: Int,
    enabled: Boolean,
    onRebuild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = when {
                        !enabled -> "Indexing is off"
                        indexing && progressPercent != null -> "Indexing files… $progressPercent%"
                        indexing -> "Indexing files…"
                        else -> "$indexedCount files indexed"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                when {
                    indexing && progressTotal > 0 -> Text(
                        text = "$progressCurrent / $progressTotal files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    enabled && !indexing -> Text(
                        text = "Rebuild to refresh cached metadata",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (indexing && progressPercent == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            FilledTonalButton(
                onClick = onRebuild,
                enabled = enabled && !indexing,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Rebuild")
            }
        }
        if (indexing) {
            if (progressPercent != null) {
                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * A masked text field for the real Claude API key. The draft is deliberately
 * local so typing remains responsive while every change is persisted by the
 * view model.
 */
@Composable
private fun AiApiKeyField(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(apiKey) }
    var revealed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(apiKey) {
        if (apiKey != draft) {
            draft = apiKey
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
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
            text = "Your key is stored on-device only and never leaves your phone except to call " +
                "Claude. It enables Claude-powered search, smart naming and automation rules.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A functional, full-row destination with a chevron affordance. */
@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .clickableModifier(
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(
            icon = icon,
            tint = iconTint,
            contentDescription = null,
            size = 48.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
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
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Static information about Jupiter; no faux navigation/action is attached. */
@Composable
private fun AboutSection(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JupiterIconBadge(
                icon = Icons.Filled.Folder,
                contentDescription = null,
                size = 56.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
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
        HorizontalDivider(
            modifier = Modifier.padding(start = 80.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "All operations run on-device. No data leaves your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val APP_VERSION = "1.0.0"

private fun selectedAccentName(argb: Long): String = when (argb) {
    0L -> "Jupiter default"
    else -> AccentPalette.firstOrNull { it.argb == argb }?.name ?: "Custom accent"
}

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
