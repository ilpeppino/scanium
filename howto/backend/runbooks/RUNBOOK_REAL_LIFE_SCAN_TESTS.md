***REMOVED*** Scanium Real-Life Scan Tests Runbook

Manual instrumented test runbook for verifying Eye Mode and Focus Mode scanning behavior.

---

***REMOVED******REMOVED*** Eye Mode vs Focus Mode

***REMOVED******REMOVED******REMOVED*** Mental Model

- **Eye Mode** = "I see what Scanium sees" (global vision)
- **Focus Mode** = "I choose what Scanium acts on" (user intent via ROI selection)

***REMOVED******REMOVED******REMOVED*** Visual Language

| State      | Visual                        | Behavior                                       |
|------------|-------------------------------|------------------------------------------------|
| `EYE`      | Thin white stroke (35% alpha) | Detection anywhere in frame - global awareness |
| `SELECTED` | Medium blue stroke            | Detection center inside ROI - user intent      |
| `READY`    | Medium-thick green stroke     | Selected + stability conditions met            |
| `LOCKED`   | Thick bright green + pulse    | Ready to scan/capture                          |

---

***REMOVED******REMOVED*** 1. Architecture Overview

***REMOVED******REMOVED******REMOVED*** Detection Flow

```
ImageAnalysis (1280x720)
    │
    ▼
ML Kit ObjectDetector
    │
    ▼
ALL detections (Eye Mode - global vision)
    │
    ├────────────────────────────────────────────┐
    ▼                                            ▼
DetectionOverlay                          SelectionManager
(renders ALL bboxes)                      (finds ROI-intersecting detection)
    │                                            │
    │                                            ▼
    │                                    selectedDetection?
    │                                            │
    └────────────────────────────────────────────┘
                        │
                        ▼
                Visual Highlighting
                (SELECTED/READY/LOCKED)
```

***REMOVED******REMOVED******REMOVED*** Key Principle

- **ROI is a SELECTION TOOL, not a detection filter**
- All objects are detected and shown (Eye Mode)
- Only the object centered in ROI can be selected/scanned (Focus Mode)

---

***REMOVED******REMOVED*** 2. MUST-Pass Test Criteria

***REMOVED******REMOVED******REMOVED*** Criterion 1: Global Detection (Eye Mode)

**Why it matters**: User must see what Scanium sees - all detected objects, anywhere in frame.
**Code path**: ML Kit → ALL detections → DetectionOverlay (no ROI filtering)
**Failure indicates**: ROI filtering still active; bboxes hidden outside ROI.

***REMOVED******REMOVED******REMOVED*** Criterion 2: Single Selection (Focus Mode)

**Why it matters**: Only ONE object can be selected/scanned at a time.
**Code path**: `OverlayTrackManager.selectBestCandidate()` → `selectedTrackingId`
**Failure indicates**: Multiple objects selected; selection logic broken.

***REMOVED******REMOVED******REMOVED*** Criterion 3: Selection via ROI Intersection

**Why it matters**: User controls selection by centering object in ROI.
**Code path**: `RoiDetectionFilter.filterByRoi()` → `selectBestCandidate()`
**Failure indicates**: Selection not tied to ROI; random object selected.

***REMOVED******REMOVED******REMOVED*** Criterion 4: Visual State Progression

**Why it matters**: User must clearly see the state: EYE → SELECTED → READY → LOCKED.
**Code path**: `mapOverlayTracks()` → `DetectionOverlay` rendering
**Failure indicates**: Wrong colors/strokes; states not visually distinct.

***REMOVED******REMOVED******REMOVED*** Criterion 5: Only Selected Object Lockable

**Why it matters**: No accidental adds from background objects.
**Code path**: `ScanGuidanceManager.evaluateState()` with selected candidate only
**Failure indicates**: Background object gets locked; unintentional adds.

---

***REMOVED******REMOVED*** 3. Test Cases

***REMOVED******REMOVED******REMOVED*** TEST-001: Global Detection - Bboxes Appear Everywhere

**Test ID**: TEST-001
**Title**: Bboxes appear for ALL detected objects, anywhere in frame

**Object Types**: 3+ objects scattered across frame (smartphone, book, water bottle)

**Setup**:

- Indoor lighting (normal room light)
- Multiple objects placed at various positions (left, right, center, edges)
- Phone held in portrait orientation

**Distances**:

- Objects spread across frame at 40-60 cm
- Some objects intentionally OUTSIDE the ROI zone

**Steps**:

1. Launch Scanium app and navigate to camera screen
2. Point camera at scene with 3+ objects
3. Observe bounding boxes for ALL detected objects
4. Pan slowly across the scene

**Expected Outcome**:

- Bboxes appear for ALL detected objects, not just those in ROI
- Objects outside ROI have EYE style (thin, subtle white stroke)
- Objects inside ROI have SELECTED style (thicker, blue stroke)
- Bboxes update continuously as camera pans

**Failure Clues**:
| Symptom | Likely Cause |
|---------|--------------|
| Only ROI objects show bboxes | ROI filtering still active; check `OverlayTrackManager` |
| Bboxes disappear when moving out of ROI | Same as above |
| All bboxes same style | Box style logic not using `selectedTrackingId` |

---

***REMOVED******REMOVED******REMOVED*** TEST-002: Selection via Centering

**Test ID**: TEST-002
**Title**: Object becomes SELECTED when centered in ROI

**Object Types**: Smartphone (to center), TV remote (off-center)

**Setup**:

- Indoor lighting
- Two objects on a surface
- Phone held in portrait orientation

**Distances**:

- Object-to-camera: 50 cm
- Object-to-object: 20 cm apart

**Steps**:

1. Point camera so smartphone is centered in ROI, remote is outside
2. Observe bbox styles
3. Pan to center the remote in ROI
4. Observe bbox styles change

**Expected Outcome**:

- Smartphone: SELECTED style (blue) when centered
- Remote: EYE style (white) when not centered
- After pan: Remote becomes SELECTED, smartphone becomes EYE
- Only ONE object is SELECTED at any time

**Failure Clues**:
| Symptom | Likely Cause |
|---------|--------------|
| Both objects SELECTED | Selection not limiting to one object |
| Wrong object SELECTED | Center score calculation wrong |
| No SELECTED objects | `selectBestCandidate` returning null |

---

***REMOVED******REMOVED******REMOVED*** TEST-003: SELECTED to READY to LOCKED Progression

**Test ID**: TEST-003
**Title**: Visual state progression works correctly

**Object Types**: Water bottle (single object)

**Setup**:

- Indoor lighting
- Single object on plain background
- Phone held steady

**Distances**:

- Object-to-camera: 45 cm (optimal)

**Steps**:

1. Center water bottle in ROI
2. Observe bbox becomes SELECTED (blue)
3. Hold phone steady for 1-2 seconds
4. Observe bbox becomes READY (green)
5. Continue holding steady
6. Observe bbox becomes LOCKED (bright green with pulse)

**Expected Outcome**:

- State progression: EYE → SELECTED → READY → LOCKED
- Each state visually distinct (color + stroke width)
- "Hold to lock" hint appears during GOOD state
- "Ready" hint appears when LOCKED

**Failure Clues**:
| Symptom | Likely Cause |
|---------|--------------|
| Stays SELECTED, never READY | `isGoodState` not propagated |
| Stays READY, never LOCKED | Lock conditions not met; stability issue |
| Colors wrong | `BboxColors` constants incorrect |

---

***REMOVED******REMOVED******REMOVED*** TEST-004: Pan Breaks Selection (No Background Adds)

**Test ID**: TEST-004
**Title**: Panning camera changes selection, no accidental adds

**Object Types**: Headphones case (A), Book (B)

**Setup**:

- Indoor lighting
- Two objects well separated
- Mixed background acceptable

**Distances**:

- Object-to-camera: 50 cm
- Object-to-object: 40 cm apart

**Steps**:

1. Center headphones case (A), achieve LOCKED state
2. **Without tapping**, slowly pan camera toward book (B)
3. Observe: Lock breaks, state returns to SEARCHING
4. Continue pan until book (B) centered
5. Wait for book to achieve LOCKED state

**Expected Outcome**:

- Pan motion breaks lock on A
- During pan: All visible objects show EYE style
- Selection transfers to B when centered
- No items added automatically during pan
- Only B can be scanned after pan completes

**Failure Clues**:
| Symptom | Likely Cause |
|---------|--------------|
| Lock doesn't break during pan | Motion detection not working |
| A stays SELECTED during pan | Selection not updating |
| Item added during pan | Auto-add bug; should require tap |

---

***REMOVED******REMOVED******REMOVED*** TEST-005: Edge Detection Without Selection

**Test ID**: TEST-005
**Title**: Objects at frame edges detected but not selectable

**Object Types**: Multiple objects across frame

**Setup**:

- Indoor lighting
- Objects placed at center and edges of frame
- ROI covers center ~65% of frame

**Distances**:

- Central object: 45 cm
- Edge objects: 50 cm

**Steps**:

1. Position camera so central object in ROI, edge objects visible but outside ROI
2. Observe ALL objects have bboxes
3. Verify edge objects show EYE style (not SELECTED)
4. Try to achieve LOCKED on edge object by holding steady (should fail)

**Expected Outcome**:

- ALL objects detected and shown with bboxes
- Edge objects: EYE style only (cannot be SELECTED)
- Central object: SELECTED → READY → LOCKED
- Edge objects never achieve READY or LOCKED

**Failure Clues**:
| Symptom | Likely Cause |
|---------|--------------|
| Edge objects hidden | ROI filtering still active |
| Edge object achieves SELECTED | ROI boundary check wrong |
| Edge object locks | Selection not enforced in lock logic |

---

***REMOVED******REMOVED*** 4. Debug Mode Reference

Enable debug overlay in Settings → Developer Options → "Show scan diagnostics":

| Metric             | Eye vs Focus Relevance                |
|--------------------|---------------------------------------|
| Detected count     | Total objects seen (Eye mode)         |
| Selected ID        | Which object is selected (Focus mode) |
| Locked ID          | Which object is locked                |
| ROI inside/outside | How many in/out of ROI                |

---

***REMOVED******REMOVED*** 5. Quick Reference

***REMOVED******REMOVED******REMOVED*** Guidance States & Hints

| State     | Hint                 | Visual                  |
|-----------|----------------------|-------------------------|
| SEARCHING | (none)               | All bboxes EYE style    |
| SELECTED  | "Center to select"   | One bbox SELECTED style |
| TOO_CLOSE | "Move back slightly" | SELECTED bbox           |
| TOO_FAR   | "Move closer"        | SELECTED bbox           |
| UNSTABLE  | "Hold steady"        | SELECTED bbox           |
| GOOD      | "Hold to lock"       | READY style             |
| LOCKED    | "Ready"              | LOCKED style + pulse    |

***REMOVED******REMOVED******REMOVED*** Box Style Stroke Widths

| Style    | Stroke Multiplier | Color      |
|----------|-------------------|------------|
| EYE      | 0.5x              | White 35%  |
| SELECTED | 0.9x              | Blue 85%   |
| READY    | 1.1x              | Green 85%  |
| LOCKED   | 1.4x + pulse      | Green 100% |

---

***REMOVED******REMOVED*** 6. Success Criteria Summary

1. **App opens** → Bboxes appear immediately for ALL detected objects
2. **Panning** → Bboxes update continuously (never disappear outside ROI)
3. **Object enters ROI** → Becomes SELECTED (visual change)
4. **Hold steady** → SELECTED → READY → LOCKED progression
5. **Shutter pressed** → Only SELECTED/LOCKED object captured
6. **Multiple objects** → User controls selection via centering

---

*Document version: 2.0 (Eye vs Focus update)*
*Updated: 2025-12-29*
