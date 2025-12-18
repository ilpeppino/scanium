package com.scanium.core.tracking

import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.ml.ItemCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectionInfoTest {

    @Test
    fun detectionInfoDefaultsUseBoundingBoxArea() {
        val box = NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f)
        val info = DetectionInfo(
            trackingId = "det_1",
            boundingBox = box,
            confidence = 0.7f,
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            thumbnail = null,
            normalizedBoxArea = box.area
        )

        assertEquals(box.area, info.normalizedBoxArea)
        assertNull(info.boundingBoxNorm)
    }

    @Test
    fun detectionInfoCanCarryNormalizedOverride() {
        val box = NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f)
        val normalizedBox = NormalizedRect(0.2f, 0.2f, 0.4f, 0.4f)

        val info = DetectionInfo(
            trackingId = "det_2",
            boundingBox = box,
            confidence = 0.8f,
            category = ItemCategory.ELECTRONICS,
            labelText = "Phone",
            thumbnail = null,
            normalizedBoxArea = box.area,
            boundingBoxNorm = normalizedBox
        )

        assertEquals(normalizedBox, info.boundingBoxNorm)
        assertEquals(box, info.boundingBox)
    }
}
