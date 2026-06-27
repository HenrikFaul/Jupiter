package com.jupiter.filemanager.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.feature.browser.components.FileRow
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView

/**
 * Search screen: a query field with an optional natural-language toggle, a
 * streamed results list (reusing the browser's [FileRow]), and empty/loading/error
 * states.
 *
 * The screen is pure UI: all work runs in [SearchViewModel]. Tapping a result
 * delegates to [onOpenFile] (directories included — the caller decides how to
 * handle them).
 *
 * @param onOpenFile invoked when a result row is tapped.
 * @param onBack invoked when the user navigates back.
 */
@Composable
fun SearchScreen(
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreenContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onToggleNaturalLanguage = viewModel::toggleNaturalLanguage,
        onClear = viewModel::clear,
        onOpenFile = onOpenFile,
        onBack = onBack,
    )
}

/**
 * Stateless content for the search screen. Receives the immutable [SearchUiState]
 * and forwards all interactions to the supplied callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreenContent(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleNaturalLanguage: () -> Unit,
    onClear: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchInputBar(
                query = uiState.query,
                onQueryChange = onQueryChange,
                onSearch = {
                    keyboardController?.hide()
                    onSearch()
                },
                onClear = onClear,
            )

            NaturalLanguageToggleRow(
                enabled = uiState.naturalLanguage,
                onToggle = onToggleNaturalLanguage,
            )

            if (uiState.aiInterpreting) {
                InterpretingIndicator()
            } else if (uiState.isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            HorizontalDivider()

            SearchResults(
                uiState = uiState,
                onOpenFile = onOpenFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/**
 * The query text field with leading search icon and a trailing clear action.
 * Submitting via the IME search action triggers [onSearch].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        placeholder = { Text(text = "Search files and folders") },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Clear",
                    )
                }
            }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = ImeAction.Search,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearch() },
        ),
    )
}

/**
 * Row with a label and a [Switch] controlling natural-language interpretation.
 */
@Composable
private fun NaturalLanguageToggleRow(
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Natural language",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Interpret your query with the AI assistant",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() },
        )
    }
}

/**
 * Inline indicator shown while the AI assistant parses a natural-language query.
 */
@Composable
private fun InterpretingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.width(18.dp),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Interpreting your query…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Renders the body below the input area, choosing between error, empty and
 * results states. Streaming results are shown as soon as they arrive even while
 * the search is still running.
 */
@Composable
private fun SearchResults(
    uiState: SearchUiState,
    onOpenFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.error != null -> {
            ErrorView(
                message = uiState.error,
                modifier = modifier,
            )
        }

        uiState.results.isNotEmpty() -> {
            LazyColumn(modifier = modifier) {
                items(
                    items = uiState.results,
                    key = { it.path },
                ) { item ->
                    FileRow(
                        item = item,
                        selected = false,
                        selectionMode = false,
                        onClick = { onOpenFile(item) },
                        onLongClick = { onOpenFile(item) },
                    )
                    HorizontalDivider()
                }
            }
        }

        uiState.isSearching || uiState.aiInterpreting -> {
            EmptyView(
                title = "Searching…",
                message = "Looking through your storage for matches.",
                icon = Icons.Outlined.Search,
                modifier = modifier,
            )
        }

        uiState.query.isBlank() -> {
            EmptyView(
                title = "Search your files",
                message = "Type a name or keyword to find files and folders.",
                icon = Icons.Outlined.Search,
                modifier = modifier,
            )
        }

        else -> {
            EmptyView(
                title = "No results",
                message = "Nothing matched \"${uiState.query}\". Try a different query.",
                icon = Icons.Outlined.SearchOff,
                modifier = modifier,
            )
        }
    }
}
