package com.scanium.app.camera

/**
 * Camera state machine for managing capture modes.
 *
 * States:
 * - IDLE: Camera preview active, no capture or scanning
 * - CAPTURING: Single frame capture in progress (transient)
 * - SCANNING: Continuous streaming analysis active
 */
enum class CameraState {
    /** Camera is idle, ready for capture */
    IDLE,

    /** Single frame capture in progress */
    CAPTURING,

    /** Continuous scanning active */
    SCANNING
}
