package com.scanium.app.ui.motion

import com.scanium.app.BuildConfig
import com.scanium.app.ui.motion.MotionConfig.setMotionOverlaysEnabled

/**
 * Configuration for motion overlays.
 *
 * Provides a debug toggle to enable/disable motion overlays during development.
 * In release builds, motion overlays are always enabled.
 */
object MotionConfig {
    /**
     * Internal flag to disable motion overlays in debug builds.
     * Can be toggled via developer settings if such a system exists.
     */
    @Volatile
    private var motionOverlaysDisabled: Boolean = false

    /**
     * Whether motion overlays are enabled.
     *
     * - In DEBUG builds: can be toggled via [setMotionOverlaysEnabled]
     * - In RELEASE builds: always enabled
     */
    val isMotionOverlaysEnabled: Boolean
        get() = if (BuildConfig.DEBUG) !motionOverlaysDisabled else true

    /**
     * Enable or disable motion overlays (DEBUG builds only).
     * Has no effect in release builds.
     */
    fun setMotionOverlaysEnabled(enabled: Boolean) {
        if (BuildConfig.DEBUG) {
            motionOverlaysDisabled = !enabled
        }
    }

    /**
     * Toggle motion overlays on/off (DEBUG builds only).
     * @return The new enabled state
     */
    fun toggleMotionOverlays(): Boolean {
        if (BuildConfig.DEBUG) {
            motionOverlaysDisabled = !motionOverlaysDisabled
        }
        return isMotionOverlaysEnabled
    }
}
