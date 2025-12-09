# Session-Level De-duplication Fix - December 8, 2024

## Problem

The SessionDeduplicator was treating all UNKNOWN category items as duplicates, causing only 1-2 items to be added during scanning while slowly panning the camera.

## Root Causes

### 1. Empty Label Bug (Critical)
The `calculateLabelSimilarity()` function checked `if (label1 == label2) return 1.0f` **before** checking for empty strings. This meant empty labels matched with 100% similarity.

### 2. Category Name Fallback Bug (Critical)
The `extractMetadata()` function used `item.category.name` as the label fallback, causing all UNKNOWN items to have labelText="UNKNOWN", which then matched as identical.

### 3. Missing Position Data
`ScannedItem` didn't include bounding box position, so spatial proximity matching couldn't work.

### 4. Missing Label Data
`ScannedItem` didn't include ML Kit label text, so label-based matching couldn't work.

## Fixes Applied

### Fix 1: Empty Label Check First (SessionDeduplicator.kt:149-156)
```kotlin
private fun calculateLabelSimilarity(label1: String, label2: String): Float {
    // CRITICAL: Check for empty labels FIRST, before comparing equality
    // Empty labels provide NO distinguishing information
    if (label1.isEmpty() || label2.isEmpty()) return 0.0f

    // Both have labels - check if identical
    if (label1 == label2) return 1.0f
    // ... rest of similarity calculation
}
```

### Fix 2: Add Position and Label Fields to ScannedItem (ScannedItem.kt:22-33)
```kotlin
data class ScannedItem(
    val id: String = UUID.randomUUID().toString(),
    val thumbnail: Bitmap? = null,
    val category: ItemCategory,
    val priceRange: Pair<Double, Double>,
    val confidence: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val recognizedText: String? = null,
    val barcodeValue: String? = null,
    val boundingBox: RectF? = null,  // NEW: Normalized position
    val labelText: String? = null     // NEW: ML Kit label
)
```

### Fix 3: Pass Position and Label from ObjectDetectorClient (ObjectDetectorClient.kt:492-513)
```kotlin
// Normalize bounding box to 0-1 coordinates for session deduplication
val imageWidth = (sourceBitmap?.width ?: fallbackWidth).toFloat()
val imageHeight = (sourceBitmap?.height ?: fallbackHeight).toFloat()
val normalizedBox = android.graphics.RectF(
    boundingBox.left / imageWidth,
    boundingBox.top / imageHeight,
    boundingBox.right / imageWidth,
    boundingBox.bottom / imageHeight
)

ScannedItem(
    id = trackingId,
    thumbnail = thumbnail,
    category = category,
    priceRange = priceRange,
    confidence = confidence,
    boundingBox = normalizedBox,  // Pass normalized position
    labelText = bestLabel?.text   // Pass ML Kit label
)
```

### Fix 4: Use Actual Position and Labels in extractMetadata (SessionDeduplicator.kt:215-256)
```kotlin
// Extract position from bounding box if available
val (centerX, centerY, boxArea, hasPosition) = when {
    item.boundingBox != null -> {
        val box = item.boundingBox
        listOf(
            box.centerX(),
            box.centerY(),
            box.width() * box.height(),
            1f // hasPosition = true
        )
    }
    // ... fallbacks
}

// CRITICAL: Only use actual distinguishing text as labels
// Priority: labelText > recognizedText > barcodeValue > empty
val labelText = when {
    item.labelText?.isNotBlank() == true -> item.labelText
    item.recognizedText?.isNotBlank() == true -> item.recognizedText
    item.barcodeValue?.isNotBlank() == true -> item.barcodeValue
    else -> "" // Empty string for items without distinguishing text
}
```

## Results

### Before Fix
- **Item accumulation**: Only 1-2 items total
- **Logs**: `labelSim=1.0` for all comparisons
- **Behavior**: Every item rejected as "similar to existing"

### After Fix
- **Item accumulation**: ~27 items in 16 seconds (~1-2 items/second)
- **Logs**: `labelSim=0.0` for empty labels, proper position-based matching
- **Behavior**:
  - ✅ Position-based deduplication: "positions very close (distance=0.045) - likely same object"
  - ✅ Spatial separation: "positions differ (distance=0.120) - treating as different"
  - ✅ Size matching: "sizeRatio=0.9196" + position → deduplicated
  - ✅ Proper rejection: "Similar item found... REJECTED"
  - ✅ Proper acceptance: "ACCEPTED: unique item"

### Memory Usage
- **Stable**: 18-29MB (out of 256MB max)
- **No leaks**: Adding items causes 0MB additional memory
- **No crashes**: App runs continuously without crashes

## Key Algorithm Details

### Similarity Rules (SessionDeduplicator.kt:77-144)

1. **Category must match** (essential)

2. **Label similarity** (if both have labels):
   - Levenshtein distance normalized
   - Minimum 70% similarity required
   - Empty labels return 0.0 similarity

3. **Size similarity**:
   - Normalized box area comparison
   - Maximum 40% size difference allowed

4. **Spatial proximity** (REQUIRED when labels are missing):
   - When no labels: position check is **mandatory**
   - Distance threshold: 5% of frame diagonal (strict)
   - When labels present: 15% of frame diagonal (lenient)

### Distance Calculation (SessionDeduplicator.kt:202-210)
```kotlin
private fun calculateNormalizedDistance(item1: ItemMetadata, item2: ItemMetadata): Float {
    val dx = item1.centerX - item2.centerX
    val dy = item1.centerY - item2.centerY
    val distance = sqrt(dx * dx + dy * dy)

    // Normalize by frame diagonal
    val frameDiagonal = sqrt(2.0f) // sqrt(1^2 + 1^2) for normalized frame
    return distance / frameDiagonal
}
```

## Testing

### Manual Test Results
1. **Launch app**: `adb shell am start -n com.example.scanium/.MainActivity`
2. **Long-press to scan**: Camera detects objects
3. **Observed behavior**:
   - Items with similar positions → Deduplicated ✅
   - Items in different positions → Added as unique ✅
   - ~1-2 items added per second ✅
   - Memory stays at 18-29MB ✅
   - No crashes ✅

### Example Log Output
```
SessionDeduplicator: No labels: positions very close (distance=0.045774244) - likely same object
SessionDeduplicator: Items are similar: labelSim=0.0, sizeRatio=0.9196152
SessionDeduplicator: Similar item found: new 90552944... matches existing ce287e5d...
ItemsViewModel:     REJECTED: similar to existing item ce287e5d...

SessionDeduplicator: No labels: positions differ (distance=0.12030976) - treating as different
ItemsViewModel:     ACCEPTED: unique item 43cf1857...
```

## Related Fixes

This fix builds on previous work:
- `ML_KIT_NATIVE_CRASH_FIX.md`: Using SINGLE_IMAGE_MODE to avoid crashes
- `MEMORY_CRASH_FIX.md`: Thumbnail downscaling to 200x200 max
- `ML_KIT_ZERO_DETECTIONS_FIX.md`: Removing `.enableClassification()` for better detection

## Files Modified

1. `app/src/main/java/com/example/scanium/items/ScannedItem.kt`
   - Added `boundingBox: RectF?` field
   - Added `labelText: String?` field

2. `app/src/main/java/com/example/scanium/items/SessionDeduplicator.kt`
   - Fixed `calculateLabelSimilarity()` to check empty labels first
   - Updated `extractMetadata()` to use actual position and label data
   - Removed category name fallback

3. `app/src/main/java/com/example/scanium/ml/ObjectDetectorClient.kt`
   - Updated `convertToScannedItem()` to pass normalized boundingBox
   - Updated `convertToScannedItem()` to pass labelText from ML Kit
   - Updated `candidateToScannedItem()` to pass position and label from ObjectCandidate

## Verification

✅ **Build successful** with all tests passing
✅ **APK installed** and tested on device
✅ **Deduplication working** - similar items rejected, unique items accepted
✅ **Memory efficient** - 18-29MB stable usage
✅ **No crashes** - app runs continuously
✅ **Reasonable rate** - ~1-2 items/second accumulation
