package com.scanium.app.core.geometry

/**
 * Normalized rectangle where coordinates are expressed between 0f and 1f.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun clamp01(): NormalizedRect = NormalizedRect(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f),
    )

    fun width(): Float = right - left

    fun height(): Float = bottom - top
}
