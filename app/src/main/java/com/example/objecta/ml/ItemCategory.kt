package com.example.objecta.ml

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
    UNKNOWN("Unknown");

    companion object {
        /**
         * Maps ML Kit category labels to our ItemCategory enum.
         * ML Kit provides categories like "Fashion good", "Home good", "Food", etc.
         */
        fun fromMlKitLabel(label: String?): ItemCategory {
            return when (label?.lowercase()) {
                "fashion good", "fashion", "clothing" -> FASHION
                "home good", "home", "furniture" -> HOME_GOOD
                "food", "food product" -> FOOD
                "place" -> PLACE
                "plant" -> PLANT
                "electronics", "electronic" -> ELECTRONICS
                else -> UNKNOWN
            }
        }
    }
}
