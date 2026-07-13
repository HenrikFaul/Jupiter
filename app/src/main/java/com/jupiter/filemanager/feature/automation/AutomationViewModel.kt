package com.jupiter.filemanager.feature.automation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jupiter.filemanager.data.automation.AutomationWorker
import com.jupiter.filemanager.data.automation.RuleEngine
import com.jupiter.filemanager.domain.model.AutomationRule
import com.jupiter.filemanager.domain.model.AutomationSafety
import com.jupiter.filemanager.domain.repository.AutomationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Automation screen: streams the user's persisted [AutomationRule]s
 * from [AutomationRepository] and lets the user toggle a rule's enabled state,
 * delete it, or explicitly run the enabled rules now via [runNow].
 *
 * Rules are only ever executed in response to an explicit, confirmed user action —
 * never silently and never on a schedule. The screen shows a confirmation dialog
 * before invoking [runNow]; once a run is enqueued, a one-shot
 * [AutomationUiState.runEnqueuedMessage] is surfaced so the user always sees that a
 * run started.
 */
@HiltViewModel
class AutomationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val automationRepository: AutomationRepository,
    private val ruleEngine: RuleEngine,
) : ViewModel() {

    /** One-shot confirmation message, set when a manual run has been enqueued. */
    private val runEnqueuedMessage = MutableStateFlow<String?>(null)
    private val previewMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AutomationUiState> = combine(
        automationRepository.observeRules(),
        runEnqueuedMessage,
        previewMessage,
    ) { rules, message, preview ->
        AutomationUiState(
            rules = rules,
            isLoading = false,
            runEnqueuedMessage = message,
            previewMessage = preview,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AutomationUiState(isLoading = true),
    )

    /** Enables or disables the rule identified by [id] according to [enabled]. */
    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            automationRepository.setEnabled(id, enabled)
        }
    }

    /** Permanently removes the rule identified by [id]. */
    fun deleteRule(id: String) {
        viewModelScope.launch {
            automationRepository.deleteRule(id)
        }
    }

    /** Saves an edited/renamed rule. Destructive or unsupported actions are never persisted. */
    fun updateRule(id: String, name: String, whenText: String, thenText: String) {
        if (name.isBlank() || whenText.isBlank()) {
            previewMessage.value = "Name and When are required."
            return
        }
        if (!AutomationSafety.isSupportedAction(thenText)) {
            previewMessage.value = if (AutomationSafety.isDestructiveAction(thenText)) {
                "Delete actions are blocked. Automation never deletes files."
            } else {
                "Use a safe action: move to a folder, or favorite."
            }
            return
        }
        viewModelScope.launch {
            automationRepository.updateRule(
                id = id,
                name = name.trim(),
                whenText = whenText.trim(),
                thenText = thenText.trim(),
            )
            previewMessage.value = "Automation updated. It remains ${
                uiState.value.rules.firstOrNull { it.id == id }
                    ?.let { if (it.enabled) "active" else "suspended" }
                    ?: "suspended"
            }."
        }
    }

    /** Performs a read-only dry run for one saved rule and reports the current matches. */
    fun previewRule(rule: AutomationRule) {
        viewModelScope.launch {
            val preview = ruleEngine.preview(rule)
            previewMessage.value = when {
                preview.destructiveBlocked ->
                    "Blocked: this rule asks to delete files. Nothing was changed."
                !preview.actionSupported ->
                    "This action isn't supported. Nothing was changed. Edit the rule first."
                preview.matchingFiles == 1 ->
                    "Safe preview: 1 file matches. Nothing was changed."
                else ->
                    "Safe preview: ${preview.matchingFiles} files match. Nothing was changed."
            }
        }
    }

    /**
     * Explicitly enqueues a one-time [AutomationWorker] run that applies the user's
     * enabled rules in the background. This is only ever called after the user has
     * confirmed the run in the UI — rules are never run on a schedule or silently.
     * Using [ExistingWorkPolicy.KEEP] avoids stacking duplicate runs if the user
     * confirms repeatedly while a run is already pending.
     *
     * On enqueue a one-shot [AutomationUiState.runEnqueuedMessage] is published so the
     * screen can confirm to the user that a run has started; the message is cleared via
     * [consumeRunEnqueuedMessage] once shown. Any failure to enqueue is caught so the
     * action can never crash the screen.
     */
    fun runNow() {
        try {
            val request = OneTimeWorkRequestBuilder<AutomationWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                AutomationWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            runEnqueuedMessage.value = "Running your enabled rules now."
        } catch (_: Exception) {
            runEnqueuedMessage.value = "Couldn't start the run. Please try again."
        }
    }

    /** Clears the one-shot run-enqueued message after the screen has shown it. */
    fun consumeRunEnqueuedMessage() {
        runEnqueuedMessage.update { null }
    }

    /** Clears the one-shot preview/edit message after it has been shown. */
    fun consumePreviewMessage() {
        previewMessage.update { null }
    }
}
