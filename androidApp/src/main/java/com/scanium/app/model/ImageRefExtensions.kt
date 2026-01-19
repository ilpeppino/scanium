package com.scanium.app.model

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.scanium.app.BuildConfig
import com.scanium.app.items.ThumbnailCache
import com.scanium.app.platform.toBitmap
import com.scanium.shared.core.models.model.ImageRef

private const val TAG = "ImageRefExtensions"

/**
 * Platform helpers to bridge portable ImageRef into Android types.
 *
 * Resolution strategy:
 * - ImageRef.Bytes: Use directly (contains actual image data)
 * - ImageRef.CacheKey: Look up in ThumbnailCache (in-memory LRU cache)
 *
 * If CacheKey lookup fails, this returns null. The caller should ensure that:
 * - Items loaded from DB have ImageRef.Bytes (see ScannedItemEntity.toModel)
 * - ThumbnailCache is populated before items are displayed (see ItemsStateManager.cacheThumbnails)
 */
fun ImageRef?.resolveBytes(): ImageRef.Bytes? =
    when (this) {
        is ImageRef.Bytes -> {
            this
        }

        is ImageRef.CacheKey -> {
            val cached = ThumbnailCache.get(key)
            if (cached == null && BuildConfig.DEBUG) {
                // Log cache miss in debug builds - this helps diagnose thumbnail issues
                Log.w(TAG, "ThumbnailCache miss for key: $key - thumbnail will show placeholder")
            }
            cached
        }

        else -> {
            null
        }
    }

fun ImageRef?.toBitmap(): Bitmap? = resolveBytes()?.toBitmap()

fun ImageRef?.toImageBitmap(): ImageBitmap? = this.toBitmap()?.asImageBitmap()
