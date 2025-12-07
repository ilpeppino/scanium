package com.example.objecta.ml

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for DetectionCandidate promotion logic and state management.
 *
 * Tests verify:
 * - Promotion criteria (seen count, confidence thresholds)
 * - Candidate updating with new observations
 * - Confidence tracking (max confidence selection)
 * - Category selection based on highest confidence
 * - Bounding box area filtering
 */
@RunWith(RobolectricTestRunner::class)
class DetectionCandidateTest {

    @Test
    fun whenNewCandidate_thenNotReadyForPromotion() {
        // Arrange
        val candidate = DetectionCandidate(
            id = "test-id",
            seenCount = 1,
            maxConfidence = 0.5f,
            category = ItemCategory.FASHION
        )

        // Act
        val isReady = candidate.isReadyForPromotion(
            minSeenCount = 2,
            minConfidence = 0.4f
        )

        // Assert
        assertThat(isReady).isFalse()
    }

    @Test
    fun whenMeetsSeenCountAndConfidence_thenReadyForPromotion() {
        // Arrange
        val candidate = DetectionCandidate(
            id = "test-id",
            seenCount = 2,
            maxConfidence = 0.6f,
            category = ItemCategory.FASHION
        )

        // Act
        val isReady = candidate.isReadyForPromotion(
            minSeenCount = 2,
            minConfidence = 0.4f
        )

        // Assert
        assertThat(isReady).isTrue()
    }

    @Test
    fun whenBelowConfidenceThreshold_thenNotReadyForPromotion() {
        // Arrange
        val candidate = DetectionCandidate(
            id = "test-id",
            seenCount = 3,
            maxConfidence = 0.3f,
            category = ItemCategory.FASHION
        )

        // Act
        val isReady = candidate.isReadyForPromotion(
            minSeenCount = 2,
            minConfidence = 0.4f
        )

        // Assert
        assertThat(isReady).isFalse()
    }

    @Test
    fun whenBelowSeenCountThreshold_thenNotReadyForPromotion() {
        // Arrange
        val candidate = DetectionCandidate(
            id = "test-id",
            seenCount = 1,
            maxConfidence = 0.8f,
            category = ItemCategory.FASHION
        )

        // Act
        val isReady = candidate.isReadyForPromotion(
            minSeenCount = 2,
            minConfidence = 0.4f
        )

        // Assert
        assertThat(isReady).isFalse()
    }

    @Test
    fun whenBoundingBoxTooSmall_thenNotReadyForPromotion() {
        // Arrange
        val candidate = DetectionCandidate(
            id = "test-id",
            seenCount = 2,
            maxConfidence = 0.6f,
            category = ItemCategory.FASHION,
            lastBoundingBox = Rect(0, 0, 10, 10) // Small box: area = 100
        )

        // Act
        val isReady = candidate.isReadyForPromotion(
            minSeenCount = 2,
            minConfidence = 0.4f,
            minBoxArea = 500f // Require larger box
        )

        // Assert
        assertThat(isReady).isFalse()
    }

    @Test
    fun whenBoundingBoxLargeEnough_thenReadyForPromotion() {
        // Arrange
        val candidate = DetectionCandidate(
            id = "test-id",
            seenCount = 2,
            maxConfidence = 0.6f,
            category = ItemCategory.FASHION,
            lastBoundingBox = Rect(0, 0, 100, 100) // Large box: area = 10000
        )

        // Act
        val isReady = candidate.isReadyForPromotion(
            minSeenCount = 2,
            minConfidence = 0.4f,
            minBoxArea = 500f
        )

        // Assert
        assertThat(isReady).isTrue()
    }

    @Test
    fun whenNoBoxAreaRequirement_thenBoxSizeIgnored() {
        // Arrange
        val candidate = DetectionCandidate(
            id = "test-id",
            seenCount = 2,
            maxConfidence = 0.6f,
            category = ItemCategory.FASHION,
            lastBoundingBox = Rect(0, 0, 1, 1) // Tiny box
        )

        // Act - No minBoxArea specified
        val isReady = candidate.isReadyForPromotion(
            minSeenCount = 2,
            minConfidence = 0.4f
        )

        // Assert
        assertThat(isReady).isTrue()
    }

    @Test
    fun whenObservingWithHigherConfidence_thenUpdatesMaxConfidenceAndCategory() {
        // Arrange
        val original = DetectionCandidate(
            id = "test-id",
            seenCount = 1,
            maxConfidence = 0.4f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion good"
        )

        // Act - New observation with higher confidence and different category
        val updated = original.withNewObservation(
            confidence = 0.7f,
            category = ItemCategory.ELECTRONICS,
            categoryLabel = "Electronics",
            boundingBox = null,
            thumbnail = null
        )

        // Assert
        assertThat(updated.seenCount).isEqualTo(2)
        assertThat(updated.maxConfidence).isEqualTo(0.7f)
        assertThat(updated.category).isEqualTo(ItemCategory.ELECTRONICS)
        assertThat(updated.categoryLabel).isEqualTo("Electronics")
    }

    @Test
    fun whenObservingWithLowerConfidence_thenKeepsOriginalCategoryAndMaxConfidence() {
        // Arrange
        val original = DetectionCandidate(
            id = "test-id",
            seenCount = 1,
            maxConfidence = 0.8f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion good"
        )

        // Act - New observation with lower confidence
        val updated = original.withNewObservation(
            confidence = 0.5f,
            category = ItemCategory.HOME_GOOD,
            categoryLabel = "Home good",
            boundingBox = null,
            thumbnail = null
        )

        // Assert
        assertThat(updated.seenCount).isEqualTo(2)
        assertThat(updated.maxConfidence).isEqualTo(0.8f) // Kept original
        assertThat(updated.category).isEqualTo(ItemCategory.FASHION) // Kept original
        assertThat(updated.categoryLabel).isEqualTo("Fashion good") // Kept original
    }

    @Test
    fun whenObservingMultipleTimes_thenSeenCountIncrementsCorrectly() {
        // Arrange
        var candidate = DetectionCandidate(
            id = "test-id",
            seenCount = 1,
            maxConfidence = 0.5f,
            category = ItemCategory.FASHION
        )

        // Act - Observe 3 more times
        candidate = candidate.withNewObservation(0.6f, ItemCategory.FASHION, "Fashion", null, null)
        candidate = candidate.withNewObservation(0.55f, ItemCategory.FASHION, "Fashion", null, null)
        candidate = candidate.withNewObservation(0.58f, ItemCategory.FASHION, "Fashion", null, null)

        // Assert
        assertThat(candidate.seenCount).isEqualTo(4)
        assertThat(candidate.maxConfidence).isEqualTo(0.6f)
    }

    @Test
    fun whenUpdateBoundingBox_thenReplacesPreviousBox() {
        // Arrange
        val original = DetectionCandidate(
            id = "test-id",
            lastBoundingBox = Rect(0, 0, 100, 100)
        )
        val newBox = Rect(50, 50, 200, 200)

        // Act
        val updated = original.withNewObservation(
            confidence = 0.5f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion",
            boundingBox = newBox,
            thumbnail = null
        )

        // Assert
        assertThat(updated.lastBoundingBox).isEqualTo(newBox)
    }

    @Test
    fun whenNoBoundingBoxProvided_thenKeepsOriginalBox() {
        // Arrange
        val originalBox = Rect(0, 0, 100, 100)
        val original = DetectionCandidate(
            id = "test-id",
            lastBoundingBox = originalBox
        )

        // Act
        val updated = original.withNewObservation(
            confidence = 0.5f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion",
            boundingBox = null,
            thumbnail = null
        )

        // Assert
        assertThat(updated.lastBoundingBox).isEqualTo(originalBox)
    }

    @Test
    fun whenCalculatingAge_thenReturnsPositiveMilliseconds() {
        // Arrange
        val candidate = DetectionCandidate(
            id = "test-id",
            firstSeenTimestamp = System.currentTimeMillis() - 1000 // 1 second ago
        )

        // Act
        val age = candidate.ageMs()

        // Assert
        assertThat(age).isAtLeast(900L) // Allow some tolerance
        assertThat(age).isAtMost(1200L)
    }

    @Test
    fun whenCalculatingTimeSinceLastSeen_thenReturnsPositiveMilliseconds() {
        // Arrange
        val candidate = DetectionCandidate(
            id = "test-id",
            lastSeenTimestamp = System.currentTimeMillis() - 500 // 500ms ago
        )

        // Act
        val timeSince = candidate.timeSinceLastSeenMs()

        // Assert
        assertThat(timeSince).isAtLeast(400L)
        assertThat(timeSince).isAtMost(700L)
    }

    @Test
    fun whenEdgeCaseExactlyAtThresholds_thenReadyForPromotion() {
        // Arrange
        val candidate = DetectionCandidate(
            id = "test-id",
            seenCount = 2,
            maxConfidence = 0.4f,
            category = ItemCategory.FASHION
        )

        // Act - Exactly at thresholds
        val isReady = candidate.isReadyForPromotion(
            minSeenCount = 2,
            minConfidence = 0.4f
        )

        // Assert
        assertThat(isReady).isTrue()
    }
}
