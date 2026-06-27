package com.jupiter.filemanager.domain.model

/**
 * Describes how a directory listing should be filtered.
 *
 * @property query case-insensitive substring matched against file names; blank means no name filter.
 * @property showHidden when true, hidden (dot-prefixed) entries are included.
 * @property typeFilter when non-null, only entries of this [FileType] are retained.
 */
data class FilterOption(
    val query: String = "",
    val showHidden: Boolean = false,
    val typeFilter: FileType? = null,
)
