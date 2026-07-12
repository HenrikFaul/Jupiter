package com.jupiter.filemanager.feature.search

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.FilterOption

/**
 * User-selectable scopes on the Search screen.
 *
 * These scopes are intentionally presentation-owned rather than new repository
 * search modes: the existing index and filesystem search pipelines still own
 * query semantics. [applyTo] only narrows a repository filter where that is
 * representable, and [matches] is applied to every emitted result (including
 * index-backed results) so both pipelines return the same visible set.
 */
enum class SearchResultFilter(
    val label: String,
) {
    ALL("All"),
    FILES("Files"),
    FOLDERS("Folders"),
    PDFS("PDFs"),
    IMAGES("Images"),
    AI_SEARCH("AI search"),
    ;

    /** Adds the representable type constraint to an existing search filter. */
    fun applyTo(filter: FilterOption): FilterOption = when (this) {
        FOLDERS -> filter.copy(typeFilter = FileType.FOLDER)
        PDFS -> filter.copy(typeFilter = FileType.PDF)
        IMAGES -> filter.copy(typeFilter = FileType.IMAGE)
        // FileRepository has no "not a folder" constraint. FILES is therefore
        // narrowed in [matches] after each real result is emitted.
        ALL, FILES, AI_SEARCH -> filter
    }

    /** Whether a real [FileItem] belongs in this scope. */
    fun matches(item: FileItem): Boolean = when (this) {
        ALL, AI_SEARCH -> true
        FILES -> !item.isDirectory
        FOLDERS -> item.isDirectory
        PDFS -> !item.isDirectory && item.type == FileType.PDF
        IMAGES -> !item.isDirectory && item.type == FileType.IMAGE
    }

    /** AI search routes the query through the existing natural-language parser. */
    val enablesNaturalLanguage: Boolean
        get() = this == AI_SEARCH
}
