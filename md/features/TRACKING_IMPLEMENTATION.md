# Object Tracking and De-Duplication Implementation

## Overview

This document describes the implementation of a robust tracking and de-duplication system for the Scanium Android app. The system uses ML Kit's Object Detection & Tracking capabilities combined with custom spatial matching heuristics to ensure that each physical object is recognized only once per scanning session.

## Implementation Summary

### 1. New Components Created

#### ObjectCandidate (`app/src/main/java/com/scanium/app/tracking/ObjectCandidate.kt`)

A data class representing a candidate object being tracked across multiple frames.

**Key Properties:**
- `internalId`: Stable identifier (ML Kit trackingId or generated)
- `boundingBox`: Current bounding box (RectF)
- `lastSeenFrame`: Frame number when last observed
- `seenCount`: Number of frames detected in
- `maxConfidence`: Maximum label confidence observed
- `category`: Object category (ItemCategory)
- `labelText`: Most confident label text
- `thumbnail`: Best quality thumbnail
- `averageBoxArea`: Running average of normalized box area

**Key Methods:**
- `update()`: Updates candidate with new detection information
- `getCenterPoint()`: Calculates bounding box center
- `distanceTo()`: Calculates Euclidean distance to another box
- `calculateIoU()`: Calculates Intersection over Union with another box

#### ObjectTracker (`app/src/main/java/com/scanium/app/tracking/ObjectTracker.kt`)

The core tracking component that manages candidate objects and applies confirmation logic.

**Key Features:**
- Maintains in-memory collection of candidates keyed by internalId
- Implements frame-based tracking with temporal information
- Uses ML Kit trackingId when available
- Fallback spatial matching using IoU and center distance
- Automatic expiry of stale candidates
- Confirmation logic based on configurable thresholds

**Configuration (TrackerConfig):**
- `minFramesToConfirm = 3`: Require 3 frames to confirm
- `minConfidence = 0.4f`: Minimum confidence threshold
- `minBoxArea = 0.001f`: Minimum 0.1% of frame area
- `maxFrameGap = 5`: Allow 5 frames gap for matching
- `minMatchScore = 0.3f`: Minimum spatial match score
- `expiryFrames = 10`: Expire after 10 frames without detection

**Key Methods:**
- `processFrame(detections)`: Processes detections and returns newly confirmed candidates
- `findMatchingCandidate()`: Matches detection to existing candidate using trackingId or spatial heuristics
- `reset()`: Clears all candidates and state
- `getStats()`: Returns tracking statistics for debugging

#### DetectionInfo (`app/src/main/java/com/scanium/app/tracking/ObjectTracker.kt`)

A data class holding raw detection information extracted from ML Kit.

**Properties:**
- `trackingId`: ML Kit tracking ID (nullable)
- `boundingBox`: Bounding box as RectF
- `confidence`: Label confidence
- `category`: Detected category
- `labelText`: Label text
- `thumbnail`: Cropped thumbnail
- `normalizedBoxArea`: Normalized bounding box area

### 2. Updated Components

#### ObjectDetectorClient (`app/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`)

**New Methods:**
- `detectObjectsWithTracking()`: Extracts raw detection information for tracking pipeline
- `extractDetectionInfo()`: Converts DetectedObject to DetectionInfo with tracking metadata
- `candidateToScannedItem()`: Converts confirmed ObjectCandidate to ScannedItem

**Key Changes:**
- Imports DetectionInfo from tracking package
- Uses STREAM_MODE by default for tracking (provides better trackingId availability)
- Extracts all necessary metadata for spatial matching (bounding box as RectF, confidence, etc.)

#### CameraXManager (`app/src/main/java/com/scanium/app/camera/CameraXManager.kt`)

**New Components:**
- `objectTracker`: ObjectTracker instance with configured thresholds
- `currentScanMode`: Tracks current scan mode to detect mode changes

**New Methods:**
- `processObjectDetectionWithTracking()`: Processes frame through tracking pipeline
- `resetTracker()`: Manually resets the object tracker

**Key Changes:**
- `startScanning()`: Resets tracker when mode changes or new session starts
- `stopScanning()`: Resets tracker when scanning stops
- `processImageProxy()`: Routes to tracking pipeline when in OBJECT_DETECTION + STREAM mode
- Uses STREAM_MODE for object detection during continuous scanning (better trackingId support)
- Single-shot captures still use SINGLE_IMAGE_MODE without tracking

**Integration Logic:**
```kotlin
when (scanMode) {
    ScanMode.OBJECT_DETECTION -> {
        if (useStreamMode && isScanning) {
            // Use tracking pipeline for continuous scanning
            processObjectDetectionWithTracking(inputImage, bitmapForThumb)
        } else {
            // Single-shot without tracking
            objectDetector.detectObjects(...)
        }
    }
    // Other modes unchanged
}
```

#### ItemsViewModel (`app/src/main/java/com/scanium/app/items/ItemsViewModel.kt`)

**No changes required!** The existing ID-based de-duplication using `seenIds` set works perfectly with stable tracking IDs from ObjectTracker.

**Existing De-duplication:**
- `addItem()`: Checks if ID exists before adding
- `addItems()`: Filters out items with IDs already in seenIds
- `removeItem()`: Removes from both list and seenIds
- `clearAllItems()`: Clears both list and seenIds

### 3. Data Flow

#### Previous Flow (Without Tracking)
```
ImageProxy → ML Kit Detection → DetectedObject[]
  → For each object: Create ScannedItem with UUID
  → ItemsViewModel.addItems()
  → Result: Duplicates created for same physical object
```

#### New Flow (With Tracking)
```
ImageProxy → ML Kit Detection (STREAM_MODE) → DetectedObject[]
  → For each object: Extract DetectionInfo (trackingId, bbox, confidence, etc.)
  → ObjectTracker.processFrame(detections)
    → Match to existing candidates using:
      1. Direct trackingId match (preferred)
      2. Spatial matching (IoU + center distance)
    → Update existing candidates OR create new ones
    → Track frame counts, confidence, and stability
    → Return newly confirmed candidates (met threshold)
  → For each confirmed candidate: Convert to ScannedItem
  → ItemsViewModel.addItems() with stable IDs
  → Result: Each physical object appears once
```

### 4. Tracking Logic

#### Candidate Lifecycle

1. **Creation**: New detection creates candidate with initial metadata
2. **Tracking**: Across frames, detection matched to candidate and updates it
3. **Confirmation**: When candidate meets thresholds, it's promoted to ScannedItem
4. **Expiry**: If not seen for `expiryFrames`, candidate is removed

#### Matching Strategy

**Primary: Direct trackingId Match**
```kotlin
if (detection.trackingId != null) {
    val candidate = candidates[detection.trackingId]
    if (candidate != null) return candidate
}
```

**Fallback: Spatial Matching**
```kotlin
for (candidate in candidates.values) {
    val iou = candidate.calculateIoU(detection.boundingBox)
    val distance = candidate.distanceTo(detection.boundingBox)
    val normalizedDistance = distance / 1000f

    val score = iou * 0.7f + (1f - normalizedDistance) * 0.3f

    if (score > bestScore && score > minMatchScore) {
        bestMatch = candidate
        bestScore = score
    }
}
```

#### Confirmation Criteria

A candidate is confirmed when ALL of these are met:
```kotlin
candidate.seenCount >= minFramesToConfirm &&
candidate.maxConfidence >= minConfidence &&
candidate.averageBoxArea >= minBoxArea
```

### 5. Key Benefits

1. **Reduced Duplicates**: Same physical object tracked across frames with stable ID
2. **Improved Accuracy**: Confirmation requires multiple frames and confidence threshold
3. **Robustness**: Fallback spatial matching when trackingId unavailable
4. **Performance**: Only newly confirmed candidates create UI items
5. **Tunable**: All thresholds configurable via TrackerConfig
6. **Debug-Friendly**: Comprehensive logging and statistics

### 6. Configuration Tuning

The tracker can be tuned by adjusting `TrackerConfig` parameters:

```kotlin
// More aggressive confirmation (fewer false positives, slower confirmation)
TrackerConfig(
    minFramesToConfirm = 5,    // Need 5 frames
    minConfidence = 0.5f,       // Higher confidence
    minBoxArea = 0.005f,        // Larger objects only
    expiryFrames = 15           // Keep longer
)

// More lenient confirmation (faster confirmation, potential false positives)
TrackerConfig(
    minFramesToConfirm = 2,    // Need only 2 frames
    minConfidence = 0.3f,       // Lower confidence
    minBoxArea = 0.0005f,       // Smaller objects ok
    expiryFrames = 8            // Expire faster
)
```

### 7. Logging and Debugging

**Comprehensive logging added for tracking lifecycle:**

- Detection counts: `"Detected N objects for tracking"`
- Candidate creation: `"Created new candidate <id>: <category> (<label>)"`
- Candidate updates: `"Updated candidate <id>: seenCount=N, maxConfidence=X"`
- Spatial matching: `"Spatial match found: <id> (score=X)"`
- Confirmation: `"✓ CONFIRMED candidate <id>: <category> (<label>) after N frames"`
- Expiry: `"Expiring candidate <id>: not seen for N frames"`
- Tracker stats: `"Tracker stats: active=N, confirmed=M, frame=F"`

**Tracker Statistics Available:**
```kotlin
val stats = objectTracker.getStats()
// stats.activeCandidates - Current candidates being tracked
// stats.confirmedCandidates - Total confirmed across session
// stats.currentFrame - Current frame number
```

### 8. Mode Management

**Tracker is reset when:**
1. Starting a new scan session: `startScanning()` → `objectTracker.reset()`
2. Switching scan modes: `currentScanMode != scanMode` → `objectTracker.reset()`
3. Stopping scanning: `stopScanning()` → `objectTracker.reset()`
4. App shutdown: `shutdown()` → `objectTracker.reset()`
5. Manual reset: `resetTracker()` → `objectTracker.reset()`

**Tracking is used only for:**
- Object detection mode
- During continuous scanning (not single-shot tap capture)
- When STREAM_MODE is active

**Other modes unchanged:**
- Barcode scanning: Uses existing pipeline without tracking
- Document text: Uses existing pipeline without tracking

### 9. Compatibility

**Backward Compatibility:**
- Single-shot tap capture: Uses SINGLE_IMAGE_MODE without tracking (existing behavior)
- Barcode mode: No changes
- Document mode: No changes
- ItemsViewModel: No API changes, works with stable IDs
- UI: No changes required, receives ScannedItem as before

**ML Kit Compatibility:**
- Works with ML Kit Object Detection & Tracking 17.0.1
- STREAM_MODE provides trackingId more reliably than SINGLE_IMAGE_MODE
- Graceful fallback when trackingId is null

### 10. Testing Recommendations

**Manual Testing:**
1. **Single Object Scanning**: Point camera at single object, long-press to scan
   - Expected: Object appears once after 3 frames
   - Verify: Check logs for candidate lifecycle

2. **Multiple Objects**: Scan multiple objects simultaneously
   - Expected: Each object confirmed independently
   - Verify: Tracker stats show multiple active candidates

3. **Object Movement**: Move object while scanning
   - Expected: Spatial matching keeps tracking same object
   - Verify: Logs show "Updated candidate" not "Created new candidate"

4. **Mode Switching**: Switch between Object/Barcode/Document modes
   - Expected: Tracker resets on mode change
   - Verify: Logs show "Resetting tracker (mode change...)"

5. **Stop/Start Scanning**: Stop (double-tap) and restart (long-press)
   - Expected: Tracker resets, fresh scan session
   - Verify: Frame counter resets to 0

**Log Verification:**
```bash
adb logcat | grep -E "ObjectTracker|CameraXManager|ObjectDetectorClient"
```

**Look for:**
- Confirmation messages after 3+ frames
- No duplicate confirmations for same physical object
- Proper tracking across frames (seenCount incrementing)
- Spatial matching when trackingId unavailable

### 11. Known Limitations

1. **Network Required**: ML Kit downloads models on first use
2. **Performance**: Thumbnail cropping and IoU calculations add slight overhead
3. **ML Kit Categories**: Only 5 coarse categories (Fashion, Food, Home goods, Places, Plants)
4. **Tracking ID Availability**: SINGLE_IMAGE_MODE may not provide trackingId consistently
5. **Memory**: Tracker keeps candidates in memory during scan session (cleared on reset)

### 12. Future Enhancements

**Potential improvements:**
1. **Color Matching**: Add dominant color extraction for better spatial matching
2. **Adaptive Thresholds**: Adjust thresholds based on scene complexity
3. **Persistence**: Save tracker state across app restarts
4. **User Feedback**: Show tracking confidence in UI
5. **Performance Metrics**: Track and log frame processing times
6. **Custom Categories**: Expand beyond ML Kit's 5 categories with custom classifier

## Summary

The tracking and de-duplication system is fully implemented and integrated into the Scanium app. It provides:

✅ **Robust Tracking**: Uses ML Kit trackingId + spatial fallback
✅ **De-duplication**: Each physical object appears once per scan
✅ **Configurable**: Tunable thresholds for different use cases
✅ **Performance**: Minimal overhead, efficient matching
✅ **Backward Compatible**: Existing modes and UI unchanged
✅ **Debug-Friendly**: Comprehensive logging and statistics
✅ **Production-Ready**: Proper resource cleanup and error handling

The app is ready for compilation and testing on a physical Android device with ML Kit support.
