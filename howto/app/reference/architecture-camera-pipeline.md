# Camera Pipeline Architecture Notes

## Purpose

Document the camera scanning pipeline boundaries, responsibilities, and invariants so changes to
capture, analysis, and scanning remain safe.

## Scope

- Android camera pipeline centered on
  `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`.
- UI entry via `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt`.

## High-level flow

```
CameraScreen
  -> CameraXManager (camera session + analysis wiring)
    -> ImageAnalysis analyzer
      -> processImageProxy
        -> image conversion (YUV/NV21/JPEG/Bitmap)
        -> detection routing
          -> ML / detection pipeline
            -> ItemsViewModel / ItemsStateManager
              -> aggregation + persistence
```

## Responsibilities (current)

- CameraScreen
    - Permission gating, lifecycle hooks, UI state wiring.
    - Starts/stops camera on lifecycle changes.

- CameraXManager
    - CameraX binding and session management.
    - Analyzer lifecycle + backpressure strategy.
    - Image conversion and preprocessing.
    - Detection routing and telemetry.
    - Diagnostics and watchdog behavior.

- ItemsViewModel / ItemsStateManager
    - Receives detections, aggregates items, persists item state.

## Known hot-path risks

- Per-frame allocations during YUV -> JPEG -> Bitmap conversion.
- Per-frame logging and telemetry spans.
- Analyzer work on default dispatchers can contend with other CPU work.

## Invariants to preserve

- Every `ImageProxy` must be closed exactly once.
- Analyzer must not block CameraX threads for long periods.
- Backpressure strategy remains `STRATEGY_KEEP_ONLY_LATEST` unless revalidated.
- Frame processing must tolerate permission revokes and lifecycle pauses.

## Safe change checklist

- Verify `ImageProxy` close logic under success, error, and cancellation paths.
- Ensure analyzer attaches/detaches correctly on lifecycle changes.
- Run instrumentation smoke test for analyzer attach/detach (if available).
- Validate that new telemetry or logging is sampled or aggregated.

## Suggested boundary split (target)

- CameraSessionController: CameraX binding + lifecycle.
- FrameAnalyzer: analyzer scheduling + backpressure.
- ImageConverter: reusable buffers and conversion pipeline.
- ScanDiagnostics: watchdog + telemetry sampling.
