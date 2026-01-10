***REMOVED*** Known-Good Landscape Bbox→Snapshot Correlation Path

This document traces the exact code path that produces correct landscape snapshots.
Portrait mode must replicate this contract exactly.

---

***REMOVED******REMOVED*** COORDINATE CONTRACT (Ground Truth)

**ML Kit returns bboxes in InputImage (upright) coordinate space.**

When using `InputImage.fromMediaImage(mediaImage, rotationDegrees)`:
- ML Kit applies the rotation metadata internally
- `InputImage.width` / `InputImage.height` are the **upright** (post-rotation) dimensions
- Bboxes returned by `DetectedObject.boundingBox` are in upright pixel coordinates

| Orientation | Sensor Size | rotationDegrees | InputImage Dims | Bbox Space |
|-------------|------------|-----------------|-----------------|------------|
| Landscape   | 1280×720   | 0               | 1280×720        | 1280×720   |
| Portrait    | 1280×720   | 90              | 720×1280        | 720×1280   |

**KEY**: Bbox aspect ratio MUST match visible object aspect ratio in BOTH orientations.

---

***REMOVED******REMOVED*** LANDSCAPE PATH (Known Good)

***REMOVED******REMOVED******REMOVED*** 1. Detection: `ObjectDetectorClient.kt`

```
CameraXManager.processImageProxy()
    ↓
mediaImage = ImageProxy.image
rotationDegrees = ImageProxy.imageInfo.rotationDegrees  // = 0 in landscape
    ↓
InputImage.fromMediaImage(mediaImage, rotationDegrees)
    ↓
objectDetector.detectObjectsWithTracking(inputImage, ...)
```

**File**: `ObjectDetectorClient.kt:394-505`
- `detectObjectsWithTracking()` runs ML Kit detection
- Returns both `DetectionInfo` (for tracking) and `DetectionResult` (for overlay)

***REMOVED******REMOVED******REMOVED*** 2. Bbox Normalization

**File**: `ObjectDetectorClient.kt:255-256`
```kotlin
val uprightWidth = image.width   // InputImage dimensions (upright)
val uprightHeight = image.height
```

**File**: `ObjectDetectorClient.kt:581`
```kotlin
// Normalize using upright dimensions
val bboxNorm = uprightBbox.toNormalizedRect(uprightWidth, uprightHeight)
```

**Result**: `NormalizedRect` with values in [0, 1] range, in upright coordinate space.

***REMOVED******REMOVED******REMOVED*** 3. Thumbnail Cropping

**File**: `ObjectDetectorClient.kt:564-572`
```kotlin
// Convert upright bbox to sensor space for bitmap cropping
val sensorBbox = uprightBboxToSensorBbox(
    uprightBbox = uprightBbox,
    inputImageWidth = uprightWidth,
    inputImageHeight = uprightHeight,
    rotationDegrees = imageRotationDegrees
)

// Crop from sensor-oriented bitmap, then rotate
val thumbnail = sourceBitmap?.let { cropThumbnail(it, sensorBbox, imageRotationDegrees) }
```

**CRITICAL**: `uprightBboxToSensorBbox()` converts upright coordinates → sensor coordinates.
For landscape (rotation=0), this is identity transform.

**File**: `ObjectDetectorClient.kt:745-799` - `cropThumbnail()`
1. Crops from sensor bitmap using sensor-space bbox
2. Rotates the crop by `rotationDegrees` for display orientation
3. Returns upright thumbnail

***REMOVED******REMOVED******REMOVED*** 4. Overlay Rendering

**File**: `DetectionOverlay.kt:133-140`
```kotlin
val transform = calculateTransformWithRotation(
    imageWidth = imageSize.width,         // sensor dimensions
    imageHeight = imageSize.height,       // sensor dimensions
    previewWidth = canvasWidth,
    previewHeight = canvasHeight,
    rotationDegrees = rotationDegrees,    // from ImageProxy
    scaleType = PreviewScaleType.FILL_CENTER
)
```

**File**: `DetectionOverlay.kt:208`
```kotlin
val transformedBox = mapBboxToPreview(detection.bboxNorm, transform)
```

**File**: `OverlayTransforms.kt:189-209` - `mapBboxToPreview()`
- Takes `NormalizedRect` in **upright** space
- Does NOT rotate (bbox is already upright from ML Kit)
- Applies scale + offset for FILL_CENTER preview

***REMOVED******REMOVED******REMOVED*** 5. Full Image Snapshot (for classification)

**File**: `StableItemCropper.kt:57-94`
```kotlin
// Load source bitmap from fullImageUri (high-res capture)
val sourceBitmap = loadSourceBitmap(item.fullImageUri, item.thumbnail)

// Convert normalized bbox to RectF in bitmap coordinates
val rectF = normalizedRect.toRectF(sourceBitmap.width, sourceBitmap.height)

// Calculate crop rect with padding
val cropRect = calculateCropRect(sourceBitmap.width, sourceBitmap.height, rectF)

// Crop the bitmap
val croppedBitmap = Bitmap.createBitmap(sourceBitmap, cropRect.left, ...)
```

**ISSUE IN PORTRAIT**: The bbox is in upright space, but `fullImageUri` bitmap may have EXIF rotation.

---

***REMOVED******REMOVED*** WHY LANDSCAPE WORKS

In landscape mode (`rotationDegrees = 0`):

1. **Sensor dims == Upright dims**: 1280×720 = 1280×720
2. **No coordinate transformation needed**: upright bbox → sensor bbox is identity
3. **No rotation needed**: sensor bitmap is already upright
4. **JPEG has no rotation**: ImageCapture saves with 0° rotation

All three systems (overlay, thumbnail, snapshot) see the same coordinate space.

---

***REMOVED******REMOVED*** WHY PORTRAIT BREAKS

In portrait mode (`rotationDegrees = 90`):

1. **Dims are swapped**: Sensor 1280×720, Upright 720×1280
2. **Coordinate transformation needed**: upright (720×1280) → sensor (1280×720)
3. **Rotation needed**: sensor bitmap must be rotated 90° for display
4. **JPEG may have EXIF rotation**: ImageCapture sets rotation metadata

**Potential divergence points**:
- `fullImageUri` bitmap loaded without EXIF rotation handling
- Normalized bbox applied to wrong dimensions
- Crop done before rotation
- Crop done in wrong coordinate space

---

***REMOVED******REMOVED*** CANONICAL CONTRACT (To Enforce)

***REMOVED******REMOVED******REMOVED*** Single Source of Truth

All bbox operations use **upright coordinate space**:
- `NormalizedRect` in [0,1] range
- Dimensions: `uprightWidth × uprightHeight`

***REMOVED******REMOVED******REMOVED*** Pipeline Requirements

1. **Overlay**: Map upright bbox → preview coords (scale+offset, no rotation)
2. **Thumbnail**: Convert upright bbox → sensor coords, crop, rotate to upright
3. **Snapshot**: Load bitmap to upright orientation FIRST, then apply upright bbox directly

***REMOVED******REMOVED******REMOVED*** Aspect Ratio Invariant

```
bboxAR = (bbox.height / bbox.width)   // in normalized upright space
cropAR = (crop.height / crop.width)   // in cropped bitmap

ASSERT: |bboxAR - cropAR| < 0.02 (within 2%)
```

This must hold in BOTH portrait and landscape.

---

***REMOVED******REMOVED*** FILES INVOLVED

| File | Function | Role |
|------|----------|------|
| `CameraXManager.kt` | `processImageProxy()` | Entry point, creates InputImage |
| `ObjectDetectorClient.kt` | `detectObjectsWithTracking()` | ML Kit detection |
| `ObjectDetectorClient.kt` | `uprightBboxToSensorBbox()` | Upright→Sensor conversion |
| `ObjectDetectorClient.kt` | `cropThumbnail()` | Thumbnail generation |
| `OverlayTransforms.kt` | `calculateTransformWithRotation()` | Preview transform |
| `OverlayTransforms.kt` | `mapBboxToPreview()` | Bbox→Preview mapping |
| `DetectionOverlay.kt` | Drawing code | Renders bboxes |
| `StableItemCropper.kt` | `prepare()` | High-res snapshot crop |
| `ImageUtils.kt` | `createThumbnailFromUri()` | Loads fullImageUri |
| `DetectionGeometryMapper.kt` | Utility functions | Geometry helpers |

---

***REMOVED******REMOVED*** VERIFICATION STEPS

1. Enable "Bbox Correlation Debug" in Developer Options
2. Point camera at tall object in portrait mode
3. Verify overlay shows tall bbox (height > width)
4. Verify debug overlay shows `bboxAR ≈ cropAR`
5. Save item and verify thumbnail matches overlay
6. Check item list preview is not center-cropped
