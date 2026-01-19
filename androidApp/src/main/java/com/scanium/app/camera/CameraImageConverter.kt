package com.scanium.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

internal class CameraImageConverter {
    /**
     * Converts ImageProxy to Bitmap.
     *
     * CRITICAL: Creates a FULL FRAME bitmap, not cropped to cropRect.
     * ML Kit's InputImage.fromMediaImage() does NOT honor cropRect - it processes the
     * full MediaImage buffer and returns bounding boxes in full frame coordinates.
     * Therefore, the bitmap used for thumbnail cropping must also be full frame.
     */
    fun toBitmap(imageProxy: ImageProxy): Bitmap {
        val width = imageProxy.width
        val height = imageProxy.height

        // YUV_420_888 format has 3 planes: Y, U, V
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        // NV21 format: tightly packed Y plane followed by interleaved VU
        val nv21Size = width * height + 2 * ((width + 1) / 2) * ((height + 1) / 2)
        val nv21 = ByteArray(nv21Size)

        // Copy Y plane - handle rowStride (padding between rows)
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        var pos = 0
        if (yPixelStride == 1 && yRowStride == width) {
            // Fast path: Y plane is tightly packed, copy entire buffer
            yBuffer.get(nv21, 0, width * height)
            pos = width * height
        } else {
            // Slow path: Y plane has padding, copy row by row
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        // Copy UV planes - interleave V and U into NV21 format
        // NV21 uses VU ordering (V first, then U)
        val uvRowStride = vPlane.rowStride
        val uvPixelStride = vPlane.pixelStride
        val vBuffer = vPlane.buffer
        val uBuffer = uPlane.buffer

        val uvWidth = (width + 1) / 2
        val uvHeight = (height + 1) / 2

        if (uvPixelStride == 2 && uvRowStride == width) {
            // Semi-planar UV: already interleaved and tightly packed
            // This is the common case for many devices
            vBuffer.position(0)
            vBuffer.get(nv21, pos, uvWidth * uvHeight * 2)
        } else {
            // Manual interleaving: copy row by row, interleaving V and U
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val vuPos = row * uvRowStride + col * uvPixelStride
                    nv21[pos++] = vBuffer.get(vuPos) // V comes first in NV21
                    nv21[pos++] = uBuffer.get(vuPos) // U comes second
                }
            }
        }

        // Create YUV image and compress to JPEG
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        val fullFrameRect = android.graphics.Rect(0, 0, width, height)
        yuvImage.compressToJpeg(fullFrameRect, 90, out)
        val jpegBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
}
