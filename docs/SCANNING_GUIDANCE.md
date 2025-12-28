# Camera Scanning Guidance System

This document describes the camera scanning guidance overlay system that helps users achieve reliable item detection.

## Overview

The scanning guidance system provides visual and textual feedback to help users position items correctly for optimal scanning. It consists of:

1. **Scan Zone** - A visual region showing where to place items
2. **Guidance Hints** - Contextual text hints (e.g., "Move closer", "Hold steady")
3. **State Transitions** - Visual feedback as scanning progresses
4. **Lock Mechanism** - Stability lock to prevent background detections

## Key Concept: Single Source of Truth

The `ScanRoi` (Scan Region of Interest) is the canonical model used by **both** the UI and the analyzer:

- The overlay draws the scan zone from `ScanRoi`
- The analyzer filters detections using the same `ScanRoi`
- Any tuning updates `ScanRoi` only (never duplicated logic)

This ensures what the user **sees** (scan zone + hints) matches what the system **analyzes** (ROI).

## Scan Zone Behavior

### Default Configuration
- **Position**: Centered in preview (0.5, 0.5)
- **Shape**: Rounded rectangle
- **Initial Size**: ~65% of shorter preview dimension
- **Min Size**: 45% (object too close)
- **Max Size**: 75% (object too far)

### Dynamic Sizing
The scan zone resizes based on detected object area:

| Object Area | Zone Behavior | User Hint |
|-------------|---------------|-----------|
| > 35% | Shrinks | "Move phone away" |
| 4-35% | Stable | None |
| < 4% | Expands | "Move closer" |

## Recommended Distance

| Distance | Experience |
|----------|------------|
| 10-20 cm | Too close - blur risk, scan zone shrinks |
| 30-50 cm | **Optimal** - reliable detection and focus |
| > 60 cm | Too far - may need to move closer |

## Guidance States

The system transitions through these states:

### 1. SEARCHING
Initial state when scanning starts. No candidate detected yet.
- **Visual**: Subtle scan zone outline
- **Hint**: None

### 2. TOO_CLOSE
Object detected but too close to camera.
- **Visual**: Scan zone shrinks, orange outline
- **Hint**: "Move phone away"

### 3. TOO_FAR
Object detected but too far from camera.
- **Visual**: Scan zone expands, orange outline
- **Hint**: "Move closer"

### 4. OFF_CENTER
Object detected but not centered in scan zone.
- **Visual**: Orange outline
- **Hint**: "Center the object"

### 5. UNSTABLE
Camera or scene is unstable (motion or blur detected).
- **Visual**: Pulsing outline
- **Hint**: "Hold steady"

### 6. FOCUSING
Camera is actively focusing.
- **Visual**: Blue outline
- **Hint**: "Focusing..." (brief)

### 7. GOOD
All conditions are good - object centered, focused, stable.
- **Visual**: Green pulsing outline
- **Hint**: "Hold still to scan"

### 8. LOCKED
Locked onto a candidate - stable detection achieved.
- **Visual**: Solid green outline with inner highlight
- **Action**: Item will be added from this candidate only

## Lock Behavior

The lock mechanism prevents background detections and panning artifacts:

### Lock Criteria (all must be true)
1. Candidate box center inside ScanRoi
2. Box area within acceptable range (4-35%)
3. Sharpness above threshold (120+)
4. Low motion (< 0.1)
5. Stable for ≥ 400 ms

### Behavior in LOCKED State
- Only the locked candidate may trigger item add
- Background detections are ignored
- Panning immediately breaks lock
- Lock auto-releases after item add or 5 second timeout

## Detection Selection (Phase 5 Scoring)

When multiple objects are detected, the system selects the best candidate using:

```
score = confidence × 0.5 + areaScore × 0.2 + centerScore × 0.3
```

### Hard Rejects
- Very small boxes (< 3% area)
- Boxes with center outside ScanRoi (unless confidence > 80%)
- Boxes detected during unstable focus

## Developer Diagnostics

Enable "Scanning Diagnostics" in Developer Options to see:

- Current ROI size (%)
- Detected box area (%)
- Sharpness score
- Center distance
- Lock state
- Motion score
- Current guidance state

## Files

### Core Models (shared)
- `ScanRoi.kt` - Scan region of interest data class
- `GuidanceState.kt` - Guidance state enum and composite state
- `ScanGuidanceManager.kt` - Central coordinator

### Android Implementation
- `CameraGuidanceOverlay.kt` - Visual overlay composable
- `CameraXManager.kt` - Integration with camera pipeline

## Related Documents

- [SCAN_VS_PICTURE_ASSESSMENT.md](./SCAN_VS_PICTURE_ASSESSMENT.md) - Analysis of scanning behavior
- [LIVE_SCAN_CENTERING_BUG.md](./LIVE_SCAN_CENTERING_BUG.md) - Previous centering improvements
