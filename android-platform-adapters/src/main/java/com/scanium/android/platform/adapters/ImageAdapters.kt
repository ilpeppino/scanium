package com.scanium.android.platform.adapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.scanium.shared.core.models.model.ImageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Android-specific adapters between [Bitmap] and portable [ImageRef].
 */
fun Bitmap.toImageRefJpeg(quality: Int = 85): ImageRef.Bytes {
    require(quality in 0..100) { "quality must be between 0 and 100" }

    val outputStream = ByteArrayOutputStream()
    val success = compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    require(success) { "Bitmap.compress failed" }

    val encoded = outputStream.toByteArray()
    return ImageRef.Bytes(
        bytes = encoded,
        mimeType = "image/jpeg",
        width = width,
        height = height,
    )
}

suspend fun ImageRef.Bytes.toBitmap(): Bitmap {
    val bitmap = withContext(Dispatchers.Default) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    return requireNotNull(bitmap) { "Failed to decode Bitmap from ImageRef bytes" }
}
