package com.scanium.app.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.scanium.android.platform.adapters.toBitmap

/**
 * Platform helpers to bridge portable ImageRef into Android types.
 */
fun ImageRef?.toBitmap(): Bitmap? {
    return when (this) {
        is ImageRef.Bytes -> this.toBitmap()
        else -> null
    }
}

fun ImageRef?.toImageBitmap(): ImageBitmap? {
    return this.toBitmap()?.asImageBitmap()
}
