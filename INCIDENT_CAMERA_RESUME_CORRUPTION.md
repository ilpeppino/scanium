***REMOVED*** Incident: Camera Image Corruption on Background/Resume

**Severity**: RELEASE-BLOCKING
**Date**: 2026-01-15
**Branch**: refactoring
**Baseline Commit**: 43cbfcd (docs: add AI language propagation troubleshooting report)

***REMOVED******REMOVED*** Symptoms

After app background/resume cycle:
1. **Image Overwrite**: New captures show filenames/content of previous captures
2. **Zoomed Fragments**: New captures show cropped/zoomed-in sections of old frames (e.g., "dog zoom" instead of full item)
3. **Label Mismatch**: Thumbnail labels don't match image content (e.g., "Adidas" label on wrong item thumbnail)
4. **Item Association**: Scanned items show wrong image from previous batch

***REMOVED******REMOVED*** Root Cause Categories (to be determined)

- [ ] FILE OVERWRITE: Same output path reused for multiple captures
- [ ] STALE BUFFER: ImageProxy/frame buffers not released, reused after resume
- [ ] CROP REUSE: Crop rect or viewport state persists incorrectly
- [ ] UI KEYING: LazyColumn items keyed by index instead of stable ID

***REMOVED******REMOVED*** Reproduction Steps

**Setup**:
- Install dev debug build
- Device: [TBD]
- OS: [TBD]

**Steps**:
1. Launch Scanium app, go to Camera screen
2. Capture **Batch A**: Scan 3-5 items (e.g., dog, chair, shoe) with clear distinct objects
3. Record file paths and file hashes from logs
4. Press HOME or back button (app enters background/onPause/onStop)
5. Wait 5-10 seconds
6. Reopen Scanium app (app resumes/onResume/onStart)
7. Go to Camera screen
8. Capture **Batch B**: Scan 3-5 new items with different objects
9. **Expected**: Batch B items show new distinct objects
10. **Actual**: Batch B items show cropped versions of Batch A objects or mismatched labels

***REMOVED******REMOVED*** Evidence Collection

***REMOVED******REMOVED******REMOVED*** Phase 2: Instrumentation Logs

(To be populated after instrumentation implementation)

***REMOVED******REMOVED******REMOVED******REMOVED*** A) Capture File Save Logs
```
[TEMP_CAM_BUG_DEBUG] CAPTURE_SAVED: captureId=<uuid>, itemId=<id>, path=/files/..., hash=<first16bytes>, size=<bytes>, callSite=<func>
```

***REMOVED******REMOVED******REMOVED******REMOVED*** B) Thumbnail Generation Logs
```
[TEMP_CAM_BUG_DEBUG] THUMBNAIL_ASSIGNED: itemId=<id>, captureId=<uuid>, source=<path>, dims=<w>x<h>
```

***REMOVED******REMOVED******REMOVED******REMOVED*** C) Frame/Analyzer Logs
```
[TEMP_CAM_BUG_DEBUG] FRAME_PROCESSED: frameCounter=<n>, wxh=<w>x<h>, rotation=<deg>, crop=<l,t,r,b>, deepCopy=<bool>, proxy_close=<bool>
```

***REMOVED******REMOVED******REMOVED******REMOVED*** D) Lifecycle Logs
```
[TEMP_CAM_BUG_DEBUG] CAMERA_LIFECYCLE: event=background|resume|unbind|bind, timestamp=<ms>
```

***REMOVED******REMOVED*** System Architecture Map

***REMOVED******REMOVED******REMOVED*** Capture → Storage → Thumbnail → Item Model → UI List

```
CameraXManager.startCamera() (420-602)
  ↓ ImageCapture + ImageAnalysis bound to lifecycle
CameraScreen LifecycleEventObserver (298-326)
  ↓ ON_RESUME → startCameraSession() | ON_PAUSE → stopCameraSession()
CameraFrameAnalyzer.onImageAvailable (624, 766)
  ↓ Receives ImageProxy, computes motion score, throttles
CameraImageConverter (24-81)
  ↓ YUV_420_888 → NV21 → JPEG → Bitmap (deep copy)
ObjectDetectorClient.detectObjects() (60-131)
  ↓ InputImage → ML Kit detector → DetectedObjects
DetectionMapping.isDetectionInsideSafeZone() (55-94)
  ↓ Filters by cropRect + edge margin
DetectionMapping.cropThumbnail() (411-479)
  ↓ Sensor bbox → crop canvas → rotate → JPEG encode
DetectionMapping.convertToScannedItem() (302-374)
  ↓ DetectedObject → ScannedItem (stable UUID id + thumbnail)
ObjectTracker (82-250+)
  ↓ Multi-frame stability confirmation
ScannedItemRepository.upsertAll() (37-119)
  ↓ Room DB persist + history
CameraXManager.captureHighResImage() (1382-1426)
  ↓ Filename: "SCANIUM_" + yyyyMMdd_HHmmss → /context.cacheDir/
ItemsListContent.LazyColumn items() (78-81)
  ↓ Renders with key = { it.id } (stable UUID, NOT index)
```

***REMOVED******REMOVED*** Phase 1 Findings

***REMOVED******REMOVED******REMOVED*** Critical Pipeline Components

***REMOVED******REMOVED******REMOVED******REMOVED*** Camera Capture & Analyzer
- **CameraXManager.kt:420-602**: CameraX setup with ImageCapture + ImageAnalysis bound
- **CameraFrameAnalyzer.kt:65-128**: Motion-based throttling (400-600ms intervals)
- **CameraImageConverter.kt:24-81**: YUV_420_888 conversion (must deep-copy bytes)
- **CameraXManager.kt:1047, 1288**: Session ID capture for validating stale callbacks

***REMOVED******REMOVED******REMOVED******REMOVED*** File Persistence
- **CameraXManager.kt:1392-1396**: Filename = `"SCANIUM_" + SimpleDateFormat("yyyyMMdd_HHmmss")`
  - **⚠️ CRITICAL**: Two captures in same second = same filename → overwrite!
- **CameraXManager.kt:1395**: Storage = `context.cacheDir` (OK for dev, but no collision protection)
- **StorageHelper.kt:43-44**: SAF storage also uses timestamp pattern

***REMOVED******REMOVED******REMOVED******REMOVED*** Item Association & Persistence
- **ScannedItem.kt**: Has stable UUID `id` field
- **DetectionMapping.kt:302-374**: Creates ScannedItem with UUID
- **ScannedItemRepository.kt:37-119**: Persists to Room DB (reliable)
- **LazyColumn.kt:80**: Uses `key = { it.id }` (stable keying ✓)

***REMOVED******REMOVED******REMOVED******REMOVED*** ML Detection & Cropping
- **CameraXManager.kt:1704-1733**: `calculateVisibleViewport()` - crops for aspect ratio
- **DetectionMapping.kt:111-158**: `uprightBboxToSensorBbox()` - coordinate transforms
- **DetectionMapping.kt:55-94**: `isDetectionInsideSafeZone()` - edge margin filtering

***REMOVED******REMOVED******REMOVED******REMOVED*** Lifecycle Management
- **CameraScreen.kt:298-326**: LifecycleEventObserver monitors ON_RESUME/ON_PAUSE
- **CameraScreen.kt:330-357**: ProcessLifecycleOwner app-level background/foreground tracking
- **CameraXManager.kt:263-287**: `startCameraSession()` - creates new session ID + detectionScope
- **CameraXManager.kt:295-329**: `stopCameraSession()` - cancels scope, clears analyzer
- **CameraXManager.kt:1196-1246**: No-frames watchdog + analyzer rebind recovery

***REMOVED******REMOVED******REMOVED*** Likely Root Cause Candidates

1. **FILE OVERWRITE** ⚠️
   - SimpleDateFormat("yyyyMMdd_HHmmss") has 1-second resolution
   - Two rapid captures in same second = same filename
   - Second capture overwrites first
   - Evidence: Same path/size for different images

2. **STALE ANALYZER BUFFER**
   - ImageProxy buffers may not be deep-copied in all code paths
   - On resume, analyzer re-created but previous frame data still referenced
   - Evidence: frameCounter repeats, identical bitmap bytes for different captures

3. **CROP RECT PERSISTENCE**
   - Viewport/cropRect calculated for Batch A, not reset on resume
   - Batch B captures crop-bounded to old viewport
   - Evidence: Batch B shows zoomed/cropped fragments of frame

4. **SESSION ID MISMATCH**
   - Stale analyzer callbacks from Batch A processed during Batch B
   - `isCurrentSessionValid()` check missing in some code paths
   - Evidence: captureIds from both batches mixed

***REMOVED******REMOVED*** RCA Summary

(To be populated after Phase 3)

***REMOVED******REMOVED*** Fixes Applied

(To be populated after Phase 4)

***REMOVED******REMOVED*** Tests Added

(To be populated after Phase 5)

***REMOVED******REMOVED*** Validation Checklist

- [ ] Baseline build: PASS
- [ ] Phase 1 complete: All pipeline components mapped
- [ ] Phase 2 instrumentation: Temp logs in place
- [ ] Reproduction confirmed: Bug reproducible with logs
- [ ] RCA complete: Root cause identified
- [ ] Fixes implemented: Code changes applied
- [ ] Tests passing: Unit tests for regression guards
- [ ] Temp logs removed: Debug flags removed from codebase
- [ ] Final build: PASS
- [ ] Manual verification: Tested on device (Batch A → bg → resume → Batch B)

