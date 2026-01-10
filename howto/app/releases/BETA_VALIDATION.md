***REMOVED*** Beta Validation Guide

This document describes manual test scenarios and exit criteria for validating the scanning experience.

***REMOVED******REMOVED*** Purpose

Validate that:
1. Users clearly understand how to scan
2. Visual feedback is unambiguous
3. No background items are added
4. Testers can self-correct without instructions

***REMOVED******REMOVED*** Test Scenarios

***REMOVED******REMOVED******REMOVED*** Scenario 1: Immediate Detection Feedback

**Steps:**
1. Launch camera
2. Point at an object

**Expected:**
- Bounding box appears immediately when object is inside scan zone
- Box has PREVIEW style (thin, blue)
- No delay between detection and visual feedback

**Pass criteria:** Bbox visible within 500ms of object entering frame

---

***REMOVED******REMOVED******REMOVED*** Scenario 2: Distance Warning (Too Close)

**Steps:**
1. Launch camera
2. Hold phone very close to object (< 15cm)

**Expected:**
- Scan zone border shows warning color (orange)
- Hint appears: "Move phone slightly away"
- Bbox does NOT reach LOCKED state

**Pass criteria:** Warning feedback prevents premature lock

---

***REMOVED******REMOVED******REMOVED*** Scenario 3: Distance Warning (Too Far)

**Steps:**
1. Launch camera
2. Point at object from > 80cm away

**Expected:**
- Scan zone may expand slightly
- Hint appears: "Move closer to object"
- Small bbox visible (if detected) stays in PREVIEW state

**Pass criteria:** User understands they need to move closer

---

***REMOVED******REMOVED******REMOVED*** Scenario 4: Correct Distance + Steady = Ready

**Steps:**
1. Point at object at optimal distance (30-50cm)
2. Center object in scan zone
3. Hold steady for 0.5 second

**Expected:**
- Bbox transitions: PREVIEW → READY → LOCKED
- Scan zone border turns green
- Brief "Ready to scan" hint appears
- Pulse animation on LOCKED bbox

**Pass criteria:** Clear visual progression to scan-ready state

---

***REMOVED******REMOVED******REMOVED*** Scenario 5: Shutter Without Eligible Bbox

**Steps:**
1. Point camera away from objects (or with object outside scan zone)
2. Tap shutter button

**Expected:**
- If object detected outside ROI: "Center object in scan zone for better accuracy" toast
- Capture proceeds (not blocked)
- Result explains reduced accuracy if applicable

**Pass criteria:** User understands why scan may be less accurate

---

***REMOVED******REMOVED******REMOVED*** Scenario 6: Live Scan vs Picture Consistency

**Steps:**
1. Start continuous scan (long-press shutter)
2. Wait for LOCKED state, note detected item
3. Stop scanning
4. Tap shutter to capture same scene

**Expected:**
- Both modes use same ROI
- Same object detected in both modes
- Consistent classification results

**Pass criteria:** No behavioral gap between modes

---

***REMOVED******REMOVED******REMOVED*** Scenario 7: Multiple Objects - Only Centered Selected

**Steps:**
1. Place two objects in view
2. Center one object, keep other at edge
3. Wait for LOCKED state

**Expected:**
- Only the centered object reaches LOCKED state
- Edge object stays in PREVIEW (or not shown if outside ROI)
- Only centered object is added when scanned

**Pass criteria:** Background objects never accidentally added

---

***REMOVED******REMOVED******REMOVED*** Scenario 8: Motion Breaks Lock

**Steps:**
1. Achieve LOCKED state on an object
2. Quickly pan camera to different area

**Expected:**
- Lock breaks immediately
- State returns to SEARCHING or new detection
- Previous locked item is not added

**Pass criteria:** Panning never adds wrong items

---

***REMOVED******REMOVED******REMOVED*** Scenario 9: State Transitions are Obvious

**Steps:**
1. Observe the scanning flow from start to lock

**Expected:**
- PREVIEW bbox clearly different from LOCKED bbox
- Color change is obvious (blue → green)
- Stroke thickness change is visible
- Pulse animation draws attention to lock

**Pass criteria:** Users can describe state differences without explanation

---

***REMOVED******REMOVED******REMOVED*** Scenario 10: Hints Don't Stack

**Steps:**
1. Rapidly change conditions (move close, move far, shake camera)

**Expected:**
- Only one hint shown at a time
- Hints transition smoothly, not stacking
- No flickering text

**Pass criteria:** Clean hint transitions, no visual chaos

---

***REMOVED******REMOVED*** Exit Criteria

The beta release is ready when:

***REMOVED******REMOVED******REMOVED*** Must Pass
- [ ] No background items added (Scenarios 7, 8)
- [ ] Users can self-correct distance without explanation (Scenarios 2, 3)
- [ ] Clear visual progression PREVIEW → READY → LOCKED (Scenario 4)
- [ ] Shutter tap provides feedback when no bbox (Scenario 5)
- [ ] Live and picture modes are consistent (Scenario 6)

***REMOVED******REMOVED******REMOVED*** Should Pass
- [ ] Immediate bbox feedback (< 500ms) (Scenario 1)
- [ ] State differences are obvious to new users (Scenario 9)
- [ ] Hints never stack or flicker (Scenario 10)

***REMOVED******REMOVED******REMOVED*** Nice to Have
- [ ] Avg time-to-lock < 800ms in good conditions
- [ ] Lock success rate > 70% of attempts
- [ ] < 20% shutter taps without eligible bbox

***REMOVED******REMOVED*** Metrics to Monitor

Check `ScanGuidanceManager.metrics` during testing:

```kotlin
val metrics = scanGuidanceManager.metrics.getSnapshot()
Log.d("BetaTest", metrics.toDebugString())
```

Key metrics:
- `previewBboxPct`: Should be > 60% during active scanning
- `lockPct`: Should be > 20% when conditions are good
- `avgTimeToLockMs`: Target < 800ms
- `shutterWithoutBboxPct`: Monitor for UX issues
- `unlockReasons`: Identify most common failure modes

***REMOVED******REMOVED*** Reporting Issues

When reporting scanning issues, include:
1. Device model and Android version
2. Lighting conditions
3. Object type being scanned
4. Steps to reproduce
5. Metrics snapshot if available (from debug overlay)

***REMOVED******REMOVED*** Related Documents

- [SCANNING_GUIDANCE.md](./SCANNING_GUIDANCE.md) - Technical details
- [REVIEW_REPORT.md](./REVIEW_REPORT.md) - Code review findings
