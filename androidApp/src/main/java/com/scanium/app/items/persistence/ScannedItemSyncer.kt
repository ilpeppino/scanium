package com.scanium.app.items.persistence

import com.scanium.app.ScannedItem

interface ScannedItemSyncer {
    suspend fun sync(items: List<ScannedItem>)
}

object NoopScannedItemSyncer : ScannedItemSyncer {
    override suspend fun sync(items: List<ScannedItem>) = Unit
}
