package com.jupiter.filemanager.domain.model

/**
 * Field by which a directory listing can be sorted.
 */
enum class SortField {
    NAME,
    SIZE,
    DATE_MODIFIED,
    TYPE,
}

/**
 * Direction of a sort operation.
 */
enum class SortDirection {
    ASCENDING,
    DESCENDING,
}

/**
 * Describes how a directory listing should be ordered.
 *
 * @property field the property to sort by.
 * @property direction ascending or descending order.
 * @property foldersFirst when true, directories are always grouped before files regardless of [field].
 */
data class SortOption(
    val field: SortField = SortField.NAME,
    val direction: SortDirection = SortDirection.ASCENDING,
    val foldersFirst: Boolean = true,
)
