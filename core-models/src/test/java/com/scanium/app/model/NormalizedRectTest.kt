package com.scanium.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedRectTest {

    @Test
    fun widthHeightArea_forNormalizedRect() {
        val rect = NormalizedRect(left = 0.1f, top = 0.2f, right = 0.5f, bottom = 0.7f)

        assertEquals(0.4f, rect.width, 1e-6f)
        assertEquals(0.5f, rect.height, 1e-6f)
        assertEquals(0.2f, rect.area, 1e-6f)
    }

    @Test
    fun clampToUnit_trimsOutOfRangeValues() {
        val rect = NormalizedRect(left = -0.5f, top = 1.2f, right = 1.5f, bottom = -1f)

        val clamped = rect.clampToUnit()

        assertEquals(0f, clamped.left, 0f)
        assertEquals(0f, clamped.top, 0f)
        assertEquals(1f, clamped.right, 0f)
        assertEquals(1f, clamped.bottom, 0f)
        assertTrue(clamped.isNormalized())
    }

    @Test
    fun clampToUnit_reordersWhenLeftGreaterThanRight() {
        val rect = NormalizedRect(left = 0.8f, top = 0.7f, right = 0.2f, bottom = 0.1f)

        val clamped = rect.clampToUnit()

        assertEquals(0.2f, clamped.left, 0f)
        assertEquals(0.8f, clamped.right, 0f)
        assertEquals(0.1f, clamped.top, 0f)
        assertEquals(0.7f, clamped.bottom, 0f)
        assertTrue(clamped.isNormalized())
    }

    @Test
    fun isNormalized_falseWhenOutsideBounds() {
        val rect = NormalizedRect(left = -0.1f, top = 0.2f, right = 0.5f, bottom = 0.6f)

        assertFalse(rect.isNormalized())
    }

    @Test
    fun widthAndArea_zeroWhenCollapsedHorizontally() {
        val rect = NormalizedRect(left = 0.4f, top = 0.2f, right = 0.4f, bottom = 0.8f)

        assertEquals(0f, rect.width, 0f)
        assertEquals(0f, rect.area, 0f)
    }

    @Test
    fun heightAndArea_zeroWhenCollapsedVertically() {
        val rect = NormalizedRect(left = 0.1f, top = 0.6f, right = 0.5f, bottom = 0.6f)

        assertEquals(0f, rect.height, 0f)
        assertEquals(0f, rect.area, 0f)
    }
}
