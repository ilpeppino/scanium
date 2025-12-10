package com.scanium.app.data

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.ItemCategory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ScannedItemEntity mapping.
 *
 * Tests verify:
 * - Conversion from ScannedItem to ScannedItemEntity (with bitmap compression)
 * - Conversion from ScannedItemEntity back to ScannedItem (with bitmap decompression)
 * - Handling of null values (thumbnail, bounding box, optional fields)
 * - Data integrity after round-trip conversion
 */
@RunWith(RobolectricTestRunner::class)
class ScannedItemEntityTest {

    @Test
    fun whenConvertingItemWithThumbnail_thenEntityHasCompressedBytes() {
        // Arrange
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val item = createTestItem(thumbnail = bitmap)

        // Act
        val entity = ScannedItemEntity.fromScannedItem(item)

        // Assert
        assertThat(entity.thumbnailBytes).isNotNull()
        assertThat(entity.thumbnailBytes).isNotEmpty()
        assertThat(entity.id).isEqualTo(item.id)
        assertThat(entity.category).isEqualTo(item.category.name)
    }

    @Test
    fun whenConvertingItemWithoutThumbnail_thenEntityHasNullBytes() {
        // Arrange
        val item = createTestItem(thumbnail = null)

        // Act
        val entity = ScannedItemEntity.fromScannedItem(item)

        // Assert
        assertThat(entity.thumbnailBytes).isNull()
        assertThat(entity.id).isEqualTo(item.id)
    }

    @Test
    fun whenConvertingEntityWithBytes_thenItemHasDecompressedBitmap() {
        // Arrange
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val item = createTestItem(thumbnail = bitmap)
        val entity = ScannedItemEntity.fromScannedItem(item)

        // Act
        val reconstructedItem = entity.toScannedItem()

        // Assert
        assertThat(reconstructedItem.thumbnail).isNotNull()
        assertThat(reconstructedItem.thumbnail?.width).isEqualTo(100)
        assertThat(reconstructedItem.thumbnail?.height).isEqualTo(100)
    }

    @Test
    fun whenConvertingEntityWithoutBytes_thenItemHasNullBitmap() {
        // Arrange
        val item = createTestItem(thumbnail = null)
        val entity = ScannedItemEntity.fromScannedItem(item)

        // Act
        val reconstructedItem = entity.toScannedItem()

        // Assert
        assertThat(reconstructedItem.thumbnail).isNull()
    }

    @Test
    fun whenRoundTripConversion_thenDataIntegrityPreserved() {
        // Arrange
        val originalItem = ScannedItem(
            id = "test-id-123",
            thumbnail = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888),
            category = ItemCategory.ELECTRONICS,
            priceRange = 100.0 to 200.0,
            confidence = 0.85f,
            timestamp = 1234567890L,
            recognizedText = null,
            barcodeValue = null,
            boundingBox = RectF(0.1f, 0.2f, 0.3f, 0.4f),
            labelText = "Phone"
        )

        // Act - Convert to entity and back
        val entity = ScannedItemEntity.fromScannedItem(originalItem)
        val reconstructedItem = entity.toScannedItem()

        // Assert - Core data preserved
        assertThat(reconstructedItem.id).isEqualTo(originalItem.id)
        assertThat(reconstructedItem.category).isEqualTo(originalItem.category)
        assertThat(reconstructedItem.priceRange).isEqualTo(originalItem.priceRange)
        assertThat(reconstructedItem.confidence).isEqualTo(originalItem.confidence)
        assertThat(reconstructedItem.timestamp).isEqualTo(originalItem.timestamp)
        assertThat(reconstructedItem.recognizedText).isEqualTo(originalItem.recognizedText)
        assertThat(reconstructedItem.barcodeValue).isEqualTo(originalItem.barcodeValue)
        assertThat(reconstructedItem.labelText).isEqualTo(originalItem.labelText)

        // Assert - Thumbnail dimensions preserved (lossy compression expected)
        assertThat(reconstructedItem.thumbnail).isNotNull()
        assertThat(reconstructedItem.thumbnail?.width).isEqualTo(50)
        assertThat(reconstructedItem.thumbnail?.height).isEqualTo(50)

        // Assert - Bounding box preserved
        assertThat(reconstructedItem.boundingBox).isNotNull()
        assertThat(reconstructedItem.boundingBox?.left).isWithin(0.001f).of(0.1f)
        assertThat(reconstructedItem.boundingBox?.top).isWithin(0.001f).of(0.2f)
        assertThat(reconstructedItem.boundingBox?.right).isWithin(0.001f).of(0.3f)
        assertThat(reconstructedItem.boundingBox?.bottom).isWithin(0.001f).of(0.4f)
    }

    @Test
    fun whenConvertingItemWithBoundingBox_thenEntityStoresSeparateCoordinates() {
        // Arrange
        val item = createTestItem(
            boundingBox = RectF(0.1f, 0.2f, 0.3f, 0.4f)
        )

        // Act
        val entity = ScannedItemEntity.fromScannedItem(item)

        // Assert
        assertThat(entity.bboxLeft).isWithin(0.001f).of(0.1f)
        assertThat(entity.bboxTop).isWithin(0.001f).of(0.2f)
        assertThat(entity.bboxRight).isWithin(0.001f).of(0.3f)
        assertThat(entity.bboxBottom).isWithin(0.001f).of(0.4f)
    }

    @Test
    fun whenConvertingItemWithoutBoundingBox_thenEntityHasNullCoordinates() {
        // Arrange
        val item = createTestItem(boundingBox = null)

        // Act
        val entity = ScannedItemEntity.fromScannedItem(item)

        // Assert
        assertThat(entity.bboxLeft).isNull()
        assertThat(entity.bboxTop).isNull()
        assertThat(entity.bboxRight).isNull()
        assertThat(entity.bboxBottom).isNull()
    }

    @Test
    fun whenConvertingEntityWithCoordinates_thenItemHasReconstructedBoundingBox() {
        // Arrange
        val entity = ScannedItemEntity(
            id = "test-id",
            thumbnailBytes = null,
            category = "FASHION",
            priceLow = 10.0,
            priceHigh = 20.0,
            confidence = 0.5f,
            timestamp = System.currentTimeMillis(),
            bboxLeft = 0.1f,
            bboxTop = 0.2f,
            bboxRight = 0.3f,
            bboxBottom = 0.4f
        )

        // Act
        val item = entity.toScannedItem()

        // Assert
        assertThat(item.boundingBox).isNotNull()
        assertThat(item.boundingBox?.left).isWithin(0.001f).of(0.1f)
        assertThat(item.boundingBox?.top).isWithin(0.001f).of(0.2f)
        assertThat(item.boundingBox?.right).isWithin(0.001f).of(0.3f)
        assertThat(item.boundingBox?.bottom).isWithin(0.001f).of(0.4f)
    }

    @Test
    fun whenConvertingItemWithBarcodeValue_thenEntityPreservesIt() {
        // Arrange
        val item = createTestItem(
            category = ItemCategory.DOCUMENT,
            barcodeValue = "1234567890"
        )

        // Act
        val entity = ScannedItemEntity.fromScannedItem(item)
        val reconstructedItem = entity.toScannedItem()

        // Assert
        assertThat(entity.barcodeValue).isEqualTo("1234567890")
        assertThat(reconstructedItem.barcodeValue).isEqualTo("1234567890")
    }

    @Test
    fun whenConvertingItemWithRecognizedText_thenEntityPreservesIt() {
        // Arrange
        val item = createTestItem(
            category = ItemCategory.DOCUMENT,
            recognizedText = "Hello World"
        )

        // Act
        val entity = ScannedItemEntity.fromScannedItem(item)
        val reconstructedItem = entity.toScannedItem()

        // Assert
        assertThat(entity.recognizedText).isEqualTo("Hello World")
        assertThat(reconstructedItem.recognizedText).isEqualTo("Hello World")
    }

    @Test
    fun whenConvertingMultipleCategories_thenAllCategoriesPreserved() {
        // Test all categories
        val categories = listOf(
            ItemCategory.FASHION,
            ItemCategory.FOOD,
            ItemCategory.HOME_GOOD,
            ItemCategory.PLANT,
            ItemCategory.PLACE,
            ItemCategory.ELECTRONICS,
            ItemCategory.DOCUMENT,
            ItemCategory.UNKNOWN
        )

        categories.forEach { category ->
            // Arrange
            val item = createTestItem(category = category)

            // Act
            val entity = ScannedItemEntity.fromScannedItem(item)
            val reconstructedItem = entity.toScannedItem()

            // Assert
            assertThat(entity.category).isEqualTo(category.name)
            assertThat(reconstructedItem.category).isEqualTo(category)
        }
    }

    @Test
    fun whenConvertingItemWithDifferentCompressionQuality_thenByteSizeVaries() {
        // Arrange
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val item = createTestItem(thumbnail = bitmap)

        // Act
        val entityHighQuality = ScannedItemEntity.fromScannedItem(item, compressionQuality = 100)
        val entityLowQuality = ScannedItemEntity.fromScannedItem(item, compressionQuality = 50)

        // Assert - Lower quality should result in smaller byte array
        assertThat(entityLowQuality.thumbnailBytes!!.size)
            .isLessThan(entityHighQuality.thumbnailBytes!!.size)
    }

    @Test
    fun whenEntityEqualsItself_thenReturnsTrue() {
        // Arrange
        val entity = ScannedItemEntity(
            id = "test-id",
            thumbnailBytes = byteArrayOf(1, 2, 3),
            category = "FASHION",
            priceLow = 10.0,
            priceHigh = 20.0,
            confidence = 0.5f,
            timestamp = 12345L
        )

        // Act & Assert
        assertThat(entity).isEqualTo(entity)
    }

    @Test
    fun whenTwoEntitiesHaveSameData_thenAreEqual() {
        // Arrange
        val bytes = byteArrayOf(1, 2, 3)
        val entity1 = ScannedItemEntity(
            id = "test-id",
            thumbnailBytes = bytes,
            category = "FASHION",
            priceLow = 10.0,
            priceHigh = 20.0,
            confidence = 0.5f,
            timestamp = 12345L
        )
        val entity2 = ScannedItemEntity(
            id = "test-id",
            thumbnailBytes = bytes,
            category = "FASHION",
            priceLow = 10.0,
            priceHigh = 20.0,
            confidence = 0.5f,
            timestamp = 12345L
        )

        // Act & Assert
        assertThat(entity1).isEqualTo(entity2)
        assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode())
    }

    // Helper function to create test items
    private fun createTestItem(
        id: String = "test-id",
        thumbnail: Bitmap? = null,
        category: ItemCategory = ItemCategory.FASHION,
        priceRange: Pair<Double, Double> = 10.0 to 20.0,
        confidence: Float = 0.5f,
        timestamp: Long = System.currentTimeMillis(),
        recognizedText: String? = null,
        barcodeValue: String? = null,
        boundingBox: RectF? = null,
        labelText: String? = null
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = thumbnail,
            category = category,
            priceRange = priceRange,
            confidence = confidence,
            timestamp = timestamp,
            recognizedText = recognizedText,
            barcodeValue = barcodeValue,
            boundingBox = boundingBox,
            labelText = labelText
        )
    }
}
