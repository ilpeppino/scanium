# Pipeline Alignment Analysis: Single-Photo vs Scanning Mode

## Executive Summary

Both pipelines share the SAME downstream processing (`addItemsWithVisionPrefill` → `VisionInsightsPrefiller`), but differ critically in:
1. **Frame selection timing** - scanning uses analysis frames, single-photo uses capture moment
2. **Concurrent extraction limits** - scanning can hit MAX_CONCURRENT_EXTRACTIONS limit
3. **Tracking pipeline bypass** - scanning mode NEVER uses the quality-gated tracking path

## Phase A: Pipeline Comparison Table

| Stage | Single-Photo (Short-Press) | Scanning (Long-Press) | Delta/Mismatch |
|-------|---------------------------|----------------------|----------------|
| **1. Trigger** | `onShutterTap` (CameraScreen:756) | `onShutterLongPress` (CameraScreen:856) | Same pattern |
| **2. Camera Method** | `cameraManager.captureSingleFrame()` | `cameraManager.startScanning()` | Different entry points |
| **3. Detection Call** | `processImageProxy(useStreamMode=false)` | `processImageProxy(useStreamMode=false)` | **SAME** - both use false! |
| **4. Detection Path** | `objectDetector.detectObjects()` (lines 1492-1509) | `objectDetector.detectObjects()` (lines 1492-1509) | **SAME** - tracking path NEVER used |
| **5. Tracking/ROI** | **NONE** | **NONE** (expected: `processObjectDetectionWithTracking`) | **MISSING** - tracking path bypassed |
| **6. Quality Gating** | **NONE** | **NONE** (expected: LOCKED state requirement) | **MISSING** - no quality gating |
| **7. High-Res Capture** | `captureHighResImage()` (line 789) | `captureHighResImage()` (line 865) | Same call, different timing |
| **8. URI Attachment** | `items.map { it.copy(fullImageUri = highResUri) }` | Same | Same |
| **9. Item Addition** | `addItemsWithVisionPrefill()` (line 823) | `addItemsWithVisionPrefill()` (line 895) | Same function |
| **10. Vision Prefill** | `VisionInsightsPrefiller.extractAndApply()` | Same | Same |
| **11. Concurrent Limit** | Rarely hit (single burst) | **OFTEN HIT** (rapid successive detections) | **CRITICAL DIFFERENCE** |
| **12. Frame Throttling** | None (single frame) | 400-600ms motion-based throttle | Affects frame selection |

## Phase B: Root Cause Analysis

### ROOT CAUSE 1: Tracking Pipeline Bypass (CRITICAL)

**Location**: `CameraXManager.processImageProxy()` lines 1475-1510

**Problem**: The condition for using the tracking pipeline is:
```kotlin
if (useStreamMode && isScanning) {
    // TRACKING PATH - processObjectDetectionWithTracking()
} else {
    // SINGLE-SHOT PATH - objectDetector.detectObjects()
}
```

But `startScanning()` passes `useStreamMode = false` (line 837):
```kotlin
val (items, detections) = processImageProxy(
    imageProxy = imageProxy,
    scanMode = scanMode,
    useStreamMode = false,  // <-- ALWAYS FALSE!
    onDetectionEvent = onDetectionEvent,
)
```

**Result**: Scanning NEVER uses `processObjectDetectionWithTracking()`, which means:
- No `ObjectTracker.processFrameWithRoi()` for deduplication
- No `ScanGuidanceManager` quality gating
- No LOCKED state requirement for optimal capture conditions
- No frame sharpness evaluation

**Classification**: Control-flow issue / Architectural duplication

### ROOT CAUSE 2: MAX_CONCURRENT_EXTRACTIONS Limit

**Location**: `VisionInsightsPrefiller.kt` line 69

```kotlin
private const val MAX_CONCURRENT_EXTRACTIONS = 3
```

**Problem**: In scanning mode, multiple items can be detected rapidly across successive frames. Each batch triggers `addItemsWithVisionPrefill()`, which calls `extractAndApply()` for each item. When concurrent extractions exceed 3:

```kotlin
if (inFlightExtractions.size >= MAX_CONCURRENT_EXTRACTIONS) {
    Log.d(TAG, "SCAN_ENRICH: Skipping - max concurrent extractions reached")
    return  // <-- VISION PREFILL SKIPPED ENTIRELY!
}
```

**Result**: Items detected beyond the concurrent limit get NO vision extraction:
- No OCR text
- No brand detection
- No color extraction
- Generic ML Kit labels only

**Classification**: Control-flow issue / UX timing issue

### ROOT CAUSE 3: Asynchronous High-Res Capture Race Condition

**Location**: `CameraScreen.kt` lines 864-895

**Problem**: In scanning mode, each detection batch triggers `scope.launch`:
```kotlin
onResult = { items ->
    if (items.isNotEmpty()) {
        scope.launch {  // <-- Each batch is a separate coroutine
            val highResUri = cameraManager.captureHighResImage()
            // ... attach URI ...
            itemsViewModel.addItemsWithVisionPrefill(context, itemsWithHighRes)
        }
    }
}
```

Multiple concurrent coroutines can:
1. Race for `captureHighResImage()` (camera resource contention)
2. Interleave their `addItemsWithVisionPrefill()` calls
3. Exhaust the concurrent extraction limit

**Classification**: UX timing issue / Control-flow issue

### ROOT CAUSE 4: Frame Selection Quality

**Location**: `CameraXManager.startScanning()` lines 739-743

**Problem**: Scanning uses motion-based throttling:
```kotlin
val motionScore = computeMotionScore(imageProxy)
val analysisIntervalMs = analysisIntervalMsForMotion(motionScore)
```

This means:
- Analysis happens on arbitrary frames meeting throttle criteria
- Not necessarily the sharpest or best-composed frame
- High-res capture happens AFTER detection (potentially different scene)

**Classification**: Data issue / UX timing issue

## Summary of Misalignment

| Issue | Type | Severity | Impact |
|-------|------|----------|--------|
| Tracking pipeline bypass | Control-flow | **HIGH** | Missing quality gating, dedup, ROI |
| Concurrent extraction limit | Control-flow | **HIGH** | Vision prefill skipped for items |
| Async capture race | UX timing | **MEDIUM** | Interleaved processing, resource contention |
| Frame selection | Data | **LOW** | Suboptimal frame for detection |

## Phase C: Alignment Strategy

### Problem Diagnosis

The tracking pipeline (`processObjectDetectionWithTracking`) exists and implements:
- Quality gating via LOCKED state
- ROI-based filtering
- Frame sharpness evaluation
- Proper deduplication via ObjectTracker

However, it was disabled because:
1. The condition `useStreamMode && isScanning` requires `useStreamMode = true`
2. `useStreamMode = true` causes ML Kit's STREAM_MODE to be used
3. STREAM_MODE produces unstable tracking IDs causing blinking overlays
4. Someone fixed blinking by always passing `useStreamMode = false`
5. This inadvertently disabled the entire tracking pipeline!

### Root Conflict

The variable `useStreamMode` conflates TWO separate concerns:
1. **ML Kit detector mode**: STREAM_MODE vs SINGLE_IMAGE_MODE (affects bbox stability)
2. **Pipeline selection**: Whether to use tracking pipeline (affects quality gating)

Line 1590 hardcodes `useStreamMode = true` in `detectObjectsWithTracking()`:
```kotlin
val trackingResponse = objectDetector.detectObjectsWithTracking(
    ...
    useStreamMode = true,  // <-- Causes blinking!
    ...
)
```

### Alignment Strategy: Decouple Concerns

**Solution**: Separate the ML Kit detector mode from pipeline selection.

1. **Condition change**: Use `isScanning` alone (not `useStreamMode && isScanning`)
2. **Detector change**: Use SINGLE_IMAGE_MODE in tracking path (avoid blinking)

This achieves:
- Scanning uses tracking pipeline (quality gating, dedup, ROI)
- SINGLE_IMAGE_MODE detection (stable bbox, no blinking)
- Single-capture uses direct detection (unchanged)

### Canonical Pipeline Definition

After alignment, the flow should be:

```
┌─────────────────────────────────────────────────────────────────────┐
│                     CAPTURE (Common Entry Point)                     │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│              DETECTION (objectDetector.detectObjects)                │
│                    Uses SINGLE_IMAGE_MODE                            │
│                  Returns: DetectionInfo + DetectionResult            │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
                    ▼                           ▼
        ┌───────────────────┐       ┌───────────────────────┐
        │   SINGLE-PHOTO    │       │      SCANNING         │
        │   (Short-Press)   │       │    (Long-Press)       │
        └─────────┬─────────┘       └───────────┬───────────┘
                  │                             │
                  │                             ▼
                  │               ┌─────────────────────────┐
                  │               │   TRACKING PIPELINE     │
                  │               │  - ObjectTracker        │
                  │               │  - ROI filtering        │
                  │               │  - Quality gating       │
                  │               │  - LOCKED state req     │
                  │               └───────────┬─────────────┘
                  │                           │
                  └─────────────┬─────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     ITEM CREATION (ScannedItem)                      │
│                    With: thumbnail, bbox, category                   │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                HIGH-RES CAPTURE (captureHighResImage)                │
│                    Attaches: fullImageUri                            │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   VISION PREFILL (Shared Pipeline)                   │
│                    addItemsWithVisionPrefill()                       │
│                                                                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │
│  │ Layer A (Local) │→ │ Layer B (Cloud) │→ │ Layer C (Enrichment)│  │
│  │  OCR + Colors   │  │  Brand/Logo     │  │  Full Draft         │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘

```

### Handoff Point

**Question**: At what exact point should scanning hand off to the same logic used by single photo?

**Answer**: At the **ITEM CREATION** stage. Both flows should:
1. Use the same detection method (SINGLE_IMAGE_MODE)
2. Produce identical `ScannedItem` objects
3. Feed into the same `addItemsWithVisionPrefill()` pipeline

The difference is scanning adds a **TRACKING PIPELINE** gate between detection and item creation:
- Single-photo: Detection → Item Creation (immediate)
- Scanning: Detection → Tracking → Quality Gate → Item Creation (gated)

### Minimum Shared Contract

**Input to shared pipeline** (`addItemsWithVisionPrefill`):
```kotlin
data class ScannedItem(
    id: String,                    // Unique identifier
    thumbnail: ImageRef?,          // Cropped thumbnail
    category: ItemCategory,        // ML Kit category
    confidence: Float,             // Detection confidence
    boundingBox: NormalizedRect,   // Normalized bbox
    labelText: String?,            // ML Kit label
    fullImageUri: Uri?,            // High-res capture URI (REQUIRED for vision)
    // ... other fields
)
```

**Required for vision extraction**:
- `fullImageUri` MUST be non-null
- Image should be high-quality (not analysis frame)

**Output from shared pipeline**:
- Item with populated `visionAttributes`:
  - `ocrText`: Extracted text
  - `colors`: Dominant colors
  - `logos`/`brandCandidates`: Brand detection
  - `itemType`: Product type classification

## Phase D: Implementation

### Changes Made

**File 1: `CameraXManager.kt` (line ~1474-1478)**

Changed the condition for selecting the tracking pipeline:

```kotlin
// BEFORE (broken):
if (useStreamMode && isScanning) {
    // TRACKING PATH - never reached because useStreamMode = false

// AFTER (fixed):
if (isScanning) {
    // TRACKING PATH - now used when scanning
```

**File 2: `CameraXManager.kt` (line ~1587-1594)**

Changed the detector mode in the tracking path:

```kotlin
// BEFORE (caused blinking):
useStreamMode = true,

// AFTER (stable bboxes):
useStreamMode = false, // CRITICAL: Use SINGLE_IMAGE_MODE for stable bboxes
```

### Reasoning

1. **Decoupled concerns**: ML Kit detector mode (SINGLE_IMAGE vs STREAM) is now separate from pipeline selection (tracking vs direct)

2. **Preserved stability**: Using SINGLE_IMAGE_MODE in both paths ensures stable bounding boxes without blinking

3. **Restored quality gating**: Scanning mode now uses ObjectTracker + ScanGuidanceManager for:
   - LOCKED state requirement (optimal capture conditions)
   - ROI filtering (center of frame)
   - Deduplication across frames
   - Frame sharpness evaluation

4. **Minimal changes**: Only 2 lines changed in CameraXManager.kt

### Files Changed

| File | Line(s) | Change |
|------|---------|--------|
| `CameraXManager.kt` | 1474-1478 | Condition: `useStreamMode && isScanning` → `isScanning` |
| `CameraXManager.kt` | 1594 | Detector mode: `useStreamMode = true` → `useStreamMode = false` |

## Phase E: Validation

### Unit Tests

Created: `PipelineAlignmentTest.kt`

Tests verify:
1. `pipelineSelectionLogic_shouldUseScanningStateAlone` - Scanning uses tracking path
2. `detectorMode_shouldBeSingleImageModeInBothPaths` - Both paths use SINGLE_IMAGE_MODE
3. `documentExpectedBehavior_singlePhotoVsScanning` - Expected feature differences

### Manual Acceptance Checklist

Run the app and verify for **SCANNING MODE** (long-press):

- [ ] Brand detected: Point camera at branded item, verify brand appears in attributes
- [ ] Color detected: Point camera at colored item, verify color appears in attributes
- [ ] OCR working: Point camera at item with text, verify text is extracted
- [ ] Readable label: Scanned items should have descriptive labels, not just "Object"
- [ ] Attributes visible: Before any user interaction, attributes should be populated
- [ ] Quality gating: Items should only be added when camera is stable (LOCKED state)
- [ ] Bboxes stable: Bounding boxes should not blink/jump between frames
- [ ] Same as single-photo: Final item quality should match single-photo capture

Regression tests for **SINGLE-PHOTO** (short-press):

- [ ] Brand detection still works
- [ ] Color extraction still works
- [ ] OCR still works
- [ ] Item quality unchanged from before

## Summary

### What Was Misaligned

1. **Pipeline bypass**: Scanning mode was NOT using the tracking pipeline due to incorrect condition `useStreamMode && isScanning` (always false because `useStreamMode = false`)

2. **Quality gating disabled**: Without the tracking pipeline, scanning had no LOCKED state requirement, no ROI filtering, and no deduplication

3. **Concurrent extraction limits**: Rapid item addition in scanning could exhaust the MAX_CONCURRENT_EXTRACTIONS (3) limit, causing vision prefill to be skipped

### What Was Unified

1. **Detection mode**: Both paths now use SINGLE_IMAGE_MODE for stable bounding boxes

2. **Item creation**: Both paths feed into the same `addItemsWithVisionPrefill()` pipeline

3. **Vision extraction**: Same 3-layer extraction (Local → Cloud → Enrichment) for all items

### What Remains Intentionally Different

1. **Quality gating**: Scanning uses ObjectTracker + ScanGuidanceManager for quality filtering; single-photo adds items immediately

2. **Deduplication**: Scanning deduplicates across frames; single-photo has no dedup (single frame)

3. **ROI filtering**: Scanning filters to center ROI; single-photo processes all detections

### Future Improvements

1. **Extraction queuing**: Instead of skipping when MAX_CONCURRENT_EXTRACTIONS is reached, queue items for later processing

2. **Batch extraction**: Consider batching vision extraction when scanning stops rather than per-item

3. **Shared image**: Capture high-res image ONCE and share across all items in a batch

4. **Frame selection**: Use frame sharpness scoring to select the best frame for high-res capture
