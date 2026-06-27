package com.jupiter.filemanager.feature.automation

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.domain.model.AutomationRule
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.LoadingView

/**
 * Automation screen. Lists the user's persisted "when / then" automation rules,
 * each with a real, persisted Active toggle and a delete action. A "+ New Rule"
 * action launches the rule builder. When no rules exist a clean empty state is
 * shown.
 *
 * Rule execution is a backend concern that is not yet wired up; this screen only
 * manages persistence and presentation of rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onCreateRule: () -> Unit,
    onBack: () -> Unit,
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Automation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
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

                state.rules.isEmpty() -> EmptyView(
                    title = "No automations yet",
                    message = "Create rules to automatically organize your files, " +
                        "like moving screenshots to a folder or cleaning up downloads.",
                    icon = Icons.Outlined.Bolt,
                )

                else -> AutomationList(
                    rules = state.rules,
                    onToggle = viewModel::setEnabled,
                    onDelete = viewModel::deleteRule,
                )
            }
        }
    }
}

@Composable
private fun AutomationList(
    rules: List<AutomationRule>,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = rules, key = { it.id }) { rule ->
            AutomationRuleCard(
                rule = rule,
                onToggle = { enabled -> onToggle(rule.id, enabled) },
                onDelete = { onDelete(rule.id) },
            )
        }
    }
}

@Composable
private fun AutomationRuleCard(
    rule: AutomationRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (rule.enabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = if (rule.enabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                )
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
                horizontalArrangement = Arrangement.End,
            ) {
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

@Composable
private fun ConditionRow(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(8.dp),
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
