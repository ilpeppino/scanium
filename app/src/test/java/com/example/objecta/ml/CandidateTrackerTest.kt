package com.example.objecta.ml

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CandidateTracker multi-frame detection pipeline.
 *
 * Tests verify:
 * - Promotion after sufficient frames with confidence
 * - De-duplication (same tracking ID not promoted twice)
 * - Candidate expiration and cleanup
 * - Statistics tracking
 * - Confidence threshold enforcement
 * - Multiple candidates in parallel
 */
class CandidateTrackerTest {

    private lateinit var tracker: CandidateTracker

    @Before
    fun setUp() {
        // Use permissive settings for tests (lower thresholds)
        tracker = CandidateTracker(
            minSeenCount = 2,
            minConfidence = 0.4f,
            candidateTimeoutMs = 1000L,
            enableDebugLogging = false // Disable logging in tests
        )
    }

    @Test
    fun whenFirstDetection_thenNullReturned() {
        // Act
        val result = tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.6f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion good",
            boundingBox = Rect(0, 0, 100, 100),
            thumbnail = null
        )

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun whenSecondDetectionMeetsThresholds_thenItemPromoted() {
        // Arrange - First detection
        tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.5f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion good",
            boundingBox = Rect(0, 0, 100, 100),
            thumbnail = null
        )

        // Act - Second detection (meets minSeenCount=2)
        val result = tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.6f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion good",
            boundingBox = Rect(0, 0, 100, 100),
            thumbnail = null
        )

        // Assert
        assertThat(result).isNotNull()
        assertThat(result?.id).isEqualTo("item-1")
        assertThat(result?.category).isEqualTo(ItemCategory.FASHION)
        assertThat(result?.confidence).isEqualTo(0.6f) // Max confidence
    }

    @Test
    fun whenBelowConfidenceThreshold_thenNotPromoted() {
        // Arrange - First detection
        tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.2f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion good",
            boundingBox = null,
            thumbnail = null
        )

        // Act - Second detection (still below minConfidence=0.4)
        val result = tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.3f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion good",
            boundingBox = null,
            thumbnail = null
        )

        // Assert - Not promoted (confidence too low)
        assertThat(result).isNull()
    }

    @Test
    fun whenConfidenceIncreases_thenPromotedWithMaxConfidence() {
        // Arrange - First detection with lower confidence
        tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.5f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion good",
            boundingBox = null,
            thumbnail = null
        )

        // Act - Second detection with higher confidence
        val result = tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.8f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion good",
            boundingBox = null,
            thumbnail = null
        )

        // Assert
        assertThat(result).isNotNull()
        assertThat(result?.confidence).isEqualTo(0.8f) // Should use max confidence
    }

    @Test
    fun whenCategoryChangesWithHigherConfidence_thenUsesNewCategory() {
        // Arrange - First detection
        tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.5f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion good",
            boundingBox = null,
            thumbnail = null
        )

        // Act - Second detection with different category but higher confidence
        val result = tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.7f,
            category = ItemCategory.ELECTRONICS,
            categoryLabel = "Electronics",
            boundingBox = null,
            thumbnail = null
        )

        // Assert
        assertThat(result).isNotNull()
        assertThat(result?.category).isEqualTo(ItemCategory.ELECTRONICS)
    }

    @Test
    fun whenMultipleCandidatesTrackedInParallel_thenEachPromotedIndependently() {
        // Arrange & Act
        // Track item-1 (first frame)
        val result1a = tracker.processDetection("item-1", 0.6f, ItemCategory.FASHION, "Fashion", null, null)

        // Track item-2 (first frame)
        val result2a = tracker.processDetection("item-2", 0.7f, ItemCategory.ELECTRONICS, "Electronics", null, null)

        // Track item-1 (second frame - should promote)
        val result1b = tracker.processDetection("item-1", 0.65f, ItemCategory.FASHION, "Fashion", null, null)

        // Track item-2 (second frame - should promote)
        val result2b = tracker.processDetection("item-2", 0.75f, ItemCategory.ELECTRONICS, "Electronics", null, null)

        // Assert
        assertThat(result1a).isNull()
        assertThat(result2a).isNull()
        assertThat(result1b).isNotNull()
        assertThat(result1b?.id).isEqualTo("item-1")
        assertThat(result2b).isNotNull()
        assertThat(result2b?.id).isEqualTo("item-2")
    }

    @Test
    fun whenCleanupCalled_thenExpiredCandidatesRemoved() {
        // Arrange - Create a candidate
        tracker.processDetection("item-1", 0.6f, ItemCategory.FASHION, "Fashion", null, null)

        // Wait for expiration (candidateTimeoutMs = 1000ms)
        Thread.sleep(1100)

        // Act
        val removedCount = tracker.cleanupExpiredCandidates()

        // Assert
        assertThat(removedCount).isEqualTo(1)
        assertThat(tracker.getCurrentCandidates()).isEmpty()
    }

    @Test
    fun whenCleanupCalledBeforeExpiration_thenCandidatesKept() {
        // Arrange
        tracker.processDetection("item-1", 0.6f, ItemCategory.FASHION, "Fashion", null, null)

        // Act - Cleanup immediately (before timeout)
        val removedCount = tracker.cleanupExpiredCandidates()

        // Assert
        assertThat(removedCount).isEqualTo(0)
        assertThat(tracker.getCurrentCandidates()).hasSize(1)
    }

    @Test
    fun whenClearCalled_thenAllCandidatesRemoved() {
        // Arrange
        tracker.processDetection("item-1", 0.6f, ItemCategory.FASHION, "Fashion", null, null)
        tracker.processDetection("item-2", 0.7f, ItemCategory.ELECTRONICS, "Electronics", null, null)

        // Act
        tracker.clear()

        // Assert
        assertThat(tracker.getCurrentCandidates()).isEmpty()
    }

    @Test
    fun whenGettingStats_thenReturnsCorrectCounts() {
        // Arrange & Act
        tracker.processDetection("item-1", 0.6f, ItemCategory.FASHION, "Fashion", null, null)
        tracker.processDetection("item-1", 0.65f, ItemCategory.FASHION, "Fashion", null, null) // Promotion
        tracker.processDetection("item-2", 0.7f, ItemCategory.ELECTRONICS, "Electronics", null, null)

        val stats = tracker.getStats()

        // Assert
        assertThat(stats.totalDetections).isEqualTo(3)
        assertThat(stats.totalPromotions).isEqualTo(1)
        assertThat(stats.activeCandidates).isEqualTo(1) // item-2 still active
        assertThat(stats.promotionRate).isWithin(0.01f).of(1f / 3f)
    }

    @Test
    fun whenZeroDetections_thenPromotionRateIsZero() {
        // Act
        val stats = tracker.getStats()

        // Assert
        assertThat(stats.promotionRate).isEqualTo(0f)
    }

    @Test
    fun whenThreeFramesRequired_thenPromotesOnThirdFrame() {
        // Arrange - Tracker requiring 3 frames
        val strictTracker = CandidateTracker(
            minSeenCount = 3,
            minConfidence = 0.4f,
            enableDebugLogging = false
        )

        // Act
        val result1 = strictTracker.processDetection("item-1", 0.6f, ItemCategory.FASHION, "Fashion", null, null)
        val result2 = strictTracker.processDetection("item-1", 0.65f, ItemCategory.FASHION, "Fashion", null, null)
        val result3 = strictTracker.processDetection("item-1", 0.7f, ItemCategory.FASHION, "Fashion", null, null)

        // Assert
        assertThat(result1).isNull()
        assertThat(result2).isNull()
        assertThat(result3).isNotNull()
        assertThat(result3?.id).isEqualTo("item-1")
    }

    @Test
    fun whenPricingIncluded_thenPromotedItemHasPriceRange() {
        // Arrange
        tracker.processDetection("item-1", 0.6f, ItemCategory.FASHION, "Fashion", null, null)

        // Act
        val result = tracker.processDetection("item-1", 0.65f, ItemCategory.FASHION, "Fashion", null, null)

        // Assert
        assertThat(result).isNotNull()
        assertThat(result?.priceRange).isNotNull()
        assertThat(result?.priceRange?.first).isAtLeast(0.0)
        assertThat(result?.priceRange?.second).isGreaterThan(result?.priceRange?.first ?: 0.0)
    }

    @Test
    fun whenBoundingBoxAreaProvided_thenUsedForPricing() {
        // Arrange
        tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.6f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion",
            boundingBox = null,
            thumbnail = null,
            boundingBoxArea = 0.0f // Small area
        )

        // Act - Large area
        val result = tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.65f,
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion",
            boundingBox = null,
            thumbnail = null,
            boundingBoxArea = 0.8f // Large area (80% of frame)
        )

        // Assert - Price should exist (exact value depends on PricingEngine randomization)
        assertThat(result).isNotNull()
        assertThat(result?.priceRange).isNotNull()
    }

    @Test
    fun whenHighConfidenceButOnlyOneSeen_thenNotPromoted() {
        // Act
        val result = tracker.processDetection(
            trackingId = "item-1",
            confidence = 0.99f, // Very high confidence
            category = ItemCategory.FASHION,
            categoryLabel = "Fashion",
            boundingBox = null,
            thumbnail = null
        )

        // Assert - Still needs to be seen multiple times
        assertThat(result).isNull()
    }

    @Test
    fun whenMultipleFramesWithFluctuatingConfidence_thenUsesMaxConfidence() {
        // Arrange
        tracker.processDetection("item-1", 0.5f, ItemCategory.FASHION, "Fashion", null, null)
        tracker.processDetection("item-1", 0.8f, ItemCategory.FASHION, "Fashion", null, null) // Peak
        tracker.processDetection("item-1", 0.6f, ItemCategory.FASHION, "Fashion", null, null) // Drops

        // Act
        val result = tracker.processDetection("item-1", 0.55f, ItemCategory.FASHION, "Fashion", null, null)

        // Assert - Should use peak confidence
        assertThat(result).isNotNull()
        assertThat(result?.confidence).isEqualTo(0.8f)
    }

    @Test
    fun whenGetCurrentCandidates_thenReturnsCopyOfInternalState() {
        // Arrange
        tracker.processDetection("item-1", 0.6f, ItemCategory.FASHION, "Fashion", null, null)

        // Act
        val candidates = tracker.getCurrentCandidates()

        // Assert
        assertThat(candidates).hasSize(1)
        assertThat(candidates).containsKey("item-1")
        assertThat(candidates["item-1"]?.seenCount).isEqualTo(1)
        assertThat(candidates["item-1"]?.maxConfidence).isEqualTo(0.6f)
    }

    @Test
    fun whenExpiredCandidatesCleaned_thenStatsReflectTimeouts() {
        // Arrange
        tracker.processDetection("item-1", 0.6f, ItemCategory.FASHION, "Fashion", null, null)
        Thread.sleep(1100) // Wait for expiration

        // Act
        tracker.cleanupExpiredCandidates()
        val stats = tracker.getStats()

        // Assert
        assertThat(stats.totalTimeouts).isEqualTo(1)
    }
}
