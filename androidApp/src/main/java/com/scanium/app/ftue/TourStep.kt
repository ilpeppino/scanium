package com.scanium.app.ftue

import androidx.annotation.StringRes

/**
 * Represents which screen a tour step belongs to.
 */
enum class TourScreen {
    CAMERA,
    ITEMS_LIST,
    EDIT_ITEM,
}

/**
 * Unique identifiers for each step in the FTUE tour.
 *
 * Updated to match the 7-step guided onboarding flow:
 * 1. TAKE_FIRST_PHOTO - Capture your first item
 * 2. OPEN_ITEM_LIST - View your scanned items
 * 3. ADD_EXTRA_PHOTOS - Add more photos to items
 * 4. EDIT_ATTRIBUTES - Improve item details
 * 5. USE_AI_ASSISTANT - Generate descriptions with AI
 * 6. SAVE_CHANGES - Save your edits
 * 7. SHARE_BUNDLE - Export and share items
 */
enum class TourStepKey {
    WELCOME,
    TAKE_FIRST_PHOTO,
    OPEN_ITEM_LIST,
    ADD_EXTRA_PHOTOS,
    EDIT_ATTRIBUTES,
    USE_AI_ASSISTANT,
    SAVE_CHANGES,
    SHARE_BUNDLE,
    COMPLETION,
}

/**
 * Defines the shape of the spotlight cutout overlay.
 */
enum class SpotlightShape {
    CIRCLE,
    ROUNDED_RECT,
}

/**
 * Represents a single step in the FTUE guided tour.
 *
 * @param key Unique identifier for this step
 * @param screen Which screen this step is shown on
 * @param targetKey Key for capturing target bounds (null for full-screen overlays)
 * @param titleRes String resource ID for the tooltip title
 * @param descriptionRes String resource ID for the tooltip description
 * @param requiresUserAction If true, user must interact with highlighted control to proceed
 * @param spotlightShape Shape of the spotlight cutout (default: ROUNDED_RECT)
 */
data class TourStep(
    val key: TourStepKey,
    val screen: TourScreen,
    val targetKey: String?,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val requiresUserAction: Boolean = false,
    val spotlightShape: SpotlightShape = SpotlightShape.ROUNDED_RECT,
)
