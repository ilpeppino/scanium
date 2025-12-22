package com.scanium.app.ml.classification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.android.platform.adapters.toImageRefJpeg
import com.scanium.android.platform.adapters.toBitmap
import com.scanium.app.aggregation.AggregatedItem
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.NormalizedRect
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StableItemCropperTest {

    @Test
    fun `prepare crops bitmap using bounding box`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceBitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        Canvas(sourceBitmap).drawColor(Color.RED)

        val imageRef = sourceBitmap.toImageRefJpeg(quality = 100)
        val aggregatedItem = AggregatedItem(
            aggregatedId = "agg-1",
            category = ItemCategory.HOME_GOOD,
            labelText = "Item",
            boundingBox = NormalizedRect(
                left = 0.25f,
                top = 0.25f,
                right = 0.75f,
                bottom = 0.75f
            ),
            thumbnail = imageRef,
            maxConfidence = 0.9f,
            averageConfidence = 0.9f,
            priceRange = 10.0 to 20.0
        )

        val cropper = StableItemCropper(
            context = context,
            paddingRatio = 0f,
            maxDimension = 500,
            sourceMaxDimension = 500
        )

        val croppedRef = cropper.prepare(aggregatedItem)
        assertThat(croppedRef).isNotNull()

        val croppedBitmap = (croppedRef as com.scanium.shared.core.models.model.ImageRef.Bytes).toBitmap()
        // Bounding box covers half the width and half the height
        assertThat(croppedBitmap.width).isEqualTo(100)
        assertThat(croppedBitmap.height).isEqualTo(50)
    }
}
