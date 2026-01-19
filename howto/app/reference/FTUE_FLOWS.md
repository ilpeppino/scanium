***REMOVED*** FTUE Flows Reference

**Last Updated:** 2026-01-19
**Status:** Active
**Owner:** Product/Engineering

***REMOVED******REMOVED*** Overview

Scanium implements a comprehensive First-Time User Experience (FTUE) system across 6 distinct flows that guide users through the app's core functionality. Each flow is independently managed by dedicated ViewModels and triggers based on specific conditions.

***REMOVED******REMOVED*** Table of Contents

- [FTUE System Architecture](***REMOVED***ftue-system-architecture)
- [Flow Execution Order](***REMOVED***flow-execution-order)
- [Individual Flows](***REMOVED***individual-flows)
  - [1. Guided Tour](***REMOVED***1-guided-tour-tourviewmodel)
  - [2. Camera FTUE](***REMOVED***2-camera-ftue-cameraftueviewmodel)
  - [3. Camera UI FTUE](***REMOVED***3-camera-ui-ftue-camerauiftueviewmodel)
  - [4. Items List FTUE](***REMOVED***4-items-list-ftue-itemslistftueviewmodel)
  - [5. Edit Item FTUE](***REMOVED***5-edit-item-ftue-edititemftueviewmodel)
  - [6. Settings FTUE](***REMOVED***6-settings-ftue-settingsftueviewmodel)
- [Common Mechanisms](***REMOVED***common-mechanisms)
- [Implementation Files](***REMOVED***implementation-files)

---

***REMOVED******REMOVED*** FTUE System Architecture

***REMOVED******REMOVED******REMOVED*** Core Components

| Component | Location | Purpose |
|-----------|----------|---------|
| **FtueRepository** | `androidApp/src/main/java/com/scanium/app/ftue/FtueRepository.kt` | Persists FTUE completion state using DataStore |
| **ViewModels** | `androidApp/src/main/java/com/scanium/app/ftue/*ViewModel.kt` | Manages state and logic for each FTUE flow |
| **Overlays** | `androidApp/src/main/java/com/scanium/app/ftue/*Overlay.kt` | Renders UI overlays with hints and spotlights |

***REMOVED******REMOVED******REMOVED*** Design Principles

1. **Non-blocking**: FTUEs never prevent users from using core functionality
2. **Progressive**: Multi-step sequences that build understanding incrementally
3. **Dismissible**: Users can skip or dismiss at any time
4. **Persistent**: Completion state saved to prevent re-showing
5. **Replayable**: All FTUEs support replay via Developer Options

---

***REMOVED******REMOVED*** Flow Execution Order

```
┌─────────────────────────────────────────────────────────────┐
│  APP LAUNCH                                                 │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  1. GUIDED TOUR      │ (First launch only)
        │  - Welcome           │
        │  - Take photo        │
        │  - Open list         │
        │  - Edit/AI/Share     │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  2. CAMERA FTUE      │ (After permission granted)
        │  - ROI pulse         │
        │  - BBox hint         │
        │  - Shutter hint      │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  3. CAMERA UI FTUE   │ (After Camera FTUE completes)
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
│ 4. ITEMS     │  │ 5. EDIT ITEM     │  │ 6. SETTINGS  │
│    LIST FTUE │  │    FTUE          │  │    FTUE      │
│              │  │                  │  │              │
│ - Tap        │  │ - Improve        │  │ - Language   │
│ - Swipe      │  │   details        │  │ - Replay     │
│ - Long press │  │ - Condition &    │  │   guide      │
│ - Share      │  │   price          │  │              │
└──────────────┘  └──────────────────┘  └──────────────┘
```

***REMOVED******REMOVED******REMOVED*** Sequencing Rules

1. **Guided Tour** → **Camera FTUE** → **Camera UI FTUE** (sequential)
2. **Items List**, **Edit Item**, and **Settings** FTUEs are independent
3. **Camera UI FTUE** waits for both Guided Tour AND Camera FTUE completion
4. Each flow triggers only on first visit to its screen (unless replayed)

---

***REMOVED******REMOVED*** Individual Flows

***REMOVED******REMOVED*** 1. Guided Tour (TourViewModel)

**Purpose:** Comprehensive end-to-end onboarding showing the complete Scanium workflow

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/TourViewModel.kt`

***REMOVED******REMOVED******REMOVED*** Trigger Conditions

- First app launch OR
- Force enabled in Developer Options
- Runs BEFORE other FTUEs

***REMOVED******REMOVED******REMOVED*** Steps (9 total)

| Step | Screen | Target | Title | Description | User Action |
|------|--------|--------|-------|-------------|-------------|
| 0 | Camera | Full screen | Welcome | Introduction to Scanium | Tap "Next" |
| 1 | Camera | Shutter button | Take First Photo | Guide to capturing first item | **Must capture item** |
| 2 | Camera | Items button | Open Item List | Navigate to items | **Must navigate** |
| 3 | Edit Item | Add photo button | Add Extra Photos | Add multiple angles | Tap "Next" |
| 4 | Edit Item | Brand field | Edit Attributes | Improve item details | Tap "Next" |
| 5 | Edit Item | AI button | Use AI Assistant | Generate descriptions | Tap "Next" |
| 6 | Edit Item | Save button | Save Changes | Save edits | **Must navigate back** |
| 7 | Items List | Share button | Share Bundle | Export for selling | Tap "Next" |
| 8 | Items List | Full screen | Completion | Tour complete | Tap "Finish" |

***REMOVED******REMOVED******REMOVED*** Key Features

- **Spotlight shapes**: Circle for buttons, rounded rectangles for UI areas
- **Demo item creation**: Automatically creates a demo item if items list is empty
- **User-driven progression**: Steps 1, 2, 6 require actual user actions
- **Context-aware**: Adapts to whether user has existing items

***REMOVED******REMOVED******REMOVED*** State Management

```kotlin
sealed class TourScreen {
    CAMERA, EDIT_ITEM, ITEMS_LIST
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

***REMOVED******REMOVED*** 2. Camera FTUE (CameraFtueViewModel)

**Purpose:** Teach object detection mechanics and first capture

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/CameraFtueViewModel.kt`

***REMOVED******REMOVED******REMOVED*** Trigger Conditions

- Camera permission granted
- User has NO existing items
- First visit only (unless replayed)
- Tour completed or inactive

***REMOVED******REMOVED******REMOVED*** Steps (3 sequential)

| ***REMOVED*** | Step | Duration | Description | Visual Effect | Advancement |
|---|------|----------|-------------|---------------|-------------|
| 1 | **ROI Pulse** | 1200ms | "Center an object here to scan it" | Pulsating ROI box overlay | Auto after duration |
| 2 | **BBox Hint** | 800ms | "Boxes appear around detected objects" | Glow effect on first detection | Auto after duration OR first detection (3s timeout) |
| 3 | **Shutter Hint** | Until tap | "Tap to capture your first item" | Pulsating shutter button | User taps shutter |

***REMOVED******REMOVED******REMOVED*** Timing Constants

```kotlin
ROI_PULSE_DURATION_MS = 1200L
ROI_HINT_INITIAL_DELAY_MS = 800L        // Delay before first hint
BBOX_DETECTION_TIMEOUT_MS = 3000L       // Wait for first detection
BBOX_GLOW_DURATION_MS = 800L
SHUTTER_HINT_DELAY_MS = 500L
```

***REMOVED******REMOVED******REMOVED*** State Machine

```
IDLE → WAITING_ROI → ROI_HINT_SHOWN → WAITING_BBOX →
BBOX_HINT_SHOWN → WAITING_SHUTTER → SHUTTER_HINT_SHOWN → COMPLETED
```

***REMOVED******REMOVED******REMOVED*** Key Features

- **Detection timeout**: If no object detected within 3s, BBox hint shows anyway
- **Immediate completion**: Completing capture immediately completes FTUE
- **Early dismissal**: Tapping outside dismissal marks as completed

---

***REMOVED******REMOVED*** 3. Camera UI FTUE (CameraUiFtueViewModel)

**Purpose:** Teach the 4 main camera button controls

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/CameraUiFtueViewModel.kt`

***REMOVED******REMOVED******REMOVED*** Trigger Conditions

- Camera permission granted
- Camera preview visible (width × height > 0)
- All 4 button anchors registered
- **Camera FTUE completed** (sequential dependency)
- **Tour NOT active** (waits for tour)

***REMOVED******REMOVED******REMOVED*** Steps (4 buttons)

| ***REMOVED*** | Button | Anchor ID | Description | Spotlight |
|---|--------|-----------|-------------|-----------|
| 1 | **Shutter** | `camera_ui_shutter` | "Tap here to capture an item" | Circle |
| 2 | **Flip Camera** | `camera_ui_flip` | "Switch between front and rear camera" | Rounded rect |
| 3 | **Items List** | `camera_ui_items` | "View and manage scanned items" | Rounded rect |
| 4 | **Settings** | `camera_ui_settings` | "Adjust how Scanium works" | Rounded rect |

***REMOVED******REMOVED******REMOVED*** Expected Anchors

```kotlin
const val ANCHOR_SHUTTER = "camera_ui_shutter"
const val ANCHOR_FLIP = "camera_ui_flip"
const val ANCHOR_ITEMS = "camera_ui_items"
const val ANCHOR_SETTINGS = "camera_ui_settings"
```

***REMOVED******REMOVED******REMOVED*** Behavior

- **Deterministic**: User must tap "Next" button or tap highlighted element
- **No auto-advance**: Unlike other FTUEs, stays on each step until explicit action
- **Anchor validation**: Waits for all 4 anchors before starting
- **Visual feedback**: Pulsating animation on highlighted button

***REMOVED******REMOVED******REMOVED*** State Flow

```
IDLE → SHUTTER → FLIP_CAMERA → ITEM_LIST → SETTINGS → COMPLETED
```

---

***REMOVED******REMOVED*** 4. Items List FTUE (ItemsListFtueViewModel)

**Purpose:** Teach gesture-based item list interactions

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/ItemsListFtueViewModel.kt`

***REMOVED******REMOVED******REMOVED*** Trigger Conditions

- First visit to Items List screen
- At least 1 item exists in the list

***REMOVED******REMOVED******REMOVED*** Steps (4 gestures)

| ***REMOVED*** | Hint | Duration | Target Gesture | Visual Effect | Advancement |
|---|------|----------|----------------|---------------|-------------|
| 1 | **Tap to Edit** | 2500ms | Tap item | Hint overlay on first item | Auto OR user taps item |
| 2 | **Swipe to Delete** | 2500ms | Swipe right | **Nudge animation** (25% right, snap back in 800ms) | Auto OR user swipes |
| 3 | **Long-Press** | 2500ms | Long press | Hint overlay | Auto OR user long-presses |
| 4 | **Share Goal** | 2500ms | - | Final hint about export | Auto |

***REMOVED******REMOVED******REMOVED*** Timing Constants

```kotlin
INITIAL_DELAY_MS = 500L
STEP_DISPLAY_DURATION_MS = 2500L
NUDGE_ANIMATION_DURATION_MS = 800L
STEP_TRANSITION_DELAY_MS = 300L
```

***REMOVED******REMOVED******REMOVED*** Nudge Animation

The swipe hint includes a visual nudge animation:
```kotlin
// Offset row to the right (0 → 0.25 → 0)
for (i in 0..20) {
    swipeNudgeProgress = (i / 20) * 0.25f
    delay(40ms)
}
swipeNudgeProgress = 0f  // Snap back
```

***REMOVED******REMOVED******REMOVED*** Key Features

- **Gesture recognition**: User performing the actual gesture advances immediately
- **Non-destructive**: Nudge animation doesn't actually delete the item
- **Progressive teaching**: Builds from simple (tap) to complex (long-press)

---

***REMOVED******REMOVED*** 5. Edit Item FTUE (EditItemFtueViewModel)

**Purpose:** Guide users to improve item details for better listings

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/EditItemFtueViewModel.kt`

***REMOVED******REMOVED******REMOVED*** Trigger Conditions

- First visit to Edit Item screen

***REMOVED******REMOVED******REMOVED*** Steps (2 active)

| ***REMOVED*** | Hint | Target | Duration | Description | Advancement |
|---|------|--------|----------|-------------|-------------|
| 1 | **Improve Details** | First field | 2500ms | "Improve details to get better listings" | Auto OR user edits field |
| 2 | **Condition & Price** | Condition field | 2500ms | "Set condition and price for better results" | Auto OR user sets condition/price |
| ~~3~~ | ~~AI Assistant~~ | ~~AI button~~ | ~~N/A~~ | **Removed** (was Dev-only) | N/A |

***REMOVED******REMOVED******REMOVED*** Timing Constants

```kotlin
INITIAL_DELAY_MS = 500L
STEP_DISPLAY_DURATION_MS = 2500L
STEP_TRANSITION_DELAY_MS = 300L
```

***REMOVED******REMOVED******REMOVED*** State Flow

```
IDLE → WAITING_DETAILS_HINT → DETAILS_HINT_SHOWN →
WAITING_CONDITION_PRICE_HINT → CONDITION_PRICE_HINT_SHOWN → COMPLETED
```

***REMOVED******REMOVED******REMOVED*** Key Features

- **Field-focused**: Highlights specific editable fields
- **Completion on interaction**: User editing fields advances immediately
- **Short sequence**: Only 2 steps, focused on key improvements

---

***REMOVED******REMOVED*** 6. Settings FTUE (SettingsFtueViewModel)

**Purpose:** Minimal guidance for key settings discoverability

**Location:** `androidApp/src/main/java/com/scanium/app/ftue/SettingsFtueViewModel.kt`

***REMOVED******REMOVED******REMOVED*** Trigger Conditions

- First visit to Settings screen

***REMOVED******REMOVED******REMOVED*** Steps (2 minimal)

| ***REMOVED*** | Hint | Target | Duration | Description | Advancement |
|---|------|--------|----------|-------------|-------------|
| 1 | **Language** | Language option | 2000ms | "Set your language once — AI and voice follow it" | Auto OR user changes language |
| 2 | **Replay Guide** | Replay option | 2000ms | "You can replay this guide anytime" | Auto |

***REMOVED******REMOVED******REMOVED*** Timing Constants

```kotlin
INITIAL_DELAY_MS = 500L
STEP_DISPLAY_DURATION_MS = 2000L  // Shorter than other FTUEs
STEP_TRANSITION_DELAY_MS = 300L
```

***REMOVED******REMOVED******REMOVED*** Key Features

- **Shorter hints**: 2000ms vs 2500ms for other FTUEs
- **Minimal guidance**: Only 2 steps, covering essential settings
- **Discoverability focus**: Points out language unification and replay option

---

***REMOVED******REMOVED*** Common Mechanisms

***REMOVED******REMOVED******REMOVED*** Persistence (FtueRepository)

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

***REMOVED******REMOVED******REMOVED*** Replay Mechanism

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

***REMOVED******REMOVED******REMOVED*** Dismissal Behavior

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

***REMOVED******REMOVED******REMOVED*** State Management Pattern

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

***REMOVED******REMOVED******REMOVED*** Overlay Rendering

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

***REMOVED******REMOVED*** Implementation Files

***REMOVED******REMOVED******REMOVED*** ViewModels

| File | FTUE | Lines | Complexity |
|------|------|-------|------------|
| `TourViewModel.kt` | Guided Tour | 314 | High (multi-screen orchestration) |
| `CameraFtueViewModel.kt` | Camera FTUE | 290 | Medium (detection integration) |
| `CameraUiFtueViewModel.kt` | Camera UI FTUE | 267 | Medium (anchor registry) |
| `ItemsListFtueViewModel.kt` | Items List FTUE | 380 | Medium (gesture detection + nudge) |
| `EditItemFtueViewModel.kt` | Edit Item FTUE | 249 | Low (simple field hints) |
| `SettingsFtueViewModel.kt` | Settings FTUE | 227 | Low (minimal guidance) |

***REMOVED******REMOVED******REMOVED*** Persistence

| File | Purpose |
|------|---------|
| `FtueRepository.kt` | DataStore persistence for all FTUE state |

***REMOVED******REMOVED******REMOVED*** UI Overlays

| File | Purpose |
|------|---------|
| `TourOverlay.kt` | Guided tour overlay with spotlight |
| `CameraFtueOverlay.kt` | Camera FTUE hints and animations |
| `CameraUiFtueOverlay.kt` | Camera UI button tutorials |
| `ItemsListFtueOverlay.kt` | Items list gesture hints |
| `EditItemFtueOverlay.kt` | Edit item field hints |
| `SettingsFtueOverlay.kt` | Settings hints |

***REMOVED******REMOVED******REMOVED*** Support Files

| File | Purpose |
|------|---------|
| `TourStep.kt` | Tour step data models |
| `TourStepKey.kt` | Tour step identifiers |
| `SpotlightShape.kt` | Spotlight geometry (Circle, RoundedRect) |
| `FtueAnchorRegistry.kt` | Camera UI anchor registration |

---

***REMOVED******REMOVED*** Developer Notes

***REMOVED******REMOVED******REMOVED*** Testing FTUEs

1. **Clear all FTUE state:**
   ```bash
   ***REMOVED*** Via Developer Options
   Settings → Developer Options → "Reset All FTUE Flags"

   ***REMOVED*** Or via ADB
   adb shell pm clear com.scanium.app.dev
   ```

2. **Test individual flows:**
   - Enable "Developer Mode" in Settings
   - Navigate to Developer Options
   - Use per-FTUE reset buttons

3. **Force-enable flows:**
   - Some FTUEs support force-enable flags (e.g., Guided Tour)
   - Accessible via Developer Options switches

***REMOVED******REMOVED******REMOVED*** Adding New FTUE Steps

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

***REMOVED******REMOVED******REMOVED*** Debugging Tips

- **Enable logs:** Set `BuildConfig.FLAVOR = "dev"` to see step transitions
- **Check anchor registration:** Camera UI FTUE requires all 4 anchors
- **Verify trigger conditions:** Use `initialize()` logs to diagnose why FTUE doesn't start
- **Test dismissal:** Ensure early dismissal marks as completed

---

***REMOVED******REMOVED*** Future Enhancements

Potential improvements to the FTUE system:

1. **Analytics integration**: Track completion rates per FTUE
2. **A/B testing**: Test different hint durations/sequences
3. **Conditional steps**: Skip steps based on user behavior
4. **Voice hints**: Add optional voice narration
5. **Localization**: Translate all hint strings
6. **Accessibility**: Ensure screen reader compatibility

---

***REMOVED******REMOVED*** Related Documentation

- [Settings IA](./SETTINGS_IA.md) - Settings screen structure
- [Camera Pipeline](./architecture-camera-pipeline.md) - Camera detection flow
- [Item Editing](./ITEM_EDITING.md) - Edit item screen architecture

---

**Document Version:** 1.0
**Last Review:** 2026-01-19
