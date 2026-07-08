package com.jupiter.filemanager.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * Drives the AI assistant (Nexus AI) chat screen.
 *
 * Sending a message routes the prompt to the injected [AiAssistant]. When no
 * real backend is configured the production binding is
 * [NoOpAiAssistant], whose operations return [AppResult.Failure]; in that case
 * the ViewModel emits an honest assistant reply explaining that the assistant
 * is not configured and can be enabled in Settings. It never fabricates file
 * contents or analytics — failures are surfaced verbatim.
 */
@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val aiAssistant: AiAssistant,
    private val fileRepository: FileRepository,
    private val analyticsRepository: StorageAnalyticsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiAssistantUiState(isEnabled = false),
    )
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    init {
        // Resolve availability off the main thread; never block in the field
        // initializer. The assistant's isEnabled is backed by a cached value.
        viewModelScope.launch {
            val enabled = withContext(Dispatchers.IO) { aiAssistant.isEnabled }
            _uiState.update { it.copy(isEnabled = enabled) }
        }
    }

    /** Updates the composer text. */
    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    /** Populates the composer with a tapped suggestion chip and submits it. */
    fun onSuggestionClick(suggestion: String) {
        _uiState.update { it.copy(input = suggestion) }
        sendMessage()
    }

    /**
     * Submits the current composer text as a user message and requests an
     * assistant reply. No-ops when the input is blank or a request is already
     * in flight.
     */
    fun sendMessage() {
        val state = _uiState.value
        val prompt = state.input.trim()
        if (prompt.isEmpty() || state.isSending) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            text = prompt,
            timestamp = System.currentTimeMillis(),
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                input = "",
                isSending = true,
            )
        }

        viewModelScope.launch {
            val reply = generateReply(prompt)
            val assistantMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.ASSISTANT,
                text = reply,
                timestamp = System.currentTimeMillis(),
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + assistantMessage,
                    isSending = false,
                )
            }
        }
    }

    /**
     * Produces an honest assistant reply for [prompt].
     *
     * The prompt is interpreted as a natural-language file query and forwarded
     * to [AiAssistant.parseNaturalQuery]. On success we describe the structured
     * filter that was understood. On failure (including the un-configured
     * [NoOpAiAssistant]) we surface the failure's message rather than inventing
     * an answer, and point the user to Settings to enable AI.
     */
    private suspend fun generateReply(prompt: String): String {
        return when (val result = aiAssistant.parseNaturalQuery(prompt)) {
            is AppResult.Success -> {
                val filter = result.data
                buildString {
                    append("Here's how I understood your request:\n\n")
                    filter.query.takeIf { it.isNotBlank() }?.let {
                        append("• Keywords: ")
                        append(it)
                        append('\n')
                    }
                    filter.typeFilter?.let { type ->
                        append("• Type: ")
                        append(type.name.lowercase())
                        append('\n')
                    }
                    if (filter.showHidden) {
                        append("• Including hidden files\n")
                    }
                    if (filter.query.isBlank() && filter.typeFilter == null) {
                        append("I understood a general file search.\n")
                    }
                    append("\nApply this from the Search screen to see results.")
                }.trim()
            }

            is AppResult.Failure -> {
                if (!aiAssistant.isEnabled) {
                    "AI assistant is not configured. You can enable it in " +
                        "Settings > AI. Until then I can't analyze your files " +
                        "or answer this request — and I won't make anything up."
                } else {
                    "I couldn't process that request: ${result.error.displayMessage}"
                }
            }
        }
    }
}
