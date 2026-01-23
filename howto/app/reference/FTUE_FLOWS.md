# FTUE Flows Reference

**Last Updated:** 2026-01-21
**Status:** Active
**Owner:** Product/Engineering

## Overview

Scanium implements a streamlined First-Time User Experience (FTUE) system across 4 active flows that guide users through the app's core functionality. The Guided Tour now integrates camera UI education, while the standalone Camera FTUE and Camera UI FTUE have been consolidated or disabled. Each flow is independently managed by dedicated ViewModels and triggers based on specific conditions.

## Table of Contents

- [FTUE System Architecture](#ftue-system-architecture)
- [Flow Execution Order](#flow-execution-order)
- [Individual Flows](#individual-flows)
  - [1. Guided Tour](#1-guided-tour-tourviewmodel)
  - [2. Camera FTUE](#2-camera-ftue-cameraftueviewmodel) (DISABLED)
  - [3. Camera UI FTUE](#3-camera-ui-ftue-camerauiftueviewmodel) (REMOVED - Integrated into Guided Tour)
  - [4. Items List FTUE](#4-items-list-ftue-itemslistftueviewmodel)
  - [5. Edit Item FTUE](#5-edit-item-ftue-edititemftueviewmodel)
  - [6. Settings FTUE](#6-settings-ftue-settingsftueviewmodel)
- [Common Mechanisms](#common-mechanisms)
- [Implementation Files](#implementation-files)

---

## FTUE System Architecture

### Core Components

| Component | Location | Purpose |
|-----------|----------|---------|
| **FtueRepository** | `androidApp/src/main/java/com/scanium/app/ftue/FtueRepository.kt` | Persists FTUE completion state using DataStore |
| **ViewModels** | `androidApp/src/main/java/com/scanium/app/ftue/*ViewModel.kt` | Manages state and logic for each FTUE flow |
| **Overlays** | `androidApp/src/main/java/com/scanium/app/ftue/*Overlay.kt` | Renders UI overlays with hints and spotlights |

### Design Principles

1. **Non-blocking**: FTUEs never prevent users from using core functionality
2. **Progressive**: Multi-step sequences that build understanding incrementally
3. **Dismissible**: Users can skip or dismiss at any time
4. **Persistent**: Completion state saved to prevent re-showing
5. **Replayable**: All FTUEs support replay via Developer Options

---

## Flow Execution Order

```
┌─────────────────────────────────────────────────────────────┐
│  APP LAUNCH                                                 │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  1. GUIDED TOUR      │ (First launch only)
        │  - Welcome           │
        │  - Shutter button    │
        │  - Flip camera       │
        │  - Items list        │
        │  - Settings          │
        └──────────┬───────────┘
                   │
        ┌──────────┴───────────┬──────────────────┐
        │                      │                  │
        ▼                      ▼                  ▼
┌──────────────┐  ┌──────────────────┐  ┌──────────────┐
│ 2. ITEMS     │  │ 3. EDIT ITEM     │  │ 4. SETTINGS  │
│    LIST FTUE │  │    FTUE          │  │    FTUE      │
│              │  │                  │  │              │
│ - Tap        │  │ - Improve        │  │ - Language   │
│ - Swipe      │  │   details        │  │ - Replay     │
│ - Long press │  │ - Condition &    │  │   guide      │
│ - Share      │  │   price          │  │              │
└──────────────┘  └──────────────────┘  └──────────────┘
```

### Sequencing Rules

1. **Guided Tour** runs on first launch and includes camera UI education
2. **Items List**, **Edit Item**, and **Settings** FTUEs are independent
3. Each flow triggers only on first visit to its screen (unless replayed)
4. **Camera FTUE** is disabled (previously taught object detection mechanics)
5. **Camera UI FTUE** is removed (now integrated into Guided Tour steps 1-4)

---

## Individual Flows

## 1. Guided Tour (TourViewModel)

**Purpose:** Streamlined onboarding introducing Scanium and teaching camera UI controls

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/TourViewModel.kt`

### Trigger Conditions

- First app launch OR
- Force enabled in Developer Options
- Runs BEFORE other FTUEs

### Steps (5 total)

| Step | Screen | Target | Title | Description | User Action |
|------|--------|--------|-------|-------------|-------------|
| 0 | Camera | Full screen | Welcome | Introduction to Scanium | Tap "Next" |
| 1 | Camera | Shutter button | Shutter Button | "Tap here to capture an item" | Tap "Next" |
| 2 | Camera | Flip camera | Flip Camera | "Switch between front and rear camera" | Tap "Next" |
| 3 | Camera | Items button | Items List | "View and manage scanned items" | Tap "Next" |
| 4 | Camera | Settings button | Settings | "Adjust how Scanium works" | Tap "Finish" |

### Key Features

- **Spotlight shapes**: Circle for buttons, rounded rectangles for UI areas
- **Camera UI focus**: Steps 1-4 replace the previous Camera UI FTUE flow
- **Single screen**: All steps occur on the Camera screen
- **Simple progression**: User taps "Next" to advance through all steps

### State Management

```kotlin
sealed class TourScreen {
    CAMERA
}

data class TourStep(
    key: TourStepKey,
    screen: TourScreen,
    targetKey: String?,        // Null for full-screen overlays
    titleRes: Int,
    descriptionRes: Int,
    requiresUserAction: Boolean,
    spotlightShape: SpotlightShape = SpotlightShape.CIRCLE
)
```

---

## 2. Camera FTUE (CameraFtueViewModel)

**Status:** DISABLED

**Previous Purpose:** Taught object detection mechanics (ROI pulse, bbox hints, shutter prompt)

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/CameraFtueViewModel.kt`

### Reason for Removal

This flow has been **disabled** to streamline the onboarding experience. Users can learn through natural exploration rather than guided detection hints. The shutter button education is now handled by the Guided Tour (step 1).

### Previous Steps (For Reference)

| # | Step | Duration | Description |
|---|------|----------|-------------|
| 1 | **ROI Pulse** | 1200ms | "Center an object here to scan it" |
| 2 | **BBox Hint** | 800ms | "Boxes appear around detected objects" |
| 3 | **Shutter Hint** | Until tap | "Tap to capture your first item" |

### Implementation Status

- ViewModel code remains in codebase but is not triggered
- Can be re-enabled if needed by adjusting trigger conditions
- Replay functionality still available via Developer Options

---

## 3. Camera UI FTUE (CameraUiFtueViewModel)

**Status:** REMOVED (Integrated into Guided Tour)

**Previous Purpose:** Taught the 4 main camera button controls independently

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/CameraUiFtueViewModel.kt`

### Reason for Removal

This flow has been **integrated into the Guided Tour** (steps 1-4). By consolidating camera UI education into the initial tour, users get a cohesive onboarding experience without redundant separate flows.

### Previous Steps (Now in Guided Tour)

| # | Button | Description | Now in Guided Tour |
|---|--------|-------------|---------------------|
| 1 | **Shutter** | "Tap here to capture an item" | Tour Step 1 |
| 2 | **Flip Camera** | "Switch between front and rear camera" | Tour Step 2 |
| 3 | **Items List** | "View and manage scanned items" | Tour Step 3 |
| 4 | **Settings** | "Adjust how Scanium works" | Tour Step 4 |

### Implementation Status

- ViewModel and overlay code can be removed from codebase
- All functionality now handled by `TourViewModel`
- Anchor registration still used by Guided Tour
- Replay functionality through Guided Tour replay

---

## 4. Items List FTUE (ItemsListFtueViewModel)

**Purpose:** Teach gesture-based item list interactions

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/ItemsListFtueViewModel.kt`

### Trigger Conditions

- First visit to Items List screen
- At least 1 item exists in the list

### Steps (4 gestures)

| # | Hint | Duration | Target Gesture | Visual Effect | Advancement |
|---|------|----------|----------------|---------------|-------------|
| 1 | **Tap to Edit** | 2500ms | Tap item | Hint overlay on first item | Auto OR user taps item |
| 2 | **Swipe to Delete** | 2500ms | Swipe right | **Nudge animation** (25% right, snap back in 800ms) | Auto OR user swipes |
| 3 | **Long-Press** | 2500ms | Long press | Hint overlay | Auto OR user long-presses |
| 4 | **Share Goal** | 2500ms | - | Final hint about export | Auto |

### Timing Constants

```kotlin
INITIAL_DELAY_MS = 500L
STEP_DISPLAY_DURATION_MS = 2500L
NUDGE_ANIMATION_DURATION_MS = 800L
STEP_TRANSITION_DELAY_MS = 300L
```

### Nudge Animation

The swipe hint includes a visual nudge animation:
```kotlin
// Offset row to the right (0 → 0.25 → 0)
for (i in 0..20) {
    swipeNudgeProgress = (i / 20) * 0.25f
    delay(40ms)
}
swipeNudgeProgress = 0f  // Snap back
```

### Key Features

- **Gesture recognition**: User performing the actual gesture advances immediately
- **Non-destructive**: Nudge animation doesn't actually delete the item
- **Progressive teaching**: Builds from simple (tap) to complex (long-press)

---

## 5. Edit Item FTUE (EditItemFtueViewModel)

**Purpose:** Guide users to improve item details for better listings

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/EditItemFtueViewModel.kt`

### Trigger Conditions

- First visit to Edit Item screen

### Steps (2 active)

| # | Hint | Target | Duration | Description | Advancement |
|---|------|--------|----------|-------------|-------------|
| 1 | **Improve Details** | First field | 2500ms | "Improve details to get better listings" | Auto OR user edits field |
| 2 | **Condition & Price** | Condition field | 2500ms | "Set condition and price for better results" | Auto OR user sets condition/price |
| ~~3~~ | ~~AI Assistant~~ | ~~AI button~~ | ~~N/A~~ | **Removed** (was Dev-only) | N/A |

### Timing Constants

```kotlin
INITIAL_DELAY_MS = 500L
STEP_DISPLAY_DURATION_MS = 2500L
STEP_TRANSITION_DELAY_MS = 300L
```

### State Flow

```
IDLE → WAITING_DETAILS_HINT → DETAILS_HINT_SHOWN →
WAITING_CONDITION_PRICE_HINT → CONDITION_PRICE_HINT_SHOWN → COMPLETED
```

### Key Features

- **Field-focused**: Highlights specific editable fields
- **Completion on interaction**: User editing fields advances immediately
- **Short sequence**: Only 2 steps, focused on key improvements

---

## 6. Settings FTUE (SettingsFtueViewModel)

**Purpose:** Minimal guidance for key settings discoverability

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/SettingsFtueViewModel.kt`

### Trigger Conditions

- First visit to Settings screen

### Steps (2 minimal)

| # | Hint | Target | Duration | Description | Advancement |
|---|------|--------|----------|-------------|-------------|
| 1 | **Language** | Language option | 2000ms | "Set your language once — AI and voice follow it" | Auto OR user changes language |
| 2 | **Replay Guide** | Replay option | 2000ms | "You can replay this guide anytime" | Auto |

### Timing Constants

```kotlin
INITIAL_DELAY_MS = 500L
STEP_DISPLAY_DURATION_MS = 2000L  // Shorter than other FTUEs
STEP_TRANSITION_DELAY_MS = 300L
```

### Key Features

- **Shorter hints**: 2000ms vs 2500ms for other FTUEs
- **Minimal guidance**: Only 2 steps, covering essential settings
- **Discoverability focus**: Points out language unification and replay option

---

## Common Mechanisms

### Persistence (FtueRepository)

All FTUE completion states are stored in `DataStore`:

```kotlin
// Per-FTUE completion flags
cameraFtueCompletedFlow: Flow<Boolean>
cameraUiFtueCompletedFlow: Flow<Boolean>
listFtueCompletedFlow: Flow<Boolean>
editFtueCompletedFlow: Flow<Boolean>
settingsFtueCompletedFlow: Flow<Boolean>
tourCompletedFlow: Flow<Boolean>

// Individual step tracking (for resumption/debugging)
cameraRoiHintSeenFlow: Flow<Boolean>
cameraBboxHintSeenFlow: Flow<Boolean>
cameraShutterHintSeenFlow: Flow<Boolean>
// ... (similar for other FTUEs)
```

### Replay Mechanism

All FTUEs implement `resetForReplay()`:

```kotlin
fun resetForReplay() {
    viewModelScope.launch {
        cancelTimeouts()
        // Reset all StateFlows
        _showHint.value = false
        _isActive.value = false
        _currentStep.value = IDLE

        // Clear persistence flags
        ftueRepository.setFtueCompleted(false)
        ftueRepository.setStepHintSeen(false)

        // Re-initialize
        initialize(shouldStartFtue = true)
    }
}
```

Accessible via:
- Developer Options → "Replay first-time guide"
- Per-FTUE reset buttons in Developer Options

### Dismissal Behavior

User can dismiss any FTUE:

```kotlin
fun dismiss() {
    viewModelScope.launch {
        cancelTimeouts()
        hideAllHints()
        _isActive.value = false
        _currentStep.value = COMPLETED
        // Mark as seen even if dismissed early
        ftueRepository.setFtueCompleted(true)
    }
}
```

### State Management Pattern

All FTUE ViewModels follow this pattern:

```kotlin
// State enum
enum class FtueStep {
    IDLE, WAITING_X, X_SHOWN, WAITING_Y, Y_SHOWN, COMPLETED
}

// StateFlows
private val _currentStep = MutableStateFlow(FtueStep.IDLE)
val currentStep: StateFlow<FtueStep> = _currentStep.asStateFlow()

private val _isActive = MutableStateFlow(false)
val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

private val _showHint = MutableStateFlow(false)
val showHint: StateFlow<Boolean> = _showHint.asStateFlow()

// Coroutine-based timing
private var stepTimeoutJob: Job? = null
```

### Overlay Rendering

Each FTUE has a corresponding overlay composable:

```kotlin
@Composable
fun FtueOverlay(
    isActive: Boolean,
    currentStep: FtueStep,
    showHint: Boolean,
    targetBounds: Rect?,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isActive) return

    // Scrim + Spotlight + Tooltip
    Box(modifier = Modifier.fillMaxSize()) {
        // Dark scrim with spotlight cutout
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawScrim(targetBounds)
        }

        // Tooltip with hint text
        if (showHint) {
            TooltipBubble(
                text = stringResource(hintTextRes),
                targetBounds = targetBounds,
                onNext = onNext,
                onDismiss = onDismiss
            )
        }
    }
}
```

---

## Implementation Files

### ViewModels

| File | FTUE | Status | Complexity |
|------|------|--------|------------|
| `TourViewModel.kt` | Guided Tour | **Active** (includes camera UI steps) | Medium (simplified single-screen) |
| `CameraFtueViewModel.kt` | Camera FTUE | **Disabled** | Medium (detection integration) |
| `CameraUiFtueViewModel.kt` | Camera UI FTUE | **Removed** (to be deleted) | N/A |
| `ItemsListFtueViewModel.kt` | Items List FTUE | **Active** | Medium (gesture detection + nudge) |
| `EditItemFtueViewModel.kt` | Edit Item FTUE | **Active** | Low (simple field hints) |
| `SettingsFtueViewModel.kt` | Settings FTUE | **Active** | Low (minimal guidance) |

### Persistence

| File | Purpose |
|------|---------|
| `FtueRepository.kt` | DataStore persistence for all FTUE state |

### UI Overlays

| File | Purpose | Status |
|------|---------|--------|
| `TourOverlay.kt` | Guided tour overlay with spotlight (includes camera UI) | **Active** |
| `CameraFtueOverlay.kt` | Camera FTUE hints and animations | **Disabled** |
| `CameraUiFtueOverlay.kt` | Camera UI button tutorials | **Removed** (to be deleted) |
| `ItemsListFtueOverlay.kt` | Items list gesture hints | **Active** |
| `EditItemFtueOverlay.kt` | Edit item field hints | **Active** |
| `SettingsFtueOverlay.kt` | Settings hints | **Active** |

### Support Files

| File | Purpose |
|------|---------|
| `TourStep.kt` | Tour step data models |
| `TourStepKey.kt` | Tour step identifiers |
| `SpotlightShape.kt` | Spotlight geometry (Circle, RoundedRect) |
| `FtueAnchorRegistry.kt` | Camera UI anchor registration |

---

## Developer Notes

### Testing FTUEs

1. **Clear all FTUE state:**
   ```bash
   # Via Developer Options
   Settings → Developer Options → "Reset All FTUE Flags"

   # Or via ADB
   adb shell pm clear com.scanium.app.dev
   ```

2. **Test individual flows:**
   - Enable "Developer Mode" in Settings
   - Navigate to Developer Options
   - Use per-FTUE reset buttons

3. **Force-enable flows:**
   - Some FTUEs support force-enable flags (e.g., Guided Tour)
   - Accessible via Developer Options switches

### Adding New FTUE Steps

1. **Add step to enum:**
   ```kotlin
   enum class YourFtueStep {
       IDLE, WAITING_NEW_STEP, NEW_STEP_SHOWN, COMPLETED
   }
   ```

2. **Add StateFlow for hint:**
   ```kotlin
   private val _showNewHint = MutableStateFlow(false)
   val showNewHint: StateFlow<Boolean> = _showNewHint.asStateFlow()
   ```

3. **Implement step logic:**
   ```kotlin
   private suspend fun showNewHint() {
       _currentStep.value = YourFtueStep.NEW_STEP_SHOWN
       _showNewHint.value = true
       ftueRepository.setNewHintSeen(true)

       delay(STEP_DURATION_MS)
       _showNewHint.value = false

       advanceToNextStep()
   }
   ```

4. **Add persistence:**
   ```kotlin
   // In FtueRepository
   suspend fun setNewHintSeen(seen: Boolean) {
       context.dataStore.edit { it[NEW_HINT_SEEN] = seen }
   }
   ```

5. **Update overlay UI:**
   - Add hint to overlay composable
   - Wire up StateFlow to visibility

### Debugging Tips

- **Enable logs:** Set `BuildConfig.FLAVOR = "dev"` to see step transitions
- **Check anchor registration:** Camera UI FTUE requires all 4 anchors
- **Verify trigger conditions:** Use `initialize()` logs to diagnose why FTUE doesn't start
- **Test dismissal:** Ensure early dismissal marks as completed

---

## Recent Changes

**2026-01-21**: Streamlined FTUE system
- Integrated Camera UI FTUE into Guided Tour (steps 1-4)
- Disabled Camera FTUE (object detection hints)
- Reduced onboarding complexity from 6 flows to 4 active flows
- Simplified Guided Tour to single-screen experience

## Future Enhancements

Potential improvements to the FTUE system:

1. **Analytics integration**: Track completion rates per FTUE
2. **A/B testing**: Test different hint durations/sequences
3. **Conditional steps**: Skip steps based on user behavior
4. **Voice hints**: Add optional voice narration
5. **Localization**: Translate all hint strings
6. **Accessibility**: Ensure screen reader compatibility

---

## Related Documentation

- [Settings IA](./SETTINGS_IA.md) - Settings screen structure
- [Camera Pipeline](./architecture-camera-pipeline.md) - Camera detection flow
- [Item Editing](./ITEM_EDITING.md) - Edit item screen architecture

---

**Document Version:** 1.0
**Last Review:** 2026-01-19
