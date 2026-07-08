package com.jupiter.filemanager.feature.automation

import com.jupiter.filemanager.domain.model.AutomationRule

/**
 * Immutable UI state for the Automation screen.
 *
 * @param rules the user-defined automation rules, newest first as provided by the
 *   repository. Each carries its own enabled flag which is toggled in place.
 * @param isLoading true while the initial rule list is being loaded.
 * @param runEnqueuedMessage a one-shot confirmation message set when the user has
 *   explicitly confirmed a manual run and the [com.jupiter.filemanager.data.automation.AutomationWorker]
 *   has been enqueued. The screen surfaces this (e.g. as a snackbar) so a run is never
 *   triggered silently, then clears it via
 *   [AutomationViewModel.consumeRunEnqueuedMessage].
 */
data class AutomationUiState(
    val rules: List<AutomationRule> = emptyList(),
    val isLoading: Boolean = true,
    val runEnqueuedMessage: String? = null,
) {
    /** True when at least one rule is enabled and therefore eligible to be run. */
    val canRun: Boolean
        get() = rules.any { it.enabled }
}
