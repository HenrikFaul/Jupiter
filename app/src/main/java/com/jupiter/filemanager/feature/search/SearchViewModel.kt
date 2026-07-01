package com.jupiter.filemanager.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.feature.ai.AiAssistant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UI state for the search screen.
 *
 * @property query the current raw search text entered by the user.
 * @property results files discovered so far for the active search.
 * @property isSearching whether a search is currently running.
 * @property error a user-facing error message, or null when there is none.
 * @property naturalLanguage whether natural-language interpretation is enabled.
 * @property aiInterpreting whether the AI assistant is currently parsing the query.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<FileItem> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val naturalLanguage: Boolean = false,
    val aiInterpreting: Boolean = false,
)

/**
 * Drives the search screen.
 *
 * A search walks the storage root via [FileRepository.search], streaming matches
 * into [SearchUiState.results] as they are discovered. When natural-language mode
 * is enabled and the [AiAssistant] is configured, the raw query is first parsed
 * into a structured [FilterOption]; otherwise a plain case-insensitive substring
 * filter is used.
 *
 * When the persistent file index is enabled (see [SettingsDataStore.indexingEnabled])
 * and the query is a plain substring, the cached index is queried first via
 * [FileIndexRepository.search] to render instant results, then reconciled with a
 * fresh live walk so anything new/removed since the last index pass is reflected.
 * The index is a best-effort cache: when it is empty or disabled, behavior is
 * identical to the original walk-only search.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val indexRepository: FileIndexRepository,
    private val settings: SettingsDataStore,
    private val aiAssistant: AiAssistant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Job for the in-flight search so a new search cancels the previous one. */
    private var searchJob: Job? = null

    /** Updates the current query text without triggering a search. */
    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
    }

    /** Toggles natural-language interpretation of the query. */
    fun toggleNaturalLanguage() {
        _uiState.update { it.copy(naturalLanguage = !it.naturalLanguage) }
    }

    /** Clears the query, results, and any in-flight search. */
    fun clear() {
        searchJob?.cancel()
        searchJob = null
        _uiState.value = SearchUiState()
    }

    /**
     * Executes a search for the current query against the storage root.
     *
     * A blank query is ignored. Any running search is cancelled before the new
     * one starts. Results are collected incrementally into the UI state.
     */
    fun search() {
        val rawQuery = _uiState.value.query.trim()
        if (rawQuery.isEmpty()) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isSearching = false,
                    aiInterpreting = false,
                    error = null,
                )
            }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    aiInterpreting = false,
                    error = null,
                    results = emptyList(),
                )
            }

            // Fast path: when the index is enabled, show cached matches immediately
            // so results appear without waiting for the storage walk. This is a
            // one-shot snapshot; the live walk below then supersedes it. A blank
            // query never reaches here (guarded above).
            val indexEnabled = withContext(Dispatchers.IO) {
                runCatching { settings.indexingEnabled.first() }.getOrDefault(true)
            }
            val instant: List<FileItem> = if (indexEnabled) {
                runCatching { indexRepository.search(rawQuery).first() }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            if (instant.isNotEmpty()) {
                _uiState.update { it.copy(results = instant) }
            }

            val filter = resolveFilter(rawQuery)
            val rootPath = fileRepository.rootDirectory()

            // Accumulator for the authoritative live walk. Starts empty so files
            // removed since the last index pass drop out of the results as the
            // walk progresses.
            val collected = mutableListOf<FileItem>()
            var walkEmitted = false

            fileRepository.search(rootPath, filter)
                .catch { throwable ->
                    // On walk failure keep any instant index results already shown
                    // rather than clearing them, so the user still sees something.
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            aiInterpreting = false,
                            error = throwable.message ?: "Search failed.",
                        )
                    }
                }
                .onCompletion {
                    // If the live walk produced nothing (e.g. no storage access)
                    // but the index had matches, fall back to the index results.
                    if (!walkEmitted && instant.isNotEmpty()) {
                        _uiState.update {
                            it.copy(isSearching = false, aiInterpreting = false, results = instant)
                        }
                    } else {
                        _uiState.update { it.copy(isSearching = false, aiInterpreting = false) }
                    }
                }
                .collect { item ->
                    if (!walkEmitted) {
                        // First live result: clear the instant index snapshot so the
                        // walk's authoritative results replace it cleanly.
                        walkEmitted = true
                        collected.clear()
                    }
                    collected.add(item)
                    _uiState.update { it.copy(results = collected.toList()) }
                }
        }
    }

    /**
     * Produces the [FilterOption] used for the search.
     *
     * When natural-language mode is active and the assistant is enabled, the
     * query is parsed via [AiAssistant.parseNaturalQuery]; on failure (or when AI
     * is unavailable) the method falls back to a plain substring filter.
     */
    private suspend fun resolveFilter(rawQuery: String): FilterOption {
        val plainFilter = FilterOption(query = rawQuery, showHidden = false)

        // Read availability off the main thread. isEnabled is backed by a cached
        // value, so this never blocks; reading it on IO keeps the contract explicit.
        val aiAvailable = withContext(Dispatchers.IO) { aiAssistant.isEnabled }
        if (!_uiState.value.naturalLanguage || !aiAvailable) {
            return plainFilter
        }

        _uiState.update { it.copy(aiInterpreting = true) }
        val parsed = when (val result = aiAssistant.parseNaturalQuery(rawQuery)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> plainFilter
        }
        _uiState.update { it.copy(aiInterpreting = false) }
        return parsed
    }
}
