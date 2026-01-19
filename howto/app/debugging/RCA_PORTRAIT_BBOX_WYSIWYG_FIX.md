***REMOVED*** RCA: Portrait Bounding Box Offset & WYSIWYG Violation

**Date:** 2025-01-14
**Severity:** Critical
**Status:** Fixed
**Baseline:** v1.1.0

***REMOVED******REMOVED*** Summary

In portrait mode, bounding boxes appeared offset from detected objects (typically bottom-left), and
objects outside the visible preview were being detected, violating the WYSIWYG (What You See Is What
You Get) principle.

***REMOVED******REMOVED*** Symptoms

1. **Bbox offset**: Detected boxes had correct shape but wrong position (offset bottom-left)
2. **WYSIWYG violation**: Objects not visible in camera preview were being detected
3. **Aggregation corruption**: Items from different photo captures were incorrectly merged, causing
   items to "disappear" and be replaced

***REMOVED******REMOVED*** Root Cause Analysis

***REMOVED******REMOVED******REMOVED*** Issue 1: Incorrect Bbox Normalization Dimensions

**Location:** `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`

**Problem:** ML Kit returns bounding boxes in the ROTATED coordinate space, but `InputImage.width`
and `InputImage.height` return the ORIGINAL buffer dimensions (before rotation).

```kotlin
// BROKEN (v1.1.0):
val uprightWidth = image.width   // 1440 (sensor width)
val uprightHeight = image.height // 1080 (sensor height)
// But ML Kit bbox is in rotated space: 1080x1440
```

For a 1440x1080 sensor with 90° rotation (portrait):

- `InputImage.width` = 1440 (original buffer width)
- `InputImage.height` = 1080 (original buffer height)
- ML Kit bbox coordinates are in 1080x1440 space (rotated)

When normalizing bbox coordinates using wrong dimensions:

- Object at center (540, 720 in 1080x1440 space)
- Normalized as: X = 540/1440 = 0.375, Y = 720/1080 = 0.667
- Should be: X = 540/1080 = 0.5, Y = 720/1440 = 0.5

This caused a ~32% Y-coordinate offset.

***REMOVED******REMOVED******REMOVED*** Issue 2: Preview/ImageAnalysis Aspect Ratio Mismatch

**Location:** `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`

**Problem:** Preview was configured with 4:3 aspect ratio, but ImageAnalysis targeted 1280x720 (16:
9).

```kotlin
// BROKEN:
preview = Preview.Builder()
    .setTargetAspectRatio(AspectRatio.RATIO_4_3)  // 4:3
    .build()

imageAnalysis = ImageAnalysis.Builder()
    .setResolutionSelector(buildResolutionSelector(Size(1280, 720)))  // 16:9!
    .build()
```

This caused ImageAnalysis to capture a wider field of view than Preview displayed, resulting in:

- Objects outside visible preview being detected
- Coordinate mismatch between what user sees and what ML Kit analyzes

***REMOVED******REMOVED******REMOVED*** Issue 3: Aggregation Merging Unrelated Items

**Location:**
`shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt`

**Problem:** After fixing bbox coordinates, all objects photographed at screen center had similar
normalized positions (~0.5, 0.5). The aggregation used center distance as part of similarity
calculation, causing different objects from different captures to incorrectly match and merge.

***REMOVED******REMOVED*** Fix Implementation

***REMOVED******REMOVED******REMOVED*** Fix 1: Swap Dimensions for Portrait Mode

```kotlin
// FIXED (ObjectDetectorClient.kt):
val isRotated = image.rotationDegrees == 90 || image.rotationDegrees == 270
val uprightWidth = if (isRotated) image.height else image.width
val uprightHeight = if (isRotated) image.width else image.height
```

***REMOVED******REMOVED******REMOVED*** Fix 2: Match Preview and ImageAnalysis Aspect Ratios

```kotlin
// FIXED (CameraXManager.kt):
preview = Preview.Builder()
    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
    .setTargetRotation(displayRotation)
    .build()

imageAnalysis = ImageAnalysis.Builder()
    .setTargetAspectRatio(AspectRatio.RATIO_4_3)  // SAME as Preview!
    .setTargetRotation(displayRotation)
    .build()
```

***REMOVED******REMOVED******REMOVED*** Fix 3: Timestamp Guard in Aggregation

```kotlin
// FIXED (ItemAggregator.kt):
private fun calculateSimilarity(detection: ScannedItem, item: AggregatedItem): Float {
    // Don't merge items from different captures (>2 seconds apart)
    val timestampDiffMs = kotlin.math.abs(detection.timestampMs - item.lastSeenTimestamp)
    if (timestampDiffMs > 2000) {
        return 0f
    }
    // ... rest of similarity calculation
}
```

***REMOVED******REMOVED*** Files Changed

| File                                                                                     | Change                                               |
|------------------------------------------------------------------------------------------|------------------------------------------------------|
| `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`                    | Swap dimensions for 90°/270° rotation                |
| `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`                      | Match Preview & ImageAnalysis aspect ratios          |
| `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt` | Add timestamp guard to prevent cross-capture merging |
| `androidApp/src/main/java/com/scanium/app/camera/OverlayTransforms.kt`                   | Minor comment updates                                |

***REMOVED******REMOVED*** Coordinate Flow (Corrected)

```
Camera Sensor (1440x1080, landscape)
        │
        ▼ rotation=90°
InputImage.fromMediaImage(mediaImage, 90)
        │
        ▼ ML Kit processes rotated image
Bbox in ROTATED space (1080x1440)
        │
        ▼ Normalize with SWAPPED dimensions
NormalizedRect (0-1 range, upright coords)
        │
        ▼ calculateTransformWithRotation()
Preview coordinates (screen pixels)
```

***REMOVED******REMOVED*** Key Learnings

1. **InputImage dimensions are PRE-rotation**: Always swap width/height for 90°/270° rotation when
   normalizing ML Kit bboxes.

2. **WYSIWYG requires matched aspect ratios**: Preview and ImageAnalysis MUST use the same aspect
   ratio, or objects outside visible area will be detected.

3. **Position-based aggregation fails for centered captures**: When users consistently photograph
   objects at screen center, position similarity causes false matches. Use timestamp or
   sourcePhotoId to distinguish captures.

***REMOVED******REMOVED*** Regression Tests

Added tests in `PortraitTransformRegressionTest.kt`:

- `portrait 90deg - top-left bbox does NOT appear at bottom-left`
- `portrait 90deg - realistic device dimensions produce correct mapping`

***REMOVED******REMOVED*** Verification

1. Point camera at object in portrait mode
2. Bbox should appear exactly on the object (not offset)
3. Only objects visible in preview should be detected
4. Taking photos of different objects should create separate items
