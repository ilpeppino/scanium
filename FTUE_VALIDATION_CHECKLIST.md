# FTUE Validation Checklist

This checklist provides deterministic testing protocol to verify FTUE (First-Time User Experience)
visibility and functionality.

## Prerequisites

- **Build**: DEV flavor debug APK installed (`./gradlew :androidApp:assembleDevDebug`)
- **State**: All FTUE flags reset (Developer Options → Reset Tour Progress)
- **Data**: No scanned items in database (fresh install or clear app data)

---

## Camera FTUE Validation

### Test 1: First Launch (No Items)

**Steps:**

1. Grant camera permission when prompted
2. Wait 1 second for camera initialization
3. **Expected:** ROI pulse hint appears in center of camera view
4. **Expected:** After ~1.2s, ROI pulse disappears
5. **Expected:** After ~3s timeout (or on first detection), Shutter hint appears
6. **Expected:** Tap shutter button → FTUE completes

**Logs to verify:**

```
FTUE: initialize: shouldStartFtue=true, hasExistingItems=false
FTUE: FTUE starting: step=WAITING_ROI
FTUE: Step transition: ROI_HINT_SHOWN, overlayVisible=true
FTUE: Step transition: SHUTTER_HINT_SHOWN, overlayVisible=true
FTUE: Camera FTUE sequence completed
```

**Toast notifications (DEV build):**

- "FTUE Camera step=WAITING_ROI"
- "FTUE Camera step=ROI_HINT_SHOWN"
- "FTUE Camera step=SHUTTER_HINT_SHOWN"

### Test 2: Force FTUE (With Items)

**Steps:**

1. Complete Camera FTUE and capture at least 1 item
2. Enable "Force First-Time Tour" in Developer Options
3. Navigate back to camera
4. **Expected:** FTUE shows again even though items exist
5. **Expected:** Can dismiss with tap outside overlay

**Verification:**

- Force flag overrides completion status
- FTUE triggers despite having items

### Test 3: Reset FTUE

**Steps:**

1. Complete Camera FTUE normally
2. Go to Developer Options → Reset Tour Progress
3. Return to camera screen
4. **Expected:** FTUE shows again from beginning (ROI → Shutter)

**Logs to verify:**

```
FTUE: initialize: shouldStartFtue=true, hasExistingItems=...
FTUE: FTUE starting: step=WAITING_ROI
```

### Test 4: Early Flag Setting Fixed (Regression Test)

**Steps:**

1. Install clean build
2. Open camera WITHOUT granting permission
3. Check logs
4. **Expected:** NO call to `setCameraFtueCompleted(true)`
5. **Expected:** Log shows "FTUE not starting (preconditions not met)"
6. Grant permission
7. **Expected:** FTUE now shows

**Critical verification:**

- Completion flag is NOT set when preconditions fail
- FTUE appears after preconditions are met

---

## Items List FTUE Validation

### Test 1: First Visit With Items

**Steps:**

1. Capture at least 1 item from camera
2. Navigate to Items List screen
3. **Expected:** Tap hint appears on first item row
4. **Expected:** Auto-advances through all 4 steps:
    - Tap to edit
    - Swipe right to delete (with nudge animation)
    - Long-press to select
    - Share/export goal

**Logs to verify:**

```
FTUE: initialize: shouldStartFtue=true, itemCount=1
FTUE: FTUE starting: step=WAITING_TAP_HINT
FTUE: Step transition: TAP_HINT_SHOWN, overlayVisible=true
FTUE: Step transition: SWIPE_HINT_SHOWN, overlayVisible=true
FTUE: Step transition: LONG_PRESS_HINT_SHOWN, overlayVisible=true
FTUE: Step transition: SHARE_GOAL_SHOWN, overlayVisible=true
```

### Test 2: Empty List (No FTUE)

**Steps:**

1. Reset FTUE
2. Delete all items from list
3. Navigate to Items List screen
4. **Expected:** No FTUE triggers (itemCount=0 precondition)
5. **Expected:** Log shows "FTUE not starting (preconditions not met)"

**Verification:**

- FTUE correctly skips when no items exist
- No completion flag set

---

## Edit Item FTUE Validation

### Test 1: First Edit

**Steps:**

1. Reset FTUE
2. Tap on an item to open edit screen
3. **Expected:** Details improvement hint appears on first editable field
4. **Expected:** Auto-advances to Condition/Price hint
5. **Expected:** Completes automatically after showing both hints

**Logs to verify:**

```
FTUE: initialize: shouldStartFtue=true, isDevBuild=true
FTUE: FTUE starting: step=WAITING_DETAILS_HINT
FTUE: Step transition: DETAILS_HINT_SHOWN, overlayVisible=true
FTUE: Step transition: CONDITION_PRICE_HINT_SHOWN, overlayVisible=true
FTUE: Edit Item FTUE sequence completed
```

---

## Settings FTUE Validation

### Test 1: First Settings Visit

**Steps:**

1. Reset FTUE
2. Navigate to Settings screen
3. **Expected:** Language hint appears on "Region & language" row
4. **Expected:** Auto-advances to Replay hint on "Replay first-time guide" row
5. **Expected:** Completes automatically

**Logs to verify:**

```
FTUE: initialize: shouldStartFtue=true
FTUE: FTUE starting: step=WAITING_LANGUAGE_HINT
FTUE: Step transition: LANGUAGE_HINT_SHOWN, overlayVisible=true
FTUE: Step transition: REPLAY_HINT_SHOWN, overlayVisible=true
```

---

## Debug Tools Validation

### Test 1: FTUE Debug State Display

**Steps:**

1. Enable FTUE (reset or force)
2. Navigate to Developer Options
3. Scroll to "FTUE Debug Info" section
4. Navigate through Camera/List/Edit/Settings screens
5. **Expected:** "Current Screen" updates to active screen name
6. **Expected:** "Current Step" shows current step enum name
7. **Expected:** "Last Anchor Rect" shows coordinates (x, y, w, h) when overlay renders
8. **Expected:** "Overlay Rendered" shows "Yes" when FTUE is active

**Verification:**

- Debug state updates in real-time
- All fields display accurate information

### Test 2: Debug Bounds Visualization

**Steps:**

1. Enable "Show FTUE Debug Bounds" in Developer Options
2. Trigger any FTUE sequence
3. **Expected:** White outline rectangle visible around spotlight target
4. **Expected:** Logs show target bounds, screen size, geometry calculations

**Verification:**

- Debug visualization helps identify misaligned overlays
- Bounds are accurate (not offscreen or zero-sized)

### Test 3: Toast Notifications (DEV Build Only)

**Steps:**

1. Trigger any FTUE sequence in DEV build
2. **Expected:** Toast appears when step starts: "FTUE [Screen] step=[STEP_NAME]"
3. **Expected:** Toast updates on each step transition

**Example toasts:**

- "FTUE Camera step=WAITING_ROI"
- "FTUE ItemsList step=TAP_HINT_SHOWN"
- "FTUE EditItem step=DETAILS_HINT_SHOWN"
- "FTUE Settings step=LANGUAGE_HINT_SHOWN"

**Verification:**

- Toasts confirm FTUE is triggering
- Removes ambiguity about invisible FTUE

---

## Regression Tests

### Test 1: Completion Flags NOT Set Early

**Critical test to verify the primary bug fix.**

**Steps:**

1. Install clean build
2. Open camera with `hasCameraPermission=false`
3. Check logs
4. **Expected:** NO call to `setCameraFtueCompleted(true)`
5. **Expected:** Log shows "FTUE not starting (preconditions not met)"
6. Grant permission later
7. **Expected:** FTUE now shows

**Similar tests for other screens:**

- Items List: Open with 0 items → no flag set
- Edit Item: Open with `shouldStartFtue=false` → no flag set
- Settings: Open with `shouldStartFtue=false` → no flag set

**Verification:**

- Completion flags ONLY set after actual completion or dismissal
- Flags NOT set when preconditions fail

### Test 2: Flags Set on Actual Completion

**Steps:**

1. Complete any FTUE sequence normally (all steps)
2. Check logs
3. **Expected:** "[Screen] FTUE sequence completed" logged
4. **Expected:** `set[Screen]FtueCompleted(true)` called ONLY at end
5. Revisit screen
6. **Expected:** FTUE doesn't show again

**Verification:**

- Flags correctly mark tour as seen after completion
- FTUE doesn't repeat unnecessarily

### Test 3: Flags Set on Dismiss

**Steps:**

1. Start any FTUE sequence
2. Tap "Skip" or dismiss overlay
3. Check logs
4. **Expected:** Completion flag set to true (user explicitly dismissed)
5. Revisit screen
6. **Expected:** FTUE doesn't show

**Verification:**

- User can permanently dismiss FTUE
- Dismissal is respected across sessions

---

## Pass Criteria

All tests must pass for FTUE to be considered functional:

- ✅ **Camera FTUE**: All 4 tests pass
- ✅ **Items List FTUE**: Both tests pass
- ✅ **Edit Item FTUE**: Test passes
- ✅ **Settings FTUE**: Test passes
- ✅ **Debug Tools**: All 3 tests pass
- ✅ **Regression Tests**: All 3 tests pass

**Total Tests**: 13 tests

---

## Failure Troubleshooting

### FTUE Not Appearing

1. **Check logs**: Is `initialize()` being called?
2. **Check preconditions**: Are all trigger conditions met?
3. **Check flags**: Reset FTUE flags via Developer Options
4. **Check debug state**: Does "Current Screen" show the right screen?
5. **Check bounds**: Enable "Show FTUE Debug Bounds" – is spotlight visible?

### FTUE Appears But Invisible

1. **Check z-ordering**: Is overlay behind camera preview?
2. **Check alpha**: Is dim layer alpha = 0.7 (not 0.0)?
3. **Check anchors**: Does "Last Anchor Rect" show valid coordinates (not "Not captured")?
4. **Check debug bounds**: White outline should match target element

### FTUE Triggers But Immediately Completes

1. **Check logs**: Are step transitions happening too fast?
2. **Check timeout values**: Review delay constants in ViewModels
3. **Check dismissal logic**: Is `dismiss()` being called unexpectedly?

---

## Post-Validation Steps

After all tests pass:

1. **Create PR** with validation results
2. **Demonstrate FTUE** on-device via screen recording
3. **Proceed to Phase 4**: Localization (extract strings to resources)

---

**Document Version**: 1.0
**Last Updated**: Implementation of FTUE visibility fix
**Author**: Claude Code (Implementation Assistant)
