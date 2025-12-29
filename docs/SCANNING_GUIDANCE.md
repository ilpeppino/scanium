***REMOVED*** Camera Scanning Guidance System

This document describes the camera scanning guidance overlay system that helps users achieve reliable item detection.

***REMOVED******REMOVED*** Overview

The scanning guidance system provides visual and textual feedback to help users position items correctly for optimal scanning. It consists of:

1. **Scan Zone** - A visual region showing where to place items
2. **Guidance Hints** - Contextual text hints (e.g., "Move closer", "Hold steady")
3. **State Transitions** - Visual feedback as scanning progresses
4. **Lock Mechanism** - Stability lock to prevent background detections
5. **Visual State Clarity** - Distinct bbox styles for PREVIEW → READY → LOCKED progression
6. **Distance Confidence** - Subtle visual feedback for optimal distance
7. **Scan Confidence Metrics** - Telemetry for debugging and beta testing

***REMOVED******REMOVED*** Key Concept: Single Source of Truth

The `ScanRoi` (Scan Region of Interest) is the canonical model used by **both** the UI and the analyzer:

- The overlay draws the scan zone from `ScanRoi`
- The analyzer filters detections using the same `ScanRoi`
- Any tuning updates `ScanRoi` only (never duplicated logic)

This ensures what the user **sees** (scan zone + hints) matches what the system **analyzes** (ROI).

***REMOVED******REMOVED*** Scan Zone Behavior

***REMOVED******REMOVED******REMOVED*** Default Configuration
- **Position**: Centered in preview (0.5, 0.5)
- **Shape**: Rounded rectangle
- **Initial Size**: ~65% of shorter preview dimension
- **Min Size**: 45% (object too close)
- **Max Size**: 75% (object too far)

***REMOVED******REMOVED******REMOVED*** Dynamic Sizing
The scan zone resizes based on detected object area:

| Object Area | Zone Behavior | User Hint |
|-------------|---------------|-----------|
| > 35% | Shrinks | "Move phone away" |
| 4-35% | Stable | None |
| < 4% | Expands | "Move closer" |

***REMOVED******REMOVED*** Recommended Distance

| Distance | Experience |
|----------|------------|
| 10-20 cm | Too close - blur risk, scan zone shrinks |
| 30-50 cm | **Optimal** - reliable detection and focus |
| > 60 cm | Too far - may need to move closer |

***REMOVED******REMOVED*** Guidance States

The system transitions through these states:

***REMOVED******REMOVED******REMOVED*** 1. SEARCHING
Initial state when scanning starts. No candidate detected yet.
- **Visual**: Subtle scan zone outline
- **Hint**: None

***REMOVED******REMOVED******REMOVED*** 2. TOO_CLOSE
Object detected but too close to camera.
- **Visual**: Scan zone shrinks, orange outline
- **Hint**: "Move phone away"

***REMOVED******REMOVED******REMOVED*** 3. TOO_FAR
Object detected but too far from camera.
- **Visual**: Scan zone expands, orange outline
- **Hint**: "Move closer"

***REMOVED******REMOVED******REMOVED*** 4. OFF_CENTER
Object detected but not centered in scan zone.
- **Visual**: Orange outline
- **Hint**: "Center the object"

***REMOVED******REMOVED******REMOVED*** 5. UNSTABLE
Camera or scene is unstable (motion or blur detected).
- **Visual**: Pulsing outline
- **Hint**: "Hold steady"

***REMOVED******REMOVED******REMOVED*** 6. FOCUSING
Camera is actively focusing.
- **Visual**: Blue outline
- **Hint**: "Focusing..." (brief)

***REMOVED******REMOVED******REMOVED*** 7. GOOD
All conditions are good - object centered, focused, stable.
- **Visual**: Green pulsing outline
- **Hint**: "Hold still to scan"

***REMOVED******REMOVED******REMOVED*** 8. LOCKED
Locked onto a candidate - stable detection achieved.
- **Visual**: Solid green outline with inner highlight
- **Action**: Item will be added from this candidate only
- **Hint**: "Ready to scan" (brief, 1 second)

***REMOVED******REMOVED*** Bounding Box Visual States

Bounding boxes have three distinct visual styles that progress from detection to scan-ready:

***REMOVED******REMOVED******REMOVED*** 1. PREVIEW State
Object detected but not yet ready for scanning.
- **Stroke**: Thin (0.75x)
- **Color**: Blue (neutral)
- **Alpha**: 0.6
- **Label**: Small "Detected" (optional)

***REMOVED******REMOVED******REMOVED*** 2. READY State
Scan conditions met, waiting for lock.
- **Stroke**: Medium (1.1x)
- **Color**: Green (accent, 0.85 alpha)
- **Alpha**: 0.85
- **Label**: Optional

***REMOVED******REMOVED******REMOVED*** 3. LOCKED State
Stable lock achieved, scan-ready.
- **Stroke**: Thick (1.4x) with brief pulse animation
- **Color**: Bright green
- **Alpha**: 1.0
- **Animation**: One-time scale pulse (1.0 → 1.15 → 1.0) over 350ms

**Transition Rules:**
- PREVIEW → READY: When guidance state is GOOD and detection is eligible
- READY → LOCKED: When stability lock criteria are met (400ms stable)
- Any state → PREVIEW: When conditions degrade (motion, off-center, blur)

***REMOVED******REMOVED*** Distance Confidence Indicator

The scan zone border color subtly indicates distance quality:

| Distance | Zone Border | Meaning |
|----------|-------------|---------|
| Too close | Yellow-green (***REMOVED***7CB342) | Object too close to camera |
| Optimal | Green (***REMOVED***1DB954) | Perfect scanning distance |
| Too far | Yellow-green (***REMOVED***7CB342) | Object too far from camera |

This is determined by comparing detected bbox area to thresholds:
- TOO_CLOSE: area > 35% of frame
- OPTIMAL: area between 4% and 35%
- TOO_FAR: area < 4% of frame

***REMOVED******REMOVED*** Picture Mode Alignment

Single-shot capture (shutter tap) shares the same ROI as live scanning:

1. **Same ROI**: Picture mode uses `scanGuidanceState.scanRoi`
2. **Pre-capture hint**: If no eligible bbox, shows "Center object in scan zone for better accuracy"
3. **Result feedback**: Explains when objects were outside scan zone

This ensures consistent behavior between live scan and picture capture.

***REMOVED******REMOVED*** Lock Behavior

The lock mechanism prevents background detections and panning artifacts:

***REMOVED******REMOVED******REMOVED*** Lock Criteria (all must be true)
1. Candidate box center inside ScanRoi
2. Box area within acceptable range (4-35%)
3. Sharpness above threshold (120+)
4. Low motion (< 0.1)
5. Stable for ≥ 400 ms

***REMOVED******REMOVED******REMOVED*** Behavior in LOCKED State
- Only the locked candidate may trigger item add
- Background detections are ignored
- Panning immediately breaks lock
- Lock auto-releases after item add or 5 second timeout

***REMOVED******REMOVED*** Detection Selection (Phase 5 Scoring)

When multiple objects are detected, the system selects the best candidate using:

```
score = confidence × 0.5 + areaScore × 0.2 + centerScore × 0.3
```

***REMOVED******REMOVED******REMOVED*** Hard Rejects
- Very small boxes (< 3% area)
- Boxes with center outside ScanRoi (unless confidence > 80%)
- Boxes detected during unstable focus

***REMOVED******REMOVED*** Developer Diagnostics

Enable "Scanning Diagnostics" in Developer Options to see:

- Current ROI size (%)
- Detected box area (%)
- Sharpness score
- Center distance
- Lock state
- Motion score
- Current guidance state

***REMOVED******REMOVED*** Settings

***REMOVED******REMOVED******REMOVED*** User Settings
- **Scanning Guidance** (ON by default) - Toggle visibility of the scan zone overlay and hints

***REMOVED******REMOVED******REMOVED*** Developer Settings
- **ROI Diagnostics** (OFF by default) - Show detailed numeric diagnostics:
  - ROI size percentage
  - Detected box area
  - Sharpness score
  - Center distance
  - Lock state and stable frame count
  - Motion score
  - Current guidance state name

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** "Items not being detected"
1. Ensure the object is within the scan zone
2. Check if "Move closer" or "Move phone away" hints appear
3. Hold the camera steady for 0.5-1 second
4. Ensure good lighting conditions

***REMOVED******REMOVED******REMOVED*** "Wrong object being scanned"
1. Center the desired object in the scan zone
2. Move other objects out of frame
3. Wait for the green "LOCKED" state before moving

***REMOVED******REMOVED******REMOVED*** "Detection feels laggy"
1. The system intentionally waits for stability before adding items
2. This prevents accidental background detections
3. Hold steady for the green lock to appear

***REMOVED******REMOVED******REMOVED*** "Scan zone keeps resizing"
1. This is intentional feedback about distance
2. Shrinking = move away; Expanding = move closer
3. Zone stabilizes when object is at optimal distance

***REMOVED******REMOVED*** Files

***REMOVED******REMOVED******REMOVED*** Core Models (shared)
- `ScanRoi.kt` - Scan region of interest data class
- `GuidanceState.kt` - Guidance state enum and composite state
- `ScanGuidanceManager.kt` - Central coordinator
- `RoiCoordinateMapper.kt` - Coordinate mapping between preview and analyzer spaces

***REMOVED******REMOVED******REMOVED*** Tracking (shared)
- `ObjectTracker.kt` - Multi-frame candidate tracking
- `CenterWeightedCandidateSelector.kt` - Center-weighted selection with ROI filtering

***REMOVED******REMOVED******REMOVED*** Android Implementation
- `CameraGuidanceOverlay.kt` - Visual overlay composable
- `CameraXManager.kt` - Integration with camera pipeline
- `SharpnessCalculator.kt` - Laplacian-based sharpness scoring
- `SettingsRepository.kt` - User/developer settings for guidance

***REMOVED******REMOVED*** Architecture

```
┌─────────────────────┐
│   CameraScreen      │
│ (Jetpack Compose)   │
└─────────┬───────────┘
          │ collectAsState
          ▼
┌─────────────────────┐
│  CameraXManager     │
│  (ImageAnalysis)    │
└─────────┬───────────┘
          │ processFrame
          ▼
┌─────────────────────┐      ┌─────────────────────┐
│ ScanGuidanceManager │◄─────│ CenterWeightedSelector │
│  (state machine)    │      │ (ROI-based selection)  │
└─────────┬───────────┘      └────────────────────────┘
          │ ScanGuidanceState
          ▼
┌─────────────────────┐
│ CameraGuidanceOverlay│
│  (visual feedback)   │
└─────────────────────┘

Single Source of Truth:
ScanRoi ────► UI Overlay (drawing)
        └───► ObjectTracker (filtering)
        └───► CenterWeightedSelector (gating)
```

***REMOVED******REMOVED*** User Hints (Micro-copy)

Hints are short, actionable messages:

| State | Hint Text |
|-------|-----------|
| SEARCHING | (none) |
| TOO_CLOSE | "Move phone slightly away" |
| TOO_FAR | "Move closer to object" |
| OFF_CENTER | "Center object in scan zone" |
| UNSTABLE | "Hold steady" |
| FOCUSING | "Focusing..." |
| GOOD | "Hold still..." |
| LOCKED | "Ready to scan" |

**Hint Rules:**
- Never stacked (only one at a time)
- Auto-dismiss after state-specific duration (1-1.5s for transient states)
- Rate-limited: 1.5s minimum between hint changes

***REMOVED******REMOVED*** Scan Confidence Metrics

For debugging and beta testing, `ScanConfidenceMetrics` tracks (aggregated, no images):

- **% frames with preview bbox**: How often objects are detected
- **% frames reaching lock**: Lock success rate
- **Avg time-to-lock**: Responsiveness metric
- **% shutter taps without eligible bbox**: User expectation mismatch
- **Unlock reasons**: MOTION, FOCUS, OFF_CENTER, LEFT_ROI, TIMEOUT, CANDIDATE_LOST

Access via:
- `ScanGuidanceManager.metrics.liveMetrics` (StateFlow)
- `ScanGuidanceManager.metrics.toDebugString()` (for logs)

***REMOVED******REMOVED*** Category-Aware ROI (Foundation)

The system supports category-specific ROI tuning (for future improvements):

```kotlin
enum class ScanObjectCategory {
    PHONE, TOY, DOCUMENT, ELECTRONICS, FURNITURE, UNKNOWN
}

data class CategoryRoiConfig(
    val category: ScanObjectCategory,
    val idealAreaMin: Float,
    val idealAreaMax: Float,
    val minStableTimeForLockMs: Long
)
```

Currently uses UNKNOWN/generic settings. Category hints can be passed to enable per-category behavior.

***REMOVED******REMOVED*** Related Documents

- [SCAN_VS_PICTURE_ASSESSMENT.md](./SCAN_VS_PICTURE_ASSESSMENT.md) - Analysis of scanning behavior
- [LIVE_SCAN_CENTERING_BUG.md](./LIVE_SCAN_CENTERING_BUG.md) - Previous centering improvements
- [BETA_VALIDATION.md](./BETA_VALIDATION.md) - Test scenarios and exit criteria
