package com.scanium.android.platform.adapters

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.scanium.app.model.NormalizedRect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlatformAdaptersTest {

    @Test
    fun rectF_toNormalized_and_back_isStable() {
        val frameWidth = 200
        val frameHeight = 100
        val rect = RectF(20f, 10f, 120f, 70f)

        val normalized = rect.toNormalizedRect(frameWidth, frameHeight)
        val roundTripped = normalized.toRectF(frameWidth, frameHeight)

        assertThat(normalized.left).isWithin(0.0001f).of(0.1f)
        assertThat(normalized.top).isWithin(0.0001f).of(0.1f)
        assertThat(normalized.right).isWithin(0.0001f).of(0.6f)
        assertThat(normalized.bottom).isWithin(0.0001f).of(0.7f)
        assertThat(roundTripped.left).isWithin(0.0001f).of(rect.left)
        assertThat(roundTripped.top).isWithin(0.0001f).of(rect.top)
        assertThat(roundTripped.right).isWithin(0.0001f).of(rect.right)
        assertThat(roundTripped.bottom).isWithin(0.0001f).of(rect.bottom)
    }

    @Test
    fun rectF_outsideFrame_isClamped() {
        val normalized = RectF(-10f, -5f, 250f, 150f)
            .toNormalizedRect(frameWidth = 200, frameHeight = 100)

        assertThat(normalized.left).isAtLeast(0f)
        assertThat(normalized.top).isAtLeast(0f)
        assertThat(normalized.right).isAtMost(1f)
        assertThat(normalized.bottom).isAtMost(1f)
        assertThat(normalized.isNormalized()).isTrue()
    }

    @Test
    fun normalizedRect_clampsToRectBounds() {
        val normalized = NormalizedRect(-0.1f, 0.2f, 1.3f, 0.9f)
        val rect = normalized.toRect(frameWidth = 100, frameHeight = 50)

        assertThat(rect.left).isAtLeast(0)
        assertThat(rect.top).isEqualTo(10)
        assertThat(rect.right).isAtMost(100)
        assertThat(rect.bottom).isAtMost(50)
    }

    @Test
    fun rect_toNormalized_and_back_isStable() {
        val rect = Rect(5, 10, 45, 30)
        val frameWidth = 100
        val frameHeight = 50

        val normalized = rect.toNormalizedRect(frameWidth, frameHeight)
        val roundTripped = normalized.toRect(frameWidth, frameHeight)

        assertThat(normalized.left).isWithin(0.0001f).of(0.05f)
        assertThat(normalized.top).isWithin(0.0001f).of(0.2f)
        assertThat(normalized.right).isWithin(0.0001f).of(0.45f)
        assertThat(normalized.bottom).isWithin(0.0001f).of(0.6f)
        assertThat(roundTripped.left).isEqualTo(rect.left)
        assertThat(roundTripped.top).isEqualTo(rect.top)
        assertThat(roundTripped.right).isEqualTo(rect.right)
        assertThat(roundTripped.bottom).isEqualTo(rect.bottom)
    }

    @Test
    fun bitmap_toImageRefJpeg_and_back_preservesDimensions() {
        val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)

        val imageRef = bitmap.toImageRefJpeg(quality = 90)
        val decoded = imageRef.toBitmap()

        assertThat(imageRef.mimeType).isEqualTo("image/jpeg")
        assertThat(imageRef.width).isEqualTo(2)
        assertThat(imageRef.height).isEqualTo(3)
        assertThat(imageRef.bytes).isNotEmpty()
        assertThat(decoded.width).isEqualTo(bitmap.width)
        assertThat(decoded.height).isEqualTo(bitmap.height)
    }
}
