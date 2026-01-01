package com.scanium.android.platform.adapters

import android.graphics.Rect
import android.graphics.RectF
import com.scanium.core.models.geometry.NormalizedRect

private fun requirePositiveDimensions(
    frameWidth: Int,
    frameHeight: Int,
) {
    require(frameWidth > 0 && frameHeight > 0) { "frame dimensions must be positive" }
}

fun RectF.toNormalizedRect(
    frameWidth: Int,
    frameHeight: Int,
): NormalizedRect {
    requirePositiveDimensions(frameWidth, frameHeight)

    val normalized =
        NormalizedRect(
            left = left / frameWidth,
            top = top / frameHeight,
            right = right / frameWidth,
            bottom = bottom / frameHeight,
        )
    return normalized.clampToUnit()
}

fun Rect.toNormalizedRect(
    frameWidth: Int,
    frameHeight: Int,
): NormalizedRect {
    requirePositiveDimensions(frameWidth, frameHeight)

    val normalized =
        NormalizedRect(
            left = left.toFloat() / frameWidth,
            top = top.toFloat() / frameHeight,
            right = right.toFloat() / frameWidth,
            bottom = bottom.toFloat() / frameHeight,
        )
    return normalized.clampToUnit()
}

fun NormalizedRect.toRectF(
    frameWidth: Int,
    frameHeight: Int,
): RectF {
    requirePositiveDimensions(frameWidth, frameHeight)
    val clamped = clampToUnit()
    return RectF(
        clamped.left * frameWidth,
        clamped.top * frameHeight,
        clamped.right * frameWidth,
        clamped.bottom * frameHeight,
    )
}

fun NormalizedRect.toRect(
    frameWidth: Int,
    frameHeight: Int,
): Rect {
    requirePositiveDimensions(frameWidth, frameHeight)
    val clamped = clampToUnit()
    return Rect(
        (clamped.left * frameWidth).toInt(),
        (clamped.top * frameHeight).toInt(),
        (clamped.right * frameWidth).toInt(),
        (clamped.bottom * frameHeight).toInt(),
    )
}
