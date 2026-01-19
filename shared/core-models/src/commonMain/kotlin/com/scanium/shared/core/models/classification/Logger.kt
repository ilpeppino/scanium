package com.scanium.shared.core.models.classification

/**
 * Platform-agnostic logging interface for classification subsystem.
 *
 * Implementations:
 * - Android: Uses android.util.Log
 * - iOS: Uses NSLog or os_log
 * - JVM: Uses println or SLF4J
 */
interface Logger {
    fun d(
        tag: String,
        message: String,
    )

    fun i(
        tag: String,
        message: String,
    )

    fun w(
        tag: String,
        message: String,
    )

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}

/**
 * No-op logger for environments where logging is disabled.
 */
object NoopLogger : Logger {
    override fun d(
        tag: String,
        message: String,
    ) {
    }

    override fun i(
        tag: String,
        message: String,
    ) {
    }

    override fun w(
        tag: String,
        message: String,
    ) {
    }

    override fun e(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
    }
}

/**
 * Simple console logger for testing/development.
 */
class ConsoleLogger : Logger {
    override fun d(
        tag: String,
        message: String,
    ) {
        println("D/$tag: $message")
    }

    override fun i(
        tag: String,
        message: String,
    ) {
        println("I/$tag: $message")
    }

    override fun w(
        tag: String,
        message: String,
    ) {
        println("W/$tag: $message")
    }

    override fun e(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        println("E/$tag: $message")
        throwable?.printStackTrace()
    }
}
