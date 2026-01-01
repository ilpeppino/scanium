package com.scanium.diagnostics

import java.util.concurrent.locks.ReentrantLock

/**
 * JVM implementation of Lock using ReentrantLock.
 */
actual class Lock actual constructor() {
    private val reentrantLock = ReentrantLock()

    actual fun lock() {
        reentrantLock.lock()
    }

    actual fun unlock() {
        reentrantLock.unlock()
    }
}
