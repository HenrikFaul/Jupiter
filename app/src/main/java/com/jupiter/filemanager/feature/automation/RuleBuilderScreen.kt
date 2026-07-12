package com.jupiter.filemanager.feature.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Screen for authoring a new automation rule.
 *
 * The user names the rule, optionally types a free-form natural-language
 * description and taps "AI Suggestion" for a best-effort interpretation (via
 * [RuleBuilderViewModel] -> AiAssistant), then fills the explicit "When" and
 * "Then" fields (which the AI suggestion may pre-populate). Saving persists the
 * rule and navigates back. This composable performs no IO; all work is delegated
 * to the ViewModel, and the AI path falls back honestly when no assistant is
 * configured.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleBuilderScreen(
    onBack: () -> Unit,
    viewModel: RuleBuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate back once the rule has been persisted.
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "New rule",
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
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Rule name") },
                placeholder = { Text("e.g. Sort screenshots") },
                singleLine = true,
                shape = JupiterDesign.CompactCardShape,
            )

            AiSuggestionCard(
                description = state.description,
                aiAvailable = state.aiAvailable,
                isParsing = state.isParsing,
                canParse = state.canParse,
                suggestion = state.aiSuggestion,
                error = state.aiError,
                onDescriptionChange = viewModel::onDescriptionChange,
                onRequest = viewModel::requestAiSuggestion,
            )

            FieldCard(
                title = "When",
                icon = Icons.Filled.Bolt,
                value = state.whenText,
                onValueChange = viewModel::onWhenChange,
                placeholder = "a screenshot is added",
                helper = "Describe the trigger condition.",
            )

            FieldCard(
                title = "Then",
                icon = Icons.Filled.PlayArrow,
                value = state.thenText,
                onValueChange = viewModel::onThenChange,
                placeholder = "move it to /Screenshots",
                helper = "Describe the action to perform.",
            )

            Text(
                text = "Rules run only when you choose Run rules now in Automation and confirm. " +
                    "Nothing is executed silently in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = JupiterDesign.CompactCardShape,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save rule")
                }
            }
        }
    }
}

/**
 * Card hosting the free-form description field and the "AI Suggestion" affordance.
 *
 * Shows a best-effort suggestion or an honest fallback message returned by the
 * ViewModel. When no AI assistant is configured the action is disabled and the
 * card explains that manual fields are available below.
 */
@Composable
private fun AiSuggestionCard(
    description: String,
    aiAvailable: Boolean,
    isParsing: Boolean,
    canParse: Boolean,
    suggestion: String?,
    error: String?,
    onDescriptionChange: (String) -> Unit,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JupiterIconBadge(
                    icon = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    size = 40.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Describe it in words",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = if (aiAvailable) {
                    "Tell us what you want to automate and we'll draft the trigger for you."
                } else {
                    "AI assistance isn't configured. You can still describe the rule, " +
                        "then fill in the When and Then fields manually below."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Move large videos older than a month to Archive") },
                minLines = 2,
                maxLines = 4,
                shape = JupiterDesign.CompactCardShape,
            )

            FilledTonalButton(
                onClick = onRequest,
                enabled = canParse,
                modifier = Modifier.fillMaxWidth(),
                shape = JupiterDesign.PillShape,
            ) {
                if (isParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Interpreting…")
                } else {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("AI suggestion")
                }
            }

            if (suggestion != null) {
                Surface(
                    shape = JupiterDesign.CompactCardShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Suggested trigger: $suggestion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * A titled, icon-led card wrapping a single multi-line rule field with helper text.
 */
@Composable
private fun FieldCard(
    title: String,
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    helper: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JupiterIconBadge(
                    icon = icon,
                    contentDescription = null,
                    size = 36.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                minLines = 1,
                maxLines = 3,
                shape = JupiterDesign.CompactCardShape,
            )
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
