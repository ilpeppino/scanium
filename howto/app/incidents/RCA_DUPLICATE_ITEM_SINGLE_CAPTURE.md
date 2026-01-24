# RCA: Duplicate Item Photo After Single Capture

**Severity**: HIGH
**Date**: 2026-01-24
**Component**: CameraXManager.captureSingleFrame()

## Symptoms

When the user taps the shutter button to capture a single photo, the same item appears
**twice** in the items list with nearly identical thumbnails.

## Root Cause

**Race condition between ImageProxy closure and analyzer cleanup in `captureSingleFrame()`.**

CameraX is configured with `STRATEGY_KEEP_ONLY_LATEST` (line 516), which delivers the next
buffered frame as soon as the previous `imageProxy` is closed. In `captureSingleFrame()`:

1. An image analyzer is set (line 669)
2. When a frame arrives, a coroutine is launched on `detectionScope` (Dispatchers.Default, multi-threaded)
3. `processImageProxy()` closes the imageProxy in its own `finally` block (CameraFrameAnalyzer.kt:372)
4. CameraX immediately delivers the next buffered frame (analyzer still active, backpressure released)
5. The analyzer fires again, launching a **second coroutine**
6. The first coroutine's `finally` block clears the analyzer (line 723) — **too late**

Both coroutines call `onResult()` with their respective detections, causing `onDetectionReady()`
to fire twice for the same object (from two consecutive frames).

## Execution Timeline

```
Camera Thread:  [Frame1 → Analyzer] ── wait ── [Frame2 → Analyzer]
                       │                               │
Coroutine 1:           └─ processImageProxy() ─→ imageProxy1.close() ─→ withContext(Main){onResult()} ─→ finally{clearAnalyzer}
                                                        │
Coroutine 2:                                            └─ processImageProxy() ─→ onResult()
```

The critical window is between `imageProxy.close()` (CameraFrameAnalyzer.kt:372) and
`imageAnalysis?.clearAnalyzer()` (CameraXManager.kt:723). During this window:

- The analyzer is still registered
- The imageProxy backpressure signal was released
- Dispatchers.Default allows both coroutines to run concurrently

## Affected Code Path

```
CameraScreen.kt:916    captureSingleFrame()
  → CameraXManager.kt:669    setAnalyzer callback
    → CameraXManager.kt:686    detectionScope.launch (race: fires twice)
      → CameraFrameAnalyzer.kt:277    processImageProxy()
        → CameraFrameAnalyzer.kt:372    imageProxy.close() (releases backpressure)
      → CameraXManager.kt:711    withContext(Main) { onResult(items) }
        → CameraScreen.kt:932    rawDetections.forEach { onDetectionReady(it) }
          → ItemsViewModel.kt:849    onDetectionReady() (called once per frame)
            → ItemsViewModel.kt:1100    createItemFromDetection() (unique UUID each time)
              → ItemsUiFacade.kt:219    addItem() → appears in list
```

## Why Photos Look Identical

Frames 1 and 2 are captured within milliseconds from the same camera position. ML Kit detects
the same object in both frames with similar bounding boxes, producing nearly identical WYSIWYG
thumbnails.

## Fix

Added an `AtomicBoolean` guard (`singleFrameCaptured`) in the analyzer callback to ensure only
the first frame is processed. The guard is set atomically via `compareAndSet` before launching
the processing coroutine, preventing any subsequent frames from being processed.

## Prevention

- Single-shot capture should always use a one-shot guard pattern
- `imageAnalysis?.clearAnalyzer()` alone is insufficient because it races with CameraX frame delivery
- The `STRATEGY_KEEP_ONLY_LATEST` backpressure model means frame delivery is instant once the proxy is freed
