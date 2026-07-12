package com.jupiter.filemanager.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.feature.browser.components.FileRow
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.SectionHeader
import com.jupiter.filemanager.ui.components.iconForFile

/**
 * Search screen: a query field, persistent local recent searches, meaningful
 * result-scope chips (including the existing AI/natural-language mode), a streamed
 * results list (reusing the browser's [FileRow]), and empty/loading/error states.
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
        onSelectFilter = viewModel::selectFilter,
        onSelectRecentSearch = viewModel::selectRecentSearch,
        onClearRecentSearches = viewModel::clearRecentSearches,
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
    onSelectFilter: (SearchResultFilter) -> Unit,
    onSelectRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
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

            if (uiState.recentSearches.isNotEmpty()) {
                RecentSearchesRow(
                    recentSearches = uiState.recentSearches,
                    onSelectSearch = { query ->
                        keyboardController?.hide()
                        onSelectRecentSearch(query)
                    },
                    onClearAll = onClearRecentSearches,
                )
            }

            SearchFilterChips(
                selectedFilter = uiState.selectedFilter,
                onSelect = onSelectFilter,
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

@Composable
private fun RecentSearchesRow(
    recentSearches: List<String>,
    onSelectSearch: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearAll) {
                Text(text = "Clear all")
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = recentSearches,
                key = { it },
            ) { query ->
                AssistChip(
                    onClick = { onSelectSearch(query) },
                    label = { Text(text = query, maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Mockup-aligned search scopes. They are not decorative: the selected value is
 * applied by [SearchViewModel] to both index-backed and live filesystem results.
 */
@Composable
private fun SearchFilterChips(
    selectedFilter: SearchResultFilter,
    onSelect: (SearchResultFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = SearchResultFilter.entries,
            key = { it.name },
        ) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelect(filter) },
                label = { Text(text = filter.label) },
                leadingIcon = {
                    Icon(
                        imageVector = searchFilterIcon(filter),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

private fun searchFilterIcon(filter: SearchResultFilter): ImageVector = when (filter) {
    SearchResultFilter.ALL -> Icons.Filled.Apps
    SearchResultFilter.FILES -> Icons.Filled.InsertDriveFile
    SearchResultFilter.FOLDERS -> Icons.Filled.Folder
    SearchResultFilter.PDFS -> Icons.Filled.PictureAsPdf
    SearchResultFilter.IMAGES -> Icons.Filled.Image
    SearchResultFilter.AI_SEARCH -> Icons.Filled.AutoAwesome
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
 * Horizontal strip of image-type results, surfaced above the main vertical list
 * so photos are easy to scan at a glance. Each tile is tappable and delegates to
 * [onOpenFile].
 */
@Composable
private fun ImageResultsRow(
    images: List<FileItem>,
    onOpenFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        items(
            items = images,
            key = { it.path },
        ) { item ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenFile(item) },
            ) {
                Icon(
                    imageVector = iconForFile(item),
                    contentDescription = item.name,
                    modifier = Modifier
                        .padding(18.dp)
                        .fillMaxSize(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            val imageResults = uiState.results.filter { it.type == FileType.IMAGE }
            LazyColumn(modifier = modifier) {
                item {
                    SectionHeader(
                        // Do not imply generated content: every row below is a real
                        // file-system/index entry, including when AI interpreted the query.
                        title = "Results",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                if (imageResults.isNotEmpty()) {
                    item {
                        ImageResultsRow(
                            images = imageResults,
                            onOpenFile = onOpenFile,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }

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
