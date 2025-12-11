package com.scanium.app.ml

/**
 * Categories for detected objects, used for pricing and display.
 * These map loosely to ML Kit's coarse object categories.
 */
enum class ItemCategory(val displayName: String) {
    FASHION("Fashion"),
    HOME_GOOD("Home Good"),
    FOOD("Food Product"),
    PLACE("Place"),
    PLANT("Plant"),
    ELECTRONICS("Electronics"),
    DOCUMENT("Document"),
    UNKNOWN("Unknown");

    companion object {
        /**
         * Maps ML Kit category labels to our ItemCategory enum.
         * ML Kit provides categories like "Fashion good", "Home good", "Food", etc.
         */
        fun fromMlKitLabel(label: String?): ItemCategory {
            val normalized = label?.trim()?.lowercase()
            if (normalized.isNullOrEmpty()) return UNKNOWN

            return when (normalized) {
                "fashion good", "fashion", "clothing", "apparel", "shoe", "bag" -> FASHION
                "home good", "home", "furniture", "sofa", "chair", "kitchen" -> HOME_GOOD
                "food", "food product", "fruit", "vegetable", "drink", "snack" -> FOOD
                "place" -> PLACE
                "plant", "flower" -> PLANT
                "electronics", "electronic", "device", "phone", "laptop", "monitor", "tv", "gadget" -> ELECTRONICS
                else -> UNKNOWN
            }
        }
    }
}
