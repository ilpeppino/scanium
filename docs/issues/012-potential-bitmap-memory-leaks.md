***REMOVED*** Potential Bitmap Memory Leaks (Missing recycle() calls)

**Labels:** `bug`, `priority:p2`, `area:ml`, `area:camera`, `performance`, `memory`
**Type:** Memory Leak Risk
**Severity:** Medium

***REMOVED******REMOVED*** Problem

Multiple `Bitmap.createBitmap()` calls exist throughout the codebase, but only **one** `bitmap.recycle()` call was found. On Android, bitmaps consume large amounts of native memory and should be recycled when no longer needed.

***REMOVED******REMOVED*** Evidence

***REMOVED******REMOVED******REMOVED*** Bitmap Creation (8 locations found):

1. **BarcodeScannerClient.kt:170**
   ```kotlin
   val thumbnail = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, ...)
   ```

2. **ObjectDetectorClient.kt:593**
   ```kotlin
   val thumbnail = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, ...)
   ```

3. **DocumentTextRecognitionClient.kt:147**
   ```kotlin
   val thumbnail = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, ...)
   ```

4. **CameraXManager.kt:449**
   ```kotlin
   ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
   ```

5. **CameraXManager.kt:461**
   ```kotlin
   return Bitmap.createBitmap(...)
   ```

6. **ListingImagePreparer.kt:145**
   ```kotlin
   return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
   ```

7. **OnDeviceClassifier.kt:25**
   ```kotlin
   val resized = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
   ```

***REMOVED******REMOVED******REMOVED*** Bitmap Recycling (1 location found):

1. **ListingImagePreparer.kt:103** ✅
   ```kotlin
   scaledBitmap.recycle()
   ```

***REMOVED******REMOVED*** Impact

**Symptom**: Potential OOM (Out of Memory) errors during extended scanning sessions

**Memory Profile**:
- Each 200x200 thumbnail = ~160 KB
- 50 items scanned = ~8 MB in bitmaps
- Without recycling, memory grows unbounded
- Android GC may not collect native bitmap memory promptly

**Current Mitigations**:
- Items are cleared when app closes (memory released)
- ItemsViewModel allows clearing items manually
- Bitmaps might be GC'd eventually

**Risk Level**: Medium (not immediate, but problematic for heavy users)

***REMOVED******REMOVED*** Expected Behavior

- Temporary bitmaps (scaled versions) should be recycled immediately
- Thumbnails stored in ScannedItem should be recycled when item is removed
- CameraX frame bitmaps should be recycled after ML Kit processing

***REMOVED******REMOVED*** Acceptance Criteria

- [ ] Audit all Bitmap creation sites
- [ ] Identify which bitmaps are temporary vs. retained
- [ ] Add recycle() calls for temporary bitmaps
- [ ] Add recycle() in ItemsViewModel.removeItem()
- [ ] Add recycle() in ItemsViewModel.clearAllItems()
- [ ] Document bitmap lifecycle in code comments
- [ ] Memory test: Scan 100+ items, verify no OOM

***REMOVED******REMOVED*** Suggested Approach

***REMOVED******REMOVED******REMOVED*** 1. Immediate Cleanup (Temporary Bitmaps)

**OnDeviceClassifier.kt:**
```kotlin
val resized = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
try {
    // ... use resized ...
} finally {
    if (resized != bitmap) {  // Don't recycle original
        resized.recycle()
    }
}
```

**ListingImagePreparer.kt** - Already correct ✅

***REMOVED******REMOVED******REMOVED*** 2. ViewModel Cleanup (Retained Bitmaps)

**ItemsViewModel.kt:**
```kotlin
fun removeItem(item: ScannedItem) {
    item.thumbnail?.recycle()  // Free native memory
    _items.update { currentItems ->
        currentItems.filterNot { it.id == item.id }
    }
    seenIds.remove(item.id)
}

fun clearAllItems() {
    _items.value.forEach { it.thumbnail?.recycle() }  // Cleanup all
    _items.value = emptyList()
    seenIds.clear()
}
```

***REMOVED******REMOVED******REMOVED*** 3. Detection Client Cleanup

**ML Kit thumbnail creation** - These are retained in ScannedItem:
- DON'T recycle in ObjectDetectorClient (bitmap is passed to ViewModel)
- DO recycle in ViewModel when item removed

**Temporary bitmaps in CameraXManager:**
```kotlin
val bitmapForThumbnail = imageProxyToBitmap(imageProxy)
try {
    // ... ML Kit processing ...
} finally {
    bitmapForThumbnail?.recycle()  // Only if not stored in ScannedItem
}
```

***REMOVED******REMOVED******REMOVED*** 4. Careful with Shared Bitmaps

**DON'T recycle if:**
- Bitmap is stored in a data class (ScannedItem)
- Bitmap is passed to another component
- Bitmap might be used later

**DO recycle if:**
- Bitmap is a scaled/temporary version
- Bitmap is only used in local scope
- Bitmap is being replaced

***REMOVED******REMOVED*** Testing

Manual memory leak test:

```kotlin
// Test: Scan 100 items, clear, repeat
repeat(10) {
    // Scan 100 items
    repeat(100) { scanItem() }
    // Check memory
    val runtime = Runtime.getRuntime()
    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    Log.d("MemoryTest", "Iteration $it: ${usedMemory}MB used")
    // Clear all
    viewModel.clearAllItems()
}
// Memory should stabilize, not grow unbounded
```

***REMOVED******REMOVED*** Alternative: Modern Approach (Coil/Glide)

For production app, consider using an image loading library:
- Coil (Compose-first)
- Glide
- Both handle bitmap pooling and recycling automatically

***REMOVED******REMOVED*** Related Issues

None
