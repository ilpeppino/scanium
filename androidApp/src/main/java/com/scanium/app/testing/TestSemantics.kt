package com.scanium.app.testing

/**
 * Centralized test tag constants for Compose UI testing.
 *
 * These tags are used by instrumented tests to locate UI elements.
 * All tags should be stable and not change between releases to ensure
 * test reliability.
 *
 * ## Naming Convention
 * - Use snake_case for tag names
 * - Group by feature/screen with a prefix
 * - Keep names descriptive but concise
 */
object TestSemantics {
    // ========== CameraScreen ==========
    /** Root container for camera screen */
    const val CAMERA_SCREEN = "camera_screen"

    /** Camera preview view */
    const val CAMERA_PREVIEW = "camera_preview"

    /** Shutter/capture button */
    const val CAMERA_SHUTTER = "camera_shutter"

    /** Items list navigation button */
    const val CAMERA_ITEMS_BUTTON = "camera_items_button"

    /** Settings navigation button */
    const val CAMERA_SETTINGS_BUTTON = "camera_settings_button"

    // ========== Camera Pipeline Debug Overlay ==========

    /** Root container for pipeline debug overlay */
    const val CAM_PIPELINE_DEBUG = "cam_pipeline_debug"

    /** Pipeline status text (OK, RECOVERING, STALLED, etc.) */
    const val CAM_STATUS = "cam_status"

    /** Last frame timestamp indicator */
    const val CAM_LAST_FRAME = "cam_last_frame"

    /** Last bounding box timestamp indicator */
    const val CAM_LAST_BBOX = "cam_last_bbox"

    /** Analysis FPS indicator */
    const val CAM_FPS = "cam_fps"

    /** Session ID indicator */
    const val CAM_SESSION_ID = "cam_session_id"

    /** Analysis flowing indicator */
    const val CAM_ANALYSIS_FLOWING = "cam_analysis_flowing"

    // ========== ItemsListScreen ==========

    /** Root container for items list screen */
    const val ITEMS_LIST = "items_list"

    /** Items list scrollable content */
    const val ITEMS_LIST_CONTENT = "items_list_content"

    /** Single item row in the list */
    const val ITEM_ROW = "item_row"

    /** Share/export action button */
    const val SHARE_BUTTON = "share_button"

    /** Share dropdown menu */
    const val SHARE_MENU = "share_menu"

    /** Export CSV option */
    const val EXPORT_CSV = "export_csv"

    /** Export ZIP option */
    const val EXPORT_ZIP = "export_zip"

    /** Clear all items button */
    const val CLEAR_ALL_BUTTON = "clear_all_button"

    // ========== Classification Indicators ==========

    /** Cloud mode indicator */
    const val CLOUD_MODE_INDICATOR = "cloud_mode_indicator"

    /** Classification status for an item */
    const val CLASSIFICATION_STATUS = "classification_status"

    /** Retry classification button */
    const val RETRY_CLASSIFICATION = "retry_classification"

    // ========== Developer Options ==========

    /** Backend health status indicator */
    const val BACKEND_HEALTH_STATUS = "backend_health_status"

    /** Backend latency indicator */
    const val BACKEND_LATENCY = "backend_latency"

    // ========== Detection Overlay ==========

    /** Detection overlay container */
    const val DETECTION_OVERLAY = "detection_overlay"

    /** Single bounding box */
    const val BOUNDING_BOX = "bounding_box"

    /**
     * Generate a unique tag for an item row by its ID.
     */
    fun itemRowTag(itemId: String): String = "${ITEM_ROW}_$itemId"

    /**
     * Generate a unique tag for a bounding box by detection ID.
     */
    fun bboxTag(detectionId: Int): String = "${BOUNDING_BOX}_$detectionId"
}
