package com.jupiter.filemanager.feature.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.repository.AutomationRepository
import com.jupiter.filemanager.feature.ai.AiAssistant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Rule Builder screen.
 *
 * The user composes a new [com.jupiter.filemanager.domain.model.AutomationRule] by
 * naming it and supplying explicit "When" / "Then" fields. They may additionally
 * type a free-form description and request an AI-assisted interpretation: the
 * description is forwarded to [AiAssistant.parseNaturalQuery]. On success a
 * best-effort human-readable suggestion is produced and used to pre-fill the
 * "When" field if it is still empty; on failure (including the un-configured
 * NoOpAiAssistant) an honest message is shown and the user falls back to manual
 * authoring. Nothing here fabricates a result — saving only ever persists what
 * the user actually entered.
 *
 * Rule *execution* is a backend concern that is not yet wired up; this view model
 * only validates input and persists via [AutomationRepository.addRule].
 */
@HiltViewModel
class RuleBuilderViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val aiAssistant: AiAssistant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RuleBuilderUiState(aiAvailable = aiAssistant.isEnabled),
    )
    val uiState: StateFlow<RuleBuilderUiState> = _uiState.asStateFlow()

    /** Updates the rule name. */
    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    /** Updates the free-form natural-language description. */
    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    /** Updates the manual "When" trigger text. */
    fun onWhenChange(value: String) {
        _uiState.update { it.copy(whenText = value) }
    }

    /** Updates the manual "Then" action text. */
    fun onThenChange(value: String) {
        _uiState.update { it.copy(thenText = value) }
    }

    /**
     * Requests a best-effort AI interpretation of the current [RuleBuilderUiState.description].
     *
     * Forwards the text to [AiAssistant.parseNaturalQuery]. On success the parsed
     * [FilterOption] is rendered into a readable suggestion and, if the "When"
     * field is still empty, used to pre-fill it. On failure an honest message is
     * surfaced and manual authoring remains available. No-ops when the description
     * is blank or a request is already in flight.
     */
    fun requestAiSuggestion() {
        val state = _uiState.value
        val description = state.description.trim()
        if (description.isEmpty() || state.isParsing) return

        _uiState.update { it.copy(isParsing = true, aiSuggestion = null, aiError = null) }

        viewModelScope.launch {
            when (val result = aiAssistant.parseNaturalQuery(description)) {
                is AppResult.Success -> {
                    val suggestion = describeFilter(result.data)
                    _uiState.update { current ->
                        current.copy(
                            isParsing = false,
                            aiSuggestion = suggestion,
                            aiError = null,
                            // Pre-fill the trigger only when the user hasn't authored one yet.
                            whenText = if (current.whenText.isBlank()) suggestion else current.whenText,
                        )
                    }
                }

                is AppResult.Failure -> {
                    val message = if (!aiAssistant.isEnabled) {
                        "AI assistance isn't configured. You can enable it in " +
                            "Settings > AI. Fill in the When and Then fields below manually."
                    } else {
                        "Couldn't interpret that automatically: " +
                            "${result.error.displayMessage}. Try the manual fields below."
                    }
                    _uiState.update {
                        it.copy(isParsing = false, aiSuggestion = null, aiError = message)
                    }
                }
            }
        }
    }

    /**
     * Persists the current draft as a new rule, then marks the state as [RuleBuilderUiState.saved]
     * so the screen can navigate back. No-ops when required fields are missing or a
     * save is already in progress.
     */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            automationRepository.addRule(
                name = state.name.trim(),
                whenText = state.whenText.trim(),
                thenText = state.thenText.trim(),
            )
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    /**
     * Renders a parsed [FilterOption] into a concise, human-readable trigger
     * description suitable for the "When" field of a rule.
     */
    private fun describeFilter(filter: FilterOption): String {
        val parts = buildList {
            filter.query.takeIf { it.isNotBlank() }?.let { add("name contains \"$it\"") }
            filter.typeFilter?.let { add("type is ${it.name.lowercase()}") }
            if (filter.showHidden) add("including hidden files")
        }
        return if (parts.isEmpty()) {
            "a matching file is added"
        } else {
            "a file is added where " + parts.joinToString(", ")
        }
    }
}
