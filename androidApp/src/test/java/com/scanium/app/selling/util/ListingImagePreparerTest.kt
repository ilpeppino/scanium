package com.scanium.app.selling.util

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListingImagePreparerTest {

    private lateinit var context: Context
    private lateinit var preparer: ListingImagePreparer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preparer = ListingImagePreparer(context)
    }

    @After
    fun tearDown() {
        preparer.cleanupOldImages(maxAgeMs = 0)
    }

    @Test
    fun `prepareListingImage with valid thumbnail succeeds`() = runTest {
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        val result = preparer.prepareListingImage(
            itemId = "test-item-1",
            thumbnail = bitmap
        )

        assertThat(result).isInstanceOf(ListingImagePreparer.PrepareResult.Success::class.java)
        val success = result as ListingImagePreparer.PrepareResult.Success
        assertThat(success.width).isEqualTo(800)
        assertThat(success.height).isEqualTo(800)
        assertThat(success.fileSizeBytes).isGreaterThan(0)
        assertThat(success.source).isEqualTo("thumbnail")
    }

    @Test
    fun `prepareListingImage with small thumbnail scales up`() = runTest {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val result = preparer.prepareListingImage(
            itemId = "test-item-2",
            thumbnail = bitmap
        )

        assertThat(result).isInstanceOf(ListingImagePreparer.PrepareResult.Success::class.java)
        val success = result as ListingImagePreparer.PrepareResult.Success
        assertThat(success.width).isAtLeast(500)
        assertThat(success.height).isAtLeast(500)
        assertThat(success.source).isEqualTo("thumbnail_scaled")
    }

    @Test
    fun `prepareListingImage with no sources fails`() = runTest {
        val result = preparer.prepareListingImage(
            itemId = "test-item-3",
            fullImageUri = null,
            thumbnail = null
        )

        assertThat(result).isInstanceOf(ListingImagePreparer.PrepareResult.Failure::class.java)
        val failure = result as ListingImagePreparer.PrepareResult.Failure
        assertThat(failure.reason).contains("No valid image source")
    }

    @Test
    fun `prepareListingImage returns memory cached result when available`() = runTest {
        val bitmap = Bitmap.createBitmap(700, 700, Bitmap.Config.ARGB_8888)
        val firstResult = preparer.prepareListingImage(
            itemId = "cached-item",
            thumbnail = bitmap
        ) as ListingImagePreparer.PrepareResult.Success

        val cachedResult = preparer.prepareListingImage(
            itemId = "cached-item",
            fullImageUri = null,
            thumbnail = null
        ) as ListingImagePreparer.PrepareResult.Success

        assertThat(cachedResult.source).startsWith("memory_cache")
        assertThat(cachedResult.uri).isEqualTo(firstResult.uri)
    }

    @Test
    fun `prepareListingImage uses disk cache across instances`() = runTest {
        val bitmap = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888)
        val itemId = "disk-cached-item"

        val firstResult = preparer.prepareListingImage(
            itemId = itemId,
            thumbnail = bitmap
        ) as ListingImagePreparer.PrepareResult.Success

        val newPreparer = ListingImagePreparer(context)
        val cachedResult = newPreparer.prepareListingImage(
            itemId = itemId,
            fullImageUri = null,
            thumbnail = null
        ) as ListingImagePreparer.PrepareResult.Success

        assertThat(cachedResult.source).isEqualTo("disk_cache")
        assertThat(cachedResult.uri).isEqualTo(firstResult.uri)
    }
}
