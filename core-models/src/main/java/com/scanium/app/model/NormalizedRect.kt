package com.scanium.app.model

import kotlin.math.max
import kotlin.math.min

/**
 * Normalized rectangle with coordinates expressed as fractions of the source dimensions.
 * Includes helpers to clamp into the [0f, 1f] range and basic geometry accessors.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /**
     * Width constrained to non-negative values.
     */
    val width: Float
        get() = (right - left).coerceAtLeast(0f)

    /**
     * Height constrained to non-negative values.
     */
    val height: Float
        get() = (bottom - top).coerceAtLeast(0f)

    /**
     * Area constrained to non-negative values.
     */
    val area: Float
        get() = width * height

    /**
     * Returns true when the rectangle is already within the normalized bounds and ordered.
     */
    fun isNormalized(): Boolean {
        return left in 0f..1f && right in 0f..1f && top in 0f..1f && bottom in 0f..1f && right >= left && bottom >= top
    }

    /**
     * Clamps coordinates into the [0f, 1f] range and fixes ordering when left > right or top > bottom.
     */
    fun clampToUnit(): NormalizedRect {
        val clampedLeft = left.coerceIn(0f, 1f)
        val clampedRight = right.coerceIn(0f, 1f)
        val clampedTop = top.coerceIn(0f, 1f)
        val clampedBottom = bottom.coerceIn(0f, 1f)

        return NormalizedRect(
            min(clampedLeft, clampedRight),
            min(clampedTop, clampedBottom),
            max(clampedLeft, clampedRight),
            max(clampedTop, clampedBottom),
        )
    }
}
