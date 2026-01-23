# Live Scanning vs Picture Capture Assessment

## Issue Summary

**Problem**: Live scanning sometimes produces NO detection/status (e.g., phone/toy while camera is
steady), while "take picture" on the same scene works and correctly adds/classifies items.

**Status**: Root cause IDENTIFIED - Motion-based throttling too aggressive

## Phase 0: Reproduction

### Exact Repro Steps

1. **Device**: Any Android device with camera (tested on typical setup)
2. **Lighting**: Normal indoor lighting
3. **Distance**: 30-60cm from object
4. **Resolution Setting**: Default (1280x720 analysis, higher for capture)
5. **Mode**: OBJECT_DETECTION
6. **Cloud/On-device**: Both affected (issue is pre-detection)

### Reproduction Scenario

1. Open camera screen
2. Long-press shutter to start continuous scanning
3. Point camera at a stationary object (phone, toy, etc.)
4. **Hold camera STEADY** - keep it still
5. Observe: No detection overlays appear, no items added
6. Wait 5+ seconds: Maybe one detection appears
7. Tap shutter to take picture of same scene
8. Observe: Picture immediately detects and classifies the object

### Expected vs Actual

| Scenario                 | Expected               | Actual                      |
|--------------------------|------------------------|-----------------------------|
| Steady camera, live scan | Detection within 500ms | No detection for 2+ seconds |
| Steady camera, picture   | Detection immediately  | Works correctly             |
| Moving camera, live scan | Detection within 500ms | Works (motion > 0.1)        |

## Phase 1: Pipeline Mapping

### Pipeline A: Picture Capture (`captureSingleFrame`)

```
CameraScreen → captureSingleFrame() → ImageAnalysis.setAnalyzer()
                                           │
                                           ▼
                                    [NO THROTTLE CHECK]
                                           │
                                           ▼
                                   processImageProxy()
                                           │
                                           ▼
                              objectDetector.detectObjects()
                                           │
                                           ▼
                                   ScannedItem returned
                                           │
                                           ▼
                                 clearAnalyzer() immediately
```

**Key characteristics**:

- Runs on NEXT available frame (no waiting)
- No motion detection
- No throttling
- Direct detection path (no tracking)
- Uses `useStreamMode = false` (SINGLE_IMAGE_MODE)

### Pipeline B: Live Scanning (`startScanning`)

```
CameraScreen → startScanning() → ImageAnalysis.setAnalyzer()
                                           │
                                           ▼
                              ┌─── computeMotionScore() ───┐
                              │                             │
                              ▼                             │
                      analysisIntervalMsForMotion()         │
                              │                             │
                      ┌───────┴───────┐                     │
                      ▼               ▼                     │
                motion≤0.1       motion>0.5                 │
                2000ms!!          400ms                     │
                              │                             │
                              ▼                             │
           if (currentTime - lastAnalysisTime >= interval)  │
                              │                             │
                      ┌───────┴───────┐                     │
                      ▼               ▼                     │
                    YES              NO ──────────────────┐ │
                    (process)        (DROP FRAME)         │ │
                              │                           │ │
                              ▼                           │ │
                      processImageProxy()                 │ │
                              │                           │ │
                              ▼                           │ │
              detectObjectsWithTracking()                 │ │
                              │                           │ │
                              ▼                           │ │
                    ObjectTracker.processFrame()          │ │
                              │                           │ │
                              ▼                           │ │
                      ScannedItem if confirmed            │ │
                              │                           │ │
                              ▼                           │ │
                      imageProxy.close()◄─────────────────┘ │
                                                            │
                      [Loop continues with next frame]◄─────┘
```

**Key characteristics**:

- Motion-based throttling determines interval
- **CRITICAL BUG**: When camera is steady (motion ≤ 0.1), interval is 2000ms!
- Uses tracking pipeline with candidate confirmation
- Uses `useStreamMode = true` passed to detector, BUT detector uses SINGLE_IMAGE_MODE anyway

### Diff Table

| Aspect                | Picture Capture                    | Live Scanning            | Issue?   |
|-----------------------|------------------------------------|--------------------------|----------|
| **Input resolution**  | 1280x720                           | 1280x720                 | No       |
| **Rotation handling** | Same (`imageInfo.rotationDegrees`) | Same                     | No       |
| **Crop strategy**     | `calculateVisibleViewport()`       | Same                     | No       |
| **Model threshold**   | CONFIDENCE_THRESHOLD=0.3f          | Same                     | No       |
| **Frame selection**   | First available                    | Motion-gated             | **YES!** |
| **Throttle interval** | None                               | 400-2000ms               | **YES!** |
| **Motion gating**     | No                                 | Yes (0.1-0.5 thresholds) | **YES!** |
| **Backpressure**      | KEEP_ONLY_LATEST                   | KEEP_ONLY_LATEST         | No       |
| **Detector mode**     | SINGLE_IMAGE_MODE                  | SINGLE_IMAGE_MODE        | No       |
| **Tracking**          | No (direct)                        | Yes (ObjectTracker)      | Minor    |

## Phase 2: Root Cause Analysis

### CONFIRMED ROOT CAUSE: Aggressive Motion-Based Throttling

**Location**: `CameraXManager.kt:585-589`

```kotlin
private fun analysisIntervalMsForMotion(motionScore: Double): Long = when {
    motionScore <= 0.1 -> 2000L  // ← BUG: 2 SECOND DELAY!
    motionScore <= 0.5 -> 800L
    else -> 400L
}
```

**Why this is the bug**:

1. When the camera is steady (user holding phone still to scan an object):
    - Luma difference between frames is minimal
    - `motionScore` drops to ≤ 0.1
    - Analysis interval becomes **2000ms (2 seconds!)**

2. With 2-second intervals:
    - User has to wait 2 seconds for first detection
    - If user moves slightly during that time, motion might spike briefly
    - The experience feels like "detection is not working"

3. Picture capture has NO throttling:
    - Runs immediately on next frame
    - That's why it "works" and live scanning "doesn't"

### Evidence in Code

From `CameraXManager.kt:480-481`:

```kotlin
// Only process if enough time has passed AND we're not already processing
if (currentTime - lastAnalysisTime >= analysisIntervalMs && !isProcessing) {
```

When `analysisIntervalMs = 2000`, frames are dropped for 2 seconds!

### Hypothesis Verification

| Hypothesis                | Status          | Evidence                          |
|---------------------------|-----------------|-----------------------------------|
| H1: Analyzer not attached | ❌ DISPROVED     | Logs show frames arriving         |
| H2: Over-throttling       | ✅ **CONFIRMED** | 2000ms interval when steady       |
| H3: Resolution mismatch   | ❌ DISPROVED     | Same 1280x720                     |
| H4: Rotation mismatch     | ❌ DISPROVED     | Same handling                     |
| H5: Crop mismatch         | ❌ DISPROVED     | Same `calculateVisibleViewport()` |
| H6: Confidence threshold  | ❌ DISPROVED     | Same 0.3f threshold               |
| H7: Backpressure drops    | ❌ N/A           | Using KEEP_ONLY_LATEST correctly  |
| H8: Status UI issue       | ❌ DISPROVED     | Detection itself not running      |

## Phase 3: Diagnostics Added

See Phase 2 implementation for diagnostic code:

1. **ScanPipeline logs**: Structured logging at each pipeline stage
2. **Motion metrics**: Log motion score and resulting interval
3. **Frame drop counter**: Track how many frames are being dropped
4. **Developer overlay** (optional): Show fps, inference rate, motion score

## Phase 4: The Fix

### Minimal Change

Reduce the max throttle interval for steady scenes from 2000ms to 600ms:

```kotlin
private fun analysisIntervalMsForMotion(motionScore: Double): Long = when {
    motionScore <= 0.1 -> 600L   // Changed from 2000L - still battery-friendly
    motionScore <= 0.5 -> 500L   // Changed from 800L
    else -> 400L                 // Unchanged
}
```

**Rationale**:

- 600ms = ~1.7 detections/second for steady scenes (reasonable)
- Still provides battery savings vs running at max rate
- User experience: detection within 600ms instead of 2000ms
- Falls back to faster rates when motion detected

### Alternative Considered: Time-since-last-detection boost

Could temporarily boost detection rate after no detections for N seconds, but this adds complexity.
The simple interval reduction is sufficient.

## Phase 5: Validation

### Manual Test Matrix

| Scenario              | Before Fix      | After Fix  | Status |
|-----------------------|-----------------|------------|--------|
| Steady camera + phone | 2+ second delay | <600ms     | ✅      |
| Steady camera + toy   | 2+ second delay | <600ms     | ✅      |
| Low light             | Slow            | Faster     | ✅      |
| Cluttered background  | Variable        | Consistent | ✅      |
| Different distances   | Variable        | Consistent | ✅      |

### Performance Validation

| Metric              | Before  | After           | Acceptable?  |
|---------------------|---------|-----------------|--------------|
| Steady-state FPS    | ~0.5    | ~1.7            | ✅ Yes        |
| CPU usage           | Low     | Slightly higher | ✅ Acceptable |
| Battery impact      | Minimal | Slight increase | ✅ Acceptable |
| Classification spam | N/A     | Rate limited    | ✅ OK         |

## Deliverables Checklist

- [x] docs/SCAN_VS_PICTURE_ASSESSMENT.md (this file)
- [x] Diagnostic instrumentation (debug-only)
    - `ScanPipelineDiagnostics.kt` - Structured logging for pipeline stages
    - Developer setting: `devScanningDiagnosticsEnabledFlow`
    - Integration in CameraXManager with `ScanPipeline` log tag
- [x] Minimal fix to motion throttling
    - Changed `CameraXManager.analysisIntervalMsForMotion`:
        - Steady (motion ≤ 0.1): 2000ms → 600ms
        - Low motion (motion ≤ 0.5): 800ms → 500ms
        - High motion: 400ms (unchanged)
- [x] Unit test for throttle logic
    - `MotionThrottleTest.kt` - Regression tests for interval values
- [x] Build and test validation (PR #319 merged)

## Code Changes Summary

### Files Modified

1. `CameraXManager.kt`
    - Fixed `analysisIntervalMsForMotion()` intervals
    - Added `ScanPipelineDiagnostics` integration
    - Added `setScanningDiagnosticsEnabled()` method
    - Added `scanMetricsState` StateFlow for optional overlay

2. `SettingsRepository.kt`
    - Added `devScanningDiagnosticsEnabledFlow` setting

3. `DeveloperOptionsViewModel.kt`
    - Added `scanningDiagnosticsEnabled` StateFlow
    - Added `setScanningDiagnosticsEnabled()` method

4. `CameraScreen.kt`
    - Added diagnostics setting integration

### Files Created

1. `ScanPipelineDiagnostics.kt`
    - Structured logging with `ScanPipeline` tag
    - Metrics collection (fps, inference rate, dropped frames, etc.)
    - Session start/stop with summary logging
    - `ScanMetrics` for optional overlay display

2. `MotionThrottleTest.kt`
    - Regression tests for motion throttle intervals
    - Ensures max interval ≤ 600ms for responsiveness
    - Ensures min interval ≥ 400ms for battery efficiency
