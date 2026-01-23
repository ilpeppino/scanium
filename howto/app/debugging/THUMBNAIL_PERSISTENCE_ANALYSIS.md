# Thumbnail Persistence Analysis

## Problem Summary

After process death (swipe away from Recents), items persist but thumbnails show "?" placeholders.

## Root Cause

### Data Flow Before Fix

1. **Item Creation**: Items are created with `ImageRef.Bytes` thumbnails containing actual image
   data.

2. **First State Update** (`updateItemsState()`):
    - `scannedItems = itemAggregator.getScannedItems()` → Items have `ImageRef.Bytes`
    - `cacheThumbnails(scannedItems)` runs:
        - Puts bytes into in-memory `ThumbnailCache`
        - **BUG**: Calls `itemAggregator.updateThumbnail(item.id, cachedRef)` with
          `ImageRef.CacheKey`
        - Returns items with `thumbnail = CacheKey`
    - `persistItems(scannedItems)` → Persists original items (still have Bytes) ✓

3. **Subsequent State Updates**:
    - `scannedItems = itemAggregator.getScannedItems()` → **Now returns items
      with `ImageRef.CacheKey`** (aggregator was mutated!)
    - `persistItems(scannedItems)` → Tries to persist CacheKey thumbnails
    - In `ScannedItemEntity.toImageFields()`: Returns `null` for `CacheKey` (cannot persist memory
      reference)
    - **Result**: `thumbnailBytes = NULL` written to DB

4. **Cold Start After Process Death**:
    - Items load from DB with `thumbnailBytes = null`
    - `ThumbnailCache` is empty (in-memory only, lost on process death)
    - UI renders `(thumbnailRef ?: thumbnail).toImageBitmap()` → `null`
    - Shows "?" placeholder

### The Exact Bug Location

**File**: `ItemsStateManager.kt:551`

```kotlin
private fun cacheThumbnails(items: List<ScannedItem>): List<ScannedItem> {
    return items.map { item ->
        val thumbnail = item.thumbnailRef ?: item.thumbnail
        val bytesRef = thumbnail as? ImageRef.Bytes ?: return@map item
        ThumbnailCache.put(item.id, bytesRef)
        val cachedRef = ImageRef.CacheKey(...)
        itemAggregator.updateThumbnail(item.id, cachedRef)  // ← BUG: Mutates aggregator
        item.copy(thumbnail = cachedRef, thumbnailRef = cachedRef)
    }
}
```

This line mutates the aggregator's internal thumbnail reference from `Bytes` to `CacheKey`.
On subsequent calls to `getScannedItems()`, the aggregator returns items with `CacheKey` thumbnails,
which then get persisted as `null` bytes.

## Persistence Contract

### What Gets Persisted (DB)

- `thumbnailBytes: ByteArray?` - Raw image bytes
- `thumbnailMimeType: String?` - MIME type
- `thumbnailWidth: Int?` - Width in pixels
- `thumbnailHeight: Int?` - Height in pixels
- Same fields for `thumbnailRef*`

### What's In Memory Only

- `ImageRef.CacheKey` - A string key referencing `ThumbnailCache`
- `ThumbnailCache` - LRU cache of `ImageRef.Bytes`, max 50 entries

### The Conversion Functions

```kotlin
// ScannedItemEntity.kt:159-165
private fun ImageRef?.toImageFields(): ImageFields? {
    return when (this) {
        is ImageRef.Bytes -> ImageFields(...)  // ✓ Persists
        is ImageRef.CacheKey -> null           // ✗ Cannot persist!
        null -> null
    }
}
```

## Fix Summary

### INVARIANT A (Persistence)

- DB layer MUST persist `ImageRef.Bytes` data
- Never persist items with CacheKey-only thumbnails

### INVARIANT B (Rehydration)

- On cold start, thumbnails MUST be rehydrated as `ImageRef.Bytes`
- UI must NOT depend on `ThumbnailCache` after process death

### INVARIANT C (Cache is Optional)

- `ThumbnailCache` is purely a performance optimization
- If cache miss occurs, fall back to DB bytes

### Changes Required

1. Remove `itemAggregator.updateThumbnail()` call from `cacheThumbnails()`
2. Keep aggregator state with `ImageRef.Bytes` always
3. Add fallback in `resolveBytes()` for cache misses
4. Add dev logging for persistence diagnostics
