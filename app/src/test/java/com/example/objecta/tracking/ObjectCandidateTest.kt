package com.example.objecta.tracking

import android.graphics.RectF
import com.example.objecta.ml.ItemCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ObjectCandidate.
 *
 * Tests the data class methods for tracking objects across frames,
 * including spatial matching helpers (IoU, distance calculations).
 */
@RunWith(RobolectricTestRunner::class)
class ObjectCandidateTest {

    @Test
    fun `create ObjectCandidate with initial values`() {
        val boundingBox = RectF(100f, 100f, 200f, 200f)
        val candidate = ObjectCandidate(
            internalId = "test_id_1",
            boundingBox = boundingBox,
            lastSeenFrame = 1L,
            seenCount = 1,
            maxConfidence = 0.5f,
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            thumbnail = null,
            firstSeenFrame = 1L,
            averageBoxArea = 0.01f
        )

        assertEquals("test_id_1", candidate.internalId)
        assertEquals(1L, candidate.lastSeenFrame)
        assertEquals(1, candidate.seenCount)
        assertEquals(0.5f, candidate.maxConfidence, 0.001f)
        assertEquals(ItemCategory.FASHION, candidate.category)
        assertEquals("Shirt", candidate.labelText)
        assertNull(candidate.thumbnail)
        assertEquals(1L, candidate.firstSeenFrame)
        assertEquals(0.01f, candidate.averageBoxArea, 0.001f)
    }

    @Test
    fun `update increases seenCount and updates lastSeenFrame`() {
        val candidate = createTestCandidate()

        candidate.update(
            newBoundingBox = RectF(105f, 105f, 205f, 205f),
            frameNumber = 5L,
            confidence = 0.6f,
            newCategory = ItemCategory.FASHION,
            newLabelText = "Shirt",
            newThumbnail = null,
            boxArea = 0.015f
        )

        assertEquals(2, candidate.seenCount)
        assertEquals(5L, candidate.lastSeenFrame)
    }

    @Test
    fun `update tracks maximum confidence`() {
        val candidate = createTestCandidate(initialConfidence = 0.3f)

        // Update with lower confidence - should not change maxConfidence
        candidate.update(
            newBoundingBox = RectF(105f, 105f, 205f, 205f),
            frameNumber = 2L,
            confidence = 0.2f,
            newCategory = ItemCategory.FASHION,
            newLabelText = "Shirt",
            newThumbnail = null,
            boxArea = 0.01f
        )
        assertEquals(0.3f, candidate.maxConfidence, 0.001f)

        // Update with higher confidence - should update maxConfidence
        candidate.update(
            newBoundingBox = RectF(105f, 105f, 205f, 205f),
            frameNumber = 3L,
            confidence = 0.8f,
            newCategory = ItemCategory.FASHION,
            newLabelText = "Shirt",
            newThumbnail = null,
            boxArea = 0.01f
        )
        assertEquals(0.8f, candidate.maxConfidence, 0.001f)
        assertEquals(3, candidate.seenCount)
    }

    @Test
    fun `update with higher confidence updates category and labelText`() {
        val candidate = createTestCandidate(
            initialConfidence = 0.4f,
            category = ItemCategory.FASHION,
            labelText = "Shirt"
        )

        candidate.update(
            newBoundingBox = RectF(105f, 105f, 205f, 205f),
            frameNumber = 2L,
            confidence = 0.7f,
            newCategory = ItemCategory.HOME_GOOD,
            newLabelText = "Vase",
            newThumbnail = null,
            boxArea = 0.01f
        )

        assertEquals(ItemCategory.HOME_GOOD, candidate.category)
        assertEquals("Vase", candidate.labelText)
        assertEquals(0.7f, candidate.maxConfidence, 0.001f)
    }

    @Test
    fun `update calculates running average of box area`() {
        val candidate = createTestCandidate(averageBoxArea = 0.01f)

        // First update: (0.01 * 1 + 0.02) / 2 = 0.015
        candidate.update(
            newBoundingBox = RectF(105f, 105f, 205f, 205f),
            frameNumber = 2L,
            confidence = 0.5f,
            newCategory = ItemCategory.FASHION,
            newLabelText = "Shirt",
            newThumbnail = null,
            boxArea = 0.02f
        )
        assertEquals(0.015f, candidate.averageBoxArea, 0.001f)

        // Second update: (0.015 * 2 + 0.03) / 3 = 0.02
        candidate.update(
            newBoundingBox = RectF(105f, 105f, 205f, 205f),
            frameNumber = 3L,
            confidence = 0.5f,
            newCategory = ItemCategory.FASHION,
            newLabelText = "Shirt",
            newThumbnail = null,
            boxArea = 0.03f
        )
        assertEquals(0.02f, candidate.averageBoxArea, 0.001f)
    }

    @Test
    fun `getCenterPoint returns correct center coordinates`() {
        val boundingBox = RectF(100f, 100f, 200f, 300f) // 100x200 box
        val candidate = createTestCandidate(boundingBox = boundingBox)

        val (centerX, centerY) = candidate.getCenterPoint()

        assertEquals(150f, centerX, 0.001f) // (100 + 200) / 2
        assertEquals(200f, centerY, 0.001f) // (100 + 300) / 2
    }

    @Test
    fun `distanceTo calculates Euclidean distance between centers`() {
        val boundingBox1 = RectF(0f, 0f, 100f, 100f) // Center: (50, 50)
        val boundingBox2 = RectF(100f, 100f, 200f, 200f) // Center: (150, 150)

        val candidate = createTestCandidate(boundingBox = boundingBox1)

        val distance = candidate.distanceTo(boundingBox2)

        // Distance from (50, 50) to (150, 150) = sqrt(100^2 + 100^2) = 141.42
        assertEquals(141.42f, distance, 0.01f)
    }

    @Test
    fun `distanceTo returns zero for same bounding box`() {
        val boundingBox = RectF(100f, 100f, 200f, 200f)
        val candidate = createTestCandidate(boundingBox = boundingBox)

        val distance = candidate.distanceTo(boundingBox)

        assertEquals(0f, distance, 0.001f)
    }

    @Test
    fun `calculateIoU returns 1 for identical bounding boxes`() {
        val boundingBox = RectF(100f, 100f, 200f, 200f)
        val candidate = createTestCandidate(boundingBox = boundingBox)

        val iou = candidate.calculateIoU(boundingBox)

        assertEquals(1.0f, iou, 0.001f)
    }

    @Test
    fun `calculateIoU returns 0 for non-overlapping bounding boxes`() {
        val boundingBox1 = RectF(0f, 0f, 100f, 100f)
        val boundingBox2 = RectF(200f, 200f, 300f, 300f) // No overlap

        val candidate = createTestCandidate(boundingBox = boundingBox1)

        val iou = candidate.calculateIoU(boundingBox2)

        assertEquals(0.0f, iou, 0.001f)
    }

    @Test
    fun `calculateIoU returns correct value for partially overlapping boxes`() {
        val boundingBox1 = RectF(0f, 0f, 100f, 100f) // Area: 10000
        val boundingBox2 = RectF(50f, 50f, 150f, 150f) // Area: 10000
        // Overlap: 50x50 = 2500
        // Union: 10000 + 10000 - 2500 = 17500
        // IoU: 2500 / 17500 = 0.1428

        val candidate = createTestCandidate(boundingBox = boundingBox1)

        val iou = candidate.calculateIoU(boundingBox2)

        assertEquals(0.1428f, iou, 0.01f)
    }

    @Test
    fun `calculateIoU returns correct value for one box contained in another`() {
        val boundingBox1 = RectF(0f, 0f, 200f, 200f) // Area: 40000
        val boundingBox2 = RectF(50f, 50f, 150f, 150f) // Area: 10000, fully contained
        // Overlap: 10000
        // Union: 40000 + 10000 - 10000 = 40000
        // IoU: 10000 / 40000 = 0.25

        val candidate = createTestCandidate(boundingBox = boundingBox1)

        val iou = candidate.calculateIoU(boundingBox2)

        assertEquals(0.25f, iou, 0.01f)
    }

    @Test
    fun `calculateIoU with edge-touching boxes`() {
        val boundingBox1 = RectF(0f, 0f, 100f, 100f)
        val boundingBox2 = RectF(100f, 0f, 200f, 100f) // Share edge at x=100

        val candidate = createTestCandidate(boundingBox = boundingBox1)

        val iou = candidate.calculateIoU(boundingBox2)

        // Edge touching results in zero intersection area
        assertEquals(0.0f, iou, 0.001f)
    }

    @Test
    fun `update with multiple frames tracks progression correctly`() {
        val candidate = createTestCandidate(
            boundingBox = RectF(100f, 100f, 200f, 200f),
            lastSeenFrame = 1L,
            seenCount = 1,
            initialConfidence = 0.3f,
            averageBoxArea = 0.01f
        )

        // Frame 2
        candidate.update(
            newBoundingBox = RectF(105f, 105f, 205f, 205f),
            frameNumber = 2L,
            confidence = 0.4f,
            newCategory = ItemCategory.FASHION,
            newLabelText = "Shirt",
            newThumbnail = null,
            boxArea = 0.012f
        )

        // Frame 5 (gap of 3 frames)
        candidate.update(
            newBoundingBox = RectF(110f, 110f, 210f, 210f),
            frameNumber = 5L,
            confidence = 0.6f,
            newCategory = ItemCategory.FASHION,
            newLabelText = "Shirt",
            newThumbnail = null,
            boxArea = 0.015f
        )

        // Frame 6
        candidate.update(
            newBoundingBox = RectF(112f, 112f, 212f, 212f),
            frameNumber = 6L,
            confidence = 0.7f,
            newCategory = ItemCategory.FASHION,
            newLabelText = "Shirt",
            newThumbnail = null,
            boxArea = 0.016f
        )

        assertEquals(4, candidate.seenCount)
        assertEquals(6L, candidate.lastSeenFrame)
        assertEquals(1L, candidate.firstSeenFrame)
        assertEquals(0.7f, candidate.maxConfidence, 0.001f)

        // Average box area: (0.01 + 0.012 + 0.015 + 0.016) / 4 = 0.01325
        assertEquals(0.01325f, candidate.averageBoxArea, 0.0001f)
    }

    // Helper function
    private fun createTestCandidate(
        id: String = "test_id",
        boundingBox: RectF = RectF(100f, 100f, 200f, 200f),
        lastSeenFrame: Long = 1L,
        seenCount: Int = 1,
        initialConfidence: Float = 0.5f,
        category: ItemCategory = ItemCategory.FASHION,
        labelText: String = "Test",
        firstSeenFrame: Long = 1L,
        averageBoxArea: Float = 0.01f
    ): ObjectCandidate {
        return ObjectCandidate(
            internalId = id,
            boundingBox = boundingBox,
            lastSeenFrame = lastSeenFrame,
            seenCount = seenCount,
            maxConfidence = initialConfidence,
            category = category,
            labelText = labelText,
            thumbnail = null,
            firstSeenFrame = firstSeenFrame,
            averageBoxArea = averageBoxArea
        )
    }
}
