# Camera UI Refactor

**Date**: 2025-12-12
**Goal**: Create a decluttered Android-style camera experience with a single shutter button

## Overview

The Camera UI has been refactored to provide a clean, minimal interface similar to native Android camera apps. The primary control is now a single shutter button, with advanced controls hidden by default and revealed on demand.

## Key Changes

### 1. Camera State Machine

**New File**: `app/src/main/java/com/scanium/app/camera/CameraState.kt`

A simple state machine to manage camera capture modes:

```kotlin
enum class CameraState {
    IDLE,       // Camera ready for capture
    CAPTURING,  // Single frame capture in progress
    SCANNING    // Continuous scanning active
}
```

**State Transitions**:
- `IDLE` → `CAPTURING`: When user taps shutter button
- `IDLE` → `SCANNING`: When user long-presses shutter button
- `SCANNING` → `IDLE`: When user taps shutter button while scanning
- `CAPTURING` → `IDLE`: When single capture completes

### 2. Android-Style Shutter Button

**New File**: `app/src/main/java/com/scanium/app/camera/ShutterButton.kt`

A composable that implements the primary camera control with the following behavior:

**Tap (Quick Press)**:
- Triggers single frame capture
- Uses `SINGLE_IMAGE_MODE` for ML Kit detection
- Provides immediate visual feedback
- Plays shutter click sound

**Long Press (500ms threshold)**:
- Starts continuous scanning mode
- Scanning continues after finger is lifted (no need to hold)
- Uses `STREAM_MODE` for ML Kit detection with tracking
- Shows pulsing red animation and "Scanning..." text
- Plays scan start melody

**Tap While Scanning**:
- Stops continuous scanning immediately
- Freezes current detection results
- Plays scan stop melody
- Returns to IDLE state

**Visual Design**:
- Circular button (80dp diameter)
- White outer ring (4dp stroke)
- White filled inner circle (75% of radius)
- Pulsing animation when scanning (red color)
- Press animation (scale down to 0.9)
- Contextual hint text below button

### 3. Advanced Controls Auto-Hide System

**Implementation**: `CameraScreen.kt`

Advanced controls (threshold slider and classification mode toggle) are now hidden by default:

**Show Controls**:
- Tap anywhere on the camera preview (except shutter button)
- Both controls appear simultaneously
- 3-second auto-hide timer starts

**Auto-Hide Behavior**:
- After 3 seconds of inactivity, controls fade out
- Timer resets when user interacts with slider or toggle
- Timer is cancelled when user taps shutter button
- Controls remain usable during the 3-second countdown

**Control Positions**:
- Threshold slider: Right side (as before)
- Classification toggle: Left side (vertical chips)

### 4. Refactored Camera Screen

**Modified File**: `app/src/main/java/com/scanium/app/camera/CameraScreen.kt`

**What Changed**:
- Removed old gesture detection system (tap/long-press/double-tap on preview)
- Replaced with dedicated shutter button at bottom center
- Implemented camera state machine
- Added advanced controls visibility state and auto-hide timer
- Simplified preview tap handling (only shows controls now)
- Mode switcher remains always visible at bottom

**What Stayed the Same**:
- ML Kit detection pipeline (no changes to `CameraXManager`)
- Object tracking and de-duplication logic
- Detection overlay rendering
- Mode switching (Object/Barcode/Document)
- Sound effects (shutter click, scan start/stop)
- Items list integration
- All existing detection accuracy and performance

## User Experience Flow

### Single Capture Flow
1. User opens camera → sees clean preview with only shutter button
2. User taps shutter button
3. Shutter click sound plays
4. Single frame is captured and analyzed
5. Items are added to list with toast notification
6. Returns to IDLE state immediately

### Continuous Scanning Flow
1. User opens camera → sees clean preview
2. User long-presses shutter button (500ms)
3. Button turns red and pulses
4. "Scanning..." text appears
5. User lifts finger → scanning continues
6. ML Kit processes frames every 800ms
7. Confirmed items are added to list in real-time
8. User taps shutter button → scanning stops
9. Returns to IDLE state

### Advanced Controls Flow
1. User wants to adjust threshold or classification mode
2. User taps anywhere on camera preview (not shutter)
3. Threshold slider appears on right side
4. Classification toggle appears on left side
5. User adjusts settings (timer resets on interaction)
6. After 3 seconds of no interaction → controls fade out
7. User can repeat as needed

## Technical Details

### Gesture Handling

**Preview Tap Detection**:
```kotlin
.pointerInput(Unit) {
    detectTapGestures(
        onTap = {
            // Show advanced controls
            onPreviewTap()
        }
    )
}
```

**Shutter Button Gesture**:
```kotlin
.pointerInput(cameraState) {
    detectTapGestures(
        onPress = {
            isPressed = true
            val longPressJob = launch {
                delay(500)
                if (isPressed && cameraState == IDLE) {
                    onLongPress() // Start scanning
                }
            }
            tryAwaitRelease()
            longPressJob.cancel()
            if (!longPressTriggered) {
                if (cameraState == SCANNING) {
                    onStopScanning()
                } else {
                    onTap() // Single capture
                }
            }
        }
    )
}
```

### Auto-Hide Timer

```kotlin
fun startAutoHideTimer() {
    autoHideJob?.cancel()
    autoHideJob = scope.launch {
        delay(3000)
        advancedControlsVisible = false
    }
}

fun resetAutoHideTimer() {
    if (advancedControlsVisible) {
        startAutoHideTimer()
    }
}
```

### State Management

Camera state is managed with simple Compose state:

```kotlin
var cameraState by remember { mutableStateOf(CameraState.IDLE) }
```

State transitions are explicit and deterministic:

```kotlin
// Start capture
if (cameraState == CameraState.IDLE) {
    cameraState = CameraState.CAPTURING
    // ... trigger capture
}

// Start scanning
if (cameraState == CameraState.IDLE) {
    cameraState = CameraState.SCANNING
    // ... start scanning
}

// Stop scanning
if (cameraState == CameraState.SCANNING) {
    cameraState = CameraState.IDLE
    // ... stop scanning
}
```

## Testing Checklist

### Basic Functionality
- [ ] Camera preview starts correctly
- [ ] Shutter button appears at bottom center
- [ ] Advanced controls are hidden on launch
- [ ] Mode switcher is visible and functional

### Single Capture
- [ ] Tap shutter → captures single frame
- [ ] Shutter click sound plays
- [ ] Items are detected and added to list
- [ ] Toast shows "Detected N item(s)"
- [ ] Returns to IDLE state immediately
- [ ] Can capture again without delay

### Continuous Scanning
- [ ] Long-press shutter (500ms) → starts scanning
- [ ] Button turns red and pulses
- [ ] "Scanning..." text appears
- [ ] Scan start melody plays
- [ ] Lifting finger → scanning continues
- [ ] Items are added in real-time
- [ ] Tap shutter → stops scanning
- [ ] Scan stop melody plays
- [ ] Returns to IDLE state
- [ ] Detection overlay clears

### Advanced Controls
- [ ] Tap preview (not shutter) → controls appear
- [ ] Both slider and toggle appear together
- [ ] Slider is on right side
- [ ] Toggle is on left side
- [ ] After 3 seconds → controls disappear
- [ ] Dragging slider → resets timer
- [ ] Tapping toggle → resets timer
- [ ] Controls remain functional during countdown

### Edge Cases
- [ ] Very quick tap → always single capture (never scanning)
- [ ] Long press while scanning → ignored (or stops scanning)
- [ ] Tap shutter during capture → no effect (waits for completion)
- [ ] Preview tap while scanning → shows controls (doesn't interrupt scan)
- [ ] Mode switch while scanning → stops scanning first
- [ ] App backgrounded while scanning → cleans up properly
- [ ] Rapid tap/long-press → no glitches or crashes

### Visual Verification
- [ ] Shutter button resembles Android camera app
- [ ] Pulsing animation smooth when scanning
- [ ] No overlap between controls and shutter
- [ ] Threshold slider slim and elegant
- [ ] Classification toggle compact on left side
- [ ] Hint text updates correctly ("Tap to capture • Hold to scan" / "Scanning..." / "Tap to stop")

## Performance Notes

- **No ML/Detection Changes**: The refactor only touches UI/UX state management. All ML Kit detection, tracking, and aggregation logic remains unchanged.
- **Gesture Latency**: Long-press threshold is 500ms (standard Android camera behavior)
- **Analysis Interval**: Continuous scanning processes frames every 800ms (unchanged)
- **Auto-Hide Delay**: Advanced controls hide after 3000ms (3 seconds) of inactivity
- **State Updates**: All state transitions use Compose state, ensuring efficient recomposition

## Migration Notes

### For Future Development

If you need to modify camera behavior:

1. **Add new camera states**: Extend `CameraState` enum
2. **Modify shutter behavior**: Edit `ShutterButton.kt` gesture handling
3. **Change auto-hide timing**: Adjust `delay(3000)` in `startAutoHideTimer()`
4. **Add more advanced controls**: Place them in the `if (advancedControlsVisible)` block
5. **Customize visuals**: Modify colors/animations in `ShutterButton.kt` Canvas code

### Backward Compatibility

- All existing detection modes (Object/Barcode/Document) work unchanged
- ItemsViewModel integration unchanged
- Sound effects unchanged
- Navigation unchanged
- Permissions unchanged

## Known Limitations

- **Long-press threshold**: Fixed at 500ms (could be made configurable)
- **Auto-hide delay**: Fixed at 3 seconds (could be made configurable)
- **Control positions**: Fixed (left/right sides, could support drag-to-reposition)
- **Single button**: No separate video/photo toggle (intentional simplification)

## Future Enhancements

Potential improvements for future iterations:

1. **Haptic Feedback**: Add vibration on long-press threshold
2. **Gesture Customization**: Allow users to configure long-press duration
3. **Control Persistence**: Remember user's preferred threshold/classification mode
4. **Animated Transitions**: Fade in/out animations for advanced controls
5. **Accessibility**: Voice control or large touch targets option
6. **Drag-to-Position**: Allow users to reposition advanced controls
7. **Quick Settings**: Swipe from edges to reveal more controls

## Files Modified

### New Files
- `app/src/main/java/com/scanium/app/camera/CameraState.kt`
- `app/src/main/java/com/scanium/app/camera/ShutterButton.kt`
- `md/features/CAMERA_UI_REFACTOR.md` (this file)

### Modified Files
- `app/src/main/java/com/scanium/app/camera/CameraScreen.kt`

### Unchanged Files
- `app/src/main/java/com/scanium/app/camera/CameraXManager.kt` (ML pipeline)
- `app/src/main/java/com/scanium/app/camera/VerticalThresholdSlider.kt`
- `app/src/main/java/com/scanium/app/camera/ModeSwitcher.kt`
- `app/src/main/java/com/scanium/app/camera/DetectionOverlay.kt`
- All ML Kit clients (`ObjectDetectorClient`, `BarcodeScannerClient`, etc.)
- All tracking logic (`ObjectTracker`, `ObjectCandidate`, etc.)
- All ViewModels and navigation

## Summary

This refactor successfully achieves the goal of creating a clean, decluttered camera experience while preserving all existing detection functionality. The single shutter button provides an intuitive interface familiar to Android users, while advanced controls remain easily accessible when needed.

The implementation is modular, maintainable, and follows Compose best practices. All state transitions are explicit and deterministic, ensuring a reliable user experience.
