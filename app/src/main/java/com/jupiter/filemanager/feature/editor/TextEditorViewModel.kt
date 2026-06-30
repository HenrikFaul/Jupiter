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
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
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
 * @property notice a non-fatal advisory (e.g. truncation / binary / read-only reason), or null.
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
    val notice: String? = null,
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
 * ([Destination.TextEditor.ARG_PATH]). Up to [MAX_BYTES] (~512 KB) of the file is read
 * on the injected [IoDispatcher] and decoded strictly as UTF-8 into an editable buffer.
 *
 * Safety rails:
 *  - Files larger than [MAX_BYTES] are truncated and opened **read-only** (a [notice]
 *    explains why) so a partial buffer is never written back over the full file.
 *  - Files that cannot be decoded as UTF-8 (binary content / NUL bytes / invalid byte
 *    sequences) are opened **read-only** with a [notice]; their bytes are surfaced
 *    losslessly via the Unicode replacement character so editing them is disabled.
 *  - Files that are not writable on disk are opened **read-only**.
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
        _uiState.update { it.copy(isLoading = true, error = null, notice = null) }
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

                    val length = file.length()
                    val tooLarge = length > MAX_BYTES
                    val bytes = readBounded(file)

                    // When the read was truncated at the byte cap it may have sliced a
                    // multi-byte UTF-8 sequence in half; a strict decode of that trailing
                    // partial char would (wrongly) report the whole file as binary. Trim
                    // back to the last complete UTF-8 sequence before decoding.
                    val decoded = decodeUtf8(if (tooLarge) trimToLastCompleteUtf8(bytes) else bytes)
                    val writable = file.canWrite()

                    val readOnly = tooLarge || !decoded.isText || !writable
                    val notice = buildNotice(
                        tooLarge = tooLarge,
                        isText = decoded.isText,
                        writable = writable,
                    )

                    AppResult.Success(
                        LoadedFile(
                            name = file.name,
                            text = decoded.text,
                            readOnly = readOnly,
                            notice = notice,
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
                        notice = outcome.data.notice,
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

    /** Reads at most [MAX_BYTES] of [file] into a byte array. */
    private fun readBounded(file: File): ByteArray {
        val buffer = ByteArray(MAX_BYTES)
        var read = 0
        file.inputStream().buffered().use { stream ->
            while (read < MAX_BYTES) {
                val count = stream.read(buffer, read, MAX_BYTES - read)
                if (count < 0) break
                read += count
            }
        }
        return if (read == buffer.size) buffer else buffer.copyOf(read)
    }

    /**
     * Returns [bytes] trimmed so it does not end in the middle of a multi-byte UTF-8
     * sequence.
     *
     * Used after a length-bounded read that may have sliced a character at the cap: a
     * trailing partial sequence would otherwise make a strict UTF-8 decode fail and the
     * file be mislabeled as binary. Scans back over the trailing continuation bytes
     * (`10xxxxxx`) to the lead byte; if the sequence that lead byte announces does not fit
     * within the buffer, the incomplete tail is dropped. ASCII-only or already-complete
     * buffers are returned unchanged.
     */
    private fun trimToLastCompleteUtf8(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return bytes

        // Walk back over continuation bytes (0x80..0xBF) to the lead byte of the final
        // sequence. Cap the walk at 3 (longest UTF-8 sequence is 4 bytes => 3 trailers).
        var i = bytes.size - 1
        var continuations = 0
        while (i >= 0 && (bytes[i].toInt() and 0xC0) == 0x80 && continuations < 3) {
            i--
            continuations++
        }
        if (i < 0) return bytes // all continuation bytes: malformed, leave for strict decode

        val lead = bytes[i].toInt() and 0xFF
        val expectedLen = when {
            lead and 0x80 == 0x00 -> 1 // 0xxxxxxx ASCII
            lead and 0xE0 == 0xC0 -> 2 // 110xxxxx
            lead and 0xF0 == 0xE0 -> 3 // 1110xxxx
            lead and 0xF8 == 0xF0 -> 4 // 11110xxx
            else -> 0 // not a valid lead byte; leave as-is for strict decode to judge
        }
        if (expectedLen == 0) return bytes

        val haveLen = continuations + 1
        // If the final sequence is incomplete (announced longer than what we have), drop
        // it; otherwise the buffer already ends on a complete sequence.
        return if (haveLen < expectedLen) bytes.copyOf(i) else bytes
    }

    /**
     * Strictly decodes [bytes] as UTF-8.
     *
     * Returns [Decoded.isText] = true only when the bytes contain no NUL byte and form a
     * valid UTF-8 sequence. When the content is not valid text the bytes are still decoded
     * leniently (malformed/unmappable runs become the Unicode replacement character) so the
     * editor can display a read-only preview, but [Decoded.isText] is false so editing and
     * saving stay disabled.
     */
    private fun decodeUtf8(bytes: ByteArray): Decoded {
        if (bytes.isEmpty()) return Decoded(text = "", isText = true)

        val hasNul = bytes.any { it == 0.toByte() }
        val strict = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val isText = !hasNul && try {
            strict.decode(ByteBuffer.wrap(bytes))
            true
        } catch (_: Exception) {
            false
        }

        // Lenient decode for display (and the canonical text when isText is true).
        val text = String(bytes, Charsets.UTF_8)
        return Decoded(text = text, isText = isText)
    }

    /** Builds the non-fatal advisory shown above the editor, or null when there is none. */
    private fun buildNotice(tooLarge: Boolean, isText: Boolean, writable: Boolean): String? = when {
        tooLarge -> "File is larger than 512 KB; showing the first 512 KB read-only."
        !isText -> "This file does not appear to be UTF-8 text. Opened read-only."
        !writable -> "This file is read-only."
        else -> null
    }

    private data class Decoded(
        val text: String,
        val isText: Boolean,
    )

    private data class LoadedFile(
        val name: String,
        val text: String,
        val readOnly: Boolean,
        val notice: String?,
    )

    private companion object {
        /** Upper bound (~512 KB) on the number of bytes read into the editor buffer. */
        const val MAX_BYTES: Int = 512 * 1024
    }
}
