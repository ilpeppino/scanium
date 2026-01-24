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
 * Updated to streamlined 5-step flow (Welcome + Camera UI education):
 * 0. WELCOME - Introduction to Scanium
 * 1. CAMERA_SHUTTER - Shutter button education
 * 2. CAMERA_FLIP - Flip camera button education
 * 3. CAMERA_ITEMS - Items list button education
 * 4. CAMERA_SETTINGS - Settings button education
 */
enum class TourStepKey {
    WELCOME,
    CAMERA_SHUTTER,
    CAMERA_FLIP,
    CAMERA_ITEMS,
    CAMERA_SETTINGS,

    // Legacy keys kept for reference (no longer used in tour)
    @Deprecated("Removed in streamlined tour")
    TAKE_FIRST_PHOTO,

    @Deprecated("Removed in streamlined tour")
    OPEN_ITEM_LIST,

    @Deprecated("Removed in streamlined tour")
    ADD_EXTRA_PHOTOS,

    @Deprecated("Removed in streamlined tour")
    EDIT_ATTRIBUTES,

    @Deprecated("Removed in streamlined tour")
    USE_AI_ASSISTANT,

    @Deprecated("Removed in streamlined tour")
    SAVE_CHANGES,

    @Deprecated("Removed in streamlined tour")
    SHARE_BUNDLE,

    @Deprecated("Removed in streamlined tour")
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
