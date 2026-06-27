package com.jupiter.filemanager.feature.automation

import com.jupiter.filemanager.domain.model.AutomationRule

/**
 * Immutable UI state for the Automation screen.
 *
 * @param rules the user-defined automation rules, newest first as provided by the
 *   repository. Each carries its own enabled flag which is toggled in place.
 * @param isLoading true while the initial rule list is being loaded.
 */
data class AutomationUiState(
    val rules: List<AutomationRule> = emptyList(),
    val isLoading: Boolean = true,
)
