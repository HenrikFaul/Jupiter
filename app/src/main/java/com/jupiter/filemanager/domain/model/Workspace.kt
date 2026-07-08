package com.jupiter.filemanager.domain.model

/**
 * A user-curated collection of files and folders grouped together for quick access.
 *
 * @param id stable unique identifier for the workspace.
 * @param name human-readable name shown to the user.
 * @param itemPaths absolute filesystem paths of the items collected in this workspace.
 * @param totalBytes aggregate size of the workspace items, in bytes.
 * @param lastModified epoch millis of the most recently modified item, used for sorting.
 */
data class Workspace(
    val id: String,
    val name: String,
    val itemPaths: List<String>,
    val totalBytes: Long = 0L,
    val lastModified: Long = 0L,
) {
    /** Number of items collected in this workspace. */
    val itemCount: Int get() = itemPaths.size
}
