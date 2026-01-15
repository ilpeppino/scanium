package com.scanium.app.items.state

import android.util.Log
import com.scanium.app.items.ScannedItem
import com.scanium.app.items.ThumbnailCache
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.app.aggregation.ItemAggregator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ItemsStateStore {
    companion object {
        private const val TAG = "ItemsStateManager"
        private const val DEBUG_LOGGING = false
    }

    private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())
    private val _itemAddedEvents = MutableSharedFlow<ScannedItem>(extraBufferCapacity = 10)

    val items = _items.asStateFlow()
    val itemAddedEvents = _itemAddedEvents.asSharedFlow()

    fun getItems(): List<ScannedItem> = _items.value

    fun getItemCount(): Int = _items.value.size

    fun getItem(itemId: String): ScannedItem? {
        return _items.value.find { it.id == itemId }
    }

    fun setItems(items: List<ScannedItem>) {
        _items.value = items
    }

    fun clearItems() {
        _items.value = emptyList()
    }

    fun updateFromAggregator(
        itemAggregator: ItemAggregator,
        notifyNewItems: Boolean,
        triggerCallback: Boolean,
        animationEnabled: Boolean,
        onStateChanged: (() -> Unit)?,
    ): List<ScannedItem> {
        val oldItems = _items.value
        val scannedItems = itemAggregator.getScannedItems()
        val cachedItems = cacheThumbnails(scannedItems)
        _items.value = cachedItems
        Log.d(TAG, "Updated UI state: ${cachedItems.size} items")

        if (notifyNewItems && animationEnabled) {
            val newItems = cachedItems.filter { newItem ->
                oldItems.none { oldItem -> oldItem.id == newItem.id }
            }
            newItems.forEach {
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "Emitting new item event: ${it.id}")
                }
                _itemAddedEvents.tryEmit(it)
            }
        }

        if (triggerCallback) {
            onStateChanged?.invoke()
        }

        return scannedItems
    }

    fun refreshFromAggregator(itemAggregator: ItemAggregator) {
        val scannedItems = itemAggregator.getScannedItems()
        _items.value = cacheThumbnails(scannedItems)
    }

    private fun cacheThumbnails(items: List<ScannedItem>): List<ScannedItem> {
        return items.map { item ->
            val thumbnail = item.thumbnailRef ?: item.thumbnail
            val bytesRef = thumbnail as? ImageRef.Bytes ?: return@map item
            ThumbnailCache.put(item.id, bytesRef)
            val cachedRef =
                ImageRef.CacheKey(
                    key = item.id,
                    mimeType = bytesRef.mimeType,
                    width = bytesRef.width,
                    height = bytesRef.height,
                )
            item.copy(thumbnail = cachedRef, thumbnailRef = cachedRef)
        }
    }
}
