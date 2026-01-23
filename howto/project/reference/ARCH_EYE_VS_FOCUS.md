# Scanium Architecture: Eye Mode vs Focus Mode

## Product Goal

**Scanium as an augmented "eye"** - with clear separation between passive recognition and active
user intent.

### Mental Model

- **Eye Mode** = "I see what Scanium sees" (vision)
- **Focus Mode** = "I choose what Scanium acts on" (selection)

---

## Core Principles (Non-Negotiable)

1. **Detection ≠ Selection**
    - Detection is global (whole frame)
    - Selection is local (ROI only)

2. **Nothing is added without user intent**
    - No background or automatic additions

3. **Visual feedback must be immediate and honest**
    - Bounding boxes = recognition
    - ROI highlight / lock = selection

4. **Live scan and picture capture must behave consistently**
    - Same ROI, same selection logic, same crop logic

---

## Current Architecture Assessment

### Where Detection Currently Happens

```
ImageAnalysis (1280x720)
    │
    ▼
ObjectDetectorClient (ML Kit)
    │
    ▼
detections: List<DetectionResult>   ← Full frame detections here
    │
    ▼
RoiDetectionFilter.filterByRoi()   ← PROBLEM: Filters by ROI
    │
    ▼
roiEligible: List<DetectionResult>  ← Only ROI-inside detections
    │
    ▼
OverlayTrackManager.renderOverlayTracks()
    │
    ▼
DetectionOverlay (renders bboxes)   ← Only shows ROI-eligible
```

**Key File Locations:**

- ML Detection: `ObjectDetectorClient.kt`
- ROI Filtering: `RoiDetectionFilter.kt:50` (`filterByRoi()`)
- Overlay Rendering: `OverlayTrackManager.kt:146` (filters before rendering)
- Bbox Drawing: `DetectionOverlay.kt`

### Where ROI is Currently Applied

| Component                         | ROI Usage          | Current Behavior               |
|-----------------------------------|--------------------|--------------------------------|
| `RoiDetectionFilter`              | Hard filter        | Only center-inside shown       |
| `OverlayTrackManager`             | Uses filtered list | Bboxes hidden outside ROI      |
| `CenterWeightedCandidateSelector` | Gating rule        | Rejects outside-ROI detections |
| `ScanGuidanceManager`             | Lock eligibility   | Only ROI objects can lock      |

### Where User Intent is Inferred vs Explicit

| Action          | Current              | Intent Type                  |
|-----------------|----------------------|------------------------------|
| Bbox appears    | Filtered by ROI      | Implicit (system decides)    |
| Object locks    | Auto after stability | Implicit (system decides)    |
| Item added      | After lock           | Semi-explicit (tap required) |
| Picture capture | Shutter tap          | Explicit                     |

**Problem:** User intent is mostly inferred. The system decides what to show, not the user.

### Where Scan vs Picture Diverge

| Aspect          | Live Scan    | Picture                   |
|-----------------|--------------|---------------------------|
| Detection scope | ROI-filtered | Full frame (no filtering) |
| Selection       | Auto-lock    | Manual shutter            |
| Crop            | Bbox-based   | Full frame or manual      |
| Classification  | After lock   | After capture             |

---

## Mismatches with New Goal

### Mismatch 1: ROI Hides Detections

**Current:** `RoiDetectionFilter.filterByRoi()` removes detections outside ROI before rendering.
**Problem:** User cannot see what Scanium detects globally.
**Fix:** Show ALL detections; use ROI only for selection highlighting.

### Mismatch 2: No Visual Distinction Between "Seen" and "Selected"

**Current:** All visible bboxes have same base style (PREVIEW).
**Problem:** User cannot distinguish "Scanium sees this" from "Scanium will act on this".
**Fix:** Two visual states: neutral (Eye) vs highlighted (Focus).

### Mismatch 3: Lock is Implicit

**Current:** Lock happens automatically after stability conditions met.
**Problem:** User doesn't explicitly choose which object to scan.
**Fix:** Lock only the ROI-intersecting object; break lock when selection changes.

### Mismatch 4: Picture Capture Has No ROI Selection

**Current:** Picture captures full frame, then classifies.
**Problem:** Inconsistent with live scan's ROI-based behavior.
**Fix:** Picture should also use ROI selection; capture crops to selected object.

---

## Target Architecture

### Eye Mode (Always Active)

```
ImageAnalysis (1280x720)
    │
    ▼
ObjectDetectorClient (ML Kit)
    │
    ▼
allDetections: List<DetectionResult>  ← ALL detections
    │
    ├──────────────────────────────────────┐
    ▼                                      ▼
DetectionOverlay                    SelectionManager
(renders ALL bboxes                 (determines which
 with neutral style)                 intersects ROI)
```

### Focus Mode (On Selection)

```
allDetections + ROI
    │
    ▼
SelectionManager.selectByRoi()
    │
    ▼
selectedDetection: DetectionResult?   ← At most ONE
    │
    ├──────────────────────────┬──────────────────────┐
    ▼                          ▼                      ▼
DetectionOverlay          ScanGuidanceManager    PictureCapture
(highlights selected)     (locks selected only)  (crops to selected)
```

---

## Implementation Changes Required

### 1. Remove ROI Filtering from Overlay

**File:** `OverlayTrackManager.kt`
**Change:** Remove `RoiDetectionFilter.filterByRoi()` call; pass ALL detections to overlay.

### 2. Add Selection Logic

**New Concept:** `SelectedDetection` - the ONE detection that intersects ROI.
**Logic:**

```kotlin
fun selectByRoi(detections: List<DetectionResult>, roi: ScanRoi): DetectionResult? {
    // Find detections whose center or significant area overlaps ROI
    val candidates = detections.filter { roi.containsBoxCenter(it.centerX, it.centerY) }
    // Choose closest to ROI center
    return candidates.minByOrNull { roi.distanceFromCenter(it.centerX, it.centerY) }
}
```

### 3. Update Overlay Visual States

**File:** `DetectionOverlay.kt`, `OverlayTrack.kt`
**New States:**

- `EYE` - Thin, neutral color (global detection)
- `SELECTED` - Medium, accent color (ROI-intersecting)
- `LOCKED` - Thick, bright accent (ready to act)

### 4. Update Lock Logic

**File:** `ScanGuidanceManager.kt`
**Change:** Lock should only target the `selectedDetection`, not any ROI-eligible detection.

### 5. Unify Picture Capture

**File:** `CameraXManager.kt`
**Change:** Picture capture should use same selection logic; crop to selected bbox.

---

## Data Flow Diagram (Target)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EYE MODE                                        │
│                         (Always Active)                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌──────────────┐                                                         │
│   │  ML Kit      │                                                         │
│   │  Detection   │                                                         │
│   └──────┬───────┘                                                         │
│          │                                                                  │
│          ▼                                                                  │
│   allDetections[]  ──────────────────────────────────────────────────────▶ │
│          │                                           │                      │
│          │                                           ▼                      │
│          │                                    ┌──────────────┐             │
│          │                                    │ Detection    │             │
│          │                                    │ Overlay      │             │
│          │                                    │ (ALL boxes,  │             │
│          │                                    │  neutral)    │             │
│          │                                    └──────────────┘             │
│          │                                                                  │
└──────────┼──────────────────────────────────────────────────────────────────┘
           │
           │  + ROI
           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             FOCUS MODE                                       │
│                        (User Intent Active)                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌──────────────┐                                                         │
│   │  Selection   │                                                         │
│   │  Manager     │                                                         │
│   └──────┬───────┘                                                         │
│          │                                                                  │
│          ▼                                                                  │
│   selectedDetection?  ───────────┬───────────────────────────────────────▶ │
│          │                       │                     │                    │
│          │                       ▼                     ▼                    │
│          │              ┌──────────────┐      ┌──────────────┐             │
│          │              │ Overlay      │      │ Guidance     │             │
│          │              │ (highlight   │      │ Manager      │             │
│          │              │  selected)   │      │ (lock only   │             │
│          │              └──────────────┘      │  selected)   │             │
│          │                                    └──────────────┘             │
│          │                                                                  │
│          └──────────────────────────────────────────────────────────────▶  │
│                                                         │                   │
│                                                         ▼                   │
│                                                 ┌──────────────┐           │
│                                                 │ Item Add /   │           │
│                                                 │ Capture      │           │
│                                                 │ (selected    │           │
│                                                 │  only)       │           │
│                                                 └──────────────┘           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Visual Language

| State      | Stroke         | Color                    | When                     |
|------------|----------------|--------------------------|--------------------------|
| `EYE`      | Thin (1-2px)   | Neutral blue (30% alpha) | Any detection, anywhere  |
| `SELECTED` | Medium (2-3px) | Accent blue              | Detection intersects ROI |
| `READY`    | Medium (3px)   | Green                    | Selected + stability met |
| `LOCKED`   | Thick (4px)    | Bright green             | Ready to add/capture     |

---

## Migration Steps

1. **Create `SelectionManager`** - new class for ROI-based selection logic
2. **Update `OverlayTrackManager`** - pass all detections, add selection state
3. **Update `OverlayTrack`** - add `EYE` box style, track selection state
4. **Update `DetectionOverlay`** - render EYE style, highlight selected
5. **Update `ScanGuidanceManager`** - lock only selected detection
6. **Update `CameraXManager`** - unify picture capture with selection
7. **Update guidance hints** - new microcopy for Eye/Focus model
8. **Update tests** - new success criteria

---

## Success Criteria

1. App opens → bboxes appear immediately for ALL detected objects
2. Panning → bboxes update continuously (no disappearing outside ROI)
3. Object enters ROI → becomes highlighted (SELECTED state)
4. Shutter pressed → only SELECTED object captured
5. Scan completes → only SELECTED object added
6. Multiple objects → user clearly controls selection via centering

---

*Document version: 1.0*
*Created: 2025-12-29*
