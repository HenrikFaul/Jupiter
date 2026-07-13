package com.jupiter.filemanager.feature.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.domain.model.AutomationRule
import com.jupiter.filemanager.domain.model.AutomationSafety
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Automation screen. Lists the user's persisted "when / then" automation rules,
 * each with a real, persisted Active toggle and a delete action. A "+ New Rule"
 * action launches the rule builder. When no rules exist a clean empty state is
 * shown.
 *
 * A "Run rules now" action in the top bar lets the user explicitly apply their
 * enabled rules: tapping it opens a confirmation dialog, and only after the user
 * confirms is an [com.jupiter.filemanager.data.automation.AutomationWorker] enqueued.
 * Rules are never executed automatically, on a schedule, or silently — the run is
 * always user-initiated and confirmed, with a snackbar confirming it started.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onCreateRule: () -> Unit,
    onBack: () -> Unit,
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showRunConfirm by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<AutomationRule?>(null) }
    var ruleToDelete by remember { mutableStateOf<AutomationRule?>(null) }

    // Surface the one-shot "run enqueued" confirmation, then clear it so a rerun can
    // show it again. The user always sees that a run started — it never runs silently.
    LaunchedEffect(state.runEnqueuedMessage) {
        val message = state.runEnqueuedMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeRunEnqueuedMessage()
        }
    }

    LaunchedEffect(state.previewMessage) {
        val message = state.previewMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumePreviewMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Automation",
                        style = MaterialTheme.typography.headlineSmall,
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
                actions = {
                    // Explicit, user-initiated run of the enabled rules. Tapping only
                    // opens a confirmation dialog; rules are never executed
                    // automatically, on a schedule, or without confirmation.
                    IconButton(
                        onClick = { showRunConfirm = true },
                        enabled = state.canRun,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Run rules now",
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRule,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                    )
                },
                text = { Text("New Rule") },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> LoadingView()

                else -> AutomationList(
                    rules = state.rules,
                    onToggle = viewModel::setEnabled,
                    onPreview = viewModel::previewRule,
                    onEdit = { ruleToEdit = it },
                    onDelete = { ruleToDelete = it },
                )
            }
        }
    }

    if (showRunConfirm) {
        RunRulesConfirmDialog(
            enabledCount = state.rules.count { it.enabled },
            onConfirm = {
                showRunConfirm = false
                viewModel.runNow()
            },
            onDismiss = { showRunConfirm = false },
        )
    }


    ruleToEdit?.let { rule ->
        EditRuleDialog(
            rule = rule,
            onSave = { name, whenText, thenText ->
                viewModel.updateRule(rule.id, name, whenText, thenText)
                ruleToEdit = null
            },
            onDismiss = { ruleToEdit = null },
        )
    }

    ruleToDelete?.let { rule ->
        DeleteRuleDialog(
            ruleName = rule.name,
            onConfirm = {
                viewModel.deleteRule(rule.id)
                ruleToDelete = null
            },
            onDismiss = { ruleToDelete = null },
        )
    }
}

@Composable
private fun RunRulesConfirmDialog(
    enabledCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ruleLabel = if (enabledCount == 1) "1 enabled rule" else "$enabledCount enabled rules"
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
            )
        },
        title = { Text(text = "Run rules now?") },
        text = {
            Text(
                text = "This will apply your $ruleLabel to your files right now. " +
                    "Rules can move or favorite matching files. Automation never deletes files. " +
                    "Only active rules run.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Run now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun AutomationList(
    rules: List<AutomationRule>,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onPreview: (AutomationRule) -> Unit,
    onEdit: (AutomationRule) -> Unit,
    onDelete: (AutomationRule) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "automation-guide") {
            AutomationGuideCard()
        }

        item(key = "automation-summary") {
            Text(
                text = "${rules.size} saved · ${rules.count { it.enabled }} active · " +
                    "${rules.count { !it.enabled }} suspended",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }

        if (rules.isEmpty()) {
            item(key = "automation-empty") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = JupiterDesign.CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("No saved automations", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap New Rule to create one. It will start suspended so you can " +
                                "preview it before activation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(items = rules, key = { it.id }) { rule ->
            AutomationRuleCard(
                rule = rule,
                onToggle = { enabled -> onToggle(rule.id, enabled) },
                onPreview = { onPreview(rule) },
                onEdit = { onEdit(rule) },
                onDelete = { onDelete(rule) },
            )
        }
    }
}

@Composable
private fun AutomationRuleCard(
    rule: AutomationRule,
    onToggle: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JupiterIconBadge(
                    icon = Icons.Filled.Bolt,
                    tint = if (rule.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    contentDescription = null,
                    size = 44.dp,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (rule.enabled) "Active" else "Suspended",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (rule.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = onToggle,
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            ConditionRow(
                label = "When",
                value = rule.whenText,
            )
            Spacer(modifier = Modifier.size(8.dp))
            ConditionRow(
                label = "Then",
                value = rule.thenText,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPreview) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Try safely")
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit rule",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete rule",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationGuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "How Automation works",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Automation organizes matching files only when you ask it to. " +
                    "The five examples below start suspended, and file deletion is never allowed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GuideStep("1", "Choose a saved example, or create a New Rule.")
            GuideStep("2", "Tap Try safely to count matches without changing anything.")
            GuideStep("3", "Use Edit to rename it or change its When and Then fields.")
            GuideStep("4", "Switch Suspended to Active only when the preview looks right.")
            GuideStep("5", "Tap the play icon, review the confirmation, then Run now.")
        }
    }
}

@Composable
private fun GuideStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = JupiterDesign.PillShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EditRuleDialog(
    rule: AutomationRule,
    onSave: (name: String, whenText: String, thenText: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(rule.id) { mutableStateOf(rule.name) }
    var whenText by remember(rule.id) { mutableStateOf(rule.whenText) }
    var thenText by remember(rule.id) { mutableStateOf(rule.thenText) }
    val destructive = AutomationSafety.isDestructiveAction(thenText)
    val supported = AutomationSafety.isSupportedAction(thenText)
    val canSave = name.isNotBlank() && whenText.isNotBlank() && supported

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit automation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = whenText,
                    onValueChange = { whenText = it },
                    label = { Text("When") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = thenText,
                    onValueChange = { thenText = it },
                    label = { Text("Then") },
                    supportingText = {
                        Text(
                            when {
                                destructive -> "Delete actions are forbidden."
                                !supported -> "Use: move to a folder, or favorite."
                                else -> "Safe action · file deletion is never allowed."
                            },
                        )
                    },
                    isError = thenText.isNotBlank() && !supported,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), whenText.trim(), thenText.trim()) },
                enabled = canSave,
            ) {
                Text("Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteRuleDialog(
    ruleName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete automation?") },
        text = {
            Text(
                "\"$ruleName\" will be removed from this list. No phone files will be deleted.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete automation") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ConditionRow(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = JupiterDesign.PillShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
