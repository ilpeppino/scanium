***REMOVED*** Portrait Mode Bbox & Preview Misalignment Fix

***REMOVED******REMOVED*** Problem Statement

In PORTRAIT mode:
- (a) Live bounding boxes don't correctly surround detected objects (misaligned/offset)
- (b) Saved/preview thumbnails in items list show only a small/cropped portion of the object

In LANDSCAPE mode: Both bbox overlay and thumbnails work correctly (reference behavior).

***REMOVED******REMOVED*** Root Cause Analysis

***REMOVED******REMOVED******REMOVED*** Where bbox coords originate
- ML Kit Object Detection returns `DetectedObject.boundingBox` as a pixel `Rect`
- The coordinate space depends on how `InputImage` was created:
  - `InputImage.fromMediaImage(mediaImage, rotationDegrees)`: bbox is in ROTATED (upright) coordinate space
  - The sensor produces 1280x720 landscape, but ML Kit processes it as if rotated to 720x1280 portrait

***REMOVED******REMOVED******REMOVED*** Where bbox mapping to screen happens
- `ObjectDetectorClient.convertToDetectionResult()` normalizes bbox using `toNormalizedRect(imageWidth, imageHeight)`
- `DetectionOverlay.kt` uses `calculateTransformWithRotation()` and `mapBboxToPreview()` to map to screen coords
- **Issue**: The normalization uses `bitmap?.width ?: image.width` which can be inconsistent:
  - In preview mode (no bitmap): uses `InputImage.width/height` (possibly 720x1280 rotated)
  - In capture mode (with bitmap): uses `bitmap.width/height` (1280x720 unrotated)

***REMOVED******REMOVED******REMOVED*** Where thumbnail crop happens
- `ObjectDetectorClient.cropThumbnail()` crops from unrotated sensor bitmap
- ML Kit bbox is in ROTATED space (720x1280 for portrait)
- **BUG**: Cropping uses rotated-space coords on unrotated-space bitmap!
- Current code tries to fix by rotating AFTER crop, but the crop itself is wrong

***REMOVED******REMOVED******REMOVED*** What differs between portrait vs landscape
| Aspect | Landscape (rotation=0) | Portrait (rotation=90) |
|--------|----------------------|----------------------|
| Sensor | 1280x720 | 1280x720 |
| InputImage | 1280x720 | 720x1280 (post-rotation) |
| ML Kit bbox space | 1280x720 | 720x1280 |
| Bitmap from sensor | 1280x720 | 1280x720 |
| Coord mismatch? | No | **YES** |

***REMOVED******REMOVED*** Canonical Mapping Contract

***REMOVED******REMOVED******REMOVED*** Data Class: `GeometryContext`
```kotlin
data class GeometryContext(
    val sensorWidth: Int,        // Raw sensor width (1280)
    val sensorHeight: Int,       // Raw sensor height (720)
    val rotationDegrees: Int,    // 0, 90, 180, 270
    val previewWidth: Float,     // Preview composable width
    val previewHeight: Float,    // Preview composable height
    val scaleType: PreviewScaleType
)
```

***REMOVED******REMOVED******REMOVED*** Canonical Flow

```
ML Kit bbox (rotated space)
    │
    ▼
┌─────────────────────────────────────┐
│ toSensorSpaceRect()                 │
│ Convert from ML Kit rotated space   │
│ to sensor (unrotated) space         │
└────────────────────┬────────────────┘
    │
    ▼
NormalizedRect in SENSOR space
    │
    ├──────────────────────┐
    │                      │
    ▼                      ▼
OVERLAY                 THUMBNAIL
    │                      │
    ▼                      ▼
rotateNormalizedRect()  Crop from sensor
(sensor → upright)      bitmap directly
    │                   (no rotation needed
    ▼                    for crop coords)
mapBboxToPreview()         │
    │                      ▼
    ▼                   Rotate result
Screen RectF            to display orientation
```

***REMOVED******REMOVED*** Fix Strategy

***REMOVED******REMOVED******REMOVED*** 1. Unified Geometry Mapper (`DetectionGeometryMapper.kt`)

Create a single source of truth for all coordinate transformations:

```kotlin
object DetectionGeometryMapper {
    // Convert ML Kit bbox to sensor space
    fun mlKitBboxToSensorSpace(bbox: Rect, rotationDegrees: Int, sensorW: Int, sensorH: Int): Rect

    // Convert sensor-space normalized rect to preview coords
    fun sensorToPreviewRect(bboxNorm: NormalizedRect, context: GeometryContext): RectF

    // Convert sensor-space normalized rect to bitmap crop coords
    fun sensorToBitmapCrop(bboxNorm: NormalizedRect, bitmapW: Int, bitmapH: Int, padding: Float): Rect
}
```

***REMOVED******REMOVED******REMOVED*** 2. Key Transformations

**ML Kit rotated space → Sensor space (inverse rotation):**
- For 90°: `(x, y)_rotated → (sensorH - 1 - y, x)_sensor`
- For 270°: `(x, y)_rotated → (y, sensorW - 1 - x)_sensor`

**Sensor space → Preview coords (forward rotation + scale):**
- Apply `rotateNormalizedRect()` to get upright coords
- Apply scale and offset from `calculateTransformWithRotation()`

***REMOVED******REMOVED******REMOVED*** 3. Implementation Changes

1. **ObjectDetectorClient**: Convert ML Kit bbox to sensor space IMMEDIATELY after detection
2. **DetectionOverlay**: Use unified mapper for overlay rendering (no change to rotation logic)
3. **cropThumbnail**: Use sensor-space bbox directly (no coordinate confusion)

***REMOVED******REMOVED*** Debug Overlay

***REMOVED******REMOVED******REMOVED*** Developer Option Toggle
Add "Show geometry debug overlay" toggle in Developer Settings.

***REMOVED******REMOVED******REMOVED*** Diagnostic Info Displayed
1. Preview composable size (px)
2. Analyzer ImageProxy: width, height, rotationDegrees, cropRect
3. Effective content rect after scale/crop
4. For top detection: raw bbox, mapped screen rect

***REMOVED******REMOVED******REMOVED*** GeomMap Logs (rate-limited 1/sec)
```
[GeomMap] Frame: sensor=1280x720, rotation=90, preview=1080x2400
[GeomMap] ML Kit bbox: Rect(100,200,300,400) in 720x1280 rotated space
[GeomMap] Sensor bbox: Rect(200,580,400,680) in 1280x720 sensor space
[GeomMap] Normalized: (0.156,0.806,0.313,0.944)
[GeomMap] Preview rect: RectF(168,1935,338,2268)
```

***REMOVED******REMOVED*** Validation Checklist

***REMOVED******REMOVED******REMOVED*** Portrait Mode
- [ ] Single centered object: bbox tightly surrounds object
- [ ] Object near ROI edge: bbox still matches
- [ ] Saved item preview shows full object (not cropped)
- [ ] Bbox stable while panning

***REMOVED******REMOVED******REMOVED*** Landscape Mode
- [ ] No regression in bbox alignment
- [ ] No regression in preview crop

***REMOVED******REMOVED******REMOVED*** Debug Overlay
- [ ] Dimensions and rotation values correct
- [ ] Mapped rect visually matches bbox on screen

***REMOVED******REMOVED*** Files Modified

| File | Changes |
|------|---------|
| `DetectionGeometryMapper.kt` | NEW: Unified geometry mapping utility with `mlKitBboxToSensorSpace`, `sensorBboxToBitmapCrop`, `cropAndRotateThumbnail`, debug logging |
| `ObjectDetectorClient.kt` | Added `getSensorDimensions()` helper; updated `detectObjects` and `detectObjectsWithTracking` to use sensor dimensions for normalization |
| `DetectionOverlay.kt` | Added `showGeometryDebug` parameter; geometry debug overlay with diagnostic info |
| `DeveloperOptionsViewModel.kt` | Added `bboxMappingDebugEnabled` state flow and setter |
| `DeveloperOptionsScreen.kt` | Added "Geometry Debug Overlay" toggle UI |
| `CameraScreen.kt` | Pass `bboxMappingDebugEnabled` state to DetectionOverlay |

***REMOVED******REMOVED*** Root Cause

The bug was caused by a coordinate space mismatch between ML Kit's bounding box output and the normalization/cropping code:

1. **InputImage dimensions are post-rotation**: `InputImage.fromMediaImage(mediaImage, rotationDegrees)` creates an image where `width` and `height` reflect the image AFTER applying rotation. In portrait mode (rotation=90), a 1280x720 sensor becomes InputImage with width=720, height=1280.

2. **ML Kit bbox is in ORIGINAL sensor coordinates**: Despite InputImage having rotated dimensions, ML Kit returns bounding boxes in the ORIGINAL (pre-rotation/sensor) coordinate space (1280x720).

3. **Normalization used wrong dimensions**: When `bitmap` was null (preview detection mode), the code normalized using `image.width` and `image.height` (post-rotation 720x1280), but the bbox was in sensor space (1280x720). This caused incorrect normalization:
   - Correct: `left=320 / 1280 = 0.25`
   - Bug: `left=320 / 720 = 0.444` (wrong!)

4. **Thumbnail cropping worked by accident**: When bitmap was available, `bitmap.width` was used (1280), which matched sensor space. But this masked the underlying issue.

***REMOVED******REMOVED*** The Fix

Added `getSensorDimensions()` helper that converts InputImage dimensions back to sensor dimensions by swapping width/height for 90° and 270° rotation. This ensures all normalization uses consistent sensor-space coordinates.

```kotlin
private fun getSensorDimensions(
    inputImageWidth: Int,
    inputImageHeight: Int,
    rotationDegrees: Int
): Pair<Int, Int> {
    return when (rotationDegrees) {
        90, 270 -> Pair(inputImageHeight, inputImageWidth) // Swap
        else -> Pair(inputImageWidth, inputImageHeight)    // No swap
    }
}
```

***REMOVED******REMOVED*** Test Cases (DetectionGeometryMapperTest.kt)

1. `mlKitBboxToSensorSpace with 0 rotation returns unchanged bbox`
2. `mlKitBboxToSensorSpace with 90 rotation inverts rotation correctly`
3. `mlKitBboxToSensorSpace with 270 rotation inverts rotation correctly`
4. `mlKitBboxToSensorSpace with 180 rotation flips both axes`
5. `mlKitBboxToSensorSpace centered box stays centered`
6. `sensorBboxToBitmapCrop returns rect within bitmap bounds`
7. `sensorBboxToBitmapCrop adds padding correctly`
8. `sensorBboxToBitmapCrop clamps edge cases to bitmap bounds`
9. `GeometryContext isPortrait returns correct values`
10. `validateMappedRect validates bounds correctly`
