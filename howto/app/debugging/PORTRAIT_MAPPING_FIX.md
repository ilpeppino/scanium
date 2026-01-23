# Portrait Mode Bounding Box Mapping Fix

## Summary

This document describes the root cause analysis and fix for portrait-mode coordinate/cropping bugs
in the Scanium Android app.

## Problem Statement

In PORTRAIT mode:

- Bounding boxes did not correctly surround detected objects (misaligned/incorrect size/position)
- Item preview thumbnails appeared cropped (only partial object visible)

In LANDSCAPE mode:

- Bbox overlay and preview were correct (reference behavior)

## Root Cause Analysis

### Coordinate Pipeline Overview

```
┌─────────────────────────────────────────────────────────────┐
│ CAMERA SENSOR (Native Landscape)                            │
│ ImageProxy: width=1280, height=720                          │
│ rotationDegrees=90 (portrait) or 0 (landscape)             │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ ML KIT DETECTION                                            │
│ Returns boundingBox in SENSOR coordinates (unrotated)       │
│ e.g., Rect(320, 180, 480, 360) for 1280x720 image          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ NORMALIZATION                                               │
│ toNormalizedRect(1280, 720) → NormalizedRect(0.25, 0.25...)│
│ Coordinates are in sensor space (unrotated)                 │
└────────────────────┬────────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        │                         │
        ▼                         ▼
  OVERLAY DISPLAY          THUMBNAIL GENERATION
  (needs rotation fix)     (already correct)
```

### Issue 1: Missing Rotation in Overlay Transform

**Before (Broken):**
The `calculateTransform` function in `OverlayTransforms.kt` did NOT account for `rotationDegrees`:

```kotlin
// OLD CODE - Wrong for portrait mode
val transform = calculateTransform(
    imageWidth = imageSize.width,    // 1280 (sensor landscape)
    imageHeight = imageSize.height,  // 720
    previewWidth = canvasWidth,      // ~1080 (phone portrait)
    previewHeight = canvasHeight     // ~1920
)
```

This caused:

- Aspect ratio mismatch (1280x720 mapped to 1080x1920)
- No coordinate rotation for portrait orientation
- Bounding boxes appeared in wrong positions

**After (Fixed):**
New rotation-aware transform that:

1. Swaps dimensions for 90°/270° rotation
2. Rotates bbox coordinates to match display orientation
3. Uses FILL_CENTER (center-crop) scaling to match PreviewView

```kotlin
// NEW CODE - Handles rotation correctly
val transform = calculateTransformWithRotation(
    imageWidth = imageSize.width,    // 1280
    imageHeight = imageSize.height,  // 720
    previewWidth = canvasWidth,      // ~1080
    previewHeight = canvasHeight,    // ~1920
    rotationDegrees = rotationDegrees, // 90 for portrait
    scaleType = PreviewScaleType.FILL_CENTER
)
```

### Issue 2: Scale Type Mismatch

**Before (Broken):**
The transform used FIT_CENTER (letterbox) math, but CameraX PreviewView uses FILL_CENTER (
center-crop).

**After (Fixed):**

- Added `PreviewScaleType` enum with `FIT_CENTER` and `FILL_CENTER`
- Default to `FILL_CENTER` to match PreviewView behavior

## Coordinate Transformation Math

### Rotation Transformations (Normalized 0-1 Coordinates)

| Rotation | Transform           | Description              |
|----------|---------------------|--------------------------|
| 0°       | (x, y) → (x, y)     | No change                |
| 90°      | (x, y) → (y, 1-x)   | Rotate clockwise         |
| 180°     | (x, y) → (1-x, 1-y) | Flip both axes           |
| 270°     | (x, y) → (1-y, x)   | Rotate counter-clockwise |

### Scale Type Comparison

| Scale Type  | Behavior                                | Use Case                          |
|-------------|-----------------------------------------|-----------------------------------|
| FIT_CENTER  | Scale to fit within bounds, add padding | Letterbox                         |
| FILL_CENTER | Scale to fill bounds, crop overflow     | Center-crop (PreviewView default) |

## Files Modified

| File                   | Changes                                                                                                     |
|------------------------|-------------------------------------------------------------------------------------------------------------|
| `OverlayTransforms.kt` | Added `calculateTransformWithRotation`, `mapBboxToPreview`, `rotateNormalizedRect`, `PreviewScaleType` enum |
| `DetectionOverlay.kt`  | Added `rotationDegrees` parameter, use new transform functions                                              |
| `CameraXManager.kt`    | Added `onRotation` callback to pass rotation from ImageProxy                                                |
| `CameraScreen.kt`      | Track `imageRotationDegrees` state, pass to DetectionOverlay                                                |

## Thumbnail Cropping (No Changes Needed)

The thumbnail cropping in `ObjectDetectorClient.cropThumbnail()` was already correct:

1. Crops from unrotated bitmap using unrotated bbox coordinates
2. Rotates the cropped result by `rotationDegrees`
3. Displays with `ContentScale.Fit` (no additional crop)

## Debug Diagnostics

Rate-limited logging (once per second) is available via `logBboxMappingDebug()`:

```
[BboxMap] [MAPPING] rotation=90°, effectiveDims=720x1280, scale=1.5, offset=(-80, 0), scaleType=FILL_CENTER
[BboxMap] [MAPPING] input=(0.25,0.25)-(0.375,0.5) -> output=(200,320)-(380,680)
```

## Validation Matrix

### Manual Tests (Must Pass)

1. **Portrait Mode:**
    - [ ] Open camera → bboxes align with objects
    - [ ] Pan around → bboxes track correctly
    - [ ] Take picture of a cup → preview thumbnail shows full cup

2. **Landscape Mode:**
    - [ ] Ensure no regression (still perfect)

3. **Multiple Devices/Resolutions:**
    - [ ] Repeat with low/normal/high resolution settings

### Automated Tests

Unit tests for `OverlayTransforms.kt`:

- `rotateNormalizedRect()` for 0°, 90°, 180°, 270°
- `calculateTransformWithRotation()` for FIT vs FILL
- `mapBboxToPreview()` end-to-end mapping

## Architecture Notes

### Eye vs Focus Mode (Preserved)

- **Eye Mode:** Global bboxes shown everywhere (very subtle styling)
- **Focus Mode:** ROI selection + actions (highlighted styling)
- Both modes use the same rotation-aware mapping

### Data Flow

```
ImageProxy.rotationDegrees
    ↓
CameraXManager.onRotation callback
    ↓
CameraScreen.imageRotationDegrees state
    ↓
DetectionOverlay.rotationDegrees param
    ↓
calculateTransformWithRotation()
    ↓
mapBboxToPreview() per detection
    ↓
Canvas.drawRoundRect()
```
