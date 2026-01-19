***REMOVED*** Comparison: Current Solution vs v1.1.0

**Date:** 2025-01-14
**Baseline Tag:** v1.1.0
**Current:** main (post-fix)

***REMOVED******REMOVED*** Executive Summary

The current solution fixes three critical issues that existed in v1.1.0:

1. Portrait bbox coordinate normalization
2. Preview/ImageAnalysis aspect ratio mismatch
3. Cross-capture item aggregation (NEW - not in v1.1.0)

***REMOVED******REMOVED*** Detailed Comparison

***REMOVED******REMOVED******REMOVED*** 1. ObjectDetectorClient.kt - Bbox Normalization

***REMOVED******REMOVED******REMOVED******REMOVED*** v1.1.0 (Broken)

```kotlin
// Used raw InputImage dimensions (wrong for rotated images)
val uprightWidth = image.width   // 1440 (sensor width)
val uprightHeight = image.height // 1080 (sensor height)
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Current (Fixed)

```kotlin
// Swap dimensions for 90°/270° rotation to match ML Kit's rotated coordinate space
val isRotated = image.rotationDegrees == 90 || image.rotationDegrees == 270
val uprightWidth = if (isRotated) image.height else image.width
val uprightHeight = if (isRotated) image.width else image.height
```

**Impact:** In v1.1.0, portrait bboxes were normalized against wrong dimensions (1440x1080 instead
of 1080x1440), causing ~32% Y-coordinate offset.

---

***REMOVED******REMOVED******REMOVED*** 2. CameraXManager.kt - Preview/ImageAnalysis Configuration

***REMOVED******REMOVED******REMOVED******REMOVED*** v1.1.0

```kotlin
// Preview: No explicit aspect ratio (CameraX auto-selects)
preview = Preview.Builder()
    .build()
    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

// ImageAnalysis: Targeted 16:9 resolution
imageAnalysis = ImageAnalysis.Builder()
    .setTargetResolution(android.util.Size(1280, 720))  // 16:9
    .build()
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Current (Fixed)

```kotlin
// Preview: Explicit 4:3 aspect ratio
preview = Preview.Builder()
    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
    .setTargetRotation(displayRotation)
    .build()

// ImageAnalysis: SAME 4:3 aspect ratio as Preview
imageAnalysis = ImageAnalysis.Builder()
    .setTargetAspectRatio(AspectRatio.RATIO_4_3)  // Matches Preview!
    .setTargetRotation(displayRotation)
    .build()
```

**Impact:** In v1.1.0, ImageAnalysis (16:9) captured wider field of view than Preview displayed,
causing WYSIWYG violation - objects outside visible area were detected.

---

***REMOVED******REMOVED******REMOVED*** 3. ItemAggregator.kt - Timestamp Guard (NEW)

***REMOVED******REMOVED******REMOVED******REMOVED*** v1.1.0

```kotlin
private fun calculateSimilarity(detection: ScannedItem, item: AggregatedItem): Float {
    // Hard filter: category must match if required
    if (config.categoryMatchRequired && detection.category != item.category) {
        return 0f
    }
    // ... position-based similarity calculation
}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Current (Fixed)

```kotlin
private fun calculateSimilarity(detection: ScannedItem, item: AggregatedItem): Float {
    // NEW: Don't merge items from different captures (>2 seconds apart)
    val timestampDiffMs = kotlin.math.abs(detection.timestampMs - item.lastSeenTimestamp)
    if (timestampDiffMs > 2000) {
        return 0f
    }

    // Hard filter: category must match if required
    if (config.categoryMatchRequired && detection.category != item.category) {
        return 0f
    }
    // ... position-based similarity calculation
}
```

**Impact:** This is a NEW fix not present in v1.1.0. After correcting bbox coordinates, all centered
objects had similar positions (~0.5, 0.5), causing different objects from separate captures to
incorrectly merge. The timestamp guard prevents this.

**Note:** This issue was LATENT in v1.1.0 - it didn't manifest because the broken coordinates made
objects appear at different positions even when centered.

---

***REMOVED******REMOVED******REMOVED*** 4. OverlayTransforms.kt - No Functional Change

Both v1.1.0 and current use the same `mapBboxToPreview()` logic:

```kotlin
fun mapBboxToPreview(bboxNorm: NormalizedRect, transform: BboxMappingTransform): RectF {
    // ML Kit returns bboxes in InputImage coordinate space (upright, post-rotation)
    // No rotation transformation needed here - bbox is already in upright space

    val pixelLeft = bboxNorm.left * transform.effectiveImageWidth
    val pixelTop = bboxNorm.top * transform.effectiveImageHeight
    // ... scale and offset application
}
```

The overlay transform was always correct - the issue was in how bboxes were NORMALIZED before being
passed to this function.

---

***REMOVED******REMOVED*** Summary Table

| Component                   | v1.1.0           | Current                | Change Type   |
|-----------------------------|------------------|------------------------|---------------|
| Bbox normalization          | Wrong dimensions | Swapped for rotation   | Bug fix       |
| Preview aspect ratio        | Auto (undefined) | Explicit 4:3           | Configuration |
| ImageAnalysis aspect ratio  | 16:9 (1280x720)  | 4:3 (matching Preview) | Bug fix       |
| Aggregation timestamp guard | None             | 2-second threshold     | New feature   |
| Overlay transform           | Correct          | Correct                | No change     |

***REMOVED******REMOVED*** Why v1.1.0 "Worked" (Sort Of)

In v1.1.0, the broken coordinate normalization caused bboxes to appear offset, but the system was "
consistently wrong":

- All bboxes had the same offset
- The aggregation didn't incorrectly merge items because their (wrong) coordinates were different
- Users noticed the offset but items were still tracked separately

After fixing the normalization, bboxes became correct but:

- All centered objects now had similar coordinates (~0.5, 0.5)
- This caused the aggregation to incorrectly merge different objects
- The timestamp guard was added to prevent this side effect

***REMOVED******REMOVED*** Files Changed Summary

```
androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt
  - Added dimension swap for 90°/270° rotation

androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt
  - Changed Preview to explicit 4:3 aspect ratio
  - Changed ImageAnalysis from 16:9 to 4:3 aspect ratio

shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt
  - Added timestamp guard (>2s = no merge)
```

***REMOVED******REMOVED*** Regression Risk Assessment

| Change             | Risk   | Mitigation                                                       |
|--------------------|--------|------------------------------------------------------------------|
| Dimension swap     | Low    | Only affects 90°/270° rotation; landscape unchanged              |
| Aspect ratio match | Low    | Both use 4:3; sensor native ratio                                |
| Timestamp guard    | Medium | May prevent legitimate fast merges; 2s threshold is conservative |

***REMOVED******REMOVED*** Testing Checklist

- [ ] Portrait mode: bbox appears on object (not offset)
- [ ] Landscape mode: bbox still works correctly
- [ ] WYSIWYG: only visible objects detected
- [ ] Multiple captures: different objects create separate items
- [ ] Fast scanning: same object across frames still merges correctly
