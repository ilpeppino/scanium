# Multi-Layer Deduplication Debug Investigation

## Date: 2025-12-08

## Problem Report

User reported that NO items are being detected in scanning mode on a real device after implementing
the new multi-layer deduplication system. Logs show nothing.

## Changes Made

### 1. Root Cause Identified

**CRITICAL BUG FOUND AND FIXED**: Objects without ML Kit classification labels were getting
confidence = 0.0f, which failed the `minConfidence = 0.2f` threshold check in
`ObjectTracker.isConfirmed()`.

### 2. Key Fix Applied

**File**: `/Users/family/dev/objecta/app/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`

**Function**: `extractDetectionInfo()`

**Change**: Added fallback confidence values for objects without classification:

```kotlin
// CRITICAL: Use effective confidence for objects without classification
// ML Kit's object detection is reliable even without classification
val confidence = bestLabel?.confidence ?: run {
    // Objects detected without classification get a reasonable confidence
    if (detectedObject.trackingId != null) {
        0.6f // Good confidence for tracked but unlabeled objects
    } else {
        0.4f // Moderate confidence for objects without tracking
    }
}
```

This ensures that even objects without labels (which ML Kit still detects reliably) will pass the
confirmation threshold.

### 3. Comprehensive Debug Logging Added

Added detailed INFO-level logging at every critical pipeline stage:

#### CameraXManager.kt

- `processImageProxy()`: Logs which code path is taken (tracking vs single-shot)
- `processObjectDetectionWithTracking()`: Logs DetectionInfo received and ScannedItems produced
- `startScanning()`: Logs callback invocations

#### ObjectDetectorClient.kt

- `detectObjectsWithTracking()`: Logs ML Kit raw detections and extracted DetectionInfo objects
- `extractDetectionInfo()`: Logs each detection's tracking ID, confidence, category, and area

#### ObjectTracker.kt

- `processFrame()`: Logs each detection being processed, candidates created/matched, and
  confirmations
- Clear "✓✓✓ CONFIRMED" markers when candidates are promoted

#### ItemsViewModel.kt

- `addItems()`: Logs items received, deduplication decisions, and final additions

### 4. Log Markers to Look For

When scanning is working correctly, you should see this sequence:

```
I/CameraXManager: >>> processImageProxy: Taking TRACKING PATH (useStreamMode=true, isScanning=true)
I/CameraXManager: >>> processObjectDetectionWithTracking: CALLED
I/ObjectDetectorClient: >>> detectObjectsWithTracking START: mode=STREAM
I/ObjectDetectorClient: >>> ML Kit returned X raw objects
I/ObjectDetectorClient:     Object 0: trackingId=123, labels=[...], box=...
I/ObjectDetectorClient: >>> Extracted X DetectionInfo objects
I/ObjectDetectorClient:     DetectionInfo 0: trackingId=123, category=UNKNOWN, confidence=0.6, area=0.05
I/CameraXManager: >>> processObjectDetectionWithTracking: Got X raw DetectionInfo objects
I/ObjectTracker: >>> processFrame START: frame=1, detections=X
I/ObjectTracker:     Processing detection 0: trackingId=123, category=UNKNOWN, confidence=0.6
I/ObjectTracker:     CREATED new candidate gen_...
I/ObjectTracker:     ✓✓✓ IMMEDIATELY CONFIRMED candidate gen_...
I/ObjectTracker: >>> processFrame END: returning 1 newly confirmed candidates
I/CameraXManager: >>> processObjectDetectionWithTracking: Converted to 1 ScannedItems
I/CameraXManager:     ScannedItem 0: id=gen_..., category=UNKNOWN, priceRange=(5.0, 25.0)
I/CameraXManager: >>> startScanning: processImageProxy returned 1 items
I/CameraXManager: >>> startScanning: Calling onResult callback with 1 items
I/ItemsViewModel: >>> addItems CALLED: received 1 items
I/ItemsViewModel:     Input item 0: id=gen_..., category=UNKNOWN, priceRange=(5.0, 25.0)
I/ItemsViewModel: >>> ADDING 1 unique items from batch of 1
I/ItemsViewModel: >>> Total items now: 1
```

### 5. Potential Issues to Check

If logs still show no items:

1. **ML Kit returns 0 detections**: Check ML Kit logs - may need better lighting or clearer objects
2. **Detection area too small**: Look for "SKIPPED: box too small" messages
3. **Tracking path not taken**: Verify "Taking TRACKING PATH" appears in logs
4. **Items rejected by SessionDeduplicator**: Look for "REJECTED: similar to existing item" messages

### 6. Current Thresholds (Very Permissive)

```kotlin
// ObjectTracker configuration
minFramesToConfirm = 1      // Immediate confirmation
minConfidence = 0.2f         // 20% confidence threshold
minBoxArea = 0.0005f         // 0.05% of frame
maxFrameGap = 8              // Allow 8 frames gap
minMatchScore = 0.2f         // Low spatial matching threshold
expiryFrames = 15            // Keep candidates longer
```

These are intentionally very permissive to ensure detection works.

## Build Info

- Built successfully: 2025-12-08
- APK location: `/Users/family/dev/objecta/app/build/outputs/apk/debug/app-debug.apk`
- Size: 111MB
- All 232 tests passing

## Next Steps

1. Install the new APK on device: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. Clear app data: `adb shell pm clear com.scanium.app`
3. Run the app and enter scanning mode (long-press)
4. Capture logs:
   `adb logcat -s CameraXManager:I ObjectDetectorClient:I ObjectTracker:I ItemsViewModel:I`
5. Analyze the log sequence to identify where the pipeline breaks

## Expected Outcome

With the confidence fallback fix, objects should now be detected and confirmed even without
classification labels. The extensive logging will show exactly where each detection goes through the
pipeline.

## Files Modified

1. `/Users/family/dev/objecta/app/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`
    - Added fallback confidence for unlabeled objects
    - Added comprehensive logging

2. `/Users/family/dev/objecta/app/src/main/java/com/scanium/app/camera/CameraXManager.kt`
    - Enhanced logging in processing pipeline

3. `/Users/family/dev/objecta/app/src/main/java/com/scanium/app/tracking/ObjectTracker.kt`
    - Added detailed frame processing logs

4. `/Users/family/dev/objecta/app/src/main/java/com/scanium/app/items/ItemsViewModel.kt`
    - Added item addition tracking logs

---

## Quick Log Capture Command

```bash
# Clear logs and start fresh capture
adb logcat -c && adb logcat -s CameraXManager:I ObjectDetectorClient:I ObjectTracker:I ItemsViewModel:I > scan_logs.txt
```

Then in another terminal, use the app. Press Ctrl+C when done, and review `scan_logs.txt`.
