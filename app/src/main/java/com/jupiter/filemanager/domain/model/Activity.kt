package com.jupiter.filemanager.domain.model

/**
 * The category of a recorded user or system activity.
 */
enum class ActivityType {
    MOVE,
    COPY,
    DELETE,
    RENAME,
    SHARE,
    COMPRESS,
    EXTRACT,
    BACKUP,
    SYNC,
    FAVORITE,
}

/**
 * A single entry in the activity history log.
 *
 * @param id stable unique identifier for the entry.
 * @param type the [ActivityType] describing what happened.
 * @param description a human-readable summary of the activity.
 * @param timestamp epoch millis at which the activity occurred.
 * @param affectedPaths the filesystem paths involved in the activity.
 */
data class ActivityEntry(
    val id: String,
    val type: ActivityType,
    val description: String,
    val timestamp: Long,
    val affectedPaths: List<String> = emptyList(),
)
