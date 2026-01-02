***REMOVED*** Scanium Motion Language

This document describes the motion language foundations for Scanium's camera-first scanning experience.

***REMOVED******REMOVED*** Brand Motion Rules

- **Minimal & Fast**: Motion must be confidence-inspiring, not distracting
- **No Bouncy Effects**: No spring/elastic animations
- **Yellow Accent Only**: `***REMOVED***FFD400` is used ONLY for action accents (scan pulse/confirmation), never as dominant backgrounds
- **No Continuous Loops**: No radar sweeps, rotating loaders, or infinite scanning animations

***REMOVED******REMOVED*** Timing Constraints

| Animation | Duration | Spec |
|-----------|----------|------|
| Scan Frame Appear | ≤100ms | Linear fade-in |
| Lightning Pulse | 200-300ms | Single pass, no loop |
| Price Count-Up | 1.0-1.5s | 3-4 steps, ease-out |
| Confirmation Glow | ≤200ms | Quick settle |

***REMOVED******REMOVED*** Components

***REMOVED******REMOVED******REMOVED*** Package: `com.scanium.app.ui.motion`

***REMOVED******REMOVED******REMOVED******REMOVED*** Core Files

| File | Purpose |
|------|---------|
| `MotionConstants.kt` | Timing and visual constants |
| `MotionConfig.kt` | Debug toggle (BuildConfig.DEBUG only) |
| `ScanFrameAppear.kt` | Quick fade-in rounded-rect frame |
| `LightningScanPulse.kt` | Single yellow pulse animation |
| `PriceCountUp.kt` | Animated price display (discrete steps) |
| `PriceCountUpUtil.kt` | Pure Kotlin step generator (testable) |
| `MotionEnhancedOverlay.kt` | Wrapper integrating motion with DetectionOverlay |
| `MotionPreviews.kt` | Compose preview showcase |

***REMOVED******REMOVED******REMOVED*** Colors (defined in `ui/theme/Color.kt`)

```kotlin
val ScaniumBluePrimary = Color(0xFF1F4BFF)  // Scan frame color
val LightningYellow = Color(0xFFFFD400)     // Action accent only
```

***REMOVED******REMOVED*** Integration Points

***REMOVED******REMOVED******REMOVED*** Camera Overlay Flow

1. **DetectionOverlay.kt** (`camera/`) - Renders bounding boxes with labels/prices
2. **OverlayTrack.kt** (`camera/`) - Data model with `priceText`, `boxStyle`
3. **OverlayTrackManager.kt** (`items/overlay/`) - Maps detections to tracks
4. **CameraScreen.kt** (`camera/`) - Uses `MotionEnhancedOverlay` instead of raw `DetectionOverlay`

***REMOVED******REMOVED******REMOVED*** Price Display Flow

1. `PricingEngine.kt` generates base price ranges per category
2. `AggregatedItem` holds `estimatedPriceRange` and `priceEstimationStatus`
3. `OverlayTrack.priceText` formatted via `PriceRange.formatted()` → "€10–€25"
4. `AnimatedPriceText` parses and animates the formatted string

***REMOVED******REMOVED*** Developer Toggle

Motion overlays can be toggled via:
- `SettingsRepository.devMotionOverlaysEnabledFlow` (persisted)
- `MotionConfig.setMotionOverlaysEnabled()` (in-memory, synced from settings)

In release builds, motion overlays are always enabled.

***REMOVED******REMOVED*** Performance Considerations

- Uses `remember`/`derivedStateOf` to prevent unnecessary recompositions
- Stable keys ensure animations don't restart on every frame
- Pulse debouncing prevents spam (500ms minimum between pulses)
- State tracking for confirmed items avoids duplicate animations

***REMOVED******REMOVED*** Testing

- **Unit Tests**: `PriceCountUpUtilTest.kt` - Tests step generator logic
- **Compose Previews**: `MotionPreviews.kt` - Visual preview showcase
- **Manual Verification**:
  1. Scan frame appears quickly on detection
  2. Pulse triggers only on item confirmation (LOCKED state)
  3. Prices count up once then remain stable
  4. No major FPS drop during animations
