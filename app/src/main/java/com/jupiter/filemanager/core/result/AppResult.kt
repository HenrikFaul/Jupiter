package com.jupiter.filemanager.core.result

/**
 * Discriminated result type wrapping either a successful value or an [AppError].
 *
 * Preferred over throwing exceptions across architectural boundaries so that
 * callers can handle failures explicitly and exhaustively.
 */
sealed interface AppResult<out T> {

    /** Successful outcome carrying the produced [data]. */
    data class Success<out T>(val data: T) : AppResult<T>

    /** Failed outcome carrying the [error] describing what went wrong. */
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** Runs [block] with the wrapped value when this result is a [AppResult.Success]. */
inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

/** Runs [block] with the wrapped error when this result is a [AppResult.Failure]. */
inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

/** Returns the wrapped value when successful, or `null` otherwise. */
fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

/** Transforms a successful value with [transform], propagating any failure unchanged. */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}
