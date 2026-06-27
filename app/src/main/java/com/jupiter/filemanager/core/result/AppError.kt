package com.jupiter.filemanager.core.result

/**
 * Domain-level error type used across the application.
 *
 * Each variant carries a user-facing [displayMessage] suitable for surfacing in the UI,
 * and may optionally expose the originating [cause].
 */
sealed class AppError {

    /** Human-readable message safe to show to the user. */
    abstract val displayMessage: String

    /** Optional underlying throwable that triggered this error. */
    open val cause: Throwable? = null

    /** Storage permission has not been granted. */
    data object PermissionDenied : AppError() {
        override val displayMessage: String = "Storage permission is required."
    }

    /** The requested path does not exist. */
    data class NotFound(val path: String) : AppError() {
        override val displayMessage: String = "Not found: " + path
    }

    /** Access to the given path was denied by the system. */
    data class AccessDenied(val path: String) : AppError() {
        override val displayMessage: String = "Access denied: " + path
    }

    /** A file or folder with the given name already exists. */
    data class AlreadyExists(val name: String) : AppError() {
        override val displayMessage: String = "Already exists: " + name
    }

    /** A general IO failure occurred. */
    data class Io(
        val detail: String,
        override val cause: Throwable? = null,
    ) : AppError() {
        override val displayMessage: String = detail
    }

    /** An unexpected/unclassified failure occurred. */
    data class Unknown(
        val detail: String,
        override val cause: Throwable? = null,
    ) : AppError() {
        override val displayMessage: String = detail
    }
}
