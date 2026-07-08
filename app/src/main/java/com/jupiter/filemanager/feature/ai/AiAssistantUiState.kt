package com.jupiter.filemanager.feature.ai

/**
 * Author of a single chat message in the AI assistant conversation.
 */
enum class ChatRole {
    /** Message authored by the user. */
    USER,

    /** Message authored by the assistant. */
    ASSISTANT,
}

/**
 * A single message in the AI assistant chat transcript.
 *
 * @property id stable identifier used as a list key.
 * @property role who authored the message.
 * @property text the rendered message body. Never contains fabricated file
 * contents — when the assistant cannot fulfil a request it returns an honest
 * explanation instead.
 * @property timestamp epoch-millis the message was created.
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestamp: Long,
)

/**
 * Immutable UI state for the AI assistant (Nexus AI) screen.
 *
 * @property messages the ordered chat transcript, oldest first.
 * @property input the current value of the composer text field.
 * @property suggestions quick-tap example prompts shown when the chat is empty.
 * @property isSending whether a request is currently in flight.
 * @property isEnabled whether a real AI backend is configured. When `false`,
 * the screen still works but every assistant reply honestly explains that the
 * assistant is not configured.
 */
data class AiAssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val suggestions: List<String> = DEFAULT_SUGGESTIONS,
    val isSending: Boolean = false,
    val isEnabled: Boolean = false,
) {
    /** Whether the composer can submit the current [input]. */
    val canSend: Boolean
        get() = input.isNotBlank() && !isSending

    companion object {
        /** Example prompts surfaced as chips on the empty state. */
        val DEFAULT_SUGGESTIONS: List<String> = listOf(
            "Show me large files not used in 3 months",
            "Find duplicate photos taking up space",
            "What's filling up my storage?",
            "Rename my latest screenshots",
        )
    }
}
