package com.scanium.app.copy

/**
 * Pure Kotlin formatter for customer-safe, resale-ready display copy.
 *
 * Constraints:
 * - NEVER outputs banned tokens (case-insensitive): unknown, generic, unbranded, confidence, %, score, might be, possibly, cannot determine
 * - NEVER outputs confidence numbers or percentages
 * - Titles must be product-type level (never "Item", "Object", "Thing", "Unknown", etc.)
 * - Pricing follows format: "Typical resale value: €X–€Y" with separate "Based on …" line
 * - Supports "fewer but confident" option: drops items when title cannot be made product-type level
 *
 * No Compose or Android framework dependencies.
 */
object CustomerSafeCopyFormatter {
    /**
     * Format item data into customer-safe display copy.
     *
     * @param input Item data to format
     * @param mode Display mode (ITEM_LIST, ITEM_CARD, ASSISTANT)
     * @param dropIfWeak If true, returns null when title cannot be made product-type level
     * @return Customer-safe copy, or null if dropIfWeak=true and title is weak
     */
    fun format(
        input: ItemInput,
        mode: CopyDisplayMode = CopyDisplayMode.ITEM_CARD,
        dropIfWeak: Boolean = false,
    ): CustomerSafeCopy? {
        // Construct the title
        val rawTitle = constructTitle(input)
        val sanitizedTitle = CustomerSafeCopyPolicy.sanitize(rawTitle)

        // Check if title is product-type level
        if (!CustomerSafeCopyPolicy.isProductTypeLevel(sanitizedTitle)) {
            return if (dropIfWeak) {
                null // Drop this item: cannot make title product-type level
            } else {
                // Return default copy with a generic but safe title
                CustomerSafeCopy(
                    title = "Item",
                    pricing = null,
                    highlights = emptyList(),
                    tags = emptyList(),
                )
            }
        }

        // Format pricing into structured display (language-agnostic)
        val pricing = formatPricingStructured(input.pricingRange, input.pricingContextHint)

        // Generate highlights and tags based on mode
        val (highlights, tags) =
            when (mode) {
                CopyDisplayMode.ASSISTANT -> generateHighlightsAndTags(input)
                else -> Pair(emptyList(), emptyList())
            }

        return CustomerSafeCopy(
            title = capitalizeFirstLetter(sanitizedTitle),
            pricing = pricing,
            highlights = highlights,
            tags = tags,
        )
    }

    /**
     * Construct a title from available hints.
     * Priority: itemType > (material + color) > brand > generic
     *
     * @param input Item data
     * @return Raw title string (before sanitization)
     */
    private fun constructTitle(input: ItemInput): String {
        // Prefer itemType if present
        if (!input.itemType.isNullOrBlank()) {
            return input.itemType
        }

        // Fallback: try to build from material and color
        if (!input.material.isNullOrBlank() || !input.color.isNullOrBlank()) {
            val parts =
                listOfNotNull(
                    input.color?.takeIf { it.isNotBlank() },
                    input.material?.takeIf { it.isNotBlank() },
                    "item",
                )
            return parts.joinToString(" ")
        }

        // Last resort: use brand if present
        if (!input.inferredBrand.isNullOrBlank()) {
            return input.inferredBrand
        }

        // Fallback: generic placeholder (will be caught as weak title)
        return "Item"
    }

    /**
     * Format price information into structured display (language-agnostic).
     * UI layer handles localized rendering via stringResource().
     *
     * @param range Optional pricing range
     * @param contextHint Optional pricing context
     * @return Structured PricingDisplay, or null if no valid range
     */
    private fun formatPricingStructured(
        range: PricingRange?,
        contextHint: String?,
    ): PricingDisplay? {
        if (range == null) {
            return null
        }

        if (!CustomerSafeCopyPolicy.isValidPriceRange(range)) {
            return null
        }

        val contextKey = CustomerSafeCopyPolicy.getPricingContextKey(contextHint)

        return PricingDisplay(
            min = range.min,
            max = range.max,
            currency = range.currency,
            contextKey = contextKey,
        )
    }

    /**
     * Format price information into pricing lines (legacy).
     *
     * @param range Optional pricing range
     * @param contextHint Optional pricing context
     * @return Pair of (priceLine, priceContext), both may be null
     * @deprecated Use formatPricingStructured() for localization-safe output
     */
    private fun formatPricing(
        range: PricingRange?,
        contextHint: String?,
    ): Pair<String?, String?> {
        if (range == null) {
            return Pair(null, null)
        }

        if (!CustomerSafeCopyPolicy.isValidPriceRange(range)) {
            return Pair(null, null)
        }

        val priceLine = "Typical resale value: ${CustomerSafeCopyPolicy.formatPriceRange(range)}"
        val priceContext = CustomerSafeCopyPolicy.formatPricingContext(contextHint)

        return Pair(priceLine, priceContext)
    }

    /**
     * Generate highlights and tags for ASSISTANT mode.
     * Safe for LLM processing: no banned tokens, no confidence indicators.
     *
     * @param input Item data
     * @return Pair of (highlights, tags)
     */
    private fun generateHighlightsAndTags(input: ItemInput): Pair<List<String>, List<String>> {
        val highlights = mutableListOf<String>()
        val tags = mutableListOf<String>()

        // Build highlights from available attributes
        if (!input.color.isNullOrBlank()) {
            val sanitized = CustomerSafeCopyPolicy.sanitize(input.color)
            if (sanitized.isNotEmpty()) {
                highlights.add("Color: $sanitized")
            }
        }

        if (!input.material.isNullOrBlank()) {
            val sanitized = CustomerSafeCopyPolicy.sanitize(input.material)
            if (sanitized.isNotEmpty()) {
                highlights.add("Material: $sanitized")
            }
        }

        if (!input.imageHint.isNullOrBlank()) {
            val sanitized = CustomerSafeCopyPolicy.sanitize(input.imageHint)
            if (sanitized.isNotEmpty()) {
                highlights.add("Details: $sanitized")
            }
        }

        // Build tags from brand and category hints
        if (!input.inferredBrand.isNullOrBlank()) {
            val sanitized = CustomerSafeCopyPolicy.sanitize(input.inferredBrand)
            if (sanitized.isNotEmpty()) {
                tags.add(sanitized)
            }
        }

        // Infer category tag from itemType if available
        if (!input.itemType.isNullOrBlank()) {
            val category = inferCategoryTag(input.itemType)
            if (category.isNotEmpty()) {
                tags.add(category)
            }
        }

        return Pair(highlights, tags)
    }

    /**
     * Infer a category tag from itemType.
     * Simple heuristic: look for common keywords.
     *
     * @param itemType The item type to categorize
     * @return A sanitized category tag, or empty string if no match
     */
    private fun inferCategoryTag(itemType: String): String {
        val lower = itemType.lowercase()

        val category =
            when {
                lower.contains("shoe") || lower.contains("boot") || lower.contains("sneaker") -> "Footwear"
                lower.contains("dress") || lower.contains("shirt") || lower.contains("pant") || lower.contains("jacket") -> "Apparel"
                lower.contains("bag") || lower.contains("purse") || lower.contains("wallet") -> "Accessories"
                lower.contains("watch") || lower.contains("jewelry") -> "Jewelry"
                lower.contains("phone") || lower.contains("laptop") || lower.contains("tablet") -> "Electronics"
                lower.contains("book") || lower.contains("magazine") -> "Media"
                lower.contains("furniture") || lower.contains("chair") || lower.contains("table") -> "Furniture"
                lower.contains("kitchen") || lower.contains("cookware") -> "Kitchen"
                lower.contains("sport") || lower.contains("equipment") -> "Sports"
                lower.contains("toy") || lower.contains("game") -> "Toys"
                else -> ""
            }

        return if (category.isNotEmpty()) {
            CustomerSafeCopyPolicy.sanitize(category)
        } else {
            ""
        }
    }

    /**
     * Format multiple items, optionally dropping weak titles.
     *
     * @param items Items to format
     * @param mode Display mode
     * @param dropIfWeak If true, drops items with weak titles instead of using fallback
     * @return List of formatted copy, excluding nulls from dropIfWeak
     */
    fun formatBatch(
        items: List<ItemInput>,
        mode: CopyDisplayMode = CopyDisplayMode.ITEM_CARD,
        dropIfWeak: Boolean = false,
    ): List<CustomerSafeCopy> = items.mapNotNull { format(it, mode, dropIfWeak) }

    /**
     * Capitalize the first letter of a string.
     * Ensures product type strings are properly capitalized.
     *
     * @param text The text to capitalize
     * @return Text with first letter capitalized
     */
    private fun capitalizeFirstLetter(text: String): String {
        if (text.isEmpty()) return text
        val first = text[0].uppercaseChar()
        return if (text.length == 1) {
            first.toString()
        } else {
            first.toString() + text.substring(1)
        }
    }
}
