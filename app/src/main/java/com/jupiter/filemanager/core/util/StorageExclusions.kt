package com.jupiter.filemanager.core.util

import java.util.Locale

/**
 * The single source of truth for storage paths Jupiter must NEVER surface to the user, applied at
 * EVERY file-enumeration boundary (MediaStore category/album queries, the index survey, the
 * filesystem walk, analytics, and index reads).
 *
 * These are directories whose contents are volatile, app-private, or system-managed, so listing
 * them yields entries that open to a "Not found" error the moment the OS/vendor moves or purges
 * them — most visibly **vendor recycle bins** like Samsung My Files' `Android/.Trash/…`, whose files
 * MediaStore happily indexes even though they are not meant to be opened as normal files.
 *
 * Matching is on full, delimited path SEGMENTS (case-insensitive) so a folder that merely CONTAINS
 * the token in its name (e.g. `my.trash.notes`) is never excluded by accident. Content-URI paths
 * (which have no filesystem segments) never match, so scoped-storage rows stay openable.
 */
object StorageExclusions {

    /** Lowercased path segments to exclude. `.trash` covers vendor bins (Samsung `Android/.Trash`). */
    val EXCLUDED_SEGMENTS: List<String> = listOf(
        "android/data",
        "android/obb",
        ".thumbnails",
        ".trashed",
        ".trash",
    )

    /** True when [path] lies inside any excluded directory (matched on full, case-insensitive segments). */
    fun isExcluded(path: String): Boolean {
        if (path.isEmpty()) return false
        val lower = path.lowercase(Locale.US)
        return EXCLUDED_SEGMENTS.any { lower.contains("/$it/") || lower.endsWith("/$it") }
    }
}
