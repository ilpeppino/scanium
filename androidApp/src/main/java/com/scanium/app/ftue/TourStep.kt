package com.scanium.app.ftue

/**
 * Represents which screen a tour step belongs to.
 */
enum class TourScreen {
    CAMERA,
    ITEMS_LIST
}

/**
 * Unique identifiers for each step in the FTUE tour.
 */
enum class TourStepKey {
    WELCOME,
    CAMERA_SETTINGS,
    CAMERA_MODE_ICONS,
    CAMERA_SHUTTER,
    CAMERA_ITEMS_BUTTON,
    ITEMS_ACTION_FAB,
    ITEMS_AI_ASSISTANT,
    ITEMS_SWIPE_DELETE,
    ITEMS_SELECTION,
    COMPLETION
}

/**
 * Defines the shape of the spotlight cutout overlay.
 */
enum class SpotlightShape {
    CIRCLE,
    ROUNDED_RECT
}

/**
 * Represents a single step in the FTUE guided tour.
 *
 * @param key Unique identifier for this step
 * @param screen Which screen this step is shown on
 * @param targetKey Key for capturing target bounds (null for full-screen overlays)
 * @param title Title text for the tooltip
 * @param description Detailed description text for the tooltip
 * @param requiresUserAction If true, user must interact with highlighted control to proceed
 * @param spotlightShape Shape of the spotlight cutout (default: ROUNDED_RECT)
 */
data class TourStep(
    val key: TourStepKey,
    val screen: TourScreen,
    val targetKey: String?,
    val title: String,
    val description: String,
    val requiresUserAction: Boolean = false,
    val spotlightShape: SpotlightShape = SpotlightShape.ROUNDED_RECT
)
