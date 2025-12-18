package com.scanium.test

import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.items.ScannedItem
import com.scanium.core.models.ml.ItemCategory
import com.scanium.core.tracking.DetectionInfo
import com.scanium.core.tracking.TrackerStats
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Custom assertion helpers for testing Scanium types.
 *
 * These matchers provide more readable test assertions and better error messages.
 */

/**
 * Asserts that two NormalizedRect instances are approximately equal within a tolerance.
 *
 * Example:
 * ```
 * assertRectsEqual(expected, actual, tolerance = 0.01f)
 * ```
 */
fun assertRectsEqual(
    expected: NormalizedRect,
    actual: NormalizedRect,
    tolerance: Float = 0.001f,
    message: String? = null
) {
    val prefix = if (message != null) "$message: " else ""

    assertEquals(
        expected.left, actual.left, tolerance,
        "${prefix}Left coordinate mismatch"
    )
    assertEquals(
        expected.top, actual.top, tolerance,
        "${prefix}Top coordinate mismatch"
    )
    assertEquals(
        expected.right, actual.right, tolerance,
        "${prefix}Right coordinate mismatch"
    )
    assertEquals(
        expected.bottom, actual.bottom, tolerance,
        "${prefix}Bottom coordinate mismatch"
    )
}

/**
 * Asserts that a NormalizedRect has the expected dimensions.
 *
 * Example:
 * ```
 * assertRectDimensions(rect, expectedWidth = 0.2f, expectedHeight = 0.3f)
 * ```
 */
fun assertRectDimensions(
    rect: NormalizedRect,
    expectedWidth: Float,
    expectedHeight: Float,
    tolerance: Float = 0.001f
) {
    assertEquals(
        expectedWidth, rect.width, tolerance,
        "Width mismatch: expected $expectedWidth, got ${rect.width}"
    )
    assertEquals(
        expectedHeight, rect.height, tolerance,
        "Height mismatch: expected $expectedHeight, got ${rect.height}"
    )
}

/**
 * Asserts that a NormalizedRect has the expected area.
 *
 * Example:
 * ```
 * assertRectArea(rect, expectedArea = 0.06f)
 * ```
 */
fun assertRectArea(
    rect: NormalizedRect,
    expectedArea: Float,
    tolerance: Float = 0.001f
) {
    assertEquals(
        expectedArea, rect.area, tolerance,
        "Area mismatch: expected $expectedArea, got ${rect.area}"
    )
}

/**
 * Asserts that two DetectionInfo instances match on key fields.
 *
 * Example:
 * ```
 * assertDetectionMatches(expected, actual, checkBoundingBox = true)
 * ```
 */
fun assertDetectionMatches(
    expected: DetectionInfo,
    actual: DetectionInfo,
    checkTrackingId: Boolean = true,
    checkBoundingBox: Boolean = true,
    checkCategory: Boolean = true,
    checkConfidence: Boolean = true,
    confidenceTolerance: Float = 0.01f
) {
    if (checkTrackingId) {
        assertEquals(
            expected.trackingId, actual.trackingId,
            "Tracking ID mismatch"
        )
    }

    if (checkBoundingBox) {
        assertRectsEqual(
            expected.boundingBox, actual.boundingBox,
            message = "Bounding box mismatch"
        )
    }

    if (checkCategory) {
        assertEquals(
            expected.category, actual.category,
            "Category mismatch"
        )
    }

    if (checkConfidence) {
        assertEquals(
            expected.confidence, actual.confidence, confidenceTolerance,
            "Confidence mismatch"
        )
    }
}

/**
 * Asserts that a ScannedItem matches expected values.
 *
 * Example:
 * ```
 * assertItemMatches(
 *     item = scannedItem,
 *     expectedCategory = ItemCategory.FASHION,
 *     expectedMergeCount = 3
 * )
 * ```
 */
fun assertItemMatches(
    item: ScannedItem,
    expectedId: String? = null,
    expectedCategory: ItemCategory? = null,
    expectedLabelText: String? = null,
    expectedMergeCount: Int? = null,
    minConfidence: Float? = null
) {
    expectedId?.let {
        assertEquals(it, item.aggregatedId, "Item ID mismatch")
    }

    expectedCategory?.let {
        assertEquals(it, item.category, "Item category mismatch")
    }

    expectedLabelText?.let {
        assertEquals(it, item.labelText, "Item label text mismatch")
    }

    expectedMergeCount?.let {
        assertEquals(it, item.mergeCount, "Item merge count mismatch")
    }

    minConfidence?.let {
        assertTrue(
            item.confidence >= it,
            "Item confidence ${item.confidence} is below minimum $it"
        )
    }
}

/**
 * Asserts that tracker stats match expected values.
 *
 * Example:
 * ```
 * assertTrackerStats(
 *     stats = tracker.getStats(),
 *     expectedActiveCandidates = 2,
 *     expectedConfirmedCandidates = 1
 * )
 * ```
 */
fun assertTrackerStats(
    stats: TrackerStats,
    expectedActiveCandidates: Int? = null,
    expectedConfirmedCandidates: Int? = null,
    expectedCurrentFrame: Int? = null
) {
    expectedActiveCandidates?.let {
        assertEquals(
            it, stats.activeCandidates,
            "Active candidates mismatch: expected $it, got ${stats.activeCandidates}"
        )
    }

    expectedConfirmedCandidates?.let {
        assertEquals(
            it, stats.confirmedCandidates,
            "Confirmed candidates mismatch: expected $it, got ${stats.confirmedCandidates}"
        )
    }

    expectedCurrentFrame?.let {
        assertEquals(
            it, stats.currentFrame,
            "Current frame mismatch: expected $it, got ${stats.currentFrame}"
        )
    }
}

/**
 * Asserts that a collection contains an item matching the predicate.
 *
 * Example:
 * ```
 * assertContainsMatching(items) { it.category == ItemCategory.FASHION }
 * ```
 */
fun <T> assertContainsMatching(
    collection: Collection<T>,
    message: String = "No matching item found",
    predicate: (T) -> Boolean
) {
    assertTrue(
        collection.any(predicate),
        "$message (collection size: ${collection.size})"
    )
}

/**
 * Asserts that a collection does not contain any item matching the predicate.
 *
 * Example:
 * ```
 * assertDoesNotContainMatching(items) { it.confidence < 0.5f }
 * ```
 */
fun <T> assertDoesNotContainMatching(
    collection: Collection<T>,
    message: String = "Found unexpected matching item",
    predicate: (T) -> Boolean
) {
    val found = collection.any(predicate)
    if (found) {
        fail("$message (collection size: ${collection.size})")
    }
}

/**
 * Asserts that two lists contain the same IDs (order-independent).
 *
 * Example:
 * ```
 * assertSameIds(expected = listOf("a", "b"), actual = listOf("b", "a"))
 * ```
 */
fun assertSameIds(
    expected: List<String>,
    actual: List<String>,
    message: String = "ID lists don't match"
) {
    val expectedSet = expected.toSet()
    val actualSet = actual.toSet()

    if (expectedSet != actualSet) {
        val missing = expectedSet - actualSet
        val extra = actualSet - expectedSet
        fail(
            "$message\n" +
                "Expected: $expected\n" +
                "Actual: $actual\n" +
                "Missing: $missing\n" +
                "Extra: $extra"
        )
    }
}

/**
 * Asserts that a list is ordered by a specific property.
 *
 * Example:
 * ```
 * assertOrdered(items, descending = true) { it.confidence }
 * ```
 */
fun <T, R : Comparable<R>> assertOrdered(
    list: List<T>,
    descending: Boolean = false,
    message: String = "List is not ordered",
    selector: (T) -> R
) {
    if (list.size < 2) return // Single item or empty is always ordered

    for (i in 0 until list.size - 1) {
        val current = selector(list[i])
        val next = selector(list[i + 1])

        val isCorrectOrder = if (descending) {
            current >= next
        } else {
            current <= next
        }

        if (!isCorrectOrder) {
            fail(
                "$message at index $i: " +
                    "${list[i]} (${current}) vs ${list[i + 1]} (${next})"
            )
        }
    }
}

/**
 * Asserts that two floats are approximately equal with a relative tolerance.
 *
 * Example:
 * ```
 * assertApproximately(expected = 1.0f, actual = 1.001f, relativeTolerance = 0.01f)
 * ```
 */
fun assertApproximately(
    expected: Float,
    actual: Float,
    relativeTolerance: Float = 0.01f,
    message: String? = null
) {
    val absoluteTolerance = expected * relativeTolerance
    assertEquals(expected, actual, absoluteTolerance, message)
}

/**
 * Extension function for more fluent assertions.
 *
 * Example:
 * ```
 * items.assertSize(3)
 * ```
 */
fun <T> Collection<T>.assertSize(expectedSize: Int, message: String? = null) {
    val prefix = if (message != null) "$message: " else ""
    assertEquals(
        expectedSize, this.size,
        "${prefix}Expected size $expectedSize but got ${this.size}"
    )
}

/**
 * Extension function to assert collection is not empty.
 *
 * Example:
 * ```
 * items.assertNotEmpty("Items should not be empty")
 * ```
 */
fun <T> Collection<T>.assertNotEmpty(message: String = "Collection should not be empty") {
    assertTrue(this.isNotEmpty(), message)
}

/**
 * Extension function to assert collection is empty.
 *
 * Example:
 * ```
 * items.assertEmpty("Items should be empty after clear")
 * ```
 */
fun <T> Collection<T>.assertEmpty(message: String = "Collection should be empty") {
    assertTrue(this.isEmpty(), message)
}
