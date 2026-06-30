package com.jupiter.filemanager.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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
 * Immutable UI state for the plain-text editor.
 *
 * @property fileName display name of the file being edited.
 * @property content the current (possibly edited) text body.
 * @property savedContent the last persisted text body, used to detect unsaved changes.
 * @property isLoading whether the file body is still being read.
 * @property isSaving whether a save is currently in flight.
 * @property isReadOnly true when the file cannot be written; editing/saving is disabled.
 * @property error a human-readable error message, or null when there is none.
 * @property savedMessage a transient confirmation message (e.g. "Saved"), or null.
 */
data class TextEditorUiState(
    val fileName: String = "",
    val content: String = "",
    val savedContent: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isReadOnly: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
) {
    /** Whether the buffer differs from what is persisted on disk. */
    val isDirty: Boolean get() = content != savedContent
}

/**
 * Drives a lightweight plain-text editor.
 *
 * The target path is read from the navigation arguments
 * ([Destination.TextEditor.ARG_PATH]). The file body (up to [MAX_BYTES]) is read on
 * the injected [IoDispatcher] and can be edited and saved back in place. Files that
 * exceed the size cap or cannot be written are opened read-only.
 *
 * All blocking IO runs off the main thread and every interaction is guarded; failures
 * surface as [TextEditorUiState.error] rather than crashing the app.
 */
@HiltViewModel
class TextEditorViewModel @Inject constructor(
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TextEditorUiState())
    val uiState: StateFlow<TextEditorUiState> = _uiState.asStateFlow()

    private val path: String? = savedStateHandle.get<String>(Destination.TextEditor.ARG_PATH)
        ?.takeIf { it.isNotBlank() }
        ?.let { android.net.Uri.decode(it) }

    init {
        val target = path
        if (target == null) {
            _uiState.update { it.copy(isLoading = false, error = "No file to edit.") }
        } else {
            load(target)
        }
    }

    /** Reads the file body at [target] into the editor buffer. */
    private fun load(target: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val outcome: AppResult<LoadedFile> = withContext(dispatcher) {
                try {
                    val file = File(target)
                    if (!file.isFile) {
                        return@withContext AppResult.Failure(AppError.NotFound(target))
                    }
                    if (!file.canRead()) {
                        return@withContext AppResult.Failure(AppError.AccessDenied(target))
                    }
                    val tooLarge = file.length() > MAX_BYTES
                    val text = if (tooLarge) {
                        readBounded(file)
                    } else {
                        file.readText(Charsets.UTF_8)
                    }
                    AppResult.Success(
                        LoadedFile(
                            name = file.name,
                            text = text,
                            readOnly = tooLarge || !file.canWrite(),
                        ),
                    )
                } catch (e: SecurityException) {
                    AppResult.Failure(AppError.AccessDenied(target))
                } catch (e: IOException) {
                    AppResult.Failure(AppError.Io(e.message ?: "Failed to read file.", e))
                } catch (e: OutOfMemoryError) {
                    AppResult.Failure(AppError.Io("File is too large to edit."))
                }
            }

            when (outcome) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        fileName = outcome.data.name,
                        content = outcome.data.text,
                        savedContent = outcome.data.text,
                        isReadOnly = outcome.data.readOnly,
                        isLoading = false,
                        error = null,
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = outcome.error.displayMessage)
                }
            }
        }
    }

    /** Updates the in-memory buffer as the user types. */
    fun onContentChange(value: String) {
        if (_uiState.value.isReadOnly) return
        _uiState.update { it.copy(content = value, savedMessage = null) }
    }

    /** Persists the current buffer back to disk. No-op when read-only or unchanged. */
    fun save() {
        val target = path ?: return
        val state = _uiState.value
        if (state.isReadOnly || state.isSaving) return
        val body = state.content
        _uiState.update { it.copy(isSaving = true, error = null, savedMessage = null) }
        viewModelScope.launch {
            val outcome: AppResult<Unit> = withContext(dispatcher) {
                try {
                    val file = File(target)
                    if (!file.canWrite()) {
                        return@withContext AppResult.Failure(AppError.AccessDenied(target))
                    }
                    file.writeText(body, Charsets.UTF_8)
                    AppResult.Success(Unit)
                } catch (e: SecurityException) {
                    AppResult.Failure(AppError.AccessDenied(target))
                } catch (e: IOException) {
                    AppResult.Failure(AppError.Io(e.message ?: "Failed to save file.", e))
                }
            }

            when (outcome) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedContent = body,
                        error = null,
                        savedMessage = "Saved",
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, error = outcome.error.displayMessage)
                }
            }
        }
    }

    /** Dismisses any currently displayed error message. */
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Clears the transient "Saved" confirmation message. */
    fun dismissSavedMessage() {
        _uiState.update { it.copy(savedMessage = null) }
    }

    /** Reads at most [MAX_BYTES] of [file] as UTF-8, for previewing oversized files read-only. */
    private fun readBounded(file: File): String {
        val buffer = ByteArray(MAX_BYTES)
        var read = 0
        file.inputStream().buffered().use { stream ->
            while (read < MAX_BYTES) {
                val count = stream.read(buffer, read, MAX_BYTES - read)
                if (count < 0) break
                read += count
            }
        }
        return String(buffer, 0, read, Charsets.UTF_8)
    }

    private data class LoadedFile(
        val name: String,
        val text: String,
        val readOnly: Boolean,
    )

    private companion object {
        /** Upper bound (~1 MB) on the size of a file opened for editing. */
        const val MAX_BYTES: Int = 1024 * 1024
    }
}
