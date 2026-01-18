package com.scanium.app.ftue

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Registry for tracking anchor positions of Camera UI elements for the FTUE system.
 * Uses global root coordinates for accurate positioning across orientation changes.
 *
 * Phase 2: Anchor Registration
 * - Tracks button positions using onGloballyPositioned
 * - Converts to root coordinates for overlay rendering
 * - Logs anchor registration for debugging
 */
@Stable
class CameraUiFtueAnchorRegistry {
    companion object {
        private const val TAG = "FTUE_CAMERA_UI"
    }

    private val _anchors = MutableStateFlow<Map<String, Rect>>(emptyMap())

    /**
     * StateFlow of registered anchor rects.
     * Key: anchor ID (e.g., "camera_ui_shutter")
     * Value: Rect in root coordinates
     */
    val anchors: StateFlow<Map<String, Rect>> = _anchors.asStateFlow()

    /**
     * Register an anchor rect for a UI element.
     * @param id Unique identifier (e.g., "camera_ui_shutter")
     * @param bounds Rect in root coordinates
     */
    fun register(
        id: String,
        bounds: Rect,
    ) {
        _anchors.value = _anchors.value + (id to bounds)
        Log.d(
            TAG,
            "Anchor registered: id=$id, " +
                "x=${bounds.left.toInt()}, y=${bounds.top.toInt()}, " +
                "w=${bounds.width.toInt()}, h=${bounds.height.toInt()}",
        )
    }

    /**
     * Remove an anchor registration.
     * @param id Anchor identifier to clear
     */
    fun clear(id: String) {
        _anchors.value = _anchors.value - id
        Log.d(TAG, "Anchor cleared: id=$id")
    }

    /**
     * Clear all anchors.
     * Called when CameraScreen is disposed.
     */
    fun clearAll() {
        if (_anchors.value.isNotEmpty()) {
            Log.d(TAG, "Clearing all anchors (${_anchors.value.size} total)")
        }
        _anchors.value = emptyMap()
    }

    /**
     * Get anchor rect for a specific ID.
     * @return Rect if registered, null otherwise
     */
    fun getAnchor(id: String): Rect? = _anchors.value[id]

    /**
     * Check if all expected anchors are registered.
     * @param expectedIds List of required anchor IDs
     * @return true if all anchors are present
     */
    fun hasAllAnchors(expectedIds: List<String>): Boolean {
        val currentAnchors = _anchors.value
        return expectedIds.all { currentAnchors.containsKey(it) }
    }
}

/**
 * Modifier extension to register an element as an FTUE anchor.
 * Uses onGloballyPositioned to capture the element's bounds in root coordinates.
 *
 * @param id Unique anchor identifier (e.g., "camera_ui_shutter")
 * @param registry Registry to store the anchor position
 */
fun Modifier.ftueAnchor(
    id: String,
    registry: CameraUiFtueAnchorRegistry,
): Modifier =
    this.onGloballyPositioned { coordinates ->
        val positionInRoot = coordinates.positionInRoot()
        val size = coordinates.size

        val anchorRect =
            Rect(
                left = positionInRoot.x,
                top = positionInRoot.y,
                right = positionInRoot.x + size.width,
                bottom = positionInRoot.y + size.height,
            )

        registry.register(id, anchorRect)
    }
