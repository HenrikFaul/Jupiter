package com.jupiter.filemanager.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.JupiterFloatingBottomNavigation
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.JupiterMainTab
import com.jupiter.filemanager.ui.components.JupiterPill
import com.jupiter.filemanager.ui.components.JupiterWordmark
import com.jupiter.filemanager.ui.components.iconForFile
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Reference-faithful search surface backed exclusively by the existing index and
 * filesystem search pipelines. Every visible result, path and metadata value is
 * read from a real [FileItem]; the UI never generates search-result snippets.
 */
@Composable
fun SearchScreen(
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
    onMainTabSelected: (JupiterMainTab) -> Unit = {},
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
        onMainTabSelected = onMainTabSelected,
    )
}

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
    onMainTabSelected: (JupiterMainTab) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var showHistory by remember { mutableStateOf(true) }
    var moreExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            JupiterFloatingBottomNavigation(
                selectedTab = JupiterMainTab.HOME,
                onTabSelected = onMainTabSelected,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 18.dp, end = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JupiterWordmark(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { showHistory = !showHistory },
                    enabled = uiState.recentSearches.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = if (showHistory) "Hide recent searches" else "Show recent searches",
                    )
                }
                Box {
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Search actions")
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Back") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            },
                            onClick = {
                                moreExpanded = false
                                onBack()
                            },
                        )
                        if (uiState.query.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Clear current search") },
                                leadingIcon = { Icon(Icons.Outlined.Clear, contentDescription = null) },
                                onClick = {
                                    moreExpanded = false
                                    onClear()
                                },
                            )
                        }
                        if (uiState.recentSearches.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Clear search history") },
                                leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                                onClick = {
                                    moreExpanded = false
                                    onClearRecentSearches()
                                },
                            )
                        }
                    }
                }
            }

            SearchInputBar(
                query = uiState.query,
                selectedFilter = uiState.selectedFilter,
                onQueryChange = onQueryChange,
                onSelectFilter = onSelectFilter,
                onSearch = {
                    keyboardController?.hide()
                    onSearch()
                },
                onClear = onClear,
            )

            if (showHistory && uiState.recentSearches.isNotEmpty()) {
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

            when {
                uiState.aiInterpreting -> InterpretingIndicator()
                uiState.isSearching -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

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

@Composable
private fun SearchInputBar(
    query: String,
    selectedFilter: SearchResultFilter,
    onQueryChange: (String) -> Unit,
    onSelectFilter: (SearchResultFilter) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = JupiterDesign.PillShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(14.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search files and folders",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                    }
                }
                IconButton(onClick = { filterMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Search scope: ${selectedFilter.label}",
                        tint = if (selectedFilter == SearchResultFilter.ALL) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
        DropdownMenu(
            expanded = filterMenuExpanded,
            onDismissRequest = { filterMenuExpanded = false },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            SearchResultFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.label) },
                    leadingIcon = {
                        Icon(searchFilterIcon(filter), contentDescription = null)
                    },
                    trailingIcon = if (filter == selectedFilter) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        filterMenuExpanded = false
                        onSelectFilter(filter)
                    },
                )
            }
        }
    }
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
                .padding(start = 24.dp, end = 14.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearAll) { Text("Clear all") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(recentSearches, key = { it }) { recentQuery ->
                Surface(
                    modifier = Modifier
                        .height(52.dp)
                        .widthIn(min = 132.dp)
                        .clickable { onSelectSearch(recentQuery) },
                    shape = JupiterDesign.PillShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(recentQuery, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterChips(
    selectedFilter: SearchResultFilter,
    onSelect: (SearchResultFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(vertical = 18.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(SearchResultFilter.entries, key = { it.name }) { filter ->
            JupiterPill(
                selected = selectedFilter == filter,
                onClick = { onSelect(filter) },
                modifier = Modifier
                    .height(58.dp)
                    .widthIn(min = 104.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = searchFilterIcon(filter),
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(filter.label, style = MaterialTheme.typography.labelLarge)
                }
            }
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

@Composable
private fun InterpretingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Interpreting your query…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResults(
    uiState: SearchUiState,
    onOpenFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.error != null && uiState.results.isEmpty() -> ErrorView(
            message = uiState.error,
            modifier = modifier,
        )

        uiState.results.isNotEmpty() -> {
            val sections = remember(uiState.query, uiState.results) {
                sectionSearchResults(uiState.query, uiState.results)
            }
            var showAllRecent by remember(uiState.query) { mutableStateOf(false) }
            var showAllSuggested by remember(uiState.query) { mutableStateOf(false) }
            val expandedRecentPaths = if (showAllRecent) {
                sections.recentResults.asSequence().map(FileItem::path).toHashSet()
            } else {
                emptySet()
            }
            val suggestedMatches = sections.suggestedMatches.filterNot {
                it.path in expandedRecentPaths
            }

            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            ) {
                if (uiState.error != null) {
                    item(key = "partial-error") {
                        Text(
                            text = uiState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (sections.recentResults.isNotEmpty()) {
                    searchResultSection(
                        keyPrefix = "recent",
                        title = "Recent results",
                        items = sections.recentResults,
                        query = uiState.query,
                        expanded = showAllRecent,
                        onToggleExpanded = { showAllRecent = !showAllRecent },
                        onOpenFile = onOpenFile,
                    )
                }
                if (suggestedMatches.isNotEmpty()) {
                    if (sections.recentResults.isNotEmpty()) {
                        item(key = "section-gap") { Spacer(modifier = Modifier.height(18.dp)) }
                    }
                    searchResultSection(
                        keyPrefix = "suggested",
                        title = "Suggested matches",
                        items = suggestedMatches,
                        query = uiState.query,
                        expanded = showAllSuggested,
                        onToggleExpanded = { showAllSuggested = !showAllSuggested },
                        onOpenFile = onOpenFile,
                    )
                }
            }
        }

        uiState.isSearching || uiState.aiInterpreting -> EmptyView(
            title = "Searching…",
            message = "Looking through your storage for matches.",
            icon = Icons.Outlined.Search,
            modifier = modifier,
        )

        uiState.query.isBlank() -> EmptyView(
            title = "Search your files",
            message = "Type a name or keyword to find files and folders.",
            icon = Icons.Outlined.Search,
            modifier = modifier,
        )

        else -> EmptyView(
            title = "No results",
            message = "Nothing matched \"${uiState.query}\". Try a different query.",
            icon = Icons.Outlined.SearchOff,
            modifier = modifier,
        )
    }
}

private fun LazyListScope.searchResultSection(
    keyPrefix: String,
    title: String,
    items: List<FileItem>,
    query: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
) {
    val visibleItems = if (expanded) items else items.take(4)
    item(key = "$keyPrefix:header") {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        ) {
            SearchSectionHeader(
                title = title,
                showViewAll = items.size > 4,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded,
            )
        }
    }
    itemsIndexed(
        items = visibleItems,
        key = { _, item -> "$keyPrefix:${item.path}" },
    ) { index, item ->
        val isLast = index == visibleItems.lastIndex
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = if (isLast) {
                RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            } else {
                RoundedCornerShape(0.dp)
            },
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
        ) {
            SearchResultRow(
                item = item,
                query = query,
                onClick = { onOpenFile(item) },
            )
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    showViewAll: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 10.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (showViewAll) {
            TextButton(onClick = onToggleExpanded) {
                Text(if (expanded) "Show less" else "View all")
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: FileItem,
    query: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(
            icon = iconForFile(item),
            tint = when {
                item.isDirectory -> JupiterDesign.CategoryDownload
                item.type.name == "PDF" -> JupiterDesign.CategoryPdf
                else -> MaterialTheme.colorScheme.primary
            },
            size = 52.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightedText(item.name, query),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val parent = displayParent(item.parentPath)
            if (parent.isNotEmpty()) {
                Text(
                    text = highlightedText(parent, query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = itemMetadata(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open ${item.name}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun highlightedText(text: String, query: String): AnnotatedString {
    val needles = query
        .trim()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
    if (needles.isEmpty()) return AnnotatedString(text)
    val highlight = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val next = needles
                .mapNotNull { needle ->
                    text.indexOf(needle, startIndex = cursor, ignoreCase = true)
                        .takeIf { it >= 0 }
                        ?.let { index -> index to needle.length }
                }
                .minWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
            if (next == null) {
                append(text.substring(cursor))
                break
            }
            val (index, length) = next
            append(text.substring(cursor, index))
            withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(index, index + length))
            }
            cursor = index + length
        }
    }
}

private fun displayParent(parentPath: String?): String = parentPath
    .orEmpty()
    .replace('\\', '/')
    .split('/')
    .filter(String::isNotBlank)
    .takeLast(3)
    .joinToString("  ›  ")

private fun itemMetadata(item: FileItem): String {
    val parts = buildList {
        if (item.lastModified > 0L) add(formatRelativeTime(item.lastModified))
        if (item.isDirectory) {
            item.childCount?.let { add(formatItemCount(it)) } ?: add("Folder")
        } else {
            add(formatBytes(item.sizeBytes))
        }
        item.mimeType?.takeIf(String::isNotBlank)?.let { add(it) }
    }
    return parts.joinToString("  •  ")
}
