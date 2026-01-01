package com.scanium.app.data

import android.database.sqlite.SQLiteException
import java.io.IOException

/**
 * Sealed class hierarchy representing all possible errors in the app.
 *
 * This provides a type-safe way to handle different error scenarios
 * and allows for proper error recovery and user messaging.
 */
sealed class AppError(
    open val message: String,
    open val cause: Throwable? = null,
) {
    /**
     * Database-related errors (Room operations).
     */
    data class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause) {
        companion object {
            fun fromException(e: Exception): DatabaseError {
                return DatabaseError(
                    message = "Database operation failed: ${e.localizedMessage}",
                    cause = e,
                )
            }
        }
    }

    /**
     * I/O related errors (file operations, bitmap compression).
     */
    data class IOError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause) {
        companion object {
            fun fromException(e: IOException): IOError {
                return IOError(
                    message = "I/O operation failed: ${e.localizedMessage}",
                    cause = e,
                )
            }
        }
    }

    /**
     * Data validation errors.
     */
    data class ValidationError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    /**
     * Unknown or unexpected errors.
     */
    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause) {
        companion object {
            fun fromException(e: Exception): UnknownError {
                return UnknownError(
                    message = "An unexpected error occurred: ${e.localizedMessage}",
                    cause = e,
                )
            }
        }
    }

    companion object {
        /**
         * Creates an appropriate AppError from any Exception.
         */
        fun fromException(e: Exception): AppError {
            return when (e) {
                is SQLiteException -> DatabaseError.fromException(e)
                is IOException -> IOError.fromException(e)
                else -> UnknownError.fromException(e)
            }
        }
    }

    /**
     * Returns a user-friendly error message suitable for display.
     */
    fun getUserMessage(): String {
        return when (this) {
            is DatabaseError -> "Failed to save or load data. Please try again."
            is IOError -> "Failed to process image data. Please try again."
            is ValidationError -> message
            is UnknownError -> "An unexpected error occurred. Please try again."
        }
    }
}
