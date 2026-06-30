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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Automation screen: streams the user's persisted [AutomationRule]s
 * from [AutomationRepository] and lets the user toggle a rule's enabled state,
 * delete it, or explicitly run the enabled rules now via [runNow]. Rules are only
 * ever executed in response to an explicit user action — never silently.
 */
@HiltViewModel
class AutomationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val automationRepository: AutomationRepository,
) : ViewModel() {

    val uiState: StateFlow<AutomationUiState> = automationRepository.observeRules()
        .map { rules -> AutomationUiState(rules = rules, isLoading = false) }
        .stateIn(
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
     * enabled rules in the background. This is a manual, user-initiated action — rules
     * are never run on a schedule or silently. Using [ExistingWorkPolicy.KEEP] avoids
     * stacking duplicate runs if the user taps repeatedly.
     */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<AutomationWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AutomationWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
