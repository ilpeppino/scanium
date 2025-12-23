package com.scanium.app.camera

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageUtilsTest {

    @Test
    fun `rotateBitmap returns same bitmap when rotation is 0`() {
        val original = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val rotated = ImageUtils.rotateBitmap(original, 0)

        assertThat(rotated).isSameInstanceAs(original)
        assertThat(rotated.isRecycled).isFalse()
    }

    @Test
    fun `rotateBitmap returns rotated bitmap when rotation is 90`() {
        val original = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val rotated = ImageUtils.rotateBitmap(original, 90)

        assertThat(rotated).isNotSameInstanceAs(original)
        assertThat(rotated.width).isEqualTo(200)
        assertThat(rotated.height).isEqualTo(100)
        assertThat(original.isRecycled).isTrue()
    }

    @Test
    fun `rotateBitmap returns rotated bitmap when rotation is 180`() {
        val original = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val rotated = ImageUtils.rotateBitmap(original, 180)

        assertThat(rotated).isNotSameInstanceAs(original)
        assertThat(rotated.width).isEqualTo(100)
        assertThat(rotated.height).isEqualTo(200)
        assertThat(original.isRecycled).isTrue()
    }

    @Test
    fun `rotateBitmap returns rotated bitmap when rotation is 270`() {
        val original = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val rotated = ImageUtils.rotateBitmap(original, 270)

        assertThat(rotated).isNotSameInstanceAs(original)
        assertThat(rotated.width).isEqualTo(200)
        assertThat(rotated.height).isEqualTo(100)
        assertThat(original.isRecycled).isTrue()
    }
}
