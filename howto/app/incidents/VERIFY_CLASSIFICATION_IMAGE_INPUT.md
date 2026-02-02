# Verify Classification Image Input

## Summary

This document explains how to verify which images (full frame vs cropped thumbnail) are used as input for ML Kit, Cloud Vision, and Local Vision classification.

**Status**: Dev-only instrumentation added in dev flavor
**Date**: 2026-02-02

## Background

When a user scans an item, the app performs classification using:
1. **ML Kit** (Layer 0): On-device object detection with labels
2. **Local Vision** (Layer A): On-device OCR and color extraction
3. **Cloud Vision** (Layer B): Backend-powered logo/brand detection
4. **Enrichment** (Layer C): Full attribute extraction and draft generation

Each layer receives an image as input. This instrumentation logs exactly which image is used (full frame or cropped thumbnail) and its characteristics (dimensions, byte size, SHA-256 hash).

## How to Use

### Step 1: Enable Debug Image Logging

In the dev flavor build, add this line before scanning:

```kotlin
import com.scanium.app.debug.ImageClassifierDebugger

// Enable saving debug images to disk for visual verification
ImageClassifierDebugger.SAVE_DEBUG_IMAGES = true
```

Or run this in Android Studio's Debug Console:
```
expr ImageClassifierDebugger.SAVE_DEBUG_IMAGES = true
```

### Step 2: Scan an Item

1. Build and install the **dev** flavor:
   ```bash
   ./gradlew :androidApp:assembleDevDebug
   adb install androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk
   ```

2. Open the app and scan an item

3. The app will automatically log image details for:
   - ML Kit input (full frame from CameraX)
   - Generated thumbnail (cropped from full frame)
   - VisionInsightsPrefiller input (thumbnail or fallback to full image)
   - Local Vision input (Layer A)
   - Cloud Vision request (Layer B)

### Step 3: View Logs

Filter logcat by tag `ImageClassifierDebug`:

```bash
adb logcat | grep ImageClassifierDebug
```

**Example output**:
```
I ImageClassifierDebug: ML Kit input (full frame): 1920x1080, 245678 bytes, SHA-256: a1b2c3d4e5f6..., (origin: CameraX ImageProxy 1920x1080)
I ImageClassifierDebug: Generated thumbnail (cropped from full frame): 512x384, 45321 bytes, SHA-256: f6e5d4c3b2a1..., (origin: Cropped from bbox Rect(100, 200 - 800, 900), rotated 90°)
I ImageClassifierDebug: [item_abc123] VisionInsightsPrefiller - thumbnail (cropped) (Layers A/B/C): 512x384, 45321 bytes, SHA-256: f6e5d4c3b2a1..., (origin: Thumbnail ImageRef)
I ImageClassifierDebug: Local Vision extraction (Layer A): 512x384, 45321 bytes, SHA-256: f6e5d4c3b2a1...
I ImageClassifierDebug: [item_abc123] Cloud Vision request (Layer B): 512x384, 45321 bytes, SHA-256: f6e5d4c3b2a1..., (origin: Resized from 512x384)
```

**Key Observations**:
- ML Kit processes the **full frame** (1920x1080 in this example)
- A **thumbnail is generated** by cropping the full frame to the detected bounding box (512x384)
- VisionInsightsPrefiller **prefers the thumbnail** over the full image
- Cloud Vision receives the **thumbnail (cropped)** image
- Local Vision also receives the **thumbnail (cropped)** image

### Step 4: Visual Verification (Optional)

If `SAVE_DEBUG_IMAGES = true`, the app saves each classifier input image to:

```
/data/data/com.scanium.app.dev/files/debug/classifier_inputs/
```

Pull the images from the device:

```bash
adb pull /data/data/com.scanium.app.dev/files/debug/classifier_inputs/ ./debug_images/
```

**Filename format**:
```
[itemId_]<source>_<timestamp>_<hash_prefix>.jpg
```

**Examples**:
- `ML_Kit_input__full_frame__1738512345678_a1b2c3d4.jpg`
- `item_abc123_VisionInsightsPrefiller_-_thumbnail__cropped___Layers_A_B_C__1738512345789_f6e5d4c3.jpg`
- `Generated_thumbnail__cropped_from_full_frame__1738512345890_f6e5d4c3.jpg`

**Visual Comparison**:
1. Open the ML Kit input image (full frame) - should show the entire camera view
2. Open the thumbnail image - should show only the detected object, cropped to the bounding box
3. Compare the SHA-256 hashes - if identical, the same image bytes are used

### Step 5: Clear Debug Images

To free up disk space:

```bash
adb shell run-as com.scanium.app.dev rm -rf /data/data/com.scanium.app.dev/files/debug/classifier_inputs/*
```

Or programmatically:
```kotlin
ImageClassifierDebugger(context).clearDebugImages()
```

## Implementation Details

### Instrumentation Points

1. **ML Kit Input** (`ObjectDetectorClient.kt:74-86`)
   - Logs the full frame bitmap provided to ML Kit's InputImage
   - Source: CameraX ImageProxy converted to Bitmap

2. **Thumbnail Generation** (`DetectionMapping.kt:582-596`)
   - Logs the cropped thumbnail after it's generated from the full frame
   - Source: `cropThumbnail()` with bounding box and rotation applied

3. **VisionInsightsPrefiller** (`VisionInsightsPrefiller.kt:129-149`)
   - Logs which image is used for Layers A/B/C (thumbnail or full image fallback)
   - Shows whether the thumbnail or full image was selected

4. **Local Vision** (`LocalVisionExtractor.kt:85-95`)
   - Logs the bitmap input to ML Kit Text Recognition and Palette API
   - Source: bitmap passed from VisionInsightsPrefiller

5. **Cloud Vision** (`VisionInsightsRepository.kt:202-211`)
   - Logs the bitmap before JPEG compression and upload to `/v1/vision/insights`
   - Source: resized bitmap (max 1024px) from VisionInsightsPrefiller

### Data Logged

For each image:
- **Dimensions**: `width x height` in pixels
- **Byte size**: JPEG-compressed size in bytes
- **SHA-256 hash**: Hex digest of JPEG bytes (for verifying identical images)
- **Origin**: Description of where the image came from (e.g., "CameraX ImageProxy", "Thumbnail ImageRef")

### Debug Configuration

```kotlin
// File: androidApp/src/main/java/com/scanium/app/debug/ImageClassifierDebugger.kt

companion object {
    // Enable/disable saving debug images to disk
    var SAVE_DEBUG_IMAGES = false  // Set to true to save images
}
```

## Findings

Based on the code analysis:

1. **ML Kit** uses the **full camera frame** (InputImage from CameraX ImageProxy)
2. **Thumbnail generation** crops the full frame to the detected bounding box
3. **VisionInsightsPrefiller** prefers the **thumbnail (cropped)** over the full image
   - Line 132: `thumbnail?.toBitmap()` (preferred)
   - Line 133: `imageUri?.let { loadBitmapFromUri(context, it) }` (fallback)
4. **Local Vision** (Layer A) receives the **thumbnail** from VisionInsightsPrefiller
5. **Cloud Vision** (Layer B) receives the **thumbnail** from VisionInsightsPrefiller
6. **Enrichment** (Layer C) receives the **thumbnail** from VisionInsightsPrefiller

**Conclusion**: Classification (Layers A/B/C) uses the **cropped thumbnail**, while ML Kit uses the **full frame**.

## Related Files

- `androidApp/src/main/java/com/scanium/app/debug/ImageClassifierDebugger.kt` - Debug utility
- `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` - ML Kit instrumentation
- `androidApp/src/main/java/com/scanium/app/ml/VisionInsightsPrefiller.kt` - Layer A/B/C instrumentation
- `androidApp/src/main/java/com/scanium/app/ml/LocalVisionExtractor.kt` - Local Vision instrumentation
- `androidApp/src/main/java/com/scanium/app/ml/VisionInsightsRepository.kt` - Cloud Vision instrumentation
- `androidApp/src/main/java/com/scanium/app/ml/detector/DetectionMapping.kt` - Thumbnail generation instrumentation

## See Also

- [Vision Pipeline Architecture](../../project/reference/ARCHITECTURE.md#vision-pipeline)
- [Developer Options](../../app/reference/DEVELOPER_OPTIONS.md)
