package com.jupiter.filemanager.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.BuildConfig
import com.jupiter.filemanager.data.vault.VaultPinMutationResult
import com.jupiter.filemanager.domain.model.SortDirection
import com.jupiter.filemanager.domain.model.SortField
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.model.ThemeMode
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterFloatingBottomNavigation
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.JupiterMainTab
import com.jupiter.filemanager.ui.navigation.Destination
import com.jupiter.filemanager.ui.theme.AccentPalette

/** Compact, reference-matched settings hub. Every control delegates to persisted state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenRoute: (String) -> Unit,
    onBack: () -> Unit,
    onMainTabSelected: (JupiterMainTab) -> Unit = {},
    showBackButton: Boolean = true,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var feedback by remember { mutableStateOf<String?>(null) }
    var themeDialog by rememberSaveable { mutableStateOf(false) }
    var sortDialog by rememberSaveable { mutableStateOf(false) }
    var retentionDialog by rememberSaveable { mutableStateOf(false) }
    var autoLockDialog by rememberSaveable { mutableStateOf(false) }
    var languageDialog by rememberSaveable { mutableStateOf(false) }
    var pinDialog by rememberSaveable { mutableStateOf(false) }
    var accentDialog by rememberSaveable { mutableStateOf(false) }
    var indexDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // AppCompat is the locale source of truth. Mirror its active locale into
        // DataStore whenever this screen is (re)created after a locale change.
        viewModel.refreshAppLanguageTag()
    }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbar.showSnackbar(it)
            feedback = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(48.dp)
                    .padding(horizontal = if (showBackButton) 4.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        bottomBar = {
            JupiterFloatingBottomNavigation(
                selectedTab = JupiterMainTab.MORE,
                onTabSelected = onMainTabSelected,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            SettingsGroup("Appearance") {
                SettingsValueRow(
                    icon = Icons.Filled.Palette,
                    title = "Theme",
                    value = themeLabel(uiState.themeMode),
                    onClick = { themeDialog = true },
                )
                SettingsGroupDivider()
                SettingsValueRow(
                    icon = Icons.Filled.Info,
                    title = "App language",
                    value = appLanguageLabel(uiState.appLanguageTag),
                    onClick = { languageDialog = true },
                )
                SettingsGroupDivider()
                AccentColorPicker(
                    selectedArgb = uiState.accentColorArgb,
                    onClick = { accentDialog = true },
                )
            }

            SettingsGroup("Browsing") {
                SettingsSwitchRow(
                    icon = Icons.Filled.Visibility,
                    title = "Show hidden files",
                    subtitle = "Show files that start with a dot",
                    checked = uiState.showHidden,
                    onCheckedChange = viewModel::setShowHidden,
                )
                SettingsGroupDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.ViewColumn,
                    title = "Dual-pane on tablets",
                    subtitle = "Use side-by-side layout on large screens",
                    checked = uiState.dualPaneEnabled,
                    onCheckedChange = viewModel::setDualPane,
                )
                SettingsGroupDivider()
                SettingsValueRow(
                    icon = Icons.Filled.Bolt,
                    title = "Default sort order",
                    value = sortOptionLabel(uiState.defaultSortOption),
                    onClick = { sortDialog = true },
                )
                SettingsGroupDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Folder,
                    title = "Group files by type",
                    subtitle = "Organize files into categories",
                    checked = uiState.groupFilesByType,
                    onCheckedChange = viewModel::setGroupFilesByType,
                )
            }

            SettingsGroup("Recycle Bin") {
                SettingsValueRow(
                    icon = Icons.Filled.DeleteSweep,
                    title = "Auto-delete trashed items",
                    value = retentionLabel(uiState.trashAutoDeleteDays),
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onClick = { retentionDialog = true },
                )
                SettingsGroupDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Refresh,
                    title = "Confirm before deleting",
                    subtitle = "Show confirmation for delete actions",
                    checked = uiState.confirmBeforeTrash,
                    onCheckedChange = viewModel::setConfirmBeforeTrash,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                )
                SettingsGroupDivider()
                SettingsNavigationRow(
                    icon = Icons.Filled.DeleteSweep,
                    title = "Clear trash now",
                    subtitle = "Review the bin before permanent deletion",
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onClick = { onOpenRoute(Destination.Trash.route) },
                )
            }

            SettingsGroup("Security") {
                SettingsSwitchRow(
                    icon = Icons.Filled.Shield,
                    title = "Biometric vault lock",
                    subtitle = "Use fingerprint, face or device credential",
                    checked = uiState.vaultBiometricLock,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onCheckedChange = { enabled ->
                        viewModel.setVaultBiometricLock(enabled) { accepted ->
                            if (!accepted) {
                                feedback = "Set a Vault PIN before disabling biometric protection."
                            }
                        }
                    },
                )
                SettingsGroupDivider()
                SettingsValueRow(
                    icon = Icons.Filled.Lock,
                    title = if (uiState.vaultPinConfigured) "Change vault PIN" else "Set vault PIN",
                    value = if (uiState.vaultPinConfigured) "PIN configured" else "Not configured",
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onClick = { pinDialog = true },
                )
                SettingsGroupDivider()
                SettingsValueRow(
                    icon = Icons.Filled.Brightness4,
                    title = "Auto-lock",
                    value = autoLockLabel(uiState.vaultAutoLockMinutes),
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onClick = { autoLockDialog = true },
                )
            }

            SettingsGroup("AI & Automation") {
                SettingsSwitchRow(
                    icon = Icons.Filled.SmartToy,
                    title = "Semantic search",
                    subtitle = "Find files using natural language",
                    checked = uiState.aiEnabled,
                    onCheckedChange = viewModel::setAiEnabled,
                )
                SettingsGroupDivider()
                SettingsNavigationRow(
                    icon = Icons.Filled.PlayArrow,
                    title = "Smart rules",
                    subtitle = "Automate organization and actions",
                    onClick = { onOpenRoute(Destination.Automation.route) },
                )
                SettingsGroupDivider()
                SettingsIndexRow(
                    icon = Icons.Filled.Bolt,
                    title = "Indexing",
                    subtitle = if (uiState.indexing) "Indexing files now" else "${uiState.indexedCount} files indexed",
                    value = if (uiState.indexingEnabled) "Enabled" else "Off",
                    onClick = { indexDialog = true },
                )
            }

            SettingsGroup("About") {
                AboutSection()
            }

            SettingsGroup("Advanced appearance") {
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
                    subtitle = "Match wallpaper colors on Android 12+",
                    checked = uiState.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            SettingsGroup("Advanced services") {
                SettingsNavigationRow(Icons.Filled.Storage, "Storage analysis", "Review usage and reclaim space") {
                    onOpenRoute(Destination.StorageAnalytics.route)
                }
                SettingsGroupDivider()
                SettingsNavigationRow(Icons.Filled.SwapHoriz, "Transfer history", "Recent copy, move and send tasks") {
                    onOpenRoute(Destination.TransferCenter.route)
                }
                SettingsGroupDivider()
                SettingsNavigationRow(Icons.Filled.Lock, "Secure vault", "Open encrypted files") {
                    onOpenRoute(Destination.Vault.route)
                }
                SettingsGroupDivider()
                SettingsNavigationRow(Icons.Filled.Cloud, "Connected accounts", "Manage cloud storage providers") {
                    onOpenRoute(Destination.CloudHub.route)
                }
                SettingsGroupDivider()
                SettingsNavigationRow(Icons.Filled.Shield, "Your data & privacy", "Access and protection details") {
                    onOpenRoute(Destination.DataTransparency.route)
                }
                SettingsGroupDivider()
                SettingsNavigationRow(Icons.Filled.Star, "Jupiscan Pro", "Benefits and support") {
                    onOpenRoute(Destination.Paywall.route)
                }
            }

            SettingsGroup("Advanced AI & privacy") {
                AiApiKeyField(uiState.aiApiKey, viewModel::setAiApiKey)
                SettingsGroupDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Insights,
                    title = "Help improve Jupiscan",
                    subtitle = "Share anonymous usage data (opt-in)",
                    checked = uiState.analyticsOptIn,
                    onCheckedChange = viewModel::setAnalyticsOptIn,
                )
            }
        }
    }

    if (themeDialog) {
        ChoiceDialog(
            title = "Theme",
            values = ThemeMode.entries,
            selected = uiState.themeMode,
            label = ::themeLabel,
            onDismiss = { themeDialog = false },
            onSelected = {
                viewModel.setThemeMode(it)
                themeDialog = false
            },
        )
    }
    if (accentDialog) {
        AccentPaletteDialog(
            selectedArgb = uiState.accentColorArgb,
            onDismiss = { accentDialog = false },
            onAccentSelected = { argb ->
                viewModel.setAccentColorArgb(argb)
                accentDialog = false
            },
        )
    }
    if (indexDialog) {
        IndexingDialog(
            enabled = uiState.indexingEnabled,
            indexedCount = uiState.indexedCount,
            indexing = uiState.indexing,
            progressPercent = uiState.indexProgressPercent,
            progressCurrent = uiState.indexProgressCurrent,
            progressTotal = uiState.indexProgressTotal,
            onEnabledChange = viewModel::setIndexingEnabled,
            onRebuild = viewModel::rebuildIndex,
            onDismiss = { indexDialog = false },
        )
    }
    if (sortDialog) {
        ChoiceDialog(
            title = "Default sort order",
            values = SORT_OPTIONS,
            selected = SORT_OPTIONS.firstOrNull { sameSort(it, uiState.defaultSortOption) } ?: SORT_OPTIONS.first(),
            label = ::sortOptionLabel,
            onDismiss = { sortDialog = false },
            onSelected = {
                viewModel.setSortOption(it)
                sortDialog = false
            },
        )
    }
    if (languageDialog) {
        ChoiceDialog(
            title = "App language",
            values = APP_LANGUAGE_OPTIONS,
            selected = appLanguageChoice(uiState.appLanguageTag),
            label = ::appLanguageLabel,
            onDismiss = { languageDialog = false },
            onSelected = {
                viewModel.setAppLanguageTag(it)
                languageDialog = false
            },
        )
    }
    if (retentionDialog) {
        ChoiceDialog(
            title = "Auto-delete trashed items",
            values = RETENTION_OPTIONS,
            selected = uiState.trashAutoDeleteDays,
            label = ::retentionLabel,
            onDismiss = { retentionDialog = false },
            onSelected = {
                viewModel.setTrashAutoDeleteDays(it)
                retentionDialog = false
            },
        )
    }
    if (autoLockDialog) {
        ChoiceDialog(
            title = "Vault auto-lock",
            values = AUTO_LOCK_OPTIONS,
            selected = uiState.vaultAutoLockMinutes,
            label = ::autoLockLabel,
            onDismiss = { autoLockDialog = false },
            onSelected = {
                viewModel.setVaultAutoLockMinutes(it)
                autoLockDialog = false
            },
        )
    }
    if (pinDialog) {
        VaultPinDialog(
            configured = uiState.vaultPinConfigured,
            onDismiss = { pinDialog = false },
            onSave = { pin ->
                viewModel.configureVaultPin(pin) { result ->
                    feedback = when (result) {
                        VaultPinMutationResult.SUCCESS -> "Vault PIN saved."
                        VaultPinMutationResult.INVALID_PIN -> "Use 4–12 numeric digits."
                        VaultPinMutationResult.PERSISTENCE_FAILED -> "Vault PIN could not be saved."
                    }
                    if (result == VaultPinMutationResult.SUCCESS) pinDialog = false
                }
            },
            onRemove = {
                viewModel.clearVaultPin { result ->
                    feedback = if (result == VaultPinMutationResult.SUCCESS) {
                        "Vault PIN removed; biometric protection remains enabled."
                    } else {
                        "Vault PIN could not be removed."
                    }
                    if (result == VaultPinMutationResult.SUCCESS) pinDialog = false
                }
            },
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp, bottom = 3.dp),
        )
        JupiterCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 50.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}

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
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(icon = icon, tint = iconTint, contentDescription = null, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.scale(0.78f),
        )
    }
}

@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(icon = icon, tint = iconTint, contentDescription = null, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(icon = icon, tint = iconTint, contentDescription = null, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun SettingsIndexRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(
            icon = icon,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
            size = 32.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun AccentColorPicker(selectedArgb: Long, onClick: () -> Unit) {
    val selectedColor = if (selectedArgb == 0L) {
        MaterialTheme.colorScheme.primary
    } else {
        AccentPalette.firstOrNull { it.argb == selectedArgb }?.color
            ?: MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(
            icon = Icons.Filled.Colorize,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
            size = 32.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Accent color", style = MaterialTheme.typography.titleSmall)
            Text(
                selectedAccentName(selectedArgb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            modifier = Modifier
                .size(28.dp)
                .semantics {
                    contentDescription = "Selected accent: ${selectedAccentName(selectedArgb)}"
                },
            shape = CircleShape,
            color = selectedColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {}
    }
}

@Composable
private fun AccentPaletteDialog(
    selectedArgb: Long,
    onDismiss: () -> Unit,
    onAccentSelected: (Long) -> Unit,
) {
    val choices = listOf(AccentChoice(0L, "Jupiscan teal", MaterialTheme.colorScheme.primary)) +
        AccentPalette.drop(1).map { AccentChoice(it.argb, it.name, it.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accent color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                choices.chunked(5).forEach { rowChoices ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        rowChoices.forEach { choice ->
                            AccentSwatch(
                                color = choice.color,
                                selected = selectedArgb == choice.argb ||
                                    (choice.argb == 0L && selectedArgb == AccentPalette.firstOrNull()?.argb),
                                description = choice.name,
                                onClick = { onAccentSelected(choice.argb) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IndexingDialog(
    enabled: Boolean,
    indexedCount: Int,
    indexing: Boolean,
    progressPercent: Int?,
    progressCurrent: Int,
    progressTotal: Int,
    onEnabledChange: (Boolean) -> Unit,
    onRebuild: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Indexing") },
        text = {
            Column {
                SettingsSwitchRow(
                    icon = Icons.Filled.Bolt,
                    title = "Keep index up to date",
                    subtitle = "Maintain fast search as files change",
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                IndexStatusRow(
                    indexedCount = indexedCount,
                    indexing = indexing,
                    progressPercent = progressPercent,
                    progressCurrent = progressCurrent,
                    progressTotal = progressTotal,
                    enabled = enabled,
                    onRebuild = onRebuild,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private data class AccentChoice(val argb: Long, val name: String, val color: Color)

@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description }
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = CircleShape,
        color = color,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (selected) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun IndexStatusRow(
    indexedCount: Int,
    indexing: Boolean,
    progressPercent: Int?,
    progressCurrent: Int,
    progressTotal: Int,
    enabled: Boolean,
    onRebuild: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        !enabled -> "Indexing is off"
                        indexing && progressPercent != null -> "Indexing files… $progressPercent%"
                        indexing -> "Indexing files…"
                        else -> "$indexedCount files indexed"
                    },
                )
                if (indexing && progressTotal > 0) {
                    Text(
                        "$progressCurrent / $progressTotal files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (indexing && progressPercent == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(onClick = onRebuild, enabled = enabled && !indexing) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rebuild")
            }
        }
        if (indexing) {
            if (progressPercent == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
                LinearProgressIndicator(progress = { progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AiApiKeyField(apiKey: String, onApiKeyChange: (String) -> Unit) {
    var draft by remember(apiKey) { mutableStateOf(apiKey) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(14.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                onApiKeyChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Claude API key") },
            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (revealed) "Hide API key" else "Show API key",
                    )
                }
            },
            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Text(
            "Stored on-device and sent only when you use Claude-powered features.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AboutSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(
            icon = Icons.Filled.Info,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
            size = 32.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("About Jupiscan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                "v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Private, native Android file management.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                values.forEach { value ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(value == selected, role = Role.RadioButton) { onSelected(value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(label(value))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun VaultPinDialog(
    configured: Boolean,
    onDismiss: () -> Unit,
    onSave: (CharArray) -> Unit,
    onRemove: () -> Unit,
) {
    // PIN material deliberately stays in non-saveable composition memory: it must
    // never be serialized into the Activity saved-state Bundle.
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = pin.length in 4..12 && pin.all(Char::isDigit) && pin == confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (configured) "Change vault PIN" else "Set vault PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Use 4–12 numeric digits. Jupiscan stores only a salted PBKDF2 verifier.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("New PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.filter(Char::isDigit).take(12) },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    isError = confirmation.isNotEmpty() && confirmation != pin,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val submittedPin = pin.toCharArray()
                    pin = ""
                    confirmation = ""
                    onSave(submittedPin)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (configured) TextButton(onClick = onRemove) { Text("Remove PIN") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private val SORT_OPTIONS = listOf(
    SortOption(SortField.NAME, SortDirection.ASCENDING, foldersFirst = true),
    SortOption(SortField.NAME, SortDirection.DESCENDING, foldersFirst = true),
    SortOption(SortField.DATE_MODIFIED, SortDirection.DESCENDING, foldersFirst = true),
    SortOption(SortField.SIZE, SortDirection.DESCENDING, foldersFirst = true),
)
private val RETENTION_OPTIONS = listOf(0, 7, 15, 30, 60)
private val AUTO_LOCK_OPTIONS = listOf(1, 5, 15, 30)
private val APP_LANGUAGE_OPTIONS = listOf("", "en")

private fun sameSort(a: SortOption, b: SortOption): Boolean = a.field == b.field && a.direction == b.direction

private fun sortOptionLabel(option: SortOption): String = when (option.field) {
    SortField.NAME -> if (option.direction == SortDirection.ASCENDING) "Name (A–Z)" else "Name (Z–A)"
    SortField.DATE_MODIFIED -> if (option.direction == SortDirection.ASCENDING) "Date (oldest)" else "Date (newest)"
    SortField.SIZE -> if (option.direction == SortDirection.ASCENDING) "Size (smallest)" else "Size (largest)"
    SortField.TYPE -> if (option.direction == SortDirection.ASCENDING) "Type (A–Z)" else "Type (Z–A)"
}

private fun retentionLabel(days: Int): String = if (days <= 0) "Off" else "$days days"
private fun autoLockLabel(minutes: Int): String = "$minutes min"
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun selectedAccentName(argb: Long): String = when (argb) {
    0L -> "Jupiscan teal"
    else -> AccentPalette.firstOrNull { it.argb == argb }?.name ?: "Custom"
}

private fun appLanguageLabel(tag: String): String = if (tag.isBlank()) {
    "System default"
} else {
    runCatching {
        java.util.Locale.forLanguageTag(tag)
            .getDisplayLanguage(java.util.Locale.getDefault())
            .replaceFirstChar(Char::titlecase)
    }.getOrDefault(tag)
}

private fun appLanguageChoice(tag: String): String = when {
    tag.isBlank() -> ""
    tag.equals("en", ignoreCase = true) || tag.startsWith("en-", ignoreCase = true) -> "en"
    else -> tag
}
