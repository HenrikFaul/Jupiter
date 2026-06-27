package com.jupiter.filemanager.feature.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.domain.repository.AutomationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Automation screen: streams the user's persisted [AutomationRule]s
 * from [AutomationRepository] and lets the user toggle a rule's enabled state or
 * delete it. Rule execution is a backend concern that is not yet wired up; this
 * view model only manages persistence and presentation.
 */
@HiltViewModel
class AutomationViewModel @Inject constructor(
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
}
