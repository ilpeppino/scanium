# Bounding Box Lifecycle Root Cause Analysis

## Problem Statement

Bounding boxes (bboxes) freeze or disappear when:
1. Navigating away from CameraScreen and returning (back gesture/button)
2. Backgrounding the app and returning (home button, lock screen)
3. Switching to Recents and back

The **only** action that reliably "revives" bboxes is opening Recents and selecting Scanium again.

## Root Cause Analysis

### 1. Bbox State Production Chain

```
ML Kit Detection
    ↓ DetectionResult list
CameraXManager.startPreviewDetection() / startScanning()
    ↓ ImageAnalysis.Analyzer callback (cameraExecutor thread)
    ↓ detectionScope coroutine
CameraScreen
    ↓ onDetectionResult callback (Main thread)
ItemsViewModel.updateOverlayDetections()
    ↓
OverlayTrackManager._overlayTracks (StateFlow)
    ↓
CameraScreen (collectAsState)
    ↓
DetectionOverlay composable
```

**Files involved:**
- `CameraXManager.kt:786-882` - startPreviewDetection/stopPreviewDetection
- `CameraXManager.kt:442-657` - startScanning/stopScanning
- `CameraScreen.kt:355-376` - LaunchedEffect triggers startPreviewDetection
- `CameraScreen.kt:250-271` - Lifecycle observer (ON_RESUME/ON_PAUSE)
- `ItemsViewModel.kt:281-288` - updateOverlayDetections
- `OverlayTrackManager.kt:220` - _overlayTracks StateFlow update

### 2. Identified Issues

#### Issue A: CoroutineScope Not Recreated After Cancellation

**Location:** `CameraXManager.kt:162`
```kotlin
private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
```

**Problem:** The scope is created once in the constructor. When `stopScanning()` calls:
```kotlin
detectionScope.coroutineContext.cancelChildren()
```
...subsequent coroutine launches in `startPreviewDetection()` may silently fail because the SupervisorJob's children are cancelled.

**Why Recents fixes it:** Recents causes Activity recreation → new CameraXManager instance → new scope.

#### Issue B: isPreviewDetectionActive Race Condition

**Location:** `CameraXManager.kt:792-795`
```kotlin
if (isScanning || isPreviewDetectionActive) {
    return  // Guard prevents restart
}
```

**Problem:** After `stopPreviewDetection()` sets `isPreviewDetectionActive = false`, the subsequent `startPreviewDetection()` call (triggered by ON_RESUME) may arrive before:
1. The previous analyzer callback has fully completed
2. The coroutine scope is ready to accept new work

#### Issue C: No ProcessLifecycleOwner Guard

**Location:** None (missing)

**Problem:** The lifecycle observer in CameraScreen only observes the NavBackStackEntry lifecycle. When the app goes to background, the NavBackStackEntry may stay RESUMED (device-dependent), but the camera pipeline should still stop.

There's no ProcessLifecycleOwner observer to handle app-level background/foreground transitions.

#### Issue D: CameraXManager Created with `remember {}` Without Keys

**Location:** `CameraScreen.kt:138-141`
```kotlin
val cameraManager = remember {
    CameraXManager(context, lifecycleOwner, app?.telemetry)
}
```

**Problem:** The CameraXManager instance persists across lifecycle events (correct), but its internal state (detectionScope, isPreviewDetectionActive, etc.) may become stale after ON_PAUSE/ON_RESUME cycles.

### 3. Why the Current Fix Attempt (`lifecycleResumeCount`) Fails

The current implementation uses a counter:
```kotlin
var lifecycleResumeCount by remember { mutableStateOf(0) }

DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> lifecycleResumeCount++
            Lifecycle.Event.ON_PAUSE -> {
                cameraManager.stopPreviewDetection()
                itemsViewModel.updateOverlayDetections(emptyList())
            }
        }
    }
    ...
}

LaunchedEffect(modelDownloadState, cameraState, isCameraBinding, lifecycleResumeCount) {
    if (...) {
        cameraManager.startPreviewDetection(...)
    }
}
```

**Why it fails:**
1. The `detectionScope` may have cancelled children that prevent new coroutine launches
2. The LaunchedEffect recomposes, but `startPreviewDetection` returns early due to stale `isPreviewDetectionActive` state
3. No delay between `stopPreviewDetection` and `startPreviewDetection` to allow cleanup

## Solution Design

### 1. Create Session-Based Camera Controller

Instead of relying on boolean flags, use a session-based approach where each "session" has a unique key. Old sessions are invalidated when a new session starts.

```kotlin
private var sessionId = AtomicInteger(0)

fun startSession(): Int {
    val newId = sessionId.incrementAndGet()
    // Cancel old session, create new scope
    recreateDetectionScope()
    return newId
}

fun isSessionValid(id: Int): Boolean = id == sessionId.get()
```

### 2. Recreate CoroutineScope on Each Session Start

```kotlin
private fun recreateDetectionScope() {
    detectionScope.cancel() // Cancel all work
    detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
}
```

### 3. Add ProcessLifecycleOwner Guard

In CameraScreen, observe ProcessLifecycleOwner to handle app background/foreground:

```kotlin
val processLifecycleOwner = ProcessLifecycleOwner.get()

DisposableEffect(processLifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> cameraManager.stopSession()
            Lifecycle.Event.ON_START -> {
                if (isCameraScreenActive) cameraManager.startSession()
            }
        }
    }
    processLifecycleOwner.lifecycle.addObserver(observer)
    onDispose { processLifecycleOwner.lifecycle.removeObserver(observer) }
}
```

### 4. Synchronize Stop/Start with Proper Ordering

Ensure `stopPreviewDetection()` fully completes before `startPreviewDetection()` is called:
- Use a suspend function for stop that awaits cleanup
- Or use a session ID to invalidate old callbacks immediately

### 5. Clear Bbox State on Session Stop

```kotlin
fun stopSession() {
    isPreviewDetectionActive = false
    isScanning = false
    detectionScope.cancel()
    imageAnalysis?.clearAnalyzer()
    // Notify UI to clear bbox state
    _bboxesCleared.emit(Unit)
}
```

## Implementation Files to Modify

1. `CameraXManager.kt` - Add session management, scope recreation
2. `CameraScreen.kt` - Add ProcessLifecycleOwner observer, session-based triggers
3. `SettingsRepository.kt` - Add debug toggle for camera pipeline diagnostics
4. Create `CameraPipelineDebugOverlay.kt` - Debug overlay showing pipeline state
5. Update developer options to include the new toggle

## Validation Criteria

After fix, all these scenarios MUST show live bboxes within 200-500ms:

| Scenario | Expected Result |
|----------|-----------------|
| Camera → Items → Back | Bboxes live |
| Camera → Settings → Back | Bboxes live |
| Home button → Return | Bboxes live |
| Lock → Unlock | Bboxes live |
| Recents → Switch → Return | Bboxes live |
| Portrait → Landscape → Portrait | Bboxes live, correct mapping |

Debug overlay should show:
- `isCameraBound: true`
- `isAnalysisRunning: true`
- `lastFrameTimestamp: <updating>`
- `lastBboxTimestamp: <updating>`

---

## Implementation Summary (Completed)

### Files Created:
- `CameraSessionController.kt` - Session-based lifecycle management with diagnostics
- `CameraPipelineDebugOverlay.kt` - Debug overlay composable for diagnostics

### Files Modified:
- `CameraXManager.kt`:
  - Added `CameraSessionController` integration
  - Added `startCameraSession()` / `stopCameraSession()` methods
  - Added automatic scope recreation when cancelled
  - Added diagnostics updates (frame timestamp, bbox timestamp, FPS)

- `CameraScreen.kt`:
  - Updated lifecycle observer to use session management
  - Added `ProcessLifecycleOwner` observer for app background/foreground
  - Added camera pipeline debug overlay

- `SettingsRepository.kt`:
  - Added `devCameraPipelineDebugEnabledFlow` setting

- `DeveloperOptionsScreen.kt`:
  - Added "Camera Pipeline Debug" toggle

- `DeveloperOptionsViewModel.kt`:
  - Added `cameraPipelineDebugEnabled` StateFlow and setter

### Key Fixes:
1. **Scope Recreation**: `detectionScope` is now recreated on each session start, preventing cancelled coroutines from blocking new launches
2. **Session-based Invalidation**: Old callbacks are ignored via session ID validation
3. **ProcessLifecycleOwner Guard**: Handles app-level background/foreground independently of navigation
4. **Immediate Overlay Clear**: Bbox state is cleared on session stop to prevent stale rendering
5. **Debug Diagnostics**: CAM_LIFE log tag and visual overlay for lifecycle debugging
