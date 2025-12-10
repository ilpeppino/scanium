package com.scanium.app.data

/**
 * A generic wrapper for data loading operations that can succeed or fail.
 *
 * This sealed class provides a type-safe way to handle results from
 * repository operations, especially those involving database or network calls.
 *
 * @param T The type of data returned on success
 */
sealed class Result<out T> {
    /**
     * Represents a successful operation with data.
     */
    data class Success<T>(val data: T) : Result<T>()

    /**
     * Represents a failed operation with an error.
     */
    data class Failure(val error: AppError) : Result<Nothing>()

    /**
     * Maps the success value using the provided transformation function.
     * Failures are passed through unchanged.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Failure -> this
        }
    }

    /**
     * Returns the data if successful, or null if failed.
     */
    fun getOrNull(): T? {
        return when (this) {
            is Success -> data
            is Failure -> null
        }
    }

    /**
     * Returns the data if successful, or the provided default value if failed.
     */
    fun getOrDefault(default: @UnsafeVariance T): T {
        return when (this) {
            is Success -> data
            is Failure -> default
        }
    }

    /**
     * Returns true if this is a Success result.
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Returns true if this is a Failure result.
     */
    fun isFailure(): Boolean = this is Failure

    companion object {
        /**
         * Creates a Success result.
         */
        fun <T> success(data: T): Result<T> = Success(data)

        /**
         * Creates a Failure result.
         */
        fun <T> failure(error: AppError): Result<T> = Failure(error)

        /**
         * Wraps a suspending operation in a try-catch and returns a Result.
         */
        suspend fun <T> catching(block: suspend () -> T): Result<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Failure(AppError.fromException(e))
            }
        }
    }
}
