package com.scanium.app.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.scanium.android.platform.adapters.toBitmap
import com.scanium.app.items.ThumbnailCache
import com.scanium.shared.core.models.model.ImageRef

/**
 * Platform helpers to bridge portable ImageRef into Android types.
 */
fun ImageRef?.resolveBytes(): ImageRef.Bytes? {
    return when (this) {
        is ImageRef.Bytes -> this
        is ImageRef.CacheKey -> ThumbnailCache.get(key)
        else -> null
    }
}

fun ImageRef?.toBitmap(): Bitmap? {
    return resolveBytes()?.toBitmap()
}

fun ImageRef?.toImageBitmap(): ImageBitmap? {
    return this.toBitmap()?.asImageBitmap()
}
