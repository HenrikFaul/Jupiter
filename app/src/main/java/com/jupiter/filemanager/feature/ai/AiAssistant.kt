package com.jupiter.filemanager.feature.ai

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.StorageOverview

/**
 * A single AI-generated suggestion surfaced to the user.
 *
 * @property title short, human-readable headline for the suggestion.
 * @property detail longer explanatory text describing the suggestion.
 * @property confidence model confidence in the range [0f, 1f].
 */
data class AiSuggestion(
    val title: String,
    val detail: String,
    val confidence: Float,
)

/**
 * Optional, isolated AI assistant abstraction.
 *
 * Implementations must never crash the app: when the assistant is not configured
 * or a request cannot be fulfilled, they return [AppResult.Failure] rather than
 * throwing. Consumers should gate AI-specific UI on [isEnabled].
 */
interface AiAssistant {

    /**
     * Whether the assistant is configured and available. When `false`, the
     * suspend operations are expected to return [AppResult.Failure].
     */
    val isEnabled: Boolean

    /**
     * Suggests a cleaner, more descriptive file name for the given [item].
     *
     * @return the suggested name on success, or a failure describing why no
     * suggestion could be produced.
     */
    suspend fun suggestName(item: FileItem): AppResult<String>

    /**
     * Produces a natural-language explanation of the provided storage [overview],
     * highlighting where space is being used and potential cleanup opportunities.
     */
    suspend fun explainStorage(overview: StorageOverview): AppResult<String>

    /**
     * Interprets a free-form natural-language [query] (for example,
     * "large videos from last month") into a structured [FilterOption].
     */
    suspend fun parseNaturalQuery(query: String): AppResult<FilterOption>
}
