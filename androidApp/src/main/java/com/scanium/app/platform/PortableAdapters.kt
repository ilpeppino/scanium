package com.scanium.app.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
import com.scanium.app.perf.PerformanceMonitor
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.model.NormalizedRect
import java.io.ByteArrayOutputStream

fun Rect.toNormalizedRect(
    frameW: Int,
    frameH: Int,
): NormalizedRect {
    if (frameW <= 0 || frameH <= 0) {
        return NormalizedRect(0f, 0f, 0f, 0f)
    }

    return NormalizedRect(
        left = left.toFloat() / frameW,
        top = top.toFloat() / frameH,
        right = right.toFloat() / frameW,
        bottom = bottom.toFloat() / frameH,
    ).clampToUnit()
}

fun RectF.toNormalizedRect(
    frameW: Int,
    frameH: Int,
): NormalizedRect {
    if (frameW <= 0 || frameH <= 0) {
        return NormalizedRect(0f, 0f, 0f, 0f)
    }

    return NormalizedRect(
        left = left / frameW,
        top = top / frameH,
        right = right / frameW,
        bottom = bottom / frameH,
    ).clampToUnit()
}

fun NormalizedRect.toRectF(
    frameW: Int,
    frameH: Int,
): RectF {
    val clamped = clampToUnit()
    return RectF(
        clamped.left * frameW,
        clamped.top * frameH,
        clamped.right * frameW,
        clamped.bottom * frameH,
    )
}

fun Bitmap.toImageRefJpeg(quality: Int = 85): ImageRef.Bytes {
    val safeQuality = quality.coerceIn(0, 100)
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, safeQuality, stream)
    return ImageRef.Bytes(
        bytes = stream.toByteArray(),
        mimeType = "image/jpeg",
        width = width,
        height = height,
    )
}

fun ImageRef.Bytes.toBitmap(): Bitmap =
    PerformanceMonitor.measureBitmapDecode("${width}x$height") {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: Bitmap.createBitmap(
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
    }
