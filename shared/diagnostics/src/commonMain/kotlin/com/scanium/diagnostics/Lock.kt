package com.scanium.diagnostics

/**
 * Platform-independent lock for thread-safe access in shared modules.
 */
expect class Lock() {
    fun lock()

    fun unlock()
}

/**
 * Executes the given [block] after acquiring the lock and releases it after.
 */
inline fun <R> Lock.withLock(block: () -> R): R {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
