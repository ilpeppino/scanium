***REMOVED*** Live Scan Centering Bug Analysis

***REMOVED******REMOVED*** Issue Summary

Live scanning fails to detect centered near objects while incorrectly adding out-of-focus background items during panning.

**Status:** In Progress
**Created:** 2025-12-28

---

***REMOVED******REMOVED*** Reproduction Steps

1. Place two objects (A and B) close together but separated
2. Position camera centered on object A (near, in focus)
3. Slowly pan to object B (also near, in focus)
4. **Observed:** Background items are added; objects A and B are missed
5. **Expected:** Objects A and B should be detected and added, not random background

---

***REMOVED******REMOVED*** Expected Behavior

- Scanning indicator shows activity when an object is centered in viewfinder
- Items added correspond to the centered object, not distant background
- During panning, scanning should pause item addition but show "Scanning..." status
- After pan settles, the new centered object should be detected and added
- Out-of-focus or blurry frames should NOT result in item additions

---

***REMOVED******REMOVED*** Hypothesis Checklist

***REMOVED******REMOVED******REMOVED*** 1. No Center-Weighting in Candidate Selection
**Likelihood: HIGH**

**Current behavior:** The ObjectTracker processes ALL detections equally. A small, distant background object with 0.25 confidence is treated the same as a large, centered object with 0.25 confidence.

**Evidence:**
- `TrackerConfig.minConfidence = 0.2f` - Very permissive
- `TrackerConfig.minBoxArea = 0.0005f` - Allows tiny boxes (0.05% of frame)
- `TrackerConfig.minFramesToConfirm = 1` - Immediate confirmation
- No center-distance scoring in candidate selection

**Fix:** Implement center-weighted scoring that prioritizes detections near frame center.

***REMOVED******REMOVED******REMOVED*** 2. No Sharpness/Focus Gating
**Likelihood: MEDIUM-HIGH**

**Current behavior:** Frames are processed regardless of focus state. Blurry frames during panning may contain detections of background objects that appear "larger" due to blur spreading.

**Evidence:**
- No sharpness computation in the pipeline
- No blur rejection logic
- Motion detection only affects throttle interval, not detection rejection

**Fix:** Implement lightweight sharpness score; reject detections when frame is very blurry.

***REMOVED******REMOVED******REMOVED*** 3. Stability Requirement Too Weak
**Likelihood: MEDIUM**

**Current behavior:** With `minFramesToConfirm = 1`, a candidate is immediately promoted. During panning, background objects that flash into view for a single frame can be confirmed.

**Evidence:**
- Single-frame confirmation in tracker
- Relies on session-level deduplication (happens after item is already added)

**Fix:** Add temporal stability requirement - candidate must be seen in N frames or for T milliseconds before confirmation.

***REMOVED******REMOVED******REMOVED*** 4. Area Threshold Too Permissive
**Likelihood: MEDIUM**

**Current behavior:** `minBoxArea = 0.0005f` allows boxes as small as 0.05% of frame area. Distant background objects appear small and should be filtered.

**Evidence:**
- Background objects are typically far away (small box area)
- Centered near objects should have larger box area

**Fix:** Increase minimum area threshold AND add area-based scoring component.

***REMOVED******REMOVED******REMOVED*** 5. Edge Inset Not Aggressive Enough
**Likelihood: LOW-MEDIUM**

**Current behavior:** 10% edge inset only filters detections whose CENTER is within 10% of edges. During panning, background objects may briefly appear centered.

**Evidence:**
- `EDGE_INSET_MARGIN_RATIO = 0.10f`
- Filtering based on center position only

**Fix:** This is less likely the issue since filtering exists. Focus on center-weighting instead.

***REMOVED******REMOVED******REMOVED*** 6. ROI/Coordinate Mismatch
**Likelihood: LOW**

**Current behavior:** Viewport calculation uses center-crop to match preview to analyzer. ML Kit sees full frame for classification.

**Evidence:**
- `calculateVisibleViewport()` computes proper center crop
- `setCropRect()` applied to ImageProxy
- Edge filtering uses relative coordinates

**Verification needed:** Confirm that the viewport matches what the user sees visually.

---

***REMOVED******REMOVED*** Diagnostic Data Required

For each "candidate add" decision, log:

1. **Frame info:**
   - frameId, timestamp
   - image size + rotation
   - ROI rect used (in analyzer space)

2. **Detection info:**
   - detected boxes count
   - for top box: center (x,y) normalized [0..1]
   - box area normalized
   - confidence

3. **Selection criteria:**
   - classification triggered? yes/no + reason
   - selected box vs rejected boxes (with reasons)
   - "center score" (distance from screen center) for selected box

4. **Quality metrics:**
   - sharpness score for frame
   - focus state if available
   - motion score at time of detection

---

***REMOVED******REMOVED*** Proposed Fix Summary

***REMOVED******REMOVED******REMOVED*** Phase 2: Diagnostics
- Add "Live scan diagnostics" developer toggle
- Implement structured LiveScan logs with center/area/sharpness info
- Add optional debug overlay showing selected candidate box

***REMOVED******REMOVED******REMOVED*** Phase 3: Sharpness Score
- Implement lightweight Laplacian variance computation
- Sample central 128x128 crop for efficiency
- Log sharpness score with each frame

***REMOVED******REMOVED******REMOVED*** Phase 4: Center-Weighted Selection
```
For each detection candidate:
  centerDistance = distance(boxCenter, frameCenter) / maxDistance
  area = boxArea normalized
  confidence = ML Kit confidence

  score = (confidence * 0.4) + (area * 0.3) + ((1 - centerDistance) * 0.3)

Select highest-scoring candidate, subject to gating rules.
```

***REMOVED******REMOVED******REMOVED*** Phase 5: Gating Rules
1. **Center gate:** Reject if `centerDistance > 0.35` (unless confidence > 0.8)
2. **Area gate:** Reject if `area < 0.03` (3% of frame minimum)
3. **Sharpness gate:** Reject if `sharpness < threshold` AND `area < 0.10`
4. **Stability gate:** Candidate must be selected for 3+ frames OR 400+ ms

***REMOVED******REMOVED******REMOVED*** Phase 6: Panning Behavior
- During high motion: Show "Scanning..." but DO NOT add items
- After motion settles for 200ms: Resume item addition
- Require stable candidate selection across motion boundary

---

***REMOVED******REMOVED*** Implementation Summary

***REMOVED******REMOVED******REMOVED*** Files Added/Modified

**New Files:**
- `androidApp/.../camera/SharpnessCalculator.kt` - Lightweight Laplacian variance sharpness computation
- `androidApp/.../camera/detection/LiveScanDiagnostics.kt` - Structured diagnostic logging (tag: LiveScan)
- `androidApp/.../camera/LiveScanDebugOverlay.kt` - Optional debug overlay for dev mode
- `shared/core-tracking/.../CenterWeightedCandidateSelector.kt` - Center-weighted scoring and gating
- `shared/core-tracking/...Test/CenterWeightedCandidateSelectorTest.kt` - Unit tests

**Modified Files:**
- `androidApp/.../ui/settings/DeveloperOptionsScreen.kt` - Added "Live Scan Diagnostics" toggle
- `androidApp/.../camera/CameraXManager.kt` - Integrated sharpness calculation and diagnostics
- `shared/core-tracking/.../ObjectTracker.kt` - Integrated center-weighted selection with stability

***REMOVED******REMOVED******REMOVED*** Key Parameters (Tunable)

| Parameter | Default | Purpose |
|-----------|---------|---------|
| `maxCenterDistance` | 0.35 | Max distance from center (0.5, 0.5) before rejection |
| `minArea` | 0.03 (3%) | Minimum box area to filter tiny background objects |
| `minSharpness` | 100.0 | Minimum sharpness score for small objects |
| `sharpnessAreaThreshold` | 0.10 (10%) | Area below which sharpness is checked |
| `highConfidenceOverride` | 0.8 | Confidence that bypasses center distance gate |
| `minStabilityFrames` | 3 | Consecutive frames required for stability |
| `minStabilityTimeMs` | 400 | Time in ms required for stability |

***REMOVED******REMOVED******REMOVED*** Scoring Formula

```
For each detection candidate:
  centerDistance = distance(boxCenter, frameCenter) / maxDistance
  normalizedArea = (area / 0.5).coerceIn(0, 1)
  normalizedCenterScore = 1 - (centerDistance / 0.707).coerceIn(0, 1)

  score = (confidence * 0.4) + (normalizedArea * 0.3) + (normalizedCenterScore * 0.3)
```

***REMOVED******REMOVED******REMOVED*** Gating Rules Applied

1. **Center Distance Gate**: Reject if `centerDistance > 0.35` (unless confidence > 0.8)
2. **Area Gate**: Reject if `area < 0.03` (3% of frame)
3. **Sharpness Gate**: Reject if `sharpness < 100` AND `area < 0.10` (small + blurry)
4. **Stability Gate**: Must be selected for 3+ frames OR 400+ ms before confirmation

***REMOVED******REMOVED*** Root Cause Evidence

*To be filled after testing with diagnostics enabled:*

- [ ] Logs showing background items being selected over centered items
- [ ] Center distances of added items vs rejected items
- [ ] Sharpness scores during panning
- [ ] Screenshot of debug overlay showing incorrect selection

---

***REMOVED******REMOVED*** Fix Verification

***REMOVED******REMOVED******REMOVED*** Test Matrix

| Scenario | Expected Result |
|----------|-----------------|
| Two near objects, slow pan A → B | Detect A, then later detect B; NO background items |
| Intentional out-of-focus | Should NOT add items while blurry |
| Different lighting conditions | No random background items |
| Performance check | Analyzer FPS not severely reduced |

***REMOVED******REMOVED******REMOVED*** Tuned Thresholds

*To be filled after testing:*

- `centerDistanceThreshold`: TBD (starting: 0.35)
- `minAreaThreshold`: TBD (starting: 0.03)
- `minSharpnessScore`: TBD (starting: 100.0)
- `stabilityFrames`: TBD (starting: 3)
- `stabilityTimeMs`: TBD (starting: 400)

---

***REMOVED******REMOVED*** Related Files

- `CameraXManager.kt` - Frame processing, motion detection
- `ObjectTracker.kt` - Candidate tracking and confirmation
- `ObjectDetectorClient.kt` - ML Kit detection, edge filtering
- `TrackerConfig` - Tracker thresholds
- `ScanPipelineDiagnostics.kt` - Existing diagnostics
- `DeveloperOptionsScreen.kt` - Developer settings UI
