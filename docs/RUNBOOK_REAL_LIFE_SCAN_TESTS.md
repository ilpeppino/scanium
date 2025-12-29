# Scanium Real-Life Scan Tests Runbook

Manual instrumented test runbook for verifying live scanning and picture capture flows.

---

## 1. Architecture Evaluation

### 1.1 Live Scanning Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          LIVE SCANNING FLOW                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                  │
│  │ ImageAnalysis│───▶│   Motion     │───▶│  Throttle    │                  │
│  │  1280x720    │    │  Detection   │    │  400-600ms   │                  │
│  │  YUV_420_888 │    │  (luma diff) │    │              │                  │
│  └──────────────┘    └──────────────┘    └──────────────┘                  │
│         │                                       │                           │
│         ▼                                       ▼                           │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                  │
│  │   Viewport   │───▶│   ML Kit     │───▶│    ROI       │                  │
│  │  Calculation │    │  ObjectDet   │    │   Filter     │                  │
│  │  (crop adj)  │    │  STREAM_MODE │    │ (center in?) │                  │
│  └──────────────┘    └──────────────┘    └──────────────┘                  │
│                                                 │                           │
│         ┌───────────────────────────────────────┘                           │
│         ▼                                                                   │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                  │
│  │   Center-    │───▶│  Stability   │───▶│    Lock      │                  │
│  │   Weighted   │    │   Gating     │    │  Mechanism   │                  │
│  │   Selector   │    │ (3 frames/   │    │  (guidance)  │                  │
│  │              │    │  400ms)      │    │              │                  │
│  └──────────────┘    └──────────────┘    └──────────────┘                  │
│                                                 │                           │
│                                                 ▼                           │
│                                          ┌──────────────┐                  │
│                                          │  Item Add    │                  │
│                                          │ (if LOCKED   │                  │
│                                          │  & canAdd)   │                  │
│                                          └──────────────┘                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Camera Input (ImageAnalysis)**
- Resolution: 1280x720 target (YUV_420_888 format)
- Strategy: KEEP_ONLY_LATEST (drops frames if processing is slow)
- Location: `CameraXManager.kt:287-292`

**Preprocessing**
- Motion detection: Compares luma samples between consecutive frames
- Throttling: 400-600ms based on motion score (high motion = slower processing)
- Viewport calculation: Adjusts for aspect ratio differences via center-crop
- Edge inset: 10% margin to filter partial objects at frame edges
- Location: `CameraXManager.kt:computeMotionScore()`, `calculateVisibleViewport()`

**Detection & BBox Preview**
- ML Kit ObjectDetector in STREAM_MODE with tracking enabled
- Multiple detection mode (up to 5 objects)
- Detections filtered by ROI: only boxes with CENTER inside ROI are shown
- Max 2 preview bboxes displayed at once
- Location: `RoiDetectionFilter.kt`, `ObjectDetectorClient.kt`

**Lock/Stability Gating**
- Center-weighted scoring: 50% confidence + 20% area + 30% center score
- Minimum area gate: 3% of frame (rejects tiny distant objects)
- Stability requirement: 3 consecutive frames OR 400ms stable time
- Lock breaks on: high motion (instant or averaged > 0.25), position shift, candidate lost, timeout
- Location: `CenterWeightedCandidateSelector.kt`, `ScanGuidanceManager.kt`

**Item Creation Path**
- Only triggered when `guidanceState.canAddItem == true` AND state is LOCKED
- Locked candidate ID must match detection ID
- Item is created from the locked candidate's tracking data
- Location: `CameraXManager.kt:1046-1078`

### 1.2 Picture Capture Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PICTURE CAPTURE FLOW                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                  │
│  │ ImageCapture │───▶│   JPEG       │───▶│   Save to    │                  │
│  │  720p/1080p/ │    │  Encoding    │    │   Cache      │                  │
│  │  4K          │    │              │    │              │                  │
│  └──────────────┘    └──────────────┘    └──────────────┘                  │
│         │                                       │                           │
│         ▼                                       ▼                           │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                  │
│  │   Rotation   │───▶│   Manual     │───▶│ Classification│                  │
│  │  Correction  │    │   Review     │    │  (on-device/ │                  │
│  │  (EXIF)      │    │              │    │   cloud)     │                  │
│  └──────────────┘    └──────────────┘    └──────────────┘                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Camera Input (ImageCapture)**
- Resolution options: 720p (LOW), 1080p (NORMAL), 4K (HIGH)
- Format: JPEG output to cache directory
- Location: `CameraXManager.kt:captureHighResImage()`

**Preprocessing**
- Rotation: Applied via EXIF metadata
- No ROI crop applied (full frame captured)
- No live detection filtering

**Classification Path**
- Manual trigger after capture
- On-device classifier + optional cloud fallback
- Location: `ClassificationOrchestrator.kt`

### 1.3 Scan vs Picture Divergence Points

| Aspect | Live Scan | Picture Capture |
|--------|-----------|-----------------|
| **Resolution** | 1280x720 | 720p - 4K |
| **Rotation** | Real-time via matrix | EXIF metadata |
| **ROI Crop** | Center inside ROI required | Full frame |
| **Compression** | None (YUV frames) | JPEG encoding |
| **Timing** | Continuous 2-3 FPS | Single capture |
| **Detection** | Live ML Kit | Post-capture |
| **Stability** | Required (lock) | Not applicable |

### 1.4 Likely Failure Points

| Failure Point | Code Location | Reason |
|--------------|---------------|--------|
| **ROI mapping mismatch** | `RoiCoordinateMapper.kt` | Aspect ratio differences between preview (phone UI) and analyzer (1280x720) can cause visible ROI to not match detection zone |
| **Center priority bypass** | `CenterWeightedCandidateSelector.kt:284` | High-confidence (>0.8) detections bypass center distance gate, allowing background objects |
| **Motion false-positive** | `ScanGuidanceManager.kt:313-314` | Both instant and average motion checked; hand tremor may break lock |
| **Area threshold mismatch** | `ScanRoi.kt:144-147` | 4% min (too far) and 35% max (too close) may not match all object types |
| **Stability timing** | `CenterWeightedConfig.kt:504-505` | 3 frames at 400ms throttle = 1.2s minimum lock time |
| **Stale frame** | `ObjectTracker.kt:733-750` | Candidates expire after 10 frames if not re-detected |

---

## 2. MUST-Pass Real-Life Test Criteria

### Criterion 1: Single Object Lock at Optimal Distance
**Why it matters**: Core scanning flow must work reliably for the most common use case.
**Code path validated**: `CenterWeightedCandidateSelector.selectBestCandidateWithRoi()` → `ObjectTracker.processFrameWithRoi()` → `ScanGuidanceManager.evaluateState()` → LOCKED
**Failure indicates**: ROI filtering broken, stability gating too strict, or area thresholds misconfigured.

### Criterion 2: Near Object Prioritized Over Background
**Why it matters**: Users expect the centered object to be scanned, not items in the background.
**Code path validated**: `CenterWeightedCandidateSelector.calculateScoreWithRoi()` (center weight 30%, area weight 20%)
**Failure indicates**: Center-weighted scoring not working, or confidence override (>0.8) incorrectly applied.

### Criterion 3: Panning Does Not Add Background Items
**Why it matters**: Moving camera should not trigger accidental item additions.
**Code path validated**: `ScanGuidanceManager.shouldBreakLock()` (instant motion check + average motion check)
**Failure indicates**: Motion detection thresholds too lenient, or lock not breaking on camera movement.

### Criterion 4: ROI Boundary Enforced
**Why it matters**: Visual scan zone must match actual detection zone.
**Code path validated**: `RoiDetectionFilter.filterByRoi()` → `scanRoi.containsBoxCenter()`
**Failure indicates**: Coordinate mapping mismatch between preview and analyzer spaces.

### Criterion 5: Too Close/Too Far States Trigger
**Why it matters**: User feedback must guide optimal distance.
**Code path validated**: `ScanGuidanceManager.evaluateState()` → area thresholds (4% min, 35% max)
**Failure indicates**: Area calculation wrong, or threshold values need adjustment for object types.

### Criterion 6: Lock Persists Through Minor Tremor
**Why it matters**: Human hand tremor should not break lock.
**Code path validated**: Motion averaging (5-frame window) in `ScanGuidanceManager.updateMotionAverage()`
**Failure indicates**: Motion threshold (0.25) too sensitive for hand-held use.

---

## 3. Test Cases

### TEST-001: Single Object Optimal Distance Lock

**Test ID**: TEST-001
**Title**: Single object achieves LOCKED state at optimal distance

**Object Types**: Smartphone, TV remote, water bottle (choose one)

**Setup**:
- Indoor lighting (normal room light, no direct sunlight)
- Plain background (table, floor, or wall - single color preferred)
- Phone held in portrait orientation
- Object placed flat or upright on surface

**Distances**:
- Object-to-camera: **50 cm** (optimal range)
- Object-to-object: N/A (single object test)

**Steps**:
1. Launch Scanium app and navigate to camera screen
2. Hold phone steady, pointed at the single object
3. Center the object within the visible scan zone (ROI overlay)
4. Wait 2-3 seconds while holding steady
5. Observe guidance state transitions

**Expected Outcome**:
- Bounding box appears around object within 1 second
- State transitions: SEARCHING → GOOD → LOCKED
- "Ready to scan" hint appears when LOCKED
- Scan zone border turns solid green when LOCKED
- If tapped, item is successfully added

**Failure Clues**:
| Symptom | Likely Cause |
|---------|--------------|
| No bbox appears | Object not detected by ML Kit; try different object or lighting |
| Bbox appears but state stays SEARCHING | Center outside ROI; adjust position |
| State reaches GOOD but never LOCKED | Stability not achieved; check for hand tremor or motion |
| Bbox flickers on/off | Confidence too low; move closer or improve lighting |
| "Too close" or "Too far" hint | Area thresholds; adjust distance |

---

### TEST-002: Single Object Too Close Boundary

**Test ID**: TEST-002
**Title**: Too close detection triggers correctly at boundary distance

**Object Types**: Smartphone, book cover (choose one)

**Setup**:
- Indoor lighting (normal room light)
- Plain background
- Phone held in portrait orientation
- Object placed flat on surface

**Distances**:
- Object-to-camera: **15 cm** (too close boundary)
- Object-to-object: N/A (single object test)

**Steps**:
1. Launch Scanium app and navigate to camera screen
2. Hold phone very close to the object (15 cm)
3. Center the object within the scan zone
4. Observe guidance state and hint text
5. Slowly move phone away to 40 cm and observe state change

**Expected Outcome**:
- At 15 cm: State shows TOO_CLOSE
- Hint displays "Move phone slightly away"
- Scan zone border shows orange/warning color
- Bbox may appear but won't reach LOCKED state
- At 40 cm: State transitions to GOOD → LOCKED

**Failure Clues**:
| Symptom | Likely Cause |
|---------|--------------|
| No TOO_CLOSE state even at 10 cm | tooCloseAreaThreshold (35%) too high; object bbox not filling enough frame |
| LOCKED achieved at 15 cm | Area threshold miscalculated; check `ScanRoi.MAX_CLOSE_AREA` |
| State oscillates between TOO_CLOSE and GOOD | Object at threshold boundary; normal behavior |

---

### TEST-003: Two Objects - Center Priority Selection

**Test ID**: TEST-003
**Title**: Centered object selected over off-center object

**Object Types**: TV remote (centered), smartphone (off-center)

**Setup**:
- Indoor lighting (normal room light)
- Plain background
- Phone held in portrait orientation
- Two objects placed on surface with clear separation

**Distances**:
- Object-to-camera: **45 cm**
- Object-to-object: **15 cm** (close but separated)

**Steps**:
1. Place TV remote in center of frame, smartphone to the side
2. Launch Scanium app and navigate to camera screen
3. Hold phone steady, ensuring TV remote is centered in ROI
4. Wait for lock to achieve
5. Observe which object gets the primary bbox
6. Swap positions (smartphone centered) and repeat

**Expected Outcome**:
- Centered object (TV remote) gets bbox first
- Off-center object (smartphone) either has no bbox or secondary bbox
- LOCKED state achieved on the centered object
- After swap, smartphone (now centered) is the locked target

**Failure Clues**:
| Symptom | Likely Cause |
|---------|--------------|
| Both objects get bboxes equally | Max 2 preview boxes shown; normal if both inside ROI |
| Off-center object gets LOCKED | Center scoring weight (30%) insufficient; smartphone may have higher confidence |
| Neither object locks | Both may be at ROI boundary; adjust positions |

---

### TEST-004: Slow Pan - No Background Adds

**Test ID**: TEST-004
**Title**: Panning from object A to object B does not add background items

**Object Types**: Headphones case (Object A), water bottle (Object B)

**Setup**:
- Indoor lighting (normal room light)
- Mixed background (table with some clutter is OK)
- Phone held in portrait orientation
- Two objects placed apart on surface

**Distances**:
- Object-to-camera: **50 cm**
- Object-to-object: **40 cm** (well separated)

**Steps**:
1. Place headphones case on the left, water bottle on the right
2. Launch Scanium app and navigate to camera screen
3. Point at headphones case, achieve LOCKED state
4. **Without tapping to add**, slowly pan camera toward water bottle (2-3 seconds)
5. Observe lock breaking and state transitions
6. Continue panning until water bottle is centered
7. Wait for new lock on water bottle

**Expected Outcome**:
- Lock on headphones case breaks during pan (state → SEARCHING or UNSTABLE)
- No items are automatically added during pan
- Background items (if any) do not achieve LOCKED
- Water bottle achieves LOCKED once pan stops and it's centered
- Only one item (water bottle) can be added after pan completes

**Failure Clues**:
| Symptom | Likely Cause |
|---------|--------------|
| Lock doesn't break during pan | Motion threshold (0.25) too high; instant motion check not working |
| Background item gets added during pan | Auto-add enabled (should require tap); or lock achieved during pan |
| State stays LOCKED throughout pan | Motion not detected; check `computeMotionScore()` |
| Water bottle never locks after pan | Stability timing too strict; wait longer |

---

### TEST-005: Cluttered Background - ROI Strict Enforcement

**Test ID**: TEST-005
**Title**: Only centered target detected in cluttered scene

**Object Types**: Small toy (e.g., LEGO minifig - target), various background items

**Setup**:
- Indoor lighting (normal room light)
- **Cluttered background**: bookshelf, desk with multiple items, etc.
- Phone held in portrait orientation
- Small toy placed in center foreground, 30 cm from camera

**Distances**:
- Target object-to-camera: **30 cm** (foreground)
- Background items: **80-150 cm** (varying distances)
- Target to nearest background: N/A (foreground vs background)

**Steps**:
1. Set up cluttered scene with multiple potential detection targets in background
2. Place small toy (LEGO minifig) in the center foreground
3. Launch Scanium app and navigate to camera screen
4. Hold phone steady, ensuring toy is centered in ROI
5. Observe which objects get bboxes
6. Wait for lock achievement

**Expected Outcome**:
- Toy in foreground gets bbox (inside ROI, large area due to proximity)
- Background items may get ML Kit detections but should NOT show bboxes (outside ROI or filtered)
- LOCKED state achieved on the toy only
- No "competing" bboxes on background items
- Debug overlay (if enabled) should show "Outside: X" count > 0

**Failure Clues**:
| Symptom | Likely Cause |
|---------|--------------|
| Background items show bboxes | ROI filter not working; check `RoiDetectionFilter.filterByRoi()` |
| Toy not detected | Object too small (< 3% area); move closer |
| Background item gets LOCKED | High confidence (>0.8) overriding center gate; or background item actually inside ROI |
| Multiple items in ROI | ROI size (65%) too large; consider if background items are truly inside ROI |

---

## Appendix: Quick Reference

### Guidance States
| State | Visual | Hint |
|-------|--------|------|
| SEARCHING | Blue outline | (none) |
| TOO_CLOSE | Orange outline | "Move phone slightly away" |
| TOO_FAR | Orange outline | "Move closer to object" |
| OFF_CENTER | Orange outline | "Center object in scan zone" |
| UNSTABLE | Blue outline | "Hold steady" |
| FOCUSING | Blue outline | "Focusing..." |
| GOOD | Green pulsing | "Hold still..." |
| LOCKED | Solid green | "Ready to scan" |

### Key Thresholds
| Parameter | Value | File |
|-----------|-------|------|
| Min area (too far) | 4% | `ScanRoi.MIN_FAR_AREA` |
| Max area (too close) | 35% | `ScanRoi.MAX_CLOSE_AREA` |
| Min stability frames | 3 | `CenterWeightedConfig.minStabilityFrames` |
| Min stability time | 400 ms | `CenterWeightedConfig.minStabilityTimeMs` |
| Motion break threshold | 0.25 | `ScanGuidanceConfig.lockBreakMotionThreshold` |
| ROI default size | 65% | `ScanRoi.DEFAULT` |
| Max preview bboxes | 2 | `RoiDetectionFilter.MAX_PREVIEW_BOXES` |

### Debug Mode
Enable debug overlay in Settings → Developer Options → "Show scan diagnostics" to see:
- Current guidance state
- ROI size percentage
- Box area percentage
- Motion score
- Sharpness score
- Lock status
- ROI filter stats (total/inside/outside)

---

*Document generated: 2025-12-29*
*Version: 1.0*
