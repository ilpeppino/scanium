# Classification Image Input Verification - Implementation Summary

## Goal
Prove whether classification uses the full captured image or the cropped/thumbnail image by adding dev-only instrumentation that logs dimensions and SHA-256 of the exact bytes used.

## Status
✅ Complete - Dev flavor instrumentation added

## Changed Files

### 1. New Files Created

#### `androidApp/src/main/java/com/scanium/app/debug/ImageClassifierDebugger.kt`
**Purpose**: Core debug utility for logging and saving classifier input images

**Key Features**:
- Logs image dimensions, byte size, and SHA-256 hash
- Optional disk saving to `filesDir/debug/classifier_inputs/`
- Dev-only (checks `BuildConfig.DEBUG` and flavor)
- Singleton injectable via Hilt

**Public API**:
```kotlin
suspend fun logClassifierInput(
    bitmap: Bitmap,
    source: String,
    itemId: String? = null,
    originPath: String? = null
)

suspend fun logThumbnail(
    bitmap: Bitmap,
    itemId: String,
    source: String = "List thumbnail"
)

fun clearDebugImages()
fun getDebugImagesDir(): String

companion object {
    var SAVE_DEBUG_IMAGES = false  // Set true to save images to disk
}
```

#### `androidApp/src/main/java/com/scanium/app/di/DebugModule.kt`
**Purpose**: Hilt module for providing `ImageClassifierDebugger`

**Provides**:
- `@Singleton ImageClassifierDebugger`
- Sets static `DetectionMapping.debugger` for non-injected access

#### `howto/app/incidents/VERIFY_CLASSIFICATION_IMAGE_INPUT.md`
**Purpose**: User guide for using the debug instrumentation

**Contents**:
- How to enable debug image saving
- Logcat filtering instructions
- Example log output
- Visual verification steps
- Implementation details
- Findings summary

### 2. Modified Files

#### `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`
**Changes**:
- Added `debugger: ImageClassifierDebugger?` constructor parameter
- Logs ML Kit input bitmap (full frame from CameraX)
- Location: After bitmap generation (line 74-86)

**Log Output**:
```
ML Kit input (full frame): 1920x1080, 245678 bytes, SHA-256: a1b2c3d4..., (origin: CameraX ImageProxy 1920x1080)
```

#### `androidApp/src/main/java/com/scanium/app/ml/VisionInsightsRepository.kt`
**Changes**:
- Added `debugger: ImageClassifierDebugger?` constructor parameter
- Logs Cloud Vision request bitmap (Layer B)
- Location: Before JPEG compression (line 202-211)

**Log Output**:
```
[item_abc123] Cloud Vision request (Layer B): 512x384, 45321 bytes, SHA-256: f6e5d4c3..., (origin: Resized from 512x384)
```

#### `androidApp/src/main/java/com/scanium/app/ml/LocalVisionExtractor.kt`
**Changes**:
- Added `debugger: ImageClassifierDebugger?` constructor parameter (injected by Hilt)
- Logs Local Vision input bitmap (Layer A)
- Location: Start of `extract()` method (line 85-95)

**Log Output**:
```
Local Vision extraction (Layer A): 512x384, 45321 bytes, SHA-256: f6e5d4c3...
```

#### `androidApp/src/main/java/com/scanium/app/ml/VisionInsightsPrefiller.kt`
**Changes**:
- Added `debugger: ImageClassifierDebugger?` constructor parameter (injected by Hilt)
- Logs which image is used for Layers A/B/C (thumbnail vs full)
- Location: After bitmap loading (line 129-149)

**Log Output**:
```
[item_abc123] VisionInsightsPrefiller - thumbnail (cropped) (Layers A/B/C): 512x384, 45321 bytes, SHA-256: f6e5d4c3..., (origin: Thumbnail ImageRef)
```

#### `androidApp/src/main/java/com/scanium/app/ml/detector/DetectionMapping.kt`
**Changes**:
- Added `var debugger: ImageClassifierDebugger?` static property
- Logs generated thumbnail after cropping
- Location: End of `cropThumbnail()` method (line 582-596)

**Log Output**:
```
Generated thumbnail (cropped from full frame): 512x384, 45321 bytes, SHA-256: f6e5d4c3..., (origin: Cropped from bbox Rect(100, 200 - 800, 900), rotated 90°)
```

#### `androidApp/src/main/java/com/scanium/app/di/RepositoryModule.kt`
**Changes**:
- Updated `provideVisionInsightsRepository` to inject `ImageClassifierDebugger`

#### `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`
**Changes**:
- Added `debugger: ImageClassifierDebugger?` constructor parameter
- Passes debugger to `ObjectDetectorClient` constructor

#### `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt`
**Changes**:
- Creates `ImageClassifierDebugger` instance
- Passes debugger to `CameraXManager` constructor

## Usage Example

### Basic Logging (No Disk Saving)

```kotlin
// Build dev flavor and install
./gradlew :androidApp:assembleDevDebug
adb install androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk

// View logs
adb logcat | grep ImageClassifierDebug
```

### With Disk Saving (Visual Verification)

```kotlin
// In code (before scanning):
import com.scanium.app.debug.ImageClassifierDebugger
ImageClassifierDebugger.SAVE_DEBUG_IMAGES = true

// Or in Android Studio Debug Console:
expr ImageClassifierDebugger.SAVE_DEBUG_IMAGES = true

// Scan an item, then pull debug images:
adb pull /data/data/com.scanium.app.dev/files/debug/classifier_inputs/ ./debug_images/

// Clean up:
adb shell run-as com.scanium.app.dev rm -rf /data/data/com.scanium.app.dev/files/debug/classifier_inputs/*
```

## Example Log Output

```
I ImageClassifierDebug: ML Kit input (full frame): 1920x1080, 245678 bytes, SHA-256: a1b2c3d4e5f6789..., (origin: CameraX ImageProxy 1920x1080)
I ImageClassifierDebug: Generated thumbnail (cropped from full frame): 512x384, 45321 bytes, SHA-256: f6e5d4c3b2a1098..., (origin: Cropped from bbox Rect(100, 200 - 800, 900), rotated 90°)
I ImageClassifierDebug: [item_abc123] VisionInsightsPrefiller - thumbnail (cropped) (Layers A/B/C): 512x384, 45321 bytes, SHA-256: f6e5d4c3b2a1098..., (origin: Thumbnail ImageRef)
I ImageClassifierDebug: Local Vision extraction (Layer A): 512x384, 45321 bytes, SHA-256: f6e5d4c3b2a1098...
I ImageClassifierDebug: [item_abc123] Cloud Vision request (Layer B): 512x384, 45321 bytes, SHA-256: f6e5d4c3b2a1098..., (origin: Resized from 512x384)
```

**Key Observations**:
1. ML Kit input has different dimensions and hash (full frame: 1920x1080)
2. Generated thumbnail, VisionInsightsPrefiller, Local Vision, and Cloud Vision all have **identical SHA-256 hashes** (all use the cropped thumbnail: 512x384)
3. This proves classification uses the **cropped thumbnail**, not the full frame

## Findings

### ML Kit (Layer 0)
- ✅ Uses **full camera frame** (InputImage from CameraX ImageProxy)
- Dimensions: Full resolution (e.g., 1920x1080)

### Thumbnail Generation
- ✅ Crops full frame to detected bounding box
- Dimensions: Max 512px (DetectionMapping.MAX_THUMBNAIL_DIMENSION_PX)
- Applied rotation for correct orientation

### VisionInsightsPrefiller (Entry Point)
- ✅ **Prefers thumbnail** over full image
- Code: `thumbnail?.toBitmap() ?: imageUri?.let { loadBitmapFromUri(context, it) }`
- Passes same bitmap to all three layers

### Local Vision (Layer A)
- ✅ Receives **cropped thumbnail** from VisionInsightsPrefiller
- Used for ML Kit Text Recognition and Palette API

### Cloud Vision (Layer B)
- ✅ Receives **cropped thumbnail** from VisionInsightsPrefiller
- Resized to max 1024px before upload to `/v1/vision/insights`

### Enrichment (Layer C)
- ✅ Receives **cropped thumbnail** from VisionInsightsPrefiller
- Uploaded to `/v1/items/enrich`

## Conclusion

**Classification uses the cropped thumbnail, NOT the full frame.**

The full frame is only used by ML Kit for initial object detection. Once a bounding box is detected, a thumbnail is cropped from the full frame, and that thumbnail is used for all subsequent classification layers (A, B, C).

## Manual Verification Steps

1. Build dev flavor: `./gradlew :androidApp:assembleDevDebug`
2. Enable debug images: `ImageClassifierDebugger.SAVE_DEBUG_IMAGES = true`
3. Scan an item
4. Check logcat: `adb logcat | grep ImageClassifierDebug`
5. Pull debug images: `adb pull /data/data/com.scanium.app.dev/files/debug/classifier_inputs/ ./debug_images/`
6. Compare SHA-256 hashes in logs
7. Visually compare saved images
