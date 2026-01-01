package com.scanium.core.tracking

import com.scanium.core.models.geometry.NormalizedRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PHASE 6: JVM unit tests for SpatialTemporalMergePolicy.
 * Tests the lightweight spatial-temporal merge logic for handling tracker ID churn.
 */
class SpatialTemporalMergePolicyTest {
    @Test
    fun `shouldMerge returns true when detections overlap in space and time`() {
        val policy =
            SpatialTemporalMergePolicy(
                SpatialTemporalMergePolicy.MergeConfig(
                    timeWindowMs = 1000L,
                    minIoU = 0.3f,
                    useIoU = true,
                ),
            )

        // Create two overlapping rectangles
        val bbox1 = NormalizedRect(left = 0.1f, top = 0.1f, right = 0.5f, bottom = 0.5f)
        val bbox2 = NormalizedRect(left = 0.15f, top = 0.15f, right = 0.55f, bottom = 0.55f)

        val candidate = policy.createCandidateMetadata(bbox1, timestampMs = 1000L, categoryId = 1)

        // Test: same object slightly moved, within time window
        val shouldMerge =
            policy.shouldMerge(
                newBbox = bbox2,
                newTimestampMs = 1500L,
                newCategoryId = 1,
                existingCandidate = candidate,
            )

        assertTrue(shouldMerge, "Overlapping detections within time window should merge")
    }

    @Test
    fun `shouldMerge returns false when time difference exceeds window`() {
        val policy =
            SpatialTemporalMergePolicy(
                SpatialTemporalMergePolicy.MergeConfig(
                    timeWindowMs = 500L,
                    minIoU = 0.3f,
                    useIoU = true,
                ),
            )

        val bbox = NormalizedRect(left = 0.1f, top = 0.1f, right = 0.5f, bottom = 0.5f)
        val candidate = policy.createCandidateMetadata(bbox, timestampMs = 1000L, categoryId = 1)

        // Test: outside time window (1000ms difference, window is 500ms)
        val shouldMerge =
            policy.shouldMerge(
                newBbox = bbox,
                newTimestampMs = 2000L,
                newCategoryId = 1,
                existingCandidate = candidate,
            )

        assertFalse(shouldMerge, "Detections outside time window should not merge")
    }

    @Test
    fun `shouldMerge returns false when bboxes do not overlap enough`() {
        val policy =
            SpatialTemporalMergePolicy(
                SpatialTemporalMergePolicy.MergeConfig(
                    timeWindowMs = 1000L,
                    minIoU = 0.5f,
                    useIoU = true,
                ),
            )

        // Create two barely overlapping rectangles (low IoU)
        val bbox1 = NormalizedRect(left = 0.1f, top = 0.1f, right = 0.3f, bottom = 0.3f)
        val bbox2 = NormalizedRect(left = 0.25f, top = 0.25f, right = 0.5f, bottom = 0.5f)

        val candidate = policy.createCandidateMetadata(bbox1, timestampMs = 1000L, categoryId = 1)

        val shouldMerge =
            policy.shouldMerge(
                newBbox = bbox2,
                newTimestampMs = 1200L,
                newCategoryId = 1,
                existingCandidate = candidate,
            )

        assertFalse(shouldMerge, "Detections with low IoU should not merge")
    }

    @Test
    fun `shouldMerge respects category match requirement`() {
        val policy =
            SpatialTemporalMergePolicy(
                SpatialTemporalMergePolicy.MergeConfig(
                    timeWindowMs = 1000L,
                    minIoU = 0.3f,
                    requireCategoryMatch = true,
                    useIoU = true,
                ),
            )

        val bbox = NormalizedRect(left = 0.1f, top = 0.1f, right = 0.5f, bottom = 0.5f)
        val candidate = policy.createCandidateMetadata(bbox, timestampMs = 1000L, categoryId = 1)

        // Test: different categories
        val shouldMerge =
            policy.shouldMerge(
                newBbox = bbox,
                newTimestampMs = 1200L,
                newCategoryId = 2,
                existingCandidate = candidate,
            )

        assertFalse(shouldMerge, "Detections with different categories should not merge when category match is required")
    }

    @Test
    fun `shouldMerge allows unknown categories when category match not required`() {
        val policy =
            SpatialTemporalMergePolicy(
                SpatialTemporalMergePolicy.MergeConfig(
                    timeWindowMs = 1000L,
                    minIoU = 0.3f,
                    requireCategoryMatch = false,
// Explicitly disable category matching
                    useIoU = true,
                ),
            )

        val bbox = NormalizedRect(left = 0.1f, top = 0.1f, right = 0.5f, bottom = 0.5f)
        val candidate = policy.createCandidateMetadata(bbox, timestampMs = 1000L, categoryId = 0)

        // Test: unknown categories (0) with category match not required
        val shouldMerge =
            policy.shouldMerge(
                newBbox = bbox,
                newTimestampMs = 1200L,
                newCategoryId = 0,
                existingCandidate = candidate,
            )

        assertTrue(shouldMerge, "Detections with unknown categories should merge when category match not required")
    }

    @Test
    fun `shouldMerge uses center distance when useIoU is false`() {
        val policy =
            SpatialTemporalMergePolicy(
                SpatialTemporalMergePolicy.MergeConfig(
                    timeWindowMs = 1000L,
                    maxNormalizedDistance = 0.2f,
                    useIoU = false,
                ),
            )

        // Create two rectangles with close centers
        val bbox1 = NormalizedRect(left = 0.1f, top = 0.1f, right = 0.3f, bottom = 0.3f)
        val bbox2 = NormalizedRect(left = 0.12f, top = 0.12f, right = 0.32f, bottom = 0.32f)

        val candidate = policy.createCandidateMetadata(bbox1, timestampMs = 1000L, categoryId = 1)

        val shouldMerge =
            policy.shouldMerge(
                newBbox = bbox2,
                newTimestampMs = 1200L,
                newCategoryId = 1,
                existingCandidate = candidate,
            )

        assertTrue(shouldMerge, "Detections with close centers should merge when using distance metric")
    }

    @Test
    fun `findBestMatch returns null when no candidates exist`() {
        val policy = SpatialTemporalMergePolicy()
        val bbox = NormalizedRect(left = 0.1f, top = 0.1f, right = 0.5f, bottom = 0.5f)

        val result =
            policy.findBestMatch(
                newBbox = bbox,
                newTimestampMs = 1000L,
                newCategoryId = 1,
                candidates = emptyList(),
            )

        assertNull(result, "Should return null when no candidates exist")
    }

    @Test
    fun `findBestMatch returns best matching candidate`() {
        val policy =
            SpatialTemporalMergePolicy(
                SpatialTemporalMergePolicy.MergeConfig(
                    timeWindowMs = 1000L,
                    minIoU = 0.2f,
                    useIoU = true,
                ),
            )

        // Create test bboxes
        val newBbox = NormalizedRect(left = 0.2f, top = 0.2f, right = 0.4f, bottom = 0.4f)

        // Candidate 1: low overlap
        val bbox1 = NormalizedRect(left = 0.5f, top = 0.5f, right = 0.7f, bottom = 0.7f)
        val candidate1 = policy.createCandidateMetadata(bbox1, timestampMs = 1000L, categoryId = 1)

        // Candidate 2: high overlap (should be best match)
        val bbox2 = NormalizedRect(left = 0.19f, top = 0.19f, right = 0.41f, bottom = 0.41f)
        val candidate2 = policy.createCandidateMetadata(bbox2, timestampMs = 1100L, categoryId = 1)

        val result =
            policy.findBestMatch(
                newBbox = newBbox,
                newTimestampMs = 1200L,
                newCategoryId = 1,
                candidates = listOf(candidate1, candidate2),
            )

        assertNotNull(result, "Should find a match")
        assertEquals(1, result.first, "Should select candidate 2 (index 1) as best match")
        assertTrue(result.second > 0.5f, "Match score should be high for overlapping detections")
    }

    @Test
    fun `findBestMatch filters by time window`() {
        val policy =
            SpatialTemporalMergePolicy(
                SpatialTemporalMergePolicy.MergeConfig(
                    timeWindowMs = 500L,
                    minIoU = 0.3f,
                    useIoU = true,
                ),
            )

        val newBbox = NormalizedRect(left = 0.2f, top = 0.2f, right = 0.4f, bottom = 0.4f)

        // Candidate outside time window
        val candidate = policy.createCandidateMetadata(newBbox, timestampMs = 500L, categoryId = 1)

        val result =
            policy.findBestMatch(
                newBbox = newBbox,
                newTimestampMs = 1500L,
// 1000ms difference, exceeds 500ms window
                newCategoryId = 1,
                candidates = listOf(candidate),
            )

        assertNull(result, "Should not match candidates outside time window")
    }

    @Test
    fun `createCandidateMetadata extracts correct center coordinates`() {
        val policy = SpatialTemporalMergePolicy()
        val bbox = NormalizedRect(left = 0.1f, top = 0.2f, right = 0.5f, bottom = 0.6f)

        val metadata = policy.createCandidateMetadata(bbox, timestampMs = 1000L, categoryId = 1)

        // Center should be at (0.3, 0.4)
        assertEquals(0.3f, metadata.centerX, 0.001f, "Center X should be (left + right) / 2")
        assertEquals(0.4f, metadata.centerY, 0.001f, "Center Y should be (top + bottom) / 2")
        assertEquals(1000L, metadata.lastSeenMs, "Timestamp should match")
        assertEquals(1, metadata.categoryId, "Category ID should match")
    }

    @Test
    fun `merge handles distinct objects correctly`() {
        val policy =
            SpatialTemporalMergePolicy(
                SpatialTemporalMergePolicy.MergeConfig(
                    timeWindowMs = 1000L,
                    minIoU = 0.3f,
                    useIoU = true,
                ),
            )

        // Two distinct objects in different locations
        val bbox1 = NormalizedRect(left = 0.1f, top = 0.1f, right = 0.3f, bottom = 0.3f)
        val bbox2 = NormalizedRect(left = 0.7f, top = 0.7f, right = 0.9f, bottom = 0.9f)

        val candidate = policy.createCandidateMetadata(bbox1, timestampMs = 1000L, categoryId = 1)

        val shouldMerge =
            policy.shouldMerge(
                newBbox = bbox2,
                newTimestampMs = 1200L,
                newCategoryId = 1,
                existingCandidate = candidate,
            )

        assertFalse(shouldMerge, "Distinct objects in different locations should not merge")
    }

    @Test
    fun `strict config requires higher similarity`() {
        val strictPolicy =
            SpatialTemporalMergePolicy(
                SpatialTemporalMergePolicy.MergeConfig.STRICT,
            )

        // Moderate overlap (IoU ~0.4)
        val bbox1 = NormalizedRect(left = 0.1f, top = 0.1f, right = 0.4f, bottom = 0.4f)
        val bbox2 = NormalizedRect(left = 0.2f, top = 0.2f, right = 0.5f, bottom = 0.5f)

        val candidate = strictPolicy.createCandidateMetadata(bbox1, timestampMs = 1000L, categoryId = 1)

        // STRICT requires minIoU = 0.5, this won't pass
        val shouldMerge =
            strictPolicy.shouldMerge(
                newBbox = bbox2,
                newTimestampMs = 1200L,
                newCategoryId = 1,
                existingCandidate = candidate,
            )

        assertFalse(shouldMerge, "STRICT config should require higher similarity")
    }

    @Test
    fun `lenient config allows more matches`() {
        val lenientPolicy =
            SpatialTemporalMergePolicy(
                SpatialTemporalMergePolicy.MergeConfig.LENIENT,
            )

        // Create overlapping boxes with IoU >= 0.2 for LENIENT
        val bbox1 = NormalizedRect(left = 0.1f, top = 0.1f, right = 0.5f, bottom = 0.5f)
        val bbox2 = NormalizedRect(left = 0.2f, top = 0.2f, right = 0.6f, bottom = 0.6f)

        val candidate = lenientPolicy.createCandidateMetadata(bbox1, timestampMs = 1000L, categoryId = 1)

        // LENIENT requires minIoU = 0.2 and timeWindow = 1200ms
        val shouldMerge =
            lenientPolicy.shouldMerge(
                newBbox = bbox2,
                newTimestampMs = 1800L,
// 800ms difference, within 1200ms window
                newCategoryId = 1,
                existingCandidate = candidate,
            )

        assertTrue(shouldMerge, "LENIENT config should allow more matches")
    }
}
