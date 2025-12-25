package com.scanium.app.items

import android.util.LruCache
import com.scanium.shared.core.models.model.ImageRef

object ThumbnailCache {
    private const val MAX_ENTRIES = 50

    private val cache = object : LruCache<String, ImageRef.Bytes>(MAX_ENTRIES) {}

    fun put(itemId: String, imageRef: ImageRef.Bytes) {
        cache.put(itemId, imageRef)
    }

    fun get(itemId: String): ImageRef.Bytes? = cache.get(itemId)

    fun remove(itemId: String) {
        cache.remove(itemId)
    }

    fun clear() {
        cache.evictAll()
    }
}
