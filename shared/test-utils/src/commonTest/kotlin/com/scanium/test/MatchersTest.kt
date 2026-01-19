package com.scanium.test

import com.scanium.core.models.ml.ItemCategory
import com.scanium.core.tracking.TrackerStats
import kotlin.test.Test
import kotlin.test.assertFails

/**
 * Tests for custom assertion matchers.
 */
class MatchersTest {
    @Test
    fun assertRectsEqual_passesForEqualRects() {
        val rect1 = testNormalizedRect(0.1f, 0.2f, 0.5f, 0.8f)
        val rect2 = testNormalizedRect(0.1f, 0.2f, 0.5f, 0.8f)

        assertRectsEqual(rect1, rect2)
    }

    @Test
    fun assertRectsEqual_passesWithinTolerance() {
        val rect1 = testNormalizedRect(0.100f, 0.200f, 0.500f, 0.800f)
        val rect2 = testNormalizedRect(0.101f, 0.201f, 0.501f, 0.801f)

        assertRectsEqual(rect1, rect2, tolerance = 0.01f)
    }

    @Test
    fun assertRectsEqual_failsOutsideTolerance() {
        val rect1 = testNormalizedRect(0.1f, 0.2f, 0.5f, 0.8f)
        val rect2 = testNormalizedRect(0.2f, 0.3f, 0.6f, 0.9f)

        assertFails {
            assertRectsEqual(rect1, rect2, tolerance = 0.01f)
        }
    }

    @Test
    fun assertRectDimensions_passesForCorrectDimensions() {
        val rect = testNormalizedRect(0.1f, 0.2f, 0.5f, 0.8f)

        assertRectDimensions(rect, expectedWidth = 0.4f, expectedHeight = 0.6f)
    }

    @Test
    fun assertRectArea_passesForCorrectArea() {
        val rect = testNormalizedRect(0.0f, 0.0f, 0.5f, 0.5f)

        assertRectArea(rect, expectedArea = 0.25f)
    }

    @Test
    fun assertDetectionMatches_passesForMatchingDetections() {
        val detection1 =
            testDetectionInfo(
                trackingId = "test_1",
                confidence = 0.85f,
                category = ItemCategory.FASHION,
            )
        val detection2 =
            testDetectionInfo(
                trackingId = "test_1",
                confidence = 0.85f,
                category = ItemCategory.FASHION,
            )

        assertDetectionMatches(detection1, detection2)
    }

    @Test
    fun assertItemMatches_passesForMatchingItem() {
        val item =
            testScannedItem(
                id = "item_123",
                category = ItemCategory.ELECTRONICS,
                labelText = "Laptop",
                mergeCount = 3,
            )

        assertItemMatches(
            item = item,
            expectedId = "item_123",
            expectedCategory = ItemCategory.ELECTRONICS,
            expectedLabelText = "Laptop",
            expectedMergeCount = 3,
        )
    }

    @Test
    fun assertTrackerStats_passesForMatchingStats() {
        val stats =
            TrackerStats(
                activeCandidates = 5,
                confirmedCandidates = 3,
                currentFrame = 10,
            )

        assertTrackerStats(
            stats = stats,
            expectedActiveCandidates = 5,
            expectedConfirmedCandidates = 3,
            expectedCurrentFrame = 10,
        )
    }

    @Test
    fun assertContainsMatching_passesWhenItemMatches() {
        val items =
            listOf(
                testScannedItem(category = ItemCategory.FASHION),
                testScannedItem(category = ItemCategory.ELECTRONICS),
                testScannedItem(category = ItemCategory.HOME_GOOD),
            )

        assertContainsMatching(items) { it.category == ItemCategory.ELECTRONICS }
    }

    @Test
    fun assertContainsMatching_failsWhenNoMatch() {
        val items =
            listOf(
                testScannedItem(category = ItemCategory.FASHION),
                testScannedItem(category = ItemCategory.HOME_GOOD),
            )

        assertFails {
            assertContainsMatching(items) { it.category == ItemCategory.ELECTRONICS }
        }
    }

    @Test
    fun assertDoesNotContainMatching_passesWhenNoMatch() {
        val items =
            listOf(
                testScannedItem(category = ItemCategory.FASHION),
                testScannedItem(category = ItemCategory.HOME_GOOD),
            )

        assertDoesNotContainMatching(items) { it.category == ItemCategory.ELECTRONICS }
    }

    @Test
    fun assertSameIds_passesForMatchingLists() {
        val list1 = listOf("a", "b", "c")
        val list2 = listOf("c", "a", "b") // Different order

        assertSameIds(list1, list2)
    }

    @Test
    fun assertSameIds_failsForDifferentLists() {
        val list1 = listOf("a", "b", "c")
        val list2 = listOf("a", "b", "d")

        assertFails {
            assertSameIds(list1, list2)
        }
    }

    @Test
    fun assertOrdered_passesForAscendingOrder() {
        val items =
            listOf(
                testScannedItem(confidence = 0.5f),
                testScannedItem(confidence = 0.7f),
                testScannedItem(confidence = 0.9f),
            )

        assertOrdered(items, descending = false) { it.confidence }
    }

    @Test
    fun assertOrdered_passesForDescendingOrder() {
        val items =
            listOf(
                testScannedItem(confidence = 0.9f),
                testScannedItem(confidence = 0.7f),
                testScannedItem(confidence = 0.5f),
            )

        assertOrdered(items, descending = true) { it.confidence }
    }

    @Test
    fun assertOrdered_failsForIncorrectOrder() {
        val items =
            listOf(
                testScannedItem(confidence = 0.5f),
                testScannedItem(confidence = 0.9f),
                // Out of order
                testScannedItem(confidence = 0.7f),
            )

        assertFails {
            assertOrdered(items, descending = false) { it.confidence }
        }
    }

    @Test
    fun collectionAssertSize_passesForCorrectSize() {
        val items = listOf(1, 2, 3)
        items.assertSize(3)
    }

    @Test
    fun collectionAssertSize_failsForWrongSize() {
        val items = listOf(1, 2, 3)
        assertFails {
            items.assertSize(5)
        }
    }

    @Test
    fun collectionAssertNotEmpty_passesForNonEmptyCollection() {
        val items = listOf(1, 2, 3)
        items.assertNotEmpty()
    }

    @Test
    fun collectionAssertNotEmpty_failsForEmptyCollection() {
        val items = emptyList<Int>()
        assertFails {
            items.assertNotEmpty()
        }
    }

    @Test
    fun collectionAssertEmpty_passesForEmptyCollection() {
        val items = emptyList<Int>()
        items.assertEmpty()
    }

    @Test
    fun collectionAssertEmpty_failsForNonEmptyCollection() {
        val items = listOf(1, 2, 3)
        assertFails {
            items.assertEmpty()
        }
    }
}
