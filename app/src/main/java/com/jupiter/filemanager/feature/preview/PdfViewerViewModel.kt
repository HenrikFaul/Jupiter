package com.jupiter.filemanager.feature.preview

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Immutable UI state for the PDF viewer.
 *
 * @property fileName display name of the document being shown.
 * @property filePath absolute path of the document on disk, used for the
 *   "Open with" external-app fallback. Empty before the document resolves.
 * @property pageCount total number of pages, or 0 before the document loads.
 * @property currentPage zero-based index of the page currently visible (driven by
 *   the scroll position of the page list). Used for the page indicator.
 * @property isLoading whether the document is currently being opened.
 * @property error a human-readable error message, or null when there is none.
 */
data class PdfViewerUiState(
    val fileName: String = "",
    val filePath: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Renders a PDF document using the platform [PdfRenderer]
 * ([android.graphics.pdf.PdfRenderer], available since API 21; minSdk here is 26).
 *
 * The target path is read from the navigation arguments
 * ([Destination.PdfViewer.ARG_PATH]). The document is opened via
 * [ParcelFileDescriptor.open] in [ParcelFileDescriptor.MODE_READ_ONLY]. Once open,
 * [PdfViewerUiState.pageCount] is published and the screen lays the pages out in a
 * vertical list; each visible page is rasterized on demand through [renderPage],
 * which runs on the IO dispatcher so the main thread is never blocked.
 *
 * [PdfRenderer] is not thread-safe and only one page may be open at a time, so all
 * access goes through [renderMutex]. The renderer and descriptor are held open for
 * the lifetime of the ViewModel and released in [onCleared].
 *
 * Every IO interaction is guarded; failures surface as [PdfViewerUiState.error]
 * (for opening) or a null bitmap (for an individual page) rather than crashing.
 */
@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    /** Guards [renderer]; PdfRenderer allows only one open page at a time. */
    private val renderMutex = Mutex()

    init {
        val rawArg = savedStateHandle.get<String>(Destination.PdfViewer.ARG_PATH)
        val path = rawArg
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.decode(it) }
        if (path == null) {
            _uiState.update {
                it.copy(isLoading = false, error = "No document to display.")
            }
        } else {
            _uiState.update { it.copy(filePath = path, fileName = File(path).name) }
            open(path)
        }
    }

    /** Opens the document at [path] and publishes its page count. */
    private fun open(path: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val outcome = withContext(ioDispatcher) {
                renderMutex.withLock {
                    try {
                        val file = File(path)
                        if (!file.isFile) return@withLock OpenResult.Failure("Document not found.")
                        if (!file.canRead()) return@withLock OpenResult.Failure("Document cannot be read.")
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
            }

            when (outcome) {
                is OpenResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = outcome.message)
                }
                is OpenResult.Success -> _uiState.update {
                    it.copy(
                        fileName = outcome.name,
                        pageCount = outcome.pageCount,
                        isLoading = false,
                        error = null,
                    )
                }
            }
        }
    }

    /**
     * Rasterizes the page at [index] to a [Bitmap], scaled to roughly [targetWidth]
     * pixels wide (height kept proportional). Returns null when the page cannot be
     * rendered (out of range, renderer closed, or an IO error). Safe to call from the
     * composition for visible pages — work runs on the IO dispatcher.
     */
    suspend fun renderPage(index: Int, targetWidth: Int): Bitmap? =
        withContext(ioDispatcher) {
            renderMutex.withLock {
                val pdf = renderer ?: return@withLock null
                try {
                    if (index !in 0 until pdf.pageCount) return@withLock null
                    pdf.openPage(index).use { page ->
                        val safeWidth = targetWidth.coerceAtLeast(1)
                        val pageWidth = page.width.coerceAtLeast(1)
                        val pageHeight = page.height.coerceAtLeast(1)
                        val scale = safeWidth.toFloat() / pageWidth.toFloat()
                        val width = safeWidth
                        val height = (pageHeight * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }

    /** Records the page currently visible in the list, for the page indicator. */
    fun onPageVisible(index: Int) {
        if (index < 0) return
        if (_uiState.value.currentPage == index) return
        _uiState.update { it.copy(currentPage = index) }
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
