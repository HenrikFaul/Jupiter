package com.jupiter.filemanager.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.feature.ai.AiAssistant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val fileRepository: FileRepository,
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

            val filter = resolveFilter(rawQuery)
            val rootPath = fileRepository.rootDirectory()
            val collected = mutableListOf<FileItem>()

            fileRepository.search(rootPath, filter)
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            aiInterpreting = false,
                            error = throwable.message ?: "Search failed.",
                        )
                    }
                }
                .onCompletion {
                    _uiState.update { it.copy(isSearching = false, aiInterpreting = false) }
                }
                .collect { item ->
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

        if (!_uiState.value.naturalLanguage || !aiAssistant.isEnabled) {
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
