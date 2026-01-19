package com.scanium.app.items

import com.scanium.app.copy.CopyDisplayMode
import com.scanium.app.copy.CustomerSafeCopyFormatter
import com.scanium.app.copy.ItemInput
import com.scanium.app.copy.PricingDisplay
import com.scanium.app.copy.PricingRange

/**
 * Maps ScannedItem to display-ready UI copy using CustomerSafeCopyFormatter.
 *
 * Pure Kotlin logic (no Android framework dependencies).
 * Extracts relevant fields from ScannedItem and applies customer-safe formatting.
 */
object ItemListViewMapper {
    /**
     * Display model for a single item in the list.
     *
     * Contains all UI-ready strings after sanitization via CustomerSafeCopyFormatter.
     * Pricing is structured for localized rendering; UI layer uses stringResource().
     */
    data class ItemListDisplay(
        val itemId: String,
        val title: String,
        val pricing: PricingDisplay?,
        // Legacy fields for backward compatibility
        val priceLine: String?,
        val priceContext: String?,
    )

    /**
     * Map a ScannedItem to list display model.
     *
     * @param item The scanned item to map
     * @param dropIfWeak If true, returns null when title cannot be made product-type level
     * @return Display model, or null if dropIfWeak=true and title is weak
     */
    fun mapToListDisplay(
        item: ScannedItem,
        dropIfWeak: Boolean = false,
    ): ItemListDisplay? {
        val itemInput = createItemInput(item)
        val customerSafeCopy =
            CustomerSafeCopyFormatter.format(
                itemInput,
                mode = CopyDisplayMode.ITEM_LIST,
                dropIfWeak = dropIfWeak,
            ) ?: return null

        // Generate legacy price strings from structured pricing for backward compatibility
        val (legacyPriceLine, legacyPriceContext) =
            if (customerSafeCopy.pricing != null) {
                val pricing = customerSafeCopy.pricing
                val priceLine = "Typical resale value: €${pricing.min}–€${pricing.max}"
                val priceContext = "Based on current market conditions"
                Pair(priceLine, priceContext)
            } else {
                Pair(null, null)
            }

        return ItemListDisplay(
            itemId = item.id,
            title = customerSafeCopy.title,
            pricing = customerSafeCopy.pricing,
            priceLine = legacyPriceLine,
            priceContext = legacyPriceContext,
        )
    }

    /**
     * Map multiple items to list display models.
     *
     * @param items Items to map
     * @param dropIfWeak If true, drops items with weak titles
     * @return List of display models (excludes null entries from dropIfWeak)
     */
    fun mapToListDisplayBatch(
        items: List<ScannedItem>,
        dropIfWeak: Boolean = false,
    ): List<ItemListDisplay> = items.mapNotNull { mapToListDisplay(it, dropIfWeak) }

    /**
     * Extract ItemInput from ScannedItem fields.
     *
     * Priority: itemType > (material + color) > brand > generic
     * Uses high-confidence attributes only.
     */
    private fun createItemInput(item: ScannedItem): ItemInput {
        // Extract attributes with confidence filtering
        val brandAttribute = item.attributes["brand"]
        val brand = brandAttribute?.value?.takeIf { it.isNotBlank() }

        val typeAttribute = item.attributes["itemType"]
        val itemType = typeAttribute?.value?.takeIf { it.isNotBlank() }

        val materialAttribute = item.attributes["material"]
        val material = materialAttribute?.value?.takeIf { it.isNotBlank() }

        val colorAttribute = item.attributes["color"]
        val color = colorAttribute?.value?.takeIf { it.isNotBlank() }

        // Extract pricing from PriceRange (has low/high Money objects)
        val pricingRange =
            item.estimatedPriceRange?.let {
                PricingRange(
                    min = it.low.amount.toInt(),
                    max = it.high.amount.toInt(),
                    currency = it.low.currencyCode,
                )
            }

        // Build pricing context from item condition if available
        val pricingContextHint =
            item.condition?.let { condition ->
                "condition: ${condition.displayName.lowercase()}"
            }

        return ItemInput(
            id = item.id,
            imageHint = null,
            inferredBrand = brand,
            itemType = itemType,
            material = material,
            color = color,
            pricingRange = pricingRange,
            pricingContextHint = pricingContextHint,
        )
    }
}
