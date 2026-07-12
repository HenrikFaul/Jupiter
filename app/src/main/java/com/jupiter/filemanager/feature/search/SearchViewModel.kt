package com.jupiter.filemanager.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.IndexStateRepository
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
 * @property selectedFilter the user-selected scope applied equally to indexed and live results.
 * @property recentSearches persisted, on-device-only submitted query history (newest first).
 */
data class SearchUiState(
    val query: String = "",
    val results: List<FileItem> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val naturalLanguage: Boolean = false,
    val aiInterpreting: Boolean = false,
    val selectedFilter: SearchResultFilter = SearchResultFilter.ALL,
    val recentSearches: List<String> = emptyList(),
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
    private val indexStateRepository: IndexStateRepository,
    private val settings: SettingsDataStore,
    private val aiAssistant: AiAssistant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Job for the in-flight search so a new search cancels the previous one. */
    private var searchJob: Job? = null

    /** Monotonic request token so a cancelled search can never overwrite a newer one. */
    private var searchGeneration: Long = 0

    init {
        viewModelScope.launch {
            settings.recentSearches
                .catch { emit(emptyList()) }
                .collect { recentSearches ->
                    _uiState.update { it.copy(recentSearches = recentSearches) }
                }
        }
    }

    /** Updates the current query text without triggering a search. */
    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
    }

    /** Toggles natural-language interpretation of the query. */
    fun toggleNaturalLanguage() {
        _uiState.update { state ->
            val enabled = !state.naturalLanguage
            state.copy(
                naturalLanguage = enabled,
                selectedFilter = when {
                    enabled -> SearchResultFilter.AI_SEARCH
                    state.selectedFilter == SearchResultFilter.AI_SEARCH -> SearchResultFilter.ALL
                    else -> state.selectedFilter
                },
            )
        }
    }

    /**
     * Selects a visible search scope. Changing a scope re-runs a non-blank query
     * without duplicating its history entry.
     */
    fun selectFilter(filter: SearchResultFilter) {
        val current = _uiState.value
        val naturalLanguage = filter.enablesNaturalLanguage
        if (current.selectedFilter == filter && current.naturalLanguage == naturalLanguage) return

        _uiState.update {
            it.copy(
                selectedFilter = filter,
                naturalLanguage = naturalLanguage,
            )
        }
        if (current.query.isNotBlank()) {
            executeSearch(rememberQuery = false)
        }
    }

    /** Restores a locally persisted recent query and immediately executes it. */
    fun selectRecentSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        onQueryChange(normalized)
        // Re-submitting an older term promotes it to the top without creating a
        // duplicate because SettingsDataStore de-duplicates case-insensitively.
        executeSearch(rememberQuery = true)
    }

    /** Removes every locally persisted search term; file index data is unaffected. */
    fun clearRecentSearches() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { settings.clearRecentSearches() }
        }
    }

    /** Clears the query, results, and any in-flight search. */
    fun clear() {
        searchGeneration += 1
        searchJob?.cancel()
        searchJob = null
        // Keep local history and the selected scope intact: clear is the text-field
        // affordance, not a request to erase private on-device search history.
        _uiState.update {
            it.copy(
                query = "",
                results = emptyList(),
                isSearching = false,
                error = null,
                aiInterpreting = false,
            )
        }
    }

    /**
     * Executes a search for the current query against the storage root.
     *
     * A blank query is ignored. Any running search is cancelled before the new
     * one starts. Results are collected incrementally into the UI state.
     */
    fun search() = executeSearch(rememberQuery = true)

    private fun executeSearch(rememberQuery: Boolean) {
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

        if (rememberQuery) {
            viewModelScope.launch(Dispatchers.IO) {
                // A history-write failure must never alter or abort the real search.
                runCatching { settings.addRecentSearch(rawQuery) }
            }
        }

        val selectedFilter = _uiState.value.selectedFilter
        val naturalLanguageRequested = _uiState.value.naturalLanguage
        val generation = ++searchGeneration
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            updateForSearch(generation) {
                it.copy(
                    isSearching = true,
                    aiInterpreting = false,
                    error = null,
                    results = emptyList(),
                )
            }

            val (indexEnabled, indexComplete) = withContext(Dispatchers.IO) {
                val enabled = runCatching { settings.indexingEnabled.first() }.getOrDefault(true)
                // Authoritative usability comes from the Room index_state (not a row count):
                // COMPLETE, or a rescan running over a prior complete generation. A first-ever
                // partial index never suppresses the walk.
                val complete = runCatching { indexStateRepository.isUsable() }
                    .getOrDefault(false)
                enabled to complete
            }
            if (!isCurrentSearch(generation)) return@launch

            // AUTHORITATIVE INDEX PATH: once the index has been fully built, a plain-text
            // search is served from Room ONLY — no filesystem walk. This is the fix for
            // "search re-scans the whole volume every time": after a completed index the
            // storage tree is never traversed for a normal search.
            val naturalLanguage = naturalLanguageRequested &&
                withContext(Dispatchers.IO) { aiAssistant.isEnabled }
            if (indexEnabled && indexComplete && !naturalLanguage) {
                val indexed = runCatching { indexRepository.search(rawQuery).first() }
                    .getOrDefault(emptyList())
                    .filter(selectedFilter::matches)
                updateForSearch(generation) {
                    it.copy(results = indexed, isSearching = false, aiInterpreting = false)
                }
                return@launch
            }

            // FALLBACK (index disabled, not yet built, or natural-language query): show any
            // cached index matches instantly, then reconcile with a live walk. This only
            // runs until the first full index completes; after that the branch above wins.
            val instant: List<FileItem> = if (indexEnabled) {
                runCatching { indexRepository.search(rawQuery).first() }
                    .getOrDefault(emptyList())
                    .filter(selectedFilter::matches)
            } else {
                emptyList()
            }
            if (!isCurrentSearch(generation)) return@launch
            if (instant.isNotEmpty()) {
                updateForSearch(generation) { it.copy(results = instant) }
            }

            val filter = selectedFilter.applyTo(
                resolveFilter(
                    rawQuery = rawQuery,
                    naturalLanguage = naturalLanguage,
                    generation = generation,
                ),
            )
            if (!isCurrentSearch(generation)) return@launch
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
                    updateForSearch(generation) {
                        it.copy(
                            isSearching = false,
                            aiInterpreting = false,
                            error = throwable.message ?: "Search failed.",
                        )
                    }
                }
                .onCompletion {
                    if (!isCurrentSearch(generation)) return@onCompletion
                    // If the live walk produced nothing (e.g. no storage access)
                    // but the index had matches, fall back to the index results.
                    if (!walkEmitted && instant.isNotEmpty()) {
                        updateForSearch(generation) {
                            it.copy(isSearching = false, aiInterpreting = false, results = instant)
                        }
                    } else {
                        updateForSearch(generation) {
                            it.copy(isSearching = false, aiInterpreting = false)
                        }
                    }
                }
                .collect { item ->
                    if (!selectedFilter.matches(item)) return@collect
                    if (!walkEmitted) {
                        // First live result: clear the instant index snapshot so the
                        // walk's authoritative results replace it cleanly.
                        walkEmitted = true
                        collected.clear()
                    }
                    collected.add(item)
                    updateForSearch(generation) { it.copy(results = collected.toList()) }
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
    private suspend fun resolveFilter(
        rawQuery: String,
        naturalLanguage: Boolean,
        generation: Long,
    ): FilterOption {
        val plainFilter = FilterOption(query = rawQuery, showHidden = false)

        if (!naturalLanguage) {
            return plainFilter
        }

        updateForSearch(generation) { it.copy(aiInterpreting = true) }
        val parsed = when (val result = aiAssistant.parseNaturalQuery(rawQuery)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> plainFilter
        }
        updateForSearch(generation) { it.copy(aiInterpreting = false) }
        return parsed
    }

    private fun isCurrentSearch(generation: Long): Boolean = generation == searchGeneration

    private inline fun updateForSearch(
        generation: Long,
        transform: (SearchUiState) -> SearchUiState,
    ) {
        if (isCurrentSearch(generation)) {
            _uiState.update(transform)
        }
    }
}
