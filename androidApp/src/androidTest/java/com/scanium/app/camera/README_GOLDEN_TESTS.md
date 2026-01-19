***REMOVED*** Detection Overlay Golden Tests

***REMOVED******REMOVED*** Overview

This directory contains comprehensive golden/snapshot tests for the vision overlay mapping system,
specifically focusing on locking down the detection box rendering behavior in `DetectionOverlay.kt`.

***REMOVED******REMOVED*** Test Files

***REMOVED******REMOVED******REMOVED*** 1. `DetectionOverlayGoldenTest.kt` (Instrumented Tests)

**Location:** `androidTest/java/com/scanium/app/camera/DetectionOverlayGoldenTest.kt`

Comprehensive visual regression tests using Compose `captureToImage()` for pixel-level validation of
overlay rendering.

**Test Coverage:**

- Visual rendering of different bbox states:
    - `EYE`: Very subtle white outline for global vision (detections anywhere in frame)
    - `SELECTED`: Blue accent outline for user-selected detection (center inside ROI)
    - `READY`: Green outline when conditions are met (holding steady)
    - `LOCKED`: Bright green thick outline when scan-ready (stable lock achieved)
- Coordinate transformation accuracy across rotations (0°, 90°, 180°, 270°)
- Multiple detections with different states rendering simultaneously
- Label positioning (above/below box depending on space)
- Edge cases: overlapping detections, tiny bboxes, edge bboxes
- FILL_CENTER vs FIT_CENTER scale type handling
- Price estimation status rendering

**Key Test Scenarios:**

1. **Single Detection Tests:** Verify each box style (EYE, SELECTED, READY, LOCKED) renders
   correctly
2. **Multiple Detection Tests:** Ensure visual hierarchy and no rendering conflicts
3. **Rotation Tests:** Validate coordinate mapping for portrait (90°/270°) and landscape (0°/180°)
   orientations
4. **Label Positioning Tests:** Verify labels position above when space available, below when near
   top edge
5. **Edge Case Tests:** Overlapping boxes, tiny detections, detections at canvas boundaries
6. **Scale Type Tests:** FILL_CENTER (center-crop) vs FIT_CENTER (letterbox) coordinate mapping

**Implementation Strategy:**
Since no snapshot library is integrated (e.g., Paparazzi, Shot), these tests use:

- Compose `captureToImage()` to capture rendered output as bitmaps
- Pixel sampling to verify visual properties (colors, positions, stroke widths)
- Color matching with tolerance for anti-aliasing
- Brightness analysis to detect presence of UI elements
- Helper methods for bbox outline detection, color presence verification, and label detection

**Helper Methods:**

- `renderAndCapture()`: Renders DetectionOverlay and captures as bitmap
- `verifyBoxOutline()`: Samples pixels along expected box edges to verify outline presence
- `verifyColorPresence()`: Scans bitmap for target color (with tolerance)
- `verifyLabelPresence()`: Checks for label background color in expected regions
- `estimateStrokeWidth()`: Measures stroke width by scanning edge pixels
- `calculateAverageBrightness()`: Computes average brightness for subtle state detection
- `colorsMatch()`: Color comparison with tolerance for anti-aliasing

***REMOVED******REMOVED******REMOVED*** 2. `OverlayTransformsTest.kt` (Unit Tests)

**Location:** `test/java/com/scanium/app/camera/OverlayTransformsTest.kt`

Extended unit tests for coordinate transformation logic in `OverlayTransforms.kt`.

**Test Coverage:**

- Transform calculation for different aspect ratios and scale types
- Bbox mapping from normalized to preview coordinates
- Rotation transformations (0°, 90°, 180°, 270°)
- Scale and offset interactions
- Edge cases and boundary conditions
- Real-world integration scenarios

**Key Test Categories:**

1. **Transform Calculation Tests:**
    - Portrait mode (90°/270°) dimension swapping
    - Landscape mode (0°/180°) dimension preservation
    - FILL_CENTER scale type (center-crop)
    - FIT_CENTER scale type (letterbox)
    - Aspect mismatch handling

2. **Bbox Mapping Tests:**
    - Centered bbox mapping
    - Scale and offset application order
    - Edge bboxes at (0,0) and (1,1)
    - Full-frame bbox mapping
    - Tiny bbox preservation

3. **Rotation Transformation Tests:**
    - 0° rotation (identity)
    - 90° clockwise rotation
    - 180° flip
    - 270° counter-clockwise rotation
    - Rotation composition (multiple rotations)
    - Center point preservation during rotation
    - Bbox validity preservation across rotations

4. **Integration Tests:**
    - Realistic portrait scan scenario
    - Realistic landscape scan scenario
    - Aspect mismatch with FILL_CENTER
    - Tiny bbox transformation
    - Multiple rotation composition

***REMOVED******REMOVED*** Running the Tests

***REMOVED******REMOVED******REMOVED*** Golden Tests (Instrumented)

```bash
***REMOVED*** Run all golden tests
./gradlew :androidApp:connectedDevDebugAndroidTest --tests "com.scanium.app.camera.DetectionOverlayGoldenTest"

***REMOVED*** Run specific test
./gradlew :androidApp:connectedDevDebugAndroidTest --tests "com.scanium.app.camera.DetectionOverlayGoldenTest.whenSingleEyeDetection_thenRendersSubtleBox"
```

**Requirements:**

- Connected Android device or emulator
- API level 24+ (minSdk)
- ~5-10 minutes execution time for full suite

***REMOVED******REMOVED******REMOVED*** Unit Tests

```bash
***REMOVED*** Run all transform tests
./gradlew :androidApp:testDevDebugUnitTest --tests "com.scanium.app.camera.OverlayTransformsTest"

***REMOVED*** Run specific test category
./gradlew :androidApp:testDevDebugUnitTest --tests "com.scanium.app.camera.OverlayTransformsTest.*rotation*"
```

**Requirements:**

- JVM (no device needed)
- ~10 seconds execution time

***REMOVED******REMOVED*** Test Maintenance

***REMOVED******REMOVED******REMOVED*** When to Update Tests

1. **Visual Changes:** If bbox styling changes (colors, stroke widths, corner radius), update color
   constants and tolerance values in golden tests
2. **Coordinate System Changes:** If the upright coordinate contract changes, update rotation
   transformation tests
3. **Scale Type Changes:** If FILL_CENTER/FIT_CENTER logic changes, update transform calculation
   tests
4. **New Box States:** If new `OverlayBoxStyle` values are added, add corresponding visual tests

***REMOVED******REMOVED******REMOVED*** Adding New Tests

When adding new detection overlay features:

1. **Add Golden Test:** Create visual validation test in `DetectionOverlayGoldenTest.kt`
2. **Add Unit Test:** If coordinate logic changes, add tests to `OverlayTransformsTest.kt`
3. **Document:** Update this README with new test coverage

***REMOVED******REMOVED******REMOVED*** Debugging Failing Tests

**Golden Tests:**

- Enable debug logging: Set `android:debuggable="true"` in manifest
- Capture actual bitmap: Add `bitmap.compress(PNG, 100, FileOutputStream("debug.png"))` in test
- Check color tolerance: Anti-aliasing may require increasing `COLOR_TOLERANCE` constant
- Verify device configuration: Some visual tests may be sensitive to screen density

**Unit Tests:**

- Check floating point tolerance: Coordinate calculations use `FLOAT_TOLERANCE` for assertions
- Verify rotation logic: Review coordinate transformation formulas in `OverlayTransforms.kt`
- Log intermediate values: Add debug logging to identify where transformation diverges

***REMOVED******REMOVED*** Architecture Notes

***REMOVED******REMOVED******REMOVED*** Coordinate System Contract

**IMPORTANT:** All bounding boxes follow the upright coordinate contract:

- ML Kit returns bboxes in `InputImage` coordinate space (upright, post-rotation)
- When using `InputImage.fromMediaImage(image, rotationDegrees)`, ML Kit applies rotation internally
- All normalized bboxes are stored in **UPRIGHT** coordinate space
- `mapBboxToPreview()` does **NOT** rotate (bbox is already upright)

***REMOVED******REMOVED******REMOVED*** Transform Pipeline

1. **Input:** Normalized bbox in upright space (0-1 range)
2. **Calculate Transform:** Determine effective dimensions, scale, and offset based on rotation
3. **Map to Preview:** Convert normalized coords to pixel coords, apply scale and offset
4. **Render:** Draw on canvas in preview coordinate space

***REMOVED******REMOVED******REMOVED*** Visual State Hierarchy

```
EYE (subtle white)
  ↓
SELECTED (blue accent)
  ↓
READY (green)
  ↓
LOCKED (bright green, thick)
```

This progression reflects the scanning state from passive detection to scan-ready lock.

***REMOVED******REMOVED*** References

- **DetectionOverlay.kt:** Main overlay rendering logic
- **OverlayTransforms.kt:** Coordinate transformation functions
- **BboxColors:** Private object defining state-specific colors
- **OverlayBoxStyle:** Enum defining visual states (EYE, SELECTED, READY, LOCKED)
- **OverlayTrack:** Data class representing a detection to render

***REMOVED******REMOVED*** Future Improvements

- [ ] Integrate snapshot testing library (Paparazzi or Shot) for automated visual regression
- [ ] Add performance benchmarks for rendering with many detections
- [ ] Add tests for animation states (pulse, lock transition)
- [ ] Add tests for different screen densities and aspect ratios
- [ ] Add property-based tests for coordinate transformations (QuickCheck/hypothesis)
