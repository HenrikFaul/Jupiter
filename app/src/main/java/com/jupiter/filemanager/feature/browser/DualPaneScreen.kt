package com.jupiter.filemanager.feature.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.feature.browser.components.Breadcrumbs
import com.jupiter.filemanager.feature.browser.components.FileRow
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView

/**
 * A two-pane file browser. Each pane is an independent column with its own
 * breadcrumb trail and file listing, backed by its own
 * [FileBrowserViewModel] instance (keyed so the two are distinct). Tapping a
 * directory navigates that pane; tapping a file invokes [onOpenFile].
 *
 * Pure UI: all IO is delegated to the per-pane ViewModels.
 *
 * @param onOpenFile invoked when a (non-directory) file is tapped in either pane.
 * @param onBack invoked when the top bar's back affordance is pressed.
 */
@Composable
fun DualPaneScreen(
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
) {
    val leftViewModel: FileBrowserViewModel = hiltViewModel(key = "paneLeft")
    val rightViewModel: FileBrowserViewModel = hiltViewModel(key = "paneRight")

    val leftState by leftViewModel.uiState.collectAsStateWithLifecycle()
    val rightState by rightViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dual pane") },
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
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Pane(
                state = leftState,
                onCrumbPath = leftViewModel::openDirectory,
                onOpenFile = onOpenFile,
                onOpenDirectory = leftViewModel::openDirectory,
                onRetry = leftViewModel::refresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Pane(
                state = rightState,
                onCrumbPath = rightViewModel::openDirectory,
                onOpenFile = onOpenFile,
                onOpenDirectory = rightViewModel::openDirectory,
                onRetry = rightViewModel::refresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * One side of the dual-pane layout: a breadcrumb trail above an independent file
 * listing. Renders honest loading / error / empty states from [state].
 */
@Composable
private fun Pane(
    state: FileBrowserUiState,
    onCrumbPath: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onOpenDirectory: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Breadcrumbs(
            crumbs = state.breadcrumbs,
            onCrumbClick = { onCrumbPath(it.path) },
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.items.isEmpty() -> {
                    LoadingView(modifier = Modifier.fillMaxSize())
                }

                state.error != null && state.items.isEmpty() -> {
                    ErrorView(
                        message = state.error,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                state.items.isEmpty() -> {
                    EmptyView(
                        title = "Empty folder",
                        message = "This folder has no items.",
                        icon = Icons.Outlined.FolderOff,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(items = state.items, key = { it.path }) { item ->
                            FileRow(
                                item = item,
                                selected = false,
                                selectionMode = false,
                                onClick = {
                                    if (item.isDirectory) {
                                        onOpenDirectory(item.path)
                                    } else {
                                        onOpenFile(item)
                                    }
                                },
                                onLongClick = {
                                    if (item.isDirectory) {
                                        onOpenDirectory(item.path)
                                    } else {
                                        onOpenFile(item)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
