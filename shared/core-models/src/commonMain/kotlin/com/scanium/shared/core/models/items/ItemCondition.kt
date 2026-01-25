package com.scanium.shared.core.models.items

/**
 * Represents the physical condition of a scanned item.
 * User-editable field for marketplace listings and inventory management.
 */
enum class ItemCondition(
    val displayName: String,
    val description: String,
) {
    NEW_SEALED(
        displayName = "New, Sealed",
        description = "Factory sealed, never opened",
    ),
    NEW_WITH_TAGS(
        displayName = "New with Tags",
        description = "New with original tags attached",
    ),
    NEW_WITHOUT_TAGS(
        displayName = "New without Tags",
        description = "New, no tags, never used",
    ),
    LIKE_NEW(
        displayName = "Like New",
        description = "Used briefly, no visible wear",
    ),
    GOOD(
        displayName = "Good",
        description = "Normal use, minor wear",
    ),
    FAIR(
        displayName = "Fair",
        description = "Noticeable wear, fully functional",
    ),
    POOR(
        displayName = "Poor",
        description = "Heavy wear, may have defects",
    ),
}
