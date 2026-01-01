package com.scanium.diagnostics

import com.scanium.telemetry.TelemetryEvent
import com.scanium.telemetry.TelemetrySeverity
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsBufferTest {
    private fun createTestEvent(
        name: String,
        attributes: Map<String, String> = emptyMap(),
    ): TelemetryEvent {
        return TelemetryEvent(
            name = name,
            severity = TelemetrySeverity.INFO,
            timestamp = Clock.System.now(),
            attributes = attributes,
        )
    }

    @Test
    fun `buffer appends events`() {
        val buffer = DiagnosticsBuffer(maxEvents = 10, maxBytes = 10000)

        buffer.append(createTestEvent("event1"))
        buffer.append(createTestEvent("event2"))
        buffer.append(createTestEvent("event3"))

        val snapshot = buffer.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals("event1", snapshot[0].name)
        assertEquals("event2", snapshot[1].name)
        assertEquals("event3", snapshot[2].name)
    }

    @Test
    fun `buffer evicts oldest events when max count exceeded`() {
        val buffer = DiagnosticsBuffer(maxEvents = 3, maxBytes = 100000)

        buffer.append(createTestEvent("event1"))
        buffer.append(createTestEvent("event2"))
        buffer.append(createTestEvent("event3"))
        buffer.append(createTestEvent("event4")) // Should evict event1
        buffer.append(createTestEvent("event5")) // Should evict event2

        val snapshot = buffer.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals("event3", snapshot[0].name)
        assertEquals("event4", snapshot[1].name)
        assertEquals("event5", snapshot[2].name)
    }

    @Test
    fun `buffer evicts oldest events when byte limit approached`() {
        // Use a very small byte limit to force evictions
        val buffer = DiagnosticsBuffer(maxEvents = 100, maxBytes = 500)

        val largeAttributes =
            mapOf(
                "key1" to "value1_with_some_padding",
                "key2" to "value2_with_some_padding",
                "key3" to "value3_with_some_padding",
            )

        buffer.append(createTestEvent("event1", largeAttributes))
        buffer.append(createTestEvent("event2", largeAttributes))
        buffer.append(createTestEvent("event3", largeAttributes))

        // Buffer should have evicted older events to stay within byte limit
        val snapshot = buffer.snapshot()
        assertTrue(snapshot.size < 3, "Buffer should have evicted events due to byte limit")
        assertTrue(buffer.currentByteSize() <= 500, "Buffer should not exceed max bytes")
    }

    @Test
    fun `buffer clears all events`() {
        val buffer = DiagnosticsBuffer(maxEvents = 10, maxBytes = 10000)

        buffer.append(createTestEvent("event1"))
        buffer.append(createTestEvent("event2"))
        buffer.append(createTestEvent("event3"))

        assertEquals(3, buffer.size())

        buffer.clear()

        assertEquals(0, buffer.size())
        assertEquals(0, buffer.currentByteSize())
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test
    fun `buffer snapshot is immutable copy`() {
        val buffer = DiagnosticsBuffer(maxEvents = 10, maxBytes = 10000)

        buffer.append(createTestEvent("event1"))
        val snapshot1 = buffer.snapshot()

        buffer.append(createTestEvent("event2"))
        val snapshot2 = buffer.snapshot()

        assertEquals(1, snapshot1.size)
        assertEquals(2, snapshot2.size)
    }

    @Test
    fun `buffer handles empty state`() {
        val buffer = DiagnosticsBuffer(maxEvents = 10, maxBytes = 10000)

        assertEquals(0, buffer.size())
        assertEquals(0, buffer.currentByteSize())
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test
    fun `buffer maintains FIFO order`() {
        val buffer = DiagnosticsBuffer(maxEvents = 5, maxBytes = 100000)

        for (i in 1..5) {
            buffer.append(createTestEvent("event$i"))
        }

        val snapshot = buffer.snapshot()
        for (i in 1..5) {
            assertEquals("event$i", snapshot[i - 1].name)
        }
    }

    @Test
    fun `buffer drops single event larger than maxBytes`() {
        // Very small limit
        val buffer = DiagnosticsBuffer(maxEvents = 10, maxBytes = 100)

        val hugeAttributes =
            mapOf(
                // This event will be much larger than 100 bytes
                "huge_key" to "x".repeat(1000),
            )

        buffer.append(createTestEvent("huge_event", hugeAttributes))

        // Event should be silently dropped
        assertEquals(0, buffer.size())
    }

    @Test
    fun `buffer size and byte tracking are consistent`() {
        val buffer = DiagnosticsBuffer(maxEvents = 10, maxBytes = 10000)

        buffer.append(createTestEvent("event1"))
        val size1 = buffer.size()
        val bytes1 = buffer.currentByteSize()

        buffer.append(createTestEvent("event2"))
        val size2 = buffer.size()
        val bytes2 = buffer.currentByteSize()

        assertEquals(1, size1)
        assertEquals(2, size2)
        assertTrue(bytes1 > 0)
        assertTrue(bytes2 > bytes1)

        buffer.clear()
        assertEquals(0, buffer.size())
        assertEquals(0, buffer.currentByteSize())
    }

    @Test
    fun `buffer handles rapid appends`() {
        val buffer = DiagnosticsBuffer(maxEvents = 50, maxBytes = 50000)

        for (i in 1..100) {
            buffer.append(createTestEvent("event$i"))
        }

        val snapshot = buffer.snapshot()
        assertEquals(50, snapshot.size) // Should keep only last 50
        assertEquals("event51", snapshot[0].name) // First in buffer should be event51
        assertEquals("event100", snapshot[49].name) // Last should be event100
    }
}
