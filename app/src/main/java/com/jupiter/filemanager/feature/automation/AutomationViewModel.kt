package com.jupiter.filemanager.feature.automation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jupiter.filemanager.data.automation.AutomationWorker
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
) : ViewModel() {

    /** One-shot confirmation message, set when a manual run has been enqueued. */
    private val runEnqueuedMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AutomationUiState> = combine(
        automationRepository.observeRules(),
        runEnqueuedMessage,
    ) { rules, message ->
        AutomationUiState(
            rules = rules,
            isLoading = false,
            runEnqueuedMessage = message,
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
}
