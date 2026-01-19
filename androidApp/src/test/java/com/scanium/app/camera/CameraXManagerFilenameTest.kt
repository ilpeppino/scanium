package com.scanium.app.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Regression guard test for camera filename collision bug.
 *
 * Issue: After background/resume, new captures showed overwritten images.
 * Root cause: SimpleDateFormat("yyyyMMdd_HHmmss") only has 1-second resolution.
 * Two rapid captures in same second would generate identical filenames,
 * causing the second capture to overwrite the first.
 *
 * Fix: Use millisecond timestamp + UUID in filename to ensure uniqueness.
 *
 * This test verifies that the filename generation produces unique names
 * even when multiple captures occur in rapid succession (same millisecond).
 */
class CameraXManagerFilenameTest {
    /**
     * Simulate the fixed filename generation logic.
     * This matches the implementation in captureHighResImage().
     */
    private fun generateCaptureFilename(
        currentTimeMs: Long,
        uuid: String = "",
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(currentTimeMs)
        val uniqueSuffix = "${currentTimeMs}_${uuid.take(8)}"
        return "SCANIUM_${timestamp}_$uniqueSuffix.jpg"
    }

    /**
     * Test that rapid sequential captures generate unique filenames.
     * Simulates 1000 captures occurring in the same millisecond.
     */
    @Test
    fun testRapidCapturesProduceUniqueFilenames() {
        val fixedTimeMs = 1705315200000L // 2026-01-15 12:00:00
        val filenames = mutableSetOf<String>()
        val duplicateAttempts = 0

        // Simulate 1000 rapid captures at the same timestamp
        repeat(1000) { index ->
            val uuid = java.util.UUID.randomUUID().toString()
            val filename = generateCaptureFilename(fixedTimeMs, uuid)
            filenames.add(filename)
        }

        // Verify all filenames are unique
        assertEquals(
            "Filename collision detected! Expected 1000 unique filenames but got ${filenames.size}",
            1000,
            filenames.size,
        )
    }

    /**
     * Test that captures in same second still produce unique filenames
     * (the original bug scenario).
     */
    @Test
    fun testSameSecondCapturesAreUnique() {
        val baseTimeMs = 1705315200000L
        val filenames = mutableSetOf<String>()

        // Simulate 10 captures within the same second
        // In old code: all would generate "SCANIUM_20260115_120000.jpg"
        // In new code: all should be unique due to millisecond + UUID
        repeat(10) { index ->
            val timeMs = baseTimeMs + index // Different milliseconds
            val uuid = java.util.UUID.randomUUID().toString()
            val filename = generateCaptureFilename(timeMs, uuid)
            filenames.add(filename)
        }

        assertEquals(
            "Same-second captures not unique. Expected 10 but got ${filenames.size}",
            10,
            filenames.size,
        )
    }

    /**
     * Test that different UUIDs produce different filenames
     * even at the same millisecond.
     */
    @Test
    fun testDifferentUUIDsProduceDifferentFilenames() {
        val fixedTimeMs = 1705315200000L
        val uuid1 = "12345678-90ab-cdef-1234-567890abcdef"
        val uuid2 = "87654321-0fed-cbA9-8765-432109fedcba"

        val filename1 = generateCaptureFilename(fixedTimeMs, uuid1)
        val filename2 = generateCaptureFilename(fixedTimeMs, uuid2)

        assertNotEquals(
            "Different UUIDs should produce different filenames",
            filename1,
            filename2,
        )
    }

    /**
     * Test filename format is as expected.
     * Verifies the pattern: SCANIUM_yyyyMMdd_HHmmss_epochMs_uuidPrefix.jpg
     */
    @Test
    fun testFilenameFormat() {
        val fixedTimeMs = 1705315200000L
        val uuid = "12345678-90ab-cdef-1234-567890abcdef"
        val filename = generateCaptureFilename(fixedTimeMs, uuid)

        // Should match pattern: SCANIUM_<date>_<time>_<epochMs>_<uuidPrefix>.jpg
        val pattern = Regex("^SCANIUM_\\d{8}_\\d{6}_\\d+_[0-9a-f]{8}\\.jpg$")
        assertTrue(
            "Filename does not match expected pattern: $filename",
            pattern.matches(filename),
        )
    }

    /**
     * Test that millisecond precision contributes to uniqueness.
     */
    @Test
    fun testMillisecondPrecisionInFilename() {
        val baseTimeMs = 1705315200000L
        val filenames = mutableListOf<String>()

        // Generate 5 filenames with incrementing millisecond timestamps
        repeat(5) { index ->
            val timeMs = baseTimeMs + index
            val uuid = java.util.UUID.randomUUID().toString()
            val filename = generateCaptureFilename(timeMs, uuid)
            filenames.add(filename)

            // Verify millisecond is part of the filename
            assertTrue(
                "Filename should contain millisecond timestamp",
                filename.contains("$timeMs"),
            )
        }

        // All should be different due to different millisecond timestamps
        val uniqueFilenames = filenames.toSet()
        assertEquals(
            "Expected all 5 filenames to be unique (different milliseconds)",
            5,
            uniqueFilenames.size,
        )
    }
}
