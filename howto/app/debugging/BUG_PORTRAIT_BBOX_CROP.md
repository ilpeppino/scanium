***REMOVED*** Portrait Bounding Box and Crop Bug - Root Cause Analysis

***REMOVED******REMOVED*** Problem Summary

In portrait mode, detected bounding boxes appear "wide" instead of matching tall objects, and saved
thumbnails show incorrect crops (strips instead of full objects). Landscape mode works correctly.

***REMOVED******REMOVED*** Root Cause

**The codebase incorrectly assumed ML Kit returns bboxes in SENSOR (raw buffer) coordinate space,
but ML Kit actually returns bboxes in InputImage (upright/post-rotation) coordinate space.**

This caused a double-rotation bug:

1. ML Kit already returns bboxes rotated to upright orientation
2. Code then applied additional rotation transformation → wrong result

***REMOVED******REMOVED*** ML Kit Coordinate Contract (CORRECT)

When using `InputImage.fromMediaImage(mediaImage, rotationDegrees)`:

| Property                     | Value                                     |
|------------------------------|-------------------------------------------|
| `InputImage.width`           | Width AFTER rotation (upright)            |
| `InputImage.height`          | Height AFTER rotation (upright)           |
| `DetectedObject.boundingBox` | Coordinates in InputImage space (upright) |

***REMOVED******REMOVED******REMOVED*** Example - Portrait Mode (90° rotation)

```
Sensor buffer:     1280 x 720  (landscape)
InputImage:        720 x 1280  (portrait, after rotation metadata applied)
ML Kit bbox:       In 720x1280 space (already upright!)

A tall bottle produces bbox approximately:
  left=300, top=100, right=420, bottom=900
  → width=120, height=800 (tall, correct!)
```

***REMOVED******REMOVED*** What Was Wrong

***REMOVED******REMOVED******REMOVED*** File: `ObjectDetectorClient.kt`

The comment and code in `getSensorDimensions()` was WRONG:

```kotlin
// WRONG ASSUMPTION:
// "ML Kit returns bounding boxes in ORIGINAL (pre-rotation/sensor) coordinate space"

// This function swapped dimensions unnecessarily
private fun getSensorDimensions(inputImageWidth, inputImageHeight, rotationDegrees): Pair<Int, Int>
```

***REMOVED******REMOVED******REMOVED*** File: `OverlayTransforms.kt`

`mapBboxToPreview()` called `rotateNormalizedRect()` which rotated already-upright coordinates:

```kotlin
// WRONG: bbox is ALREADY upright, don't rotate again!
val rotatedNorm = rotateNormalizedRect(bboxNorm, transform.rotationDegrees)
```

***REMOVED******REMOVED******REMOVED*** File: `DetectionGeometryMapper.kt`

`mlKitBboxToSensorSpace()` was designed to "reverse" ML Kit rotation, but this was based on wrong
assumptions:

```kotlin
// WRONG: Tried to convert upright bbox back to sensor space
// This function should not exist - ML Kit bbox IS already upright
```

***REMOVED******REMOVED*** Coordinate Pipeline (FIXED)

***REMOVED******REMOVED******REMOVED*** Canonical "Upright Space"

All bboxes are stored and processed in **upright space**:

- Origin: top-left of screen as user sees it
- Dimensions: `uprightWidth x uprightHeight` (720x1280 in portrait)
- This matches ML Kit's output directly

***REMOVED******REMOVED******REMOVED*** Live Detection → Overlay

```
ML Kit bbox (already upright)
    ↓
Normalize: bbox / InputImage.dimensions
    ↓
Store as NormalizedRect (0-1 range, upright space)
    ↓
Map to preview: scale + offset (NO rotation needed)
    ↓
Draw on Canvas
```

***REMOVED******REMOVED******REMOVED*** Thumbnail Cropping

```
ML Kit bbox (upright coordinates)
    ↓
Source bitmap is in SENSOR orientation (unrotated)
    ↓
Convert upright bbox → sensor bbox (reverse rotation)
    ↓
Crop from sensor bitmap
    ↓
Rotate cropped region to upright
    ↓
Display thumbnail
```

***REMOVED******REMOVED*** Key Files Modified

| File                         | Change                                                                                    |
|------------------------------|-------------------------------------------------------------------------------------------|
| `ObjectDetectorClient.kt`    | Use InputImage dimensions for normalization; add `uprightBboxToSensorBbox()` for cropping |
| `OverlayTransforms.kt`       | Remove `rotateNormalizedRect()` call - bbox is already upright                            |
| `DetectionOverlay.kt`        | Enhanced geometry debug with bbox aspect ratio display                                    |
| `DetectionGeometryMapper.kt` | Deprecated `mlKitBboxToSensorSpace()` (wrong assumptions)                                 |
| `OverlayTransformsTest.kt`   | NEW: Unit tests for upright coordinate mapping                                            |

***REMOVED******REMOVED*** Developer Geometry Debug

Enable "Show geometry debug" in Developer Options to see:

- Sensor dimensions and rotation
- Raw ML Kit bbox values
- Normalized bbox coordinates
- Mapped screen coordinates
- Bbox aspect ratio (should be >1 for tall objects)

***REMOVED******REMOVED*** Verification Checklist

***REMOVED******REMOVED******REMOVED*** Portrait Mode

- [ ] Tall bottle: bbox is tall (height > width)
- [ ] Bbox surrounds object correctly
- [ ] Thumbnail shows full object
- [ ] Item list preview shows full object

***REMOVED******REMOVED******REMOVED*** Landscape Mode

- [ ] No regression from previous behavior
- [ ] Bbox orientation matches objects
- [ ] Thumbnails crop correctly

***REMOVED******REMOVED*** Test Commands

```bash
***REMOVED*** Build
./gradlew :androidApp:assembleDebug --no-daemon

***REMOVED*** Unit tests
./gradlew :androidApp:testDebugUnitTest --no-daemon
```
