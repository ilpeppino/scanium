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
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        val fullFrameRect = android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height)
        yuvImage.compressToJpeg(fullFrameRect, 90, out)
        val jpegBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
    }
}
