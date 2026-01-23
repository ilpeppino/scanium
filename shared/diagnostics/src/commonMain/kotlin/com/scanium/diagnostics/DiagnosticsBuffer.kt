package com.scanium.diagnostics

import com.scanium.telemetry.TelemetryEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Thread-safe ring buffer for storing recent telemetry events as diagnostic breadcrumbs.
 *
 * This buffer enforces two limits:
 * 1. Maximum number of events (FIFO eviction)
 * 2. Maximum total byte size (approximate, based on JSON serialization)
 *
 * Events are assumed to be already sanitized (PII removed) before being appended.
 *
 * ## Usage
 * ```kotlin
 * val buffer = DiagnosticsBuffer(
 *     maxEvents = 200,
 *     maxBytes = 256 * 1024  // 256KB
 * )
 *
 * buffer.append(telemetryEvent)
 * val snapshot = buffer.snapshot()  // Thread-safe copy
 * buffer.clear()
 * ```
 *
 * ## Thread Safety
 * All methods are synchronized for thread-safe access from multiple threads.
 *
 * @param maxEvents Maximum number of events to store (default: 200)
 * @param maxBytes Maximum approximate byte size of stored events (default: 256KB)
 */
class DiagnosticsBuffer(
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    private val events = ArrayDeque<TelemetryEvent>()
    private var currentBytes = 0
    private val lock = Lock()

    private val json = Json { prettyPrint = false }

    /**
     * Appends a telemetry event to the buffer.
     *
     * If the buffer is full (by count or byte size), the oldest events are evicted.
     * The event is assumed to be already sanitized.
     *
     * @param event The telemetry event to append
     */
    fun append(event: TelemetryEvent) =
        lock.withLock {
            val eventBytes = estimateEventSize(event)

            // Evict old events if we're at max count
            while (events.size >= maxEvents && events.isNotEmpty()) {
                removeOldest()
            }

            // Evict old events if adding this event would exceed max bytes
            while (currentBytes + eventBytes > maxBytes && events.isNotEmpty()) {
                removeOldest()
            }

            // Only add if it fits within maxBytes (even if buffer is empty)
            if (eventBytes <= maxBytes) {
                events.addLast(event)
                currentBytes += eventBytes
            }
            // If single event is larger than maxBytes, silently drop it
        }

    /**
     * Returns a thread-safe snapshot of all events currently in the buffer.
     *
     * @return Immutable list of events (oldest first)
     */
    fun snapshot(): List<TelemetryEvent> =
        lock.withLock {
            return events.toList()
        }

    /**
     * Clears all events from the buffer.
     */
    fun clear() =
        lock.withLock {
            events.clear()
            currentBytes = 0
        }

    /**
     * Returns the current number of events in the buffer.
     */
    fun size(): Int = lock.withLock { events.size }

    /**
     * Returns the approximate current byte size of the buffer.
     */
    fun currentByteSize(): Int = lock.withLock { currentBytes }

    /**
     * Removes the oldest event from the buffer and updates byte count.
     */
    private fun removeOldest() {
        val oldest = events.removeFirstOrNull()
        if (oldest != null) {
            currentBytes -= estimateEventSize(oldest)
            if (currentBytes < 0) currentBytes = 0 // Safety check
        }
    }

    /**
     * Estimates the byte size of a telemetry event by serializing to JSON.
     *
     * This is an approximation used for buffer management.
     */
    private fun estimateEventSize(event: TelemetryEvent): Int {
        return try {
            json.encodeToString(event).encodeToByteArray().size
        } catch (e: Exception) {
            // If serialization fails, use a conservative estimate
            FALLBACK_EVENT_SIZE
        }
    }

    companion object {
        /** Default maximum number of events (200) */
        const val DEFAULT_MAX_EVENTS = 200

        /** Default maximum byte size (256KB) */
        const val DEFAULT_MAX_BYTES = 256 * 1024

        /** Fallback event size estimate if serialization fails (1KB) */
        private const val FALLBACK_EVENT_SIZE = 1024
    }
}
