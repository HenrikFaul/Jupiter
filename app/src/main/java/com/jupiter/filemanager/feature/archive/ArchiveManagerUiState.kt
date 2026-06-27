package com.jupiter.filemanager.feature.archive

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress

/**
 * Phase of the long-running archive operation currently being rendered by the
 * archive manager screen.
 */
enum class ArchiveOperationPhase {
    /** No operation is running; the listing is interactive. */
    IDLE,

    /** A create-zip operation is in progress. */
    COMPRESSING,

    /** An extract-zip operation is in progress. */
    EXTRACTING,

    /** The last operation finished successfully. */
    COMPLETED,

    /** The last operation failed. */
    FAILED,
}

/**
 * Immutable UI state for [ArchiveManagerScreen].
 *
 * The screen lists the archive files contained in [folderPath] (the optional
 * navigation `path` argument resolved to its parent folder, or the storage root
 * when none was supplied) and lets the user extract any of them or compress the
 * folder's contents into a new ZIP.
 *
 * @property folderPath the directory whose archives are listed.
 * @property folderName a human-friendly label for [folderPath].
 * @property archives the ZIP/archive files discovered in [folderPath].
 * @property allFiles every entry in [folderPath]; used as the source set when
 *   creating a new archive.
 * @property selectedArchive the archive last targeted by an extract action, if any.
 * @property phase the current long-running operation phase.
 * @property progress the latest progress snapshot while an operation runs, or null.
 * @property isLoading whether the directory listing is still loading.
 * @property emptyMessage a human-readable empty-state message, or null when there
 *   are archives to show.
 * @property error a human-readable error message, or null when there is none.
 */
data class ArchiveManagerUiState(
    val folderPath: String = "",
    val folderName: String = "",
    val archives: List<FileItem> = emptyList(),
    val allFiles: List<FileItem> = emptyList(),
    val selectedArchive: FileItem? = null,
    val phase: ArchiveOperationPhase = ArchiveOperationPhase.IDLE,
    val progress: FileOperationProgress? = null,
    val isLoading: Boolean = true,
    val emptyMessage: String? = null,
    val error: String? = null,
) {
    /** Whether a create or extract operation is currently running. */
    val isBusy: Boolean
        get() = phase == ArchiveOperationPhase.COMPRESSING ||
            phase == ArchiveOperationPhase.EXTRACTING

    /** Whether the folder has any content that could be compressed into an archive. */
    val canCreateArchive: Boolean
        get() = allFiles.isNotEmpty() && !isBusy
}
