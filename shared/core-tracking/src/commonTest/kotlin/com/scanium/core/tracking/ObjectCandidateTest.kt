package com.scanium.core.tracking

import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.ml.ItemCategory
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObjectCandidateTest {

    @Test
    fun createCandidateWithInitialValues() {
        val boundingBox = NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f)
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
            averageBoxArea = boundingBox.area
        )

        assertEquals("test_id_1", candidate.internalId)
        assertEquals(1L, candidate.lastSeenFrame)
        assertEquals(1, candidate.seenCount)
        assertEquals(0.5f, candidate.maxConfidence)
        assertEquals(ItemCategory.FASHION, candidate.category)
        assertEquals("Shirt", candidate.labelText)
        assertNull(candidate.thumbnail)
        assertEquals(1L, candidate.firstSeenFrame)
        assertEquals(boundingBox.area, candidate.averageBoxArea)
    }

    @Test
    fun updateIncrementsSeenCountAndUpdatesFrame() {
        val candidate = createTestCandidate()

        candidate.update(
            newBoundingBox = NormalizedRect(0.11f, 0.11f, 0.21f, 0.21f),
            frameNumber = 5L,
            confidence = 0.6f,
            newCategory = ItemCategory.FASHION,
            newLabelText = "Shirt",
            newThumbnail = null,
            boxArea = 0.01f
        )

        assertEquals(2, candidate.seenCount)
        assertEquals(5L, candidate.lastSeenFrame)
    }

    @Test
    fun updateTracksMaximumConfidenceAndCategory() {
        val candidate = createTestCandidate(initialConfidence = 0.3f)

        candidate.update(
            newBoundingBox = NormalizedRect(0.11f, 0.11f, 0.21f, 0.21f),
            frameNumber = 3L,
            confidence = 0.8f,
            newCategory = ItemCategory.HOME_GOOD,
            newLabelText = "Vase",
            newThumbnail = null,
            boxArea = 0.01f
        )

        assertEquals(0.8f, candidate.maxConfidence)
        assertEquals(ItemCategory.HOME_GOOD, candidate.category)
        assertEquals("Vase", candidate.labelText)
    }

    @Test
    fun updateCalculatesRunningAverageArea() {
        val candidate = createTestCandidate(averageBoxArea = 0.01f)

        candidate.update(
            newBoundingBox = NormalizedRect(0.11f, 0.11f, 0.21f, 0.21f),
            frameNumber = 2L,
            confidence = 0.5f,
            newCategory = ItemCategory.FASHION,
            newLabelText = "Shirt",
            newThumbnail = null,
            boxArea = 0.02f
        )
        assertEquals(0.015f, candidate.averageBoxArea, 0.0001f)

        candidate.update(
            newBoundingBox = NormalizedRect(0.12f, 0.12f, 0.22f, 0.22f),
            frameNumber = 3L,
            confidence = 0.5f,
            newCategory = ItemCategory.FASHION,
            newLabelText = "Shirt",
            newThumbnail = null,
            boxArea = 0.03f
        )
        assertEquals(0.02f, candidate.averageBoxArea, 0.0001f)
    }

    @Test
    fun getCenterPointReturnsCorrectCoordinates() {
        val boundingBox = NormalizedRect(0.1f, 0.1f, 0.3f, 0.5f)
        val candidate = createTestCandidate(boundingBox = boundingBox)

        val (cx, cy) = candidate.getCenterPoint()

        assertEquals(0.2f, cx, 0.0001f)
        assertEquals(0.3f, cy, 0.0001f)
    }

    @Test
    fun distanceToCalculatesEuclideanDistance() {
        val boundingBox1 = NormalizedRect(0f, 0f, 0.1f, 0.1f)
        val boundingBox2 = NormalizedRect(0.2f, 0.2f, 0.3f, 0.3f)
        val candidate = createTestCandidate(boundingBox = boundingBox1)

        val distance = candidate.distanceTo(boundingBox2)

        assertEquals(0.2f * sqrt(2f), distance, 0.0001f)
    }

    @Test
    fun calculateIoUReturnsExpectedValues() {
        val boundingBox1 = NormalizedRect(0f, 0f, 0.1f, 0.1f)
        val boundingBox2 = NormalizedRect(0.05f, 0.05f, 0.15f, 0.15f)
        val candidate = createTestCandidate(boundingBox = boundingBox1)

        val iou = candidate.calculateIoU(boundingBox2)

        val intersectionArea = 0.05f * 0.05f
        val unionArea = boundingBox1.area + boundingBox2.area - intersectionArea
        val expectedIoU = intersectionArea / unionArea

        assertEquals(expectedIoU, iou, 0.0001f)
    }

    private fun createTestCandidate(
        id: String = "test_id",
        boundingBox: NormalizedRect = NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f),
        lastSeenFrame: Long = 1L,
        seenCount: Int = 1,
        initialConfidence: Float = 0.5f,
        category: ItemCategory = ItemCategory.FASHION,
        labelText: String = "Test",
        firstSeenFrame: Long = 1L,
        averageBoxArea: Float = boundingBox.area
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
