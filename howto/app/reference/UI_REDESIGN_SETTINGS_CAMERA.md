# UI Redesign: Settings and Camera Screen

This document describes the UI changes made to the Settings screens and Camera screen for improved
visual consistency and landscape support.

## Summary of Changes

### A) Settings UI Redesign

#### A1. Theme Buttons Consistent Sizing

**File:** `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsComponents.kt`

**Change:** Updated `SettingSegmentedRow` to use equal-width buttons.

- Changed `Modifier.weight(1f, fill = false)` to `Modifier.weight(1f)`
- All FilterChip buttons (System default, Light, Dark) now have equal widths
- Buttons remain in a single row with consistent spacing

**Verification:**

1. Navigate to Settings > General
2. Check that all three theme buttons (System default, Light, Dark) have the same width
3. Verify the buttons are evenly distributed in the row

#### A2. Normalized Settings Row Style with Icons

**Files:**

- `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsComponents.kt`
- `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsCameraScreen.kt`

**Changes:**

1. Added new `SettingIconSegmentedRow` composable that provides a consistent layout:
    - Leading icon (24dp)
    - Title and description text
    - Segmented controls below with equal-width buttons
    - Selected option description at bottom

2. Updated Camera & Scanning settings to use icons:
    - **Image Resolution:** `Icons.Filled.HighQuality`
    - **Classification Mode:** `Icons.Filled.Cloud`
    - **Aggregation Accuracy:** `Icons.Filled.Tune`

**Verification:**

1. Navigate to Settings > Camera & Scanning
2. Verify each setting has:
    - An icon on the left
    - Title and subtitle text
    - Segmented buttons below
3. Compare visual consistency with other settings rows

### B) Camera Screen Landscape Layout

**File:** `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt`

**Changes:**

1. Added orientation detection using `LocalConfiguration.current.orientation`
2. Created separate composables for portrait and landscape layouts:
    - `CameraOverlayPortrait` - Original bottom-centered layout
    - `CameraOverlayLandscape` - New landscape-optimized layout

**Landscape Layout Positions:**
| Control | Position | Alignment |
|---------|----------|-----------|
| Shutter button | Right edge, vertically centered | `Alignment.CenterEnd` |
| Items button | Bottom-left corner | `Alignment.BottomStart` |
| Flip camera button | Bottom-right corner | `Alignment.BottomEnd` |
| Resolution indicator | Below shutter button | N/A |
| Top bar (menu/logo) | Unchanged | `Alignment.TopCenter` |

**Touch Targets:**

- All buttons maintain 48dp minimum touch target size
- Padding from screen edges: 24dp

**Verification:**

1. Open the Camera screen in portrait mode
2. Verify controls are at the bottom in a horizontal row (items | shutter | flip)
3. Rotate device to landscape
4. Verify:
    - Shutter button is centered vertically on the right edge
    - Items button is in the bottom-left corner
    - Flip camera button is in the bottom-right corner
    - Resolution indicator is below the shutter button
5. Test tap/long-press functionality in both orientations
6. Verify no functional regressions (capture, scan, flip camera work correctly)

## Files Modified

| File                      | Changes                                                   |
|---------------------------|-----------------------------------------------------------|
| `SettingsComponents.kt`   | Fixed FilterChip weight, added `SettingIconSegmentedRow`  |
| `SettingsCameraScreen.kt` | Added icon imports, switched to `SettingIconSegmentedRow` |
| `CameraScreen.kt`         | Added orientation detection, portrait/landscape layouts   |

## No Changes Made To

- Camera/scanning business logic
- Theme switching functionality
- Detection/classification pipelines
- Data persistence

## Testing Notes

- Build: `./gradlew :androidApp:assembleDebug --no-daemon`
- Tests: `./gradlew test --no-daemon`
- All existing tests pass
- Manual testing recommended for visual verification
