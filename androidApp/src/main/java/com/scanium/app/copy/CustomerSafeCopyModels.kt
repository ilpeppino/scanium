package com.scanium.app.copy

/**
 * Display mode for copy output.
 *
 * - ITEM_LIST: Compact output for list views
 * - ITEM_CARD: Expanded output for card views
 * - ASSISTANT: Extended output with highlights and tags (LLM-friendly structure)
 */
enum class CopyDisplayMode {
    ITEM_LIST,
    ITEM_CARD,
    ASSISTANT,
}

/**
 * Simple input model for item data, portable across the app.
 * All fields are optional to support partial data scenarios.
 *
 * @param id Unique identifier for the item
 * @param imageHint Optional hint about the item's appearance (e.g., "red", "leather")
 * @param inferredBrand Optional detected brand name
 * @param itemType Optional product-type label (e.g., "hiking boots", "leather bag")
 * @param material Optional material (e.g., "wood", "ceramic")
 * @param color Optional color
 * @param pricingRange Optional resale price range in EUR
 * @param pricingContextHint Optional hint about pricing context (e.g., "condition: excellent")
 */
data class ItemInput(
    val id: String,
    val imageHint: String? = null,
    val inferredBrand: String? = null,
    val itemType: String? = null,
    val material: String? = null,
    val color: String? = null,
    val pricingRange: PricingRange? = null,
    val pricingContextHint: String? = null,
)

/**
 * Price range in EUR.
 *
 * @param min Minimum resale value (inclusive)
 * @param max Maximum resale value (inclusive)
 * @param currency Always "EUR"
 */
data class PricingRange(
    val min: Int,
    val max: Int,
    val currency: String = "EUR",
)

/**
 * Customer-safe display output.
 *
 * All strings are sanitized to remove banned tokens, confidence indicators, and vague labels.
 *
 * @param title Product-type level title (never vague like "Item" or "Unknown")
 * @param priceLine Optional formatted price line (e.g., "Typical resale value: €20–€40")
 * @param priceContext Optional context for pricing (e.g., "Based on current market conditions")
 * @param highlights Optional list of key attributes (ASSISTANT mode only, empty otherwise)
 * @param tags Optional list of categorization tags (ASSISTANT mode only, empty otherwise)
 */
data class CustomerSafeCopy(
    val title: String,
    val priceLine: String? = null,
    val priceContext: String? = null,
    val highlights: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)
