package com.scanium.shared.core.models.items

/**
 * Represents the physical condition of a scanned item.
 * User-editable field for marketplace listings and inventory management.
 */
enum class ItemCondition(
    val displayName: String,
    val description: String,
) {
    NEW(
        displayName = "New",
        description = "Brand new, never used",
    ),
    AS_GOOD_AS_NEW(
        displayName = "As good as new",
        description = "Like new condition with minimal signs of use",
    ),
    USED(
        displayName = "Used",
        description = "Previously used with visible wear",
    ),
    REFURBISHED(
        displayName = "Refurbished",
        description = "Restored to working condition",
    ),
}
