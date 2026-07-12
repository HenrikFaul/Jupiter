package com.jupiter.filemanager.feature.preview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import java.io.File

/**
 * Full-screen PDF viewer.
 *
 * The document at the navigation `path` argument is opened by [PdfViewerViewModel]
 * via [android.os.ParcelFileDescriptor] and rasterized with the platform
 * [android.graphics.pdf.PdfRenderer]. Pages are shown in a vertical [LazyColumn];
 * each visible page is rendered to a [Bitmap] on demand (off the main thread) and
 * scaled to the list width. A page indicator in the top bar tracks the first page
 * currently in view.
 *
 * On failure (missing/unreadable/corrupt document) an info card is shown with an
 * "Open with" action that hands the file off to an external app via a
 * [FileProvider] content URI.
 *
 * The ViewModel is obtained via [hiltViewModel] and reads the document path from its
 * `SavedStateHandle`; it closes the renderer in `onCleared`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    onBack: () -> Unit,
) {
    val viewModel: PdfViewerViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val listState = rememberLazyListState()

    // Keep the page indicator in sync with the first visible page.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index -> viewModel.onPageVisible(index) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.fileName.ifBlank { "PDF" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.pageCount > 0) {
                            Text(
                                text = "Page " + (state.currentPage + 1) + " of " + state.pageCount,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            val error = state.error
            when {
                error != null -> PdfErrorCard(
                    message = error,
                    filePath = state.filePath,
                    onOpenWith = { path -> openWithExternalApp(context, path) },
                )

                state.isLoading -> LoadingView()

                state.pageCount > 0 -> {
                    // Track the rendered width of the list so pages are rasterized at
                    // the right resolution (and re-rendered if the width changes, e.g.
                    // on rotation).
                    var listWidthPx by remember { mutableStateOf(0) }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { listWidthPx = it.width },
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            count = state.pageCount,
                            key = { index -> index },
                        ) { index ->
                            PdfPage(
                                index = index,
                                widthPx = listWidthPx,
                                renderPage = viewModel::renderPage,
                            )
                        }
                    }
                }

                else -> LoadingView()
            }
        }
    }
}

/**
 * A single PDF page. Renders lazily when it enters the list and the target [widthPx]
 * is known; shows a placeholder with the page's natural-ish aspect while the bitmap
 * is produced.
 */
@Composable
private fun PdfPage(
    index: Int,
    widthPx: Int,
    renderPage: suspend (Int, Int) -> Bitmap?,
) {
    var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index, widthPx) {
        if (widthPx > 0) {
            bitmap = renderPage(index, widthPx)
        }
    }

    val rendered = bitmap
    if (rendered != null) {
        Image(
            bitmap = rendered.asImageBitmap(),
            contentDescription = "PDF page " + (index + 1),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

/**
 * Info card shown when the document cannot be displayed in-app. Offers an
 * "Open with" action that delegates to an external viewer via [FileProvider].
 */
@Composable
private fun PdfErrorCard(
    message: String,
    filePath: String,
    onOpenWith: (String) -> Unit,
) {
    JupiterCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentPadding = PaddingValues(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            JupiterIconBadge(icon = Icons.Filled.Description)
            Text(
                text = "Can't display this PDF",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (filePath.isNotBlank()) {
                Button(onClick = { onOpenWith(filePath) }) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.height(18.dp),
                    )
                    Text(
                        text = "Open with",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Launches an external app via [Intent.ACTION_VIEW] for the PDF at [path], exposing
 * it through a [FileProvider] content URI (authority = applicationId + ".fileprovider")
 * with read permission granted to the receiver. Failures are swallowed so the UI
 * never crashes when no handler exists.
 */
private fun openWithExternalApp(context: Context, path: String) {
    try {
        val authority = context.packageName + ".fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, File(path))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (_: ActivityNotFoundException) {
        // No app can handle this file; nothing else we can do from here.
    } catch (_: IllegalArgumentException) {
        // FileProvider couldn't map the path (outside configured roots); ignore.
    } catch (_: Exception) {
        // Any other launch failure — keep the viewer alive.
    }
}
