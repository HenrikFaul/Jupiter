package com.jupiter.filemanager.domain.model

/**
 * A user-defined label that can be attached to files for organization.
 *
 * @param id stable unique identifier for the tag.
 * @param name human-readable label shown to the user.
 * @param colorArgb packed ARGB color used to render the tag chip.
 * @param fileCount number of files currently associated with this tag.
 */
data class Tag(
    val id: String,
    val name: String,
    val colorArgb: Long,
    val fileCount: Int = 0,
)

/**
 * Association between a file path and the tags applied to it.
 *
 * @param path the absolute filesystem path of the tagged file.
 * @param tagIds the ids of the [Tag]s applied to this file.
 */
data class TaggedFile(
    val path: String,
    val tagIds: List<String>,
)
