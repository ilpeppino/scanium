package com.scanium.app.ui.motion

/**
 * Scanium motion language timing and visual constants.
 *
 * Brand motion rules (strict):
 * - Motion must be minimal, fast, and confidence-inspiring
 * - No bouncy/spring/elastic effects
 * - Yellow (***REMOVED***FFD400) is used ONLY as an action accent, not as dominant backgrounds
 * - No continuous looping animations for scanning
 */
object MotionConstants {
    // ==================== Timing Constraints ====================

    /** Scan frame appear: fast fade-in to feel instant */
    const val SCAN_FRAME_APPEAR_MS = 100

    /** Lightning pulse travel: single pass, not looping */
    const val LIGHTNING_PULSE_MIN_MS = 200
    const val LIGHTNING_PULSE_MAX_MS = 300
    const val LIGHTNING_PULSE_DURATION_MS = 250

    /** Price count-up: total duration for the animation */
    const val PRICE_COUNT_UP_DURATION_MS = 1200

    /** Number of discrete steps for price count-up (3-4 per spec) */
    const val PRICE_COUNT_UP_STEPS = 4

    /** Confirmation glow settle time */
    const val CONFIRMATION_GLOW_MS = 200

    // ==================== Visual Dimensions ====================

    /** Scan frame corner radius */
    const val SCAN_FRAME_CORNER_RADIUS_DP = 12f

    /** Scan frame stroke width */
    const val SCAN_FRAME_STROKE_WIDTH_DP = 2.5f

    /** Lightning pulse line width - subtle, not overwhelming */
    const val LIGHTNING_PULSE_WIDTH_DP = 3f

    /** Lightning pulse glow spread */
    const val LIGHTNING_PULSE_GLOW_WIDTH_DP = 8f

    // ==================== Alpha Values ====================

    /** Scan frame initial alpha (start of fade-in) */
    const val SCAN_FRAME_ALPHA_START = 0f

    /** Scan frame final alpha */
    const val SCAN_FRAME_ALPHA_END = 0.9f

    /** Lightning pulse core alpha */
    const val LIGHTNING_PULSE_ALPHA = 0.95f

    /** Lightning pulse glow alpha */
    const val LIGHTNING_PULSE_GLOW_ALPHA = 0.4f

    // ==================== Debounce ====================

    /** Minimum interval between pulse triggers (per item confirmation) */
    const val PULSE_DEBOUNCE_MS = 500L
}
