package com.jupiter.filemanager.feature.ai

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.StorageOverview
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [AiAssistant] backed by the Anthropic Messages API.
 *
 * The Claude API key is read from [SettingsDataStore.aiApiKey]; when it is blank the
 * assistant reports [isEnabled] as `false` and every operation returns a graceful
 * [AppResult.Failure] guiding the user to add a key in Settings — it never throws.
 *
 * Each operation builds a single-user-message request and calls the Messages API
 * (`POST https://api.anthropic.com/v1/messages`) on the injected [IoDispatcher],
 * returning the model's text. The assistant only operates on the prompt text it
 * constructs from the provided [FileItem]/[StorageOverview]; it never reads file
 * contents or fabricates them.
 */
@Singleton
class AnthropicAiAssistant @Inject constructor(
    private val settings: SettingsDataStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AiAssistant {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Long-lived scope (this is a process [Singleton]) used to observe the persisted
     * Claude API key off the main thread so [isEnabled] can be served from a cached
     * value without ever blocking.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + io)

    /**
     * Cached "is a non-blank API key persisted?" flag, kept up to date by collecting
     * [SettingsDataStore.aiApiKey] on [scope]. Defaults to `false` until the first
     * value arrives, and falls back to `false` on any read error.
     */
    @Volatile
    private var enabledCache: Boolean = false

    init {
        settings.aiApiKey
            .onEach { key -> enabledCache = key.isNotBlank() }
            .catch { enabledCache = false }
            .launchIn(scope)
    }

    /**
     * Whether a non-blank Claude API key is currently persisted.
     *
     * Served from [enabledCache], which is updated off the main thread by an
     * observer on [SettingsDataStore.aiApiKey]; reading this property never blocks
     * and never touches DataStore directly.
     */
    override val isEnabled: Boolean
        get() = enabledCache

    override suspend fun suggestName(item: FileItem): AppResult<String> {
        val key = currentKey()
        if (key.isBlank()) return notConfigured()

        val prompt = buildString {
            append("Suggest a single cleaner, more descriptive file name for the file below. ")
            append("Keep the original file extension. Reply with ONLY the suggested file name, ")
            append("no explanation, no quotes, no path.\n\n")
            append("Current name: ").append(item.name).append('\n')
            append("Type: ").append(item.type.name).append('\n')
            append("Extension: ").append(item.extension.ifBlank { "(none)" }).append('\n')
            append("Size (bytes): ").append(item.sizeBytes)
        }
        return requestText(key, prompt)
    }

    override suspend fun explainStorage(overview: StorageOverview): AppResult<String> {
        val key = currentKey()
        if (key.isBlank()) return notConfigured()

        val prompt = buildString {
            append("Explain in plain language how storage is being used on this volume, ")
            append("highlighting where space is going and concrete cleanup opportunities. ")
            append("Be concise (a short paragraph or a few bullet points).\n\n")
            append("Volume: ").append(overview.volume.label).append('\n')
            append("Total analyzed bytes: ").append(overview.totalAnalyzedBytes).append('\n')
            append("Breakdown by category:\n")
            for (usage in overview.categories) {
                append("- ").append(usage.category.name)
                    .append(": ").append(usage.sizeBytes).append(" bytes across ")
                    .append(usage.fileCount).append(" files\n")
            }
        }
        return requestText(key, prompt)
    }

    override suspend fun parseNaturalQuery(query: String): AppResult<FilterOption> {
        val key = currentKey()
        if (key.isBlank()) return notConfigured()

        val prompt = buildString {
            append("Extract a concise file search term from the request below. ")
            append("Reply with ONLY the search term — a short string matched against file names ")
            append("(for example a keyword, partial name, or extension like \"pdf\"). ")
            append("No explanation, no quotes.\n\n")
            append("Request: ").append(query)
        }

        // Best-effort: on any failure or parse issue, fall back to the raw query.
        return when (val result = requestText(key, prompt)) {
            is AppResult.Success -> {
                val extracted = result.data.trim().trim('"').takeIf { it.isNotBlank() } ?: query
                AppResult.Success(FilterOption(query = extracted))
            }
            is AppResult.Failure -> AppResult.Success(FilterOption(query = query))
        }
    }

    // region Internals --------------------------------------------------------

    /**
     * Reads the persisted Claude API key. Returns "" on any error so callers treat
     * the assistant as not configured rather than crashing.
     *
     * Suspends and reads on [io] so it never blocks the main thread.
     */
    private suspend fun currentKey(): String = withContext(io) {
        try {
            settings.aiApiKey.first()
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Issues a single-user-message request to the Anthropic Messages API and returns
     * the concatenated text content. All failures are mapped to [AppResult.Failure].
     */
    private suspend fun requestText(apiKey: String, userPrompt: String): AppResult<String> =
        withContext(io) {
            try {
                val payload = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", MAX_TOKENS)
                    put(
                        "messages",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", userPrompt)
                            },
                        ),
                    )
                }

                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", ANTHROPIC_VERSION)
                    .addHeader("content-type", "application/json")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext AppResult.Failure(
                            AppError.Unknown(extractError(body, response.code)),
                        )
                    }
                    val text = extractText(body)
                    if (text.isBlank()) {
                        AppResult.Failure(AppError.Unknown("The AI returned an empty response."))
                    } else {
                        AppResult.Success(text)
                    }
                }
            } catch (io: IOException) {
                AppResult.Failure(
                    AppError.Io(detail = io.message ?: "Network error contacting the AI.", cause = io),
                )
            } catch (t: Throwable) {
                AppResult.Failure(
                    AppError.Unknown(t.message ?: "Unexpected error contacting the AI."),
                )
            }
        }

    /**
     * Extracts and concatenates the text from the response `content` array.
     * Returns "" when the body cannot be parsed.
     */
    private fun extractText(body: String): String = try {
        val content = JSONObject(body).optJSONArray("content") ?: JSONArray()
        buildString {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") {
                    append(block.optString("text"))
                }
            }
        }.trim()
    } catch (_: Throwable) {
        ""
    }

    /** Pulls a human-readable error message out of an error response, with a fallback. */
    private fun extractError(body: String, code: Int): String = try {
        val error = JSONObject(body).optJSONObject("error")
        val message = error?.optString("message").orEmpty()
        message.ifBlank { "AI request failed (HTTP $code)." }
    } catch (_: Throwable) {
        "AI request failed (HTTP $code)."
    }

    private fun <T> notConfigured(): AppResult<T> =
        AppResult.Failure(AppError.Unknown(NOT_CONFIGURED_MESSAGE))

    // endregion

    private companion object {
        const val ENDPOINT: String = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION: String = "2023-06-01"
        const val MODEL: String = "claude-3-5-haiku-20241022"
        const val MAX_TOKENS: Int = 512
        const val NOT_CONFIGURED_MESSAGE: String =
            "Add a Claude API key in Settings to enable AI."

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
