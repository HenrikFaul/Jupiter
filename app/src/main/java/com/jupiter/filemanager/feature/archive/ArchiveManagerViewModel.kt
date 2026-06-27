package com.jupiter.filemanager.feature.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.file.ArchiveManager
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.OperationState
import com.jupiter.filemanager.domain.model.SortDirection
import com.jupiter.filemanager.domain.model.SortField
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Drives [ArchiveManagerScreen].
 *
 * On construction the optional navigation `path` argument
 * ([Destination.ArchiveManagerRoute.ARG_PATH]) is resolved to a folder: if the
 * argument points at a file, that file's parent directory is used; if it points
 * at a directory, that directory is used; when absent the storage root is used.
 * The folder's contents are listed and archive entries (ZIP and similar) are
 * surfaced for extraction.
 *
 * Both create and extract delegate to [ArchiveManager], collecting its cold
 * [com.jupiter.filemanager.domain.model.FileOperationProgress] flow on
 * [viewModelScope] so the UI can render incremental progress. All blocking IO is
 * performed by the repository / [ArchiveManager] on background dispatchers.
 */
@HiltViewModel
class ArchiveManagerViewModel @Inject constructor(
    private val archiveManager: ArchiveManager,
    private val fileRepository: FileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveManagerUiState())
    val uiState: StateFlow<ArchiveManagerUiState> = _uiState.asStateFlow()

    /** Tracks the in-flight archive operation so it can be cancelled if needed. */
    private var operationJob: Job? = null

    init {
        val rawArg = savedStateHandle.get<String>(Destination.ArchiveManagerRoute.ARG_PATH)
        val argPath = rawArg
            ?.takeIf { it.isNotBlank() }
            ?.let { android.net.Uri.decode(it) }
        val folderPath = resolveFolder(argPath)
        _uiState.value = ArchiveManagerUiState(
            folderPath = folderPath,
            folderName = labelFor(folderPath),
            isLoading = true,
        )
        load(folderPath)
    }

    /** Reloads the current folder's listing, e.g. after creating an archive. */
    fun refresh() {
        load(_uiState.value.folderPath)
    }

    /**
     * Loads the folder's entries, partitioning out the archive files that can be
     * extracted. Hidden files are included so archives such as backups are not
     * silently dropped.
     */
    private fun load(folderPath: String) {
        if (folderPath.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    archives = emptyList(),
                    allFiles = emptyList(),
                    emptyMessage = "No folder to browse.",
                )
            }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = fileRepository.listFiles(
                path = folderPath,
                sort = SortOption(field = SortField.NAME, direction = SortDirection.ASCENDING),
                filter = FilterOption(showHidden = true),
            )
            when (result) {
                is AppResult.Success -> {
                    val files = result.data.filter { !it.isDirectory }
                    val archives = files.filter { isArchive(it) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            allFiles = files,
                            archives = archives,
                            error = null,
                            emptyMessage = if (archives.isEmpty()) {
                                "No archives in this folder yet. Compress files to create one."
                            } else {
                                null
                            },
                        )
                    }
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        archives = emptyList(),
                        allFiles = emptyList(),
                        error = result.error.displayMessage,
                        emptyMessage = null,
                    )
                }
            }
        }
    }

    /**
     * Extracts [archive] into a sibling folder named after the archive (without its
     * extension), collecting [ArchiveManager.extractZip] progress into the UI state.
     */
    fun extract(archive: FileItem) {
        if (_uiState.value.isBusy) return
        val destinationDir = extractionDestination(archive)
        operationJob?.cancel()
        _uiState.update {
            it.copy(
                selectedArchive = archive,
                phase = ArchiveOperationPhase.EXTRACTING,
                progress = null,
                error = null,
            )
        }
        operationJob = archiveManager.extractZip(
            zipPath = archive.path,
            destinationDir = destinationDir,
        )
            .onEach { progress ->
                _uiState.update {
                    it.copy(
                        progress = progress,
                        phase = when (progress.state) {
                            OperationState.COMPLETED -> ArchiveOperationPhase.COMPLETED
                            OperationState.FAILED -> ArchiveOperationPhase.FAILED
                            OperationState.CANCELLED -> ArchiveOperationPhase.IDLE
                            OperationState.RUNNING -> ArchiveOperationPhase.EXTRACTING
                        },
                        error = if (progress.state == OperationState.FAILED) {
                            progress.errorMessage ?: "Failed to extract archive."
                        } else {
                            it.error
                        },
                    )
                }
            }
            .onCompletion { cause ->
                if (cause == null && _uiState.value.phase == ArchiveOperationPhase.COMPLETED) {
                    refresh()
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Compresses every entry in the current folder into a new ZIP placed in the same
     * folder, collecting [ArchiveManager.createZip] progress into the UI state.
     */
    fun createArchive() {
        val state = _uiState.value
        if (state.isBusy || state.allFiles.isEmpty()) return
        val destinationZip = newArchivePath(state.folderPath, state.folderName)
        operationJob?.cancel()
        _uiState.update {
            it.copy(
                selectedArchive = null,
                phase = ArchiveOperationPhase.COMPRESSING,
                progress = null,
                error = null,
            )
        }
        operationJob = archiveManager.createZip(
            items = state.allFiles,
            destinationZipPath = destinationZip,
        )
            .onEach { progress ->
                _uiState.update {
                    it.copy(
                        progress = progress,
                        phase = when (progress.state) {
                            OperationState.COMPLETED -> ArchiveOperationPhase.COMPLETED
                            OperationState.FAILED -> ArchiveOperationPhase.FAILED
                            OperationState.CANCELLED -> ArchiveOperationPhase.IDLE
                            OperationState.RUNNING -> ArchiveOperationPhase.COMPRESSING
                        },
                        error = if (progress.state == OperationState.FAILED) {
                            progress.errorMessage ?: "Failed to create archive."
                        } else {
                            it.error
                        },
                    )
                }
            }
            .onCompletion { cause ->
                if (cause == null && _uiState.value.phase == ArchiveOperationPhase.COMPLETED) {
                    refresh()
                }
            }
            .launchIn(viewModelScope)
    }

    /** Cancels any in-flight operation and returns the UI to the idle listing. */
    fun cancelOperation() {
        operationJob?.cancel()
        operationJob = null
        _uiState.update {
            it.copy(
                phase = ArchiveOperationPhase.IDLE,
                progress = null,
            )
        }
    }

    /** Clears a transient completed/failed banner, returning to the idle listing. */
    fun dismissResult() {
        _uiState.update {
            it.copy(
                phase = ArchiveOperationPhase.IDLE,
                progress = null,
                error = null,
            )
        }
    }

    // region Helpers ----------------------------------------------------------

    /** Resolves the [argPath] navigation argument to the folder to display. */
    private fun resolveFolder(argPath: String?): String {
        if (argPath.isNullOrBlank()) return fileRepository.rootDirectory()
        return try {
            val file = File(argPath)
            when {
                file.isDirectory -> file.absolutePath
                file.parentFile != null -> file.parentFile!!.absolutePath
                else -> fileRepository.rootDirectory()
            }
        } catch (_: SecurityException) {
            fileRepository.rootDirectory()
        }
    }

    /** Produces a short, friendly label for [path]. */
    private fun labelFor(path: String): String {
        val name = path.trimEnd('/').substringAfterLast('/')
        return name.ifBlank { "Storage" }
    }

    /** Whether [item] is an archive that can be extracted. */
    private fun isArchive(item: FileItem): Boolean {
        if (item.type == FileType.ARCHIVE) return true
        return item.extension.lowercase() in ARCHIVE_EXTENSIONS
    }

    /** Computes a non-colliding destination directory for extracting [archive]. */
    private fun extractionDestination(archive: FileItem): String {
        val parent = archive.parentPath ?: _uiState.value.folderPath
        val baseName = archive.name.substringBeforeLast('.', archive.name).ifBlank { "extracted" }
        var candidate = File(parent, baseName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(parent, baseName + " (" + index + ")")
            index += 1
        }
        return candidate.absolutePath
    }

    /** Computes a non-colliding ZIP path inside [folderPath]. */
    private fun newArchivePath(folderPath: String, folderName: String): String {
        val baseName = folderName.ifBlank { "archive" }
        var candidate = File(folderPath, baseName + ".zip")
        var index = 1
        while (candidate.exists()) {
            candidate = File(folderPath, baseName + " (" + index + ").zip")
            index += 1
        }
        return candidate.absolutePath
    }

    private inline fun MutableStateFlow<ArchiveManagerUiState>.update(
        transform: (ArchiveManagerUiState) -> ArchiveManagerUiState,
    ) {
        value = transform(value)
    }

    // endregion

    private companion object {
        /** File extensions treated as extractable archives in the listing. */
        val ARCHIVE_EXTENSIONS: Set<String> = setOf(
            "zip", "jar", "apk", "aar", "war",
        )
    }
}
