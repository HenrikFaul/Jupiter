package com.jupiter.filemanager.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.repository.FileRepository
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
 * Coarse classification of a file used to choose how the preview screen renders
 * its content.
 */
enum class PreviewKind {
    IMAGE,
    VIDEO,
    AUDIO,
    TEXT,
    PDF,
    UNSUPPORTED,
}

/**
 * Immutable UI state for the file preview screen.
 *
 * @property file the resolved [FileItem] being previewed, or null before it loads.
 * @property previewKind how the file should be rendered (image, text, etc.).
 * @property textContent the loaded text body when [previewKind] is [PreviewKind.TEXT];
 *   null otherwise, or when loading failed/was truncated to empty.
 * @property isLoading whether the file metadata and/or text body is still loading.
 * @property error a human-readable error message, or null when there is none.
 */
data class PreviewUiState(
    val file: FileItem? = null,
    val previewKind: PreviewKind = PreviewKind.UNSUPPORTED,
    val textContent: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Drives the file preview screen.
 *
 * On construction the target path is read from the navigation arguments
 * ([Destination.Preview.ARG_PATH]); the corresponding [FileItem] is resolved via
 * [FileRepository] and classified into a [PreviewKind]. For text-like files a
 * bounded amount of content (up to [MAX_TEXT_BYTES]) is read on the IO dispatcher
 * so the body can be displayed inline without risking out-of-memory on large files.
 *
 * All blocking IO is performed off the main thread; the ViewModel only orchestrates
 * state on [viewModelScope].
 */
@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    init {
        val rawArg = savedStateHandle.get<String>(Destination.Preview.ARG_PATH)
        val path = rawArg
            ?.takeIf { it.isNotBlank() }
            ?.let { android.net.Uri.decode(it) }
        if (path == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "No file to preview.",
                    previewKind = PreviewKind.UNSUPPORTED,
                )
            }
        } else {
            load(path)
        }
    }

    /**
     * Resolves the [FileItem] at [path], classifies it, and loads text content when
     * applicable. Replaces any prior state.
     */
    private fun load(path: String) {
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                file = null,
                textContent = null,
                previewKind = PreviewKind.UNSUPPORTED,
            )
        }
        viewModelScope.launch {
            when (val result = fileRepository.getFile(path)) {
                is AppResult.Success -> {
                    val item = result.data
                    val kind = previewKindFor(item)
                    if (kind == PreviewKind.TEXT) {
                        loadText(item)
                    } else {
                        _uiState.update {
                            it.copy(
                                file = item,
                                previewKind = kind,
                                textContent = null,
                                isLoading = false,
                                error = null,
                            )
                        }
                    }
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.error.displayMessage,
                        previewKind = PreviewKind.UNSUPPORTED,
                    )
                }
            }
        }
    }

    /**
     * Reads up to [MAX_TEXT_BYTES] of [item] as UTF-8 text on the IO dispatcher and
     * publishes it into [PreviewUiState.textContent]. Failures surface as an error
     * while still classifying the file as [PreviewKind.TEXT].
     */
    private suspend fun loadText(item: FileItem) {
        val outcome: AppResult<String> = withContext(Dispatchers.IO) {
            try {
                val file = File(item.path)
                if (!file.exists()) {
                    return@withContext AppResult.Failure(
                        com.jupiter.filemanager.core.result.AppError.NotFound(item.path),
                    )
                }
                if (!file.canRead()) {
                    return@withContext AppResult.Failure(
                        com.jupiter.filemanager.core.result.AppError.AccessDenied(item.path),
                    )
                }
                val buffer = ByteArray(MAX_TEXT_BYTES)
                var read = 0
                file.inputStream().buffered().use { stream ->
                    while (read < MAX_TEXT_BYTES) {
                        val count = stream.read(buffer, read, MAX_TEXT_BYTES - read)
                        if (count < 0) break
                        read += count
                    }
                }
                val text = String(buffer, 0, read, Charsets.UTF_8)
                AppResult.Success(text)
            } catch (e: SecurityException) {
                AppResult.Failure(
                    com.jupiter.filemanager.core.result.AppError.AccessDenied(item.path),
                )
            } catch (e: IOException) {
                AppResult.Failure(
                    com.jupiter.filemanager.core.result.AppError.Io(
                        detail = e.message ?: "Failed to read file.",
                        cause = e,
                    ),
                )
            } catch (e: OutOfMemoryError) {
                AppResult.Failure(
                    com.jupiter.filemanager.core.result.AppError.Io(
                        detail = "File is too large to preview.",
                    ),
                )
            }
        }

        when (outcome) {
            is AppResult.Success -> _uiState.update {
                it.copy(
                    file = item,
                    previewKind = PreviewKind.TEXT,
                    textContent = outcome.data,
                    isLoading = false,
                    error = null,
                )
            }

            is AppResult.Failure -> _uiState.update {
                it.copy(
                    file = item,
                    previewKind = PreviewKind.TEXT,
                    textContent = null,
                    isLoading = false,
                    error = outcome.error.displayMessage,
                )
            }
        }
    }

    /**
     * Maps a [FileItem] to the [PreviewKind] that should drive rendering, primarily
     * from its [FileType] with a secondary check on the extension for code/text files.
     */
    private fun previewKindFor(item: FileItem): PreviewKind {
        if (item.isDirectory) return PreviewKind.UNSUPPORTED
        return when (item.type) {
            FileType.IMAGE -> PreviewKind.IMAGE
            FileType.VIDEO -> PreviewKind.VIDEO
            FileType.AUDIO -> PreviewKind.AUDIO
            FileType.PDF -> PreviewKind.PDF
            FileType.CODE -> PreviewKind.TEXT
            FileType.DOCUMENT ->
                if (item.extension.lowercase() in TEXT_EXTENSIONS) PreviewKind.TEXT
                else PreviewKind.UNSUPPORTED
            FileType.OTHER ->
                if (item.extension.lowercase() in TEXT_EXTENSIONS) PreviewKind.TEXT
                else PreviewKind.UNSUPPORTED
            FileType.FOLDER, FileType.ARCHIVE, FileType.APK -> PreviewKind.UNSUPPORTED
        }
    }

    private companion object {
        /** Upper bound (~256 KB) on the number of bytes read for an inline text preview. */
        const val MAX_TEXT_BYTES: Int = 256 * 1024

        /**
         * Extensions that should be previewed as plain text even when their
         * [FileType] is generic (DOCUMENT/OTHER rather than CODE).
         */
        val TEXT_EXTENSIONS: Set<String> = setOf(
            "txt", "text", "log", "md", "markdown", "csv", "tsv",
            "json", "xml", "yaml", "yml", "ini", "cfg", "conf", "properties",
            "rtf", "tex", "srt", "vtt", "nfo",
        )
    }
}
