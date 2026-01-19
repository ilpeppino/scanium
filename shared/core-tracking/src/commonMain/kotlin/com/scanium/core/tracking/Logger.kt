package com.scanium.core.tracking

/**
 * Platform-agnostic logging interface for core-tracking module.
 *
 * Platform-specific implementations should use appropriate logging mechanisms:
 * - Android: android.util.Log
 * - iOS: NSLog / os_log
 * - JVM: println or SLF4J
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
    )

    companion object {
        /**
         * No-op logger for platforms that don't need logging.
         */
        val NONE: Logger =
            object : Logger {
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
                ) {
                }
            }
    }
}
