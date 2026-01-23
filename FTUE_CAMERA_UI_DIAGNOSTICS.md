# Camera UI FTUE Diagnostics & Testing Guide

This document explains how to use the diagnostics tools to debug and validate the Camera UI FTUE (
First-Time User Experience) feature.

## Overview

The Camera UI FTUE teaches users about 4 main camera buttons:

1. **SHUTTER**: Camera shutter button for capturing items
2. **FLIP**: Flip camera button (front/back)
3. **ITEMS**: Items list button
4. **SETTINGS**: Settings/hamburger menu button

Each step should display:

- Dim overlay with spotlight hole around the button
- Tooltip with instruction text and "Next" button
- **Pulsating animation** on the actual button (scale 1.0 → 1.08 → 1.0, 900ms)

## Enabling Diagnostics (DEV Flavor Only)

### Step 1: Enable the FTUE Diagnostics Toggle

1. Build and install the **dev** flavor: `./gradlew :androidApp:installDevDebug`
2. Open the app and grant camera permission
3. Navigate to: **Settings → Developer Options**
4. Find the **"Camera UI FTUE"** section
5. Enable **"Show Camera UI FTUE Bounds"**

### Step 2: Reset FTUE to Start Fresh

In the same Developer Options screen:

- Tap **"Reset Camera UI FTUE"** to clear completion flag
- Return to Camera screen

## Diagnostics Panel Features

Once "Show Camera UI FTUE Bounds" is enabled, you'll see two diagnostic panels on the camera screen:

### 1. Anchor Inspector Panel (Top-Left)

Shows real-time status:

```
FTUE DIAGNOSTICS
Step: SHUTTER
Shutter: OK (72x72)
Flip: OK (48x48)
Items: OK (48x48)
Settings: OK (48x48)
```

**Interpretation**:

- **OK (WxH)**: Anchor is registered, shows actual size in pixels
- **NULL**: Anchor is NOT registered (button not composed or layout not complete)

**Expected**: All 4 anchors should show "OK" when camera screen is fully loaded.

**If any anchor shows NULL**:

- Button may not be composed (check flavor/configuration)
- Button may be in a lazy layout that hasn't been measured yet
- `onGloballyPositioned` may not have fired (button has size 0)

### 2. Force Step Buttons Panel (Bottom-Left)

Four buttons to manually trigger each step:

- **SHUTTER**: Force step to SHUTTER
- **FLIP**: Force step to FLIP_CAMERA
- **ITEMS**: Force step to ITEM_LIST
- **SETTINGS**: Force step to SETTINGS

**Use these to**:

- Test each step in isolation
- Verify overlay rendering works for all steps
- Confirm each button anchor exists and spotlight appears correctly
- Verify pulse animation is visible on the button

### 3. Debug Borders (Magenta Rectangles)

When "Show Camera UI FTUE Bounds" is enabled, you'll see:

- **Magenta borders** drawn around each registered anchor
- This immediately shows whether anchors exist and where they are positioned

## Testing Procedure

### PHASE 1: Verify All Anchors Exist

1. Enable diagnostics (see above)
2. Open Camera screen
3. Check Anchor Inspector panel:
    - ✓ All 4 anchors should show "OK (WxH)"
    - ✓ Magenta borders should appear around all 4 buttons

**If any anchor is NULL**:

- Check logcat: `adb logcat | grep FTUE_CAMERA_UI`
- Look for "Missing anchors: [...]" log message
- Verify the button is actually visible on screen
- Check if button is conditionally rendered (flavor/orientation)

### PHASE 2: Verify Each Step Renders Correctly

Use the Force Step buttons to test each step individually:

1. Tap **"SHUTTER"** force button:
    - ✓ Dim overlay should appear
    - ✓ Spotlight hole around shutter button
    - ✓ Shutter button should **pulsate** (breathing animation)
    - ✓ Tooltip should show: "Tap here to capture an item."

2. Tap **"FLIP"** force button:
    - ✓ Overlay should move to flip camera button
    - ✓ Flip button should **pulsate**
    - ✓ Tooltip: "Switch between front and rear camera."

3. Tap **"ITEMS"** force button:
    - ✓ Overlay should move to items list button
    - ✓ Items button should **pulsate**
    - ✓ Tooltip: "View and manage scanned items."

4. Tap **"SETTINGS"** force button:
    - ✓ Overlay should move to hamburger menu (top-left)
    - ✓ Settings button should **pulsate**
    - ✓ Tooltip: "Adjust how Scanium works."

### PHASE 3: Verify Normal Step Progression

1. Reset Camera UI FTUE in Developer Options
2. Return to Camera screen
3. FTUE should start automatically (if old Camera FTUE is complete)
4. Tap **"Next"** button in tooltip to advance:
    - Step 1 (SHUTTER) → Step 2 (FLIP_CAMERA) → Step 3 (ITEM_LIST) → Step 4 (SETTINGS) → COMPLETED

**Expected**: Each step shows in order with pulsating button.

### PHASE 4: Check Initialization Logs

Enable verbose logging:

```bash
adb logcat | grep FTUE_CAMERA_UI
```

**Expected logs when FTUE starts**:

```
FTUE_CAMERA_UI: Init check: permission=true, preview=1080x2400, allAnchors=true (4/4), oldFtueComplete=true
FTUE_CAMERA_UI: initialize: permission=true, preview=true, anchors=true, existingFtueComplete=true, shouldRun=true
FTUE_CAMERA_UI: FTUE starting: stepIndex=0, stepId=SHUTTER
```

**If FTUE doesn't start**, check logs for:

- `Conditions not met, FTUE will not start`
- `Missing anchors: [...]` (lists which anchors are NULL)
- `allAnchors=false` (not all 4 anchors registered)

## Common Issues & Solutions

### Issue 1: FTUE Never Starts

**Symptoms**: No overlay appears, Anchor Inspector shows all OK.

**Check**:

1. Logcat for initialization logs
2. Ensure old Camera FTUE is complete: `cameraFtueCompleted=true`
3. Check if "Force Camera UI FTUE" is enabled in Developer Options (forces FTUE even if completed)

**Solution**: Enable "Force Camera UI FTUE" to bypass completion check.

### Issue 2: Only SHUTTER and ITEMS Show, FLIP and SETTINGS Skipped

**Symptoms**: Overlay shows for SHUTTER, then jumps to ITEMS, skipping FLIP/SETTINGS.

**Likely Causes**:

- Anchors for FLIP or SETTINGS are NULL when step tries to render
- Step advancement logic is incorrect (but this is unlikely based on code review)

**Diagnosis**:

1. Use Force Step buttons to test FLIP and SETTINGS individually
2. If forcing FLIP works, but normal progression skips it → initialization race condition
3. Check if FLIP or SETTINGS anchors are NULL in Anchor Inspector when FTUE starts

**Solution**: May need to add delay or retry logic in initialization to wait for all anchors.

### Issue 3: Pulse Animation Not Visible

**Symptoms**: Overlay and spotlight work, but button doesn't pulsate.

**Check**:

- Verify `ftuePulse` modifier is applied in correct order (before background)
- Check if `cameraUiFtueStep` is passed correctly to CameraOverlay
- Look for animation framework issues (performance/frame drops)

**Solution**: This PR adds `ftuePulse` modifier to all buttons. If still not visible, increase
scale (1.08 → 1.12) or duration.

### Issue 4: Anchor is NULL for Specific Button

**Symptoms**: Anchor Inspector shows NULL for one specific button (e.g., FLIP).

**Check**:

1. Is the button actually visible on screen?
2. Check button's modifier chain includes `.ftueAnchor(id, registry)`
3. Verify button has non-zero size (minimum 48dp touch target)
4. Check if button is conditionally rendered (flavor, orientation, state)

**Solution**: Ensure `ftueAnchor` is applied to the actual button surface, not a parent container
that might be size 0.

## Code Locations

**Diagnostics**:

- `/androidApp/src/main/java/com/scanium/app/ftue/CameraUiFtueDiagnostics.kt`

**Pulse Modifier**:

- `/androidApp/src/main/java/com/scanium/app/ftue/FtuePulseModifier.kt`

**Anchor Registration**:

- `/androidApp/src/main/java/com/scanium/app/ftue/CameraUiFtueAnchorRegistry.kt`
- `/androidApp/src/main/java/com/scanium/app/camera/CameraControlsOverlay.kt` (where anchors are
  attached)

**Initialization**:

- `/androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt` (lines 296-328)

**Step Runner**:

- `/androidApp/src/main/java/com/scanium/app/ftue/CameraUiFtueViewModel.kt`

## Validation Checklist

On **DEV build** with diagnostics enabled:

- [ ] All 4 anchors show "OK" in Anchor Inspector
- [ ] Magenta borders visible around all 4 buttons
- [ ] Force SHUTTER: overlay + spotlight + pulse animation works
- [ ] Force FLIP: overlay + spotlight + pulse animation works
- [ ] Force ITEMS: overlay + spotlight + pulse animation works
- [ ] Force SETTINGS: overlay + spotlight + pulse animation works
- [ ] Normal progression: SHUTTER → FLIP → ITEMS → SETTINGS (all show in order)
- [ ] Each step has visible pulsating button (breathing effect)
- [ ] Tooltip text is correct for each step
- [ ] Tapping "Next" advances to next step correctly

On **beta/prod build** (no diagnostics):

- [ ] No diagnostic panels visible
- [ ] FTUE shows on first install (after old Camera FTUE completes)
- [ ] All 4 steps show with pulse animation
- [ ] User can complete FTUE by tapping "Next" through all steps

## Next Steps After Diagnostics

Once diagnostics confirm the issue:

1. **If anchors are NULL**: Fix anchor registration (ensure buttons are composed with correct
   modifiers)
2. **If pulse not visible**: Adjust animation parameters (scale, duration, easing)
3. **If steps skip**: Fix step advancement logic or initialization conditions
4. **If overlay doesn't show**: Check overlay rendering conditions and z-index

This diagnostic system provides deterministic, evidence-based debugging instead of guessing.
