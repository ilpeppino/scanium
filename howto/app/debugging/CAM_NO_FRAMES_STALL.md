***REMOVED*** Camera NO_FRAMES Stall Bug - Root Cause & Fix

***REMOVED******REMOVED*** Problem Statement

After returning to CameraScreen from Items List (or other screens), the ImageAnalysis callback does
NOT receive frames even though the debug overlay shows:

- CameraBound: true
- PreviewDetection: true
- AnalysisRunning: true
- LastFrame: **never**
- FPS: **0.0**
- Lifecycle: ON_RESUME

The analyzer callback was not receiving frames despite flags indicating the pipeline was active.

***REMOVED******REMOVED*** Root Cause

**Race Condition in LaunchedEffects during navigation return.**

When returning to CameraScreen from another screen:

1. Composition starts
2. `DisposableEffect(lifecycleOwner)` adds observer, `ON_RESUME` is delivered immediately
3. `LaunchedEffect(modelDownloadState, cameraState, isCameraBinding, lifecycleResumeCount)` is
   scheduled
4. `CameraPreview` is composed, creating a NEW `PreviewView`
5. `LaunchedEffect(previewView, ...)` is scheduled

**The critical ordering issue:**

- `LaunchedEffect(modelDownloadState, ...)` runs FIRST with `isCameraBinding=false` (initial state)
- It calls `startPreviewDetection()` which sets analyzer on the **OLD** `imageAnalysis` instance
- THEN `LaunchedEffect(previewView, ...)` runs and calls `startCamera()`
- `startCamera()` creates a **NEW** `imageAnalysis` instance without any analyzer!

The analyzer was set on a stale `ImageAnalysis` instance that was replaced by the camera binding
process.

***REMOVED******REMOVED*** Solution

***REMOVED******REMOVED******REMOVED*** 1. Deferred Analyzer Application

Modified `startPreviewDetection()` to detect when called before camera binding:

```kotlin
if (imageAnalysis == null) {
    // Store callback to be applied AFTER camera binding completes
    pendingPreviewDetectionCallback = PreviewDetectionCallback(...)
    return
}
```

In `startCamera()`, after binding completes:

```kotlin
pendingPreviewDetectionCallback?.let { callback ->
    applyPreviewDetectionAnalyzer(callback.onDetectionResult, ...)
    pendingPreviewDetectionCallback = null
}
```

***REMOVED******REMOVED******REMOVED*** 2. Truthful Diagnostic States

Changed from single `isAnalysisRunning` to two states:

- `analysisAttached`: We called `setAnalyzer()` on ImageAnalysis
- `analysisFlowing`: We have received at least 1 frame in current session

This makes the debug overlay show the real state:

- `AnalysisAttached: true` + `AnalysisFlowing: false` = Analyzer set but no frames (STALL)
- `AnalysisAttached: true` + `AnalysisFlowing: true` = Working correctly

***REMOVED******REMOVED******REMOVED*** 3. NO_FRAMES Watchdog (Self-Heal)

Added a watchdog coroutine that starts after camera binding:

```kotlin
private fun startNoFramesWatchdog() {
    watchdogJob = detectionScope.launch {
        delay(600ms)  // Initial delay

        if (!hasReceivedFirstFrame && isCameraBound && isAnalysisAttached) {
            // STALL DETECTED!
            stallReason = StallReason.NO_FRAMES

            // Attempt recovery
            for (attempt in 1..2) {
                rebindAnalysisPipeline()
                delay(800ms)
                if (hasReceivedFirstFrame) break
            }
        }
    }
}
```

The watchdog detects the "attached but not flowing" condition and attempts recovery.

***REMOVED******REMOVED******REMOVED*** 4. Session-based Callback Validation

Each analyzer callback now validates it belongs to the current session:

```kotlin
val sessionIdAtSetup = sessionController.getCurrentSessionId()
analysis.setAnalyzer(executor) { imageProxy ->
    if (!sessionController.isSessionValid(sessionIdAtSetup)) {
        imageProxy.close()
        return@setAnalyzer
    }
    // ... process frame
}
```

This ensures stale callbacks from previous sessions are ignored.

***REMOVED******REMOVED*** Files Changed

- `CameraXManager.kt`
    - Added `pendingPreviewDetectionCallback` for deferred analyzer application
    - Added `hasReceivedFirstFrame` tracking
    - Added `startNoFramesWatchdog()` and `rebindAnalysisPipeline()`
    - Modified `startPreviewDetection()` to handle race condition
    - Added session validation to analyzer callbacks

- `CameraSessionController.kt`
    - Added `StallReason` enum
    - Added `analysisAttached` and `analysisFlowing` diagnostics
    - Added `recoveryAttempts` tracking

- `CameraPipelineDebugOverlay.kt`
    - Updated to show new diagnostic states
    - Added StallReason display

***REMOVED******REMOVED*** Debug Overlay States

The debug overlay now shows:

| State                        | Meaning                                    |
|------------------------------|--------------------------------------------|
| `Status: OK`                 | Pipeline working correctly                 |
| `Status: WAITING FOR FRAMES` | Analyzer attached, waiting for first frame |
| `Status: STALL_NO_FRAMES`    | Detected stall, watchdog triggered         |
| `Status: RECOVERING`         | Watchdog attempting recovery               |
| `Status: STALL_FAILED`       | Recovery failed after max attempts         |

***REMOVED******REMOVED*** Validation

The fix should pass these scenarios:

1. **Camera -> Items -> Back**: Frames flow within 500ms
2. **Camera -> Settings -> Back**: Frames flow within 500ms
3. **Home -> return**: Frames resume on app foreground
4. **Lock -> unlock**: Frames resume after device unlock

Enable "Camera Pipeline Debug" in Developer Settings to see the diagnostic overlay.

***REMOVED******REMOVED*** Prevention

To prevent similar issues:

1. Never set analyzer before camera binding completes
2. Use session IDs to validate callbacks
3. Monitor for "attached but not flowing" condition
4. Implement self-healing watchdogs for critical pipelines
