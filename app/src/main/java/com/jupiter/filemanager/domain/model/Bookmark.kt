package com.jupiter.filemanager.domain.model

/**
 * A user-saved shortcut to a directory or file path.
 *
 * @param path the absolute filesystem path the bookmark points to.
 * @param label the human-readable label shown to the user.
 */
data class Bookmark(
    val path: String,
    val label: String,
)
