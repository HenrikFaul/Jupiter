package com.jupiter.filemanager.core.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central, DI-provided path visibility policy.
 *
 * [StorageExclusions] remains the pure matching engine for tests and call-sites that cannot use
 * injection, but app code should depend on this policy seam so every user-facing surface can share
 * one classification contract instead of open-coding ".Trash" / app-private checks.
 */
interface PathPolicy {
    fun classify(path: String): PathClass

    fun isUserVisible(path: String): Boolean = classify(path) == PathClass.USER_VISIBLE
}

enum class PathClass {
    USER_VISIBLE,
    APP_TRASH,
    SYSTEM_TRASH,
    PRIVATE_PROTECTED,
    TEMPORARY,
}

@Singleton
class DefaultPathPolicy @Inject constructor() : PathPolicy {
    override fun classify(path: String): PathClass {
        if (path.isEmpty() || path.startsWith("content://", ignoreCase = true)) {
            return PathClass.USER_VISIBLE
        }
        val normalized = path.replace('\\', '/').lowercase()
        return when {
            "/android/data/" in normalized || normalized.endsWith("/android/data") ||
                "/android/obb/" in normalized || normalized.endsWith("/android/obb") ->
                PathClass.PRIVATE_PROTECTED
            "/.trash/" in normalized || normalized.endsWith("/.trash") ||
                "/.trashed/" in normalized || normalized.endsWith("/.trashed") ->
                PathClass.SYSTEM_TRASH
            "/.thumbnails/" in normalized || normalized.endsWith("/.thumbnails") ->
                PathClass.TEMPORARY
            else -> PathClass.USER_VISIBLE
        }
    }
}
