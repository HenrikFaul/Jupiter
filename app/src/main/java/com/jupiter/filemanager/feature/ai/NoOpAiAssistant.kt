package com.jupiter.filemanager.feature.ai

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.StorageOverview
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default, inert [AiAssistant] implementation used when no real AI backend is configured.
 *
 * This is the binding provided in production builds via Hilt's `AiModule`. It reports
 * [isEnabled] as `false` so that AI-specific UI can be hidden, and every operation
 * returns a graceful [AppResult.Failure] instead of throwing — the assistant must never
 * crash the app.
 */
@Singleton
class NoOpAiAssistant @Inject constructor() : AiAssistant {

    override val isEnabled: Boolean = false

    override suspend fun suggestName(item: FileItem): AppResult<String> = notConfigured()

    override suspend fun explainStorage(overview: StorageOverview): AppResult<String> = notConfigured()

    override suspend fun parseNaturalQuery(query: String): AppResult<FilterOption> = notConfigured()

    private fun <T> notConfigured(): AppResult<T> =
        AppResult.Failure(AppError.Unknown(NOT_CONFIGURED_MESSAGE))

    private companion object {
        const val NOT_CONFIGURED_MESSAGE: String = "AI assistant is not configured."
    }
}
