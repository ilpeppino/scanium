package com.scanium.core.export

import com.scanium.core.models.image.ImageRefBytes
import com.scanium.core.models.items.ScannedItem
import com.scanium.core.models.ml.ItemCategory
import com.scanium.shared.core.models.pricing.Money
import com.scanium.shared.core.models.pricing.PriceRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExportMappersTest {
    @Test
    fun toExportItem_usesDomainCategoryAndEstimatedPrice() {
        val item =
            ScannedItem(
                category = ItemCategory.ELECTRONICS,
                labelText = "Laptop",
                boundingBox = com.scanium.core.models.geometry.NormalizedRect(0f, 0f, 1f, 1f),
                thumbnail =
                    ImageRefBytes(
                        bytes = byteArrayOf(1, 2, 3),
                        mimeType = "image/jpeg",
                        width = 10,
                        height = 10,
                    ),
            ).apply {
                domainCategoryId = "laptops"
                enhancedLabelText = "Gaming Laptop"
                estimatedPriceRange =
                    PriceRange(
                        low = Money(500.0),
                        high = Money(900.0),
                    )
            }

        val export = item.toExportItem()

        assertEquals("Gaming Laptop", export.title)
        assertEquals("laptops", export.category)
        assertEquals(500.0, export.priceMin)
        assertEquals(900.0, export.priceMax)
    }

    @Test
    fun toExportItem_omitsPriceWhenUnavailable() {
        val item =
            ScannedItem(
                category = ItemCategory.UNKNOWN,
                labelText = "",
                boundingBox = com.scanium.core.models.geometry.NormalizedRect(0f, 0f, 1f, 1f),
            )

        val export = item.toExportItem()

        assertNull(export.priceMin)
        assertNull(export.priceMax)
    }
}
