package com.scanium.shared.core.models.items

/**
 * Represents the physical condition of a scanned item.
 * User-editable field for marketplace listings and inventory management.
 *
 * LOCALIZATION:
 * Display strings are resolved via ItemLocalizer using string resources.
 * This keeps the enum language-neutral while supporting full i18n.
 */
enum class ItemCondition {
    NEW_SEALED,
    NEW_WITH_TAGS,
    NEW_WITHOUT_TAGS,
    LIKE_NEW,
    GOOD,
    FAIR,
    POOR,
}
