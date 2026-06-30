package com.jupiter.filemanager.feature.preview

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Immutable UI state for the PDF viewer.
 *
 * @property fileName display name of the document being shown.
 * @property pageCount total number of pages, or 0 before the document loads.
 * @property currentPage zero-based index of the page currently rendered.
 * @property pageBitmap the rendered bitmap for [currentPage], or null while loading.
 * @property isLoading whether a page (or the document) is currently being rendered.
 * @property error a human-readable error message, or null when there is none.
 */
data class PdfViewerUiState(
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val pageBitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Renders a PDF document page-by-page using the platform [PdfRenderer]
 * ([android.graphics.pdf.PdfRenderer], available since API 21; minSdk here is 26).
 *
 * The target path is read from the navigation arguments
 * ([Destination.PdfViewer.ARG_PATH]). Each page is rasterized to a [Bitmap] on the
 * IO dispatcher so the main thread is never blocked. The renderer and descriptor
 * are held open for the lifetime of the ViewModel and released in [onCleared].
 *
 * Every IO interaction is guarded; failures surface as [PdfViewerUiState.error]
 * rather than crashing the app.
 */
@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    init {
        val rawArg = savedStateHandle.get<String>(Destination.PdfViewer.ARG_PATH)
        val path = rawArg
            ?.takeIf { it.isNotBlank() }
            ?.let { android.net.Uri.decode(it) }
        if (path == null) {
            _uiState.update {
                it.copy(isLoading = false, error = "No document to display.")
            }
        } else {
            open(path)
        }
    }

    /** Opens the document at [path] and renders its first page. */
    private fun open(path: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val file = File(path)
                    if (!file.isFile) return@withContext OpenResult.Failure("Document not found.")
                    if (!file.canRead()) return@withContext OpenResult.Failure("Document cannot be read.")
                    val fd = ParcelFileDescriptor.open(
                        file,
                        ParcelFileDescriptor.MODE_READ_ONLY,
                    )
                    val pdf = PdfRenderer(fd)
                    descriptor = fd
                    renderer = pdf
                    OpenResult.Success(file.name, pdf.pageCount)
                } catch (e: IOException) {
                    OpenResult.Failure(e.message ?: "Failed to open document.")
                } catch (e: SecurityException) {
                    OpenResult.Failure("Access denied while opening document.")
                } catch (e: Exception) {
                    OpenResult.Failure(e.message ?: "Unable to open this document.")
                }
            }

            when (outcome) {
                is OpenResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = outcome.message)
                }
                is OpenResult.Success -> {
                    _uiState.update {
                        it.copy(
                            fileName = outcome.name,
                            pageCount = outcome.pageCount,
                            currentPage = 0,
                            error = null,
                        )
                    }
                    renderPage(0)
                }
            }
        }
    }

    /** Renders the next page, if one exists. */
    fun nextPage() {
        val state = _uiState.value
        if (state.currentPage + 1 < state.pageCount) {
            renderPage(state.currentPage + 1)
        }
    }

    /** Renders the previous page, if one exists. */
    fun previousPage() {
        val state = _uiState.value
        if (state.currentPage - 1 >= 0) {
            renderPage(state.currentPage - 1)
        }
    }

    /** Renders the page at [index] into a bitmap and publishes it into state. */
    private fun renderPage(index: Int) {
        _uiState.update { it.copy(isLoading = true, error = null, currentPage = index) }
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val pdf = renderer ?: return@withContext null
                try {
                    if (index !in 0 until pdf.pageCount) return@withContext null
                    pdf.openPage(index).use { page ->
                        val scale = 2
                        val width = (page.width * scale).coerceAtLeast(1)
                        val height = (page.height * scale).coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (bitmap == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to render this page.")
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, pageBitmap = bitmap, error = null)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            renderer?.close()
        } catch (_: Exception) {
            // Already closed or never opened; nothing to recover.
        }
        try {
            descriptor?.close()
        } catch (_: Exception) {
            // Already closed or never opened; nothing to recover.
        }
        renderer = null
        descriptor = null
    }

    private sealed interface OpenResult {
        data class Success(val name: String, val pageCount: Int) : OpenResult
        data class Failure(val message: String) : OpenResult
    }
}
