package com.scanium.diagnostics

import platform.Foundation.NSRecursiveLock

/**
 * iOS implementation of Lock using NSRecursiveLock.
 */
actual class Lock actual constructor() {
    private val lock = NSRecursiveLock()

    actual fun lock() {
        lock.lock()
    }

    actual fun unlock() {
        lock.unlock()
    }
}
