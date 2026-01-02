package com.scanium.app.ml.classification

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.scanium.app.ml.ItemCategory
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnDeviceClassifierTest {
    @Test
    fun `when labeler returns no labels result is unknown`() =
        runBlocking {
            val classifier =
                OnDeviceClassifier(
                    labelerClient =
                        object : OnDeviceClassifier.ImageLabelerClient {
                            override suspend fun label(bitmap: Bitmap): List<OnDeviceClassifier.LabelResult> = emptyList()
                        },
                )

            val input =
                ClassificationInput(
                    aggregatedId = "agg-1",
                    bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888),
                )

            val result = classifier.classifySingle(input)

            assertThat(result).isNotNull()
            assertThat(result?.category).isEqualTo(ItemCategory.UNKNOWN)
            assertThat(result?.label).isNull()
        }

    @Test
    fun `when labeler returns label result uses it`() =
        runBlocking {
            val classifier =
                OnDeviceClassifier(
                    labelerClient =
                        object : OnDeviceClassifier.ImageLabelerClient {
                            override suspend fun label(bitmap: Bitmap): List<OnDeviceClassifier.LabelResult> {
                                return listOf(OnDeviceClassifier.LabelResult(text = "sofa", confidence = 0.9f))
                            }
                        },
                )

            val input =
                ClassificationInput(
                    aggregatedId = "agg-2",
                    bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888),
                )

            val result = classifier.classifySingle(input)

            assertThat(result).isNotNull()
            assertThat(result?.label).isEqualTo("sofa")
            assertThat(result?.category).isEqualTo(ItemCategory.HOME_GOOD)
        }
}
