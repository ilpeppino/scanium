package com.scanium.app.copy

/**
 * Policy for customer-safe copy generation.
 *
 * Defines banned tokens, confidence indicators, and sanitization rules.
 * All checks are case-insensitive.
 */
object CustomerSafeCopyPolicy {
    /**
     * Banned tokens that must NEVER appear in customer-facing copy.
     * Case-insensitive matching.
     */
    private val BANNED_TOKENS = setOf(
        "unknown",
        "generic",
        "unbranded",
        "confidence",
        "score",
        "might be",
        "possibly",
        "cannot determine",
    )

    /**
     * Regex patterns for confidence/percentage indicators that should be removed.
     * Examples: "58%", "confidence: 0.58", "0.95 confidence"
     */
    private val CONFIDENCE_PATTERNS = listOf(
        Regex("\\d+\\s*%"),  // Matches "58%" or "58 %"
        Regex("confidence\\s*[:\\-]?\\s*\\d+\\.?\\d*", RegexOption.IGNORE_CASE),
        Regex("\\d+\\.?\\d*\\s*confidence", RegexOption.IGNORE_CASE),
        Regex("\\d+\\.\\d+"),  // Remove decimal percentages like "0.58"
    )

    /**
     * Check if a string contains any banned tokens (case-insensitive).
     *
     * @param text The text to check
     * @return true if any banned token is found
     */
    fun containsBannedToken(text: String): Boolean {
        val lowerText = text.lowercase()
        return BANNED_TOKENS.any { token ->
            lowerText.contains(token)
        }
    }

    /**
     * Sanitize text by removing banned tokens and confidence indicators.
     * Returns empty string if text becomes empty after sanitization.
     *
     * @param text The text to sanitize
     * @return Sanitized text with banned tokens removed
     */
    fun sanitize(text: String): String {
        var result = text

        // Remove confidence patterns first
        CONFIDENCE_PATTERNS.forEach { pattern ->
            result = result.replace(pattern, "").trim()
        }

        // Remove banned tokens by word boundary
        BANNED_TOKENS.forEach { token ->
            // Case-insensitive word boundary removal
            val pattern = Regex("\\b$token\\b", RegexOption.IGNORE_CASE)
            result = result.replace(pattern, "").trim()
        }

        // Clean up extra whitespace
        result = result.replace(Regex("\\s+"), " ").trim()

        return result
    }

    /**
     * Check if a title is product-type level (not vague).
     * A vague title is one that is empty, or consists only of generic/banned terms.
     *
     * @param title The title to check
     * @return true if the title is a valid product-type level title
     */
    fun isProductTypeLevel(title: String): Boolean {
        if (title.isBlank()) return false
        if (containsBannedToken(title)) return false

        // Check for common vague patterns
        val lowerTitle = title.lowercase()
        val vaguePatterns = setOf(
            "item",
            "object",
            "thing",
            "stuff",
            "product",
        )

        return !vaguePatterns.contains(lowerTitle)
    }

    /**
     * Check if a price range is valid (min < max).
     *
     * @param range The pricing range to validate
     * @return true if the range is valid
     */
    fun isValidPriceRange(range: PricingRange): Boolean {
        return range.min >= 0 && range.max > range.min
    }

    /**
     * Format a price range into customer-safe string.
     *
     * @param range The pricing range
     * @return Formatted string like "€20–€40"
     */
    fun formatPriceRange(range: PricingRange): String {
        return "€${range.min}–€${range.max}"
    }

    /**
     * Create a default pricing context line.
     *
     * @param contextHint Optional hint about market conditions
     * @return Formatted context string like "Based on current market conditions"
     * @deprecated Use getPricingContextKey() instead for localized rendering
     */
    fun formatPricingContext(contextHint: String? = null): String {
        return if (contextHint?.isNotBlank() == true && !containsBannedToken(contextHint)) {
            "Based on $contextHint"
        } else {
            "Based on current market conditions"
        }
    }

    /**
     * Map pricing context hint to resource key for localized rendering.
     * Enables UI layer to use stringResource() for all locales.
     *
     * @param contextHint Optional hint about market conditions
     * @return Resource key (e.g., "pricing_context_current_market")
     */
    fun getPricingContextKey(contextHint: String? = null): String? {
        return if (contextHint?.isNotBlank() == true && !containsBannedToken(contextHint)) {
            // For custom hints, we'd need dynamic stringResource which isn't directly supported
            // For now, use default market conditions key
            "pricing_context_current_market"
        } else {
            "pricing_context_current_market"
        }
    }
}
