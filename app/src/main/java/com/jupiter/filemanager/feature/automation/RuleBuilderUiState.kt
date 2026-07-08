package com.jupiter.filemanager.feature.automation

/**
 * Immutable UI state for the Rule Builder screen.
 *
 * The user authors a new [com.jupiter.filemanager.domain.model.AutomationRule] by
 * supplying a [name] and explicit [whenText] / [thenText] fields. They may also
 * type a free-form [description] and request an AI-assisted interpretation; the
 * outcome of that request is surfaced via [aiSuggestion] (best-effort) or
 * [aiError] (honest fallback when no assistant is configured or parsing failed).
 *
 * @param name human-readable rule name.
 * @param description free-form natural-language description used for AI assistance.
 * @param whenText the trigger condition, in human-readable text.
 * @param thenText the action to perform, in human-readable text.
 * @param aiAvailable whether an AI assistant is configured (controls the AI card affordance).
 * @param isParsing true while a natural-language parse request is in flight.
 * @param aiSuggestion a best-effort, human-readable suggestion derived from [description]; null when none.
 * @param aiError an honest message explaining why no AI suggestion is available; null when none.
 * @param isSaving true while the rule is being persisted.
 * @param saved true once the rule has been saved (signals the screen to navigate back).
 */
data class RuleBuilderUiState(
    val name: String = "",
    val description: String = "",
    val whenText: String = "",
    val thenText: String = "",
    val aiAvailable: Boolean = false,
    val isParsing: Boolean = false,
    val aiSuggestion: String? = null,
    val aiError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    /** Whether the current draft has enough content to be saved. */
    val canSave: Boolean
        get() = name.isNotBlank() &&
            whenText.isNotBlank() &&
            thenText.isNotBlank() &&
            !isSaving &&
            !saved

    /** Whether a natural-language parse can be requested for the current draft. */
    val canParse: Boolean
        get() = description.isNotBlank() && !isParsing
}
