***REMOVED*** CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

***REMOVED******REMOVED*** Project Overview

**Scanium** is a camera-first Android app that performs real-time object detection, barcode scanning, and document text recognition using Google ML Kit. All processing happens on-device with no cloud calls.

- **Package**: `com.scanium.app`
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

***REMOVED******REMOVED*** Common Commands

***REMOVED******REMOVED******REMOVED*** Building
```bash
./gradlew assembleDebug          ***REMOVED*** Build debug APK
./gradlew assembleRelease        ***REMOVED*** Build release APK
./gradlew clean                  ***REMOVED*** Clean build artifacts
```

***REMOVED******REMOVED******REMOVED*** Testing
```bash
./gradlew test                   ***REMOVED*** Run all unit tests
./gradlew test --console=plain   ***REMOVED*** Run unit tests with detailed output
./gradlew connectedAndroidTest   ***REMOVED*** Run instrumented tests (requires device/emulator)
```

***REMOVED******REMOVED******REMOVED*** Running Single Tests
```bash
./gradlew test --tests "com.scanium.app.tracking.ObjectTrackerTest"
./gradlew test --tests "com.scanium.app.items.ItemsViewModelTest.testAddItems"
```

***REMOVED******REMOVED******REMOVED*** Installation
```bash
./gradlew installDebug           ***REMOVED*** Install debug APK on connected device
adb logcat                       ***REMOVED*** View device logs
```

***REMOVED******REMOVED******REMOVED*** Debugging ML/Tracking Issues
```bash
***REMOVED*** Filter logs for tracking and detection
adb logcat | grep -E "ObjectTracker|CameraXManager|ObjectDetector"

***REMOVED*** Watch for specific events
adb logcat | grep "CONFIRMED candidate"  ***REMOVED*** See when objects are confirmed
adb logcat | grep "Tracker stats"        ***REMOVED*** See tracking statistics
```

***REMOVED******REMOVED*** Architecture Overview

***REMOVED******REMOVED******REMOVED*** Pattern: Simplified MVVM
- **View**: Jetpack Compose screens (`camera/`, `items/`)
- **ViewModel**: `ItemsViewModel` (shared state across screens)
- **Model**: Data classes in `ml/`, `tracking/`, `items/`
- **State Management**: Kotlin `StateFlow` for reactive updates

***REMOVED******REMOVED******REMOVED*** Package Structure
```
app/src/main/java/com/scanium/app/
├── camera/          ***REMOVED*** CameraX integration, scan modes, gesture handling
├── items/           ***REMOVED*** Items list, ViewModel, data models
├── ml/              ***REMOVED*** ML Kit wrappers (object detection, barcode, OCR)
├── tracking/        ***REMOVED*** Multi-frame object tracking and de-duplication
├── navigation/      ***REMOVED*** Navigation graph
└── ui/theme/        ***REMOVED*** Material 3 theming
```

***REMOVED******REMOVED******REMOVED*** Feature-Based Organization
- Each package contains related UI, logic, and models together
- No dependency injection framework (manual constructor injection)
- Single-module architecture appropriate for current scope

***REMOVED******REMOVED*** Key Architectural Components

***REMOVED******REMOVED******REMOVED*** 1. Camera & Image Processing (`camera/`)

**CameraXManager**: Orchestrates camera lifecycle, image analysis pipeline, and scan modes
- Manages CameraX preview binding and image capture
- Routes processing based on `ScanMode` (OBJECT_DETECTION, BARCODE, DOCUMENT_TEXT)
- Handles gesture detection (tap for single capture, long-press for continuous scan)
- Integrates with tracking pipeline for continuous object detection

**Scan Modes** (`ScanMode.kt`):
- `OBJECT_DETECTION`: Detects objects with ML Kit, uses tracking for de-duplication
- `BARCODE`: Scans QR codes and barcodes
- `DOCUMENT_TEXT`: OCR text extraction

**Important**: During continuous scanning in OBJECT_DETECTION mode, the tracker is automatically reset when:
- Starting a new scan session
- Switching scan modes
- Stopping scanning

***REMOVED******REMOVED******REMOVED*** 2. ML Kit Integration (`ml/`)

**ObjectDetectorClient**: Wraps ML Kit Object Detection API
- Provides `detectObjects()` for single-shot detection (SINGLE_IMAGE_MODE)
- Provides `detectObjectsWithTracking()` for tracking pipeline (STREAM_MODE)
- Maps ML Kit categories to app's `ItemCategory` enum
- Crops thumbnails from bounding boxes

**BarcodeScannerClient**: Wraps ML Kit Barcode Scanning
- Supports multiple formats (QR, EAN, UPC, etc.)
- No tracking needed (instant recognition)

**DocumentTextRecognitionClient**: Wraps ML Kit Text Recognition
- OCR for document scanning mode

**PricingEngine**: Generates mock EUR price ranges based on category
- Replace with real API integration when backend is ready
- Current implementation is in `ml/PricingEngine.kt`

***REMOVED******REMOVED******REMOVED*** 3. Object Tracking System (`tracking/`)

**Critical for understanding de-duplication**: The tracking system ensures each physical object is detected only once during continuous scanning.

**ObjectTracker** (`tracking/ObjectTracker.kt`):
- Tracks detection candidates across multiple frames
- Uses ML Kit `trackingId` when available (STREAM_MODE)
- Falls back to spatial matching (IoU + center distance) when trackingId is null
- Confirms candidates when they meet thresholds:
  - `minFramesToConfirm = 1` (instant confirmation for responsiveness)
  - `minConfidence = 0.25` (low threshold for inclusive detection)
  - `minBoxArea = 0.0f` (no minimum size requirement)
- Auto-expires stale candidates after 3 seconds

**ObjectCandidate** (`tracking/ObjectCandidate.kt`):
- Represents a candidate object tracked across frames
- Contains spatial data (bounding box, center point)
- Tracks confidence, category, thumbnail across observations
- Methods: `update()`, `calculateIoU()`, `distanceTo()`, `getCenterPoint()`

**Tracking Pipeline Flow** (OBJECT_DETECTION + continuous scan):
```
ImageProxy → ML Kit (STREAM_MODE) → List<DetectedObject>
  → Extract DetectionInfo (trackingId, bbox, labels, thumbnail)
  → ObjectTracker.processFrame()
    → Match to existing candidates OR create new
    → Update candidate metadata (seenCount++, maxConfidence, etc.)
    → Check confirmation criteria
    → Return newly confirmed candidates
  → Convert to ScannedItem with stable IDs
  → ItemsViewModel.addItems() (ID-based de-duplication)
```

**When tracking is used**:
- Only for OBJECT_DETECTION mode during continuous scanning
- Single-shot tap capture bypasses tracking (uses SINGLE_IMAGE_MODE)
- Barcode and Document modes don't use tracking

***REMOVED******REMOVED******REMOVED*** 4. State Management (`items/`)

**ItemsViewModel**: Centralized state for detected items
- Manages `StateFlow<List<ScannedItem>>`
- De-duplicates items by ID using `seenIds` set
- Shared across CameraScreen and ItemsListScreen
- Methods: `addItem()`, `addItems()`, `removeItem()`, `clearAllItems()`

**ScannedItem** (`items/ScannedItem.kt`):
- Immutable data class representing a detected item
- Properties: `id`, `thumbnail`, `category`, `priceRange`, `confidence`, `timestamp`
- Computed: `confidenceLevel`, `formattedConfidence`, `formattedPriceRange`

**Important**: ViewModel de-duplication works seamlessly with tracking because ObjectTracker provides stable IDs.

***REMOVED******REMOVED******REMOVED*** 5. Navigation

**Single-Activity architecture** with Compose Navigation:
- `MainActivity` hosts `NavHost`
- Start destination: `Routes.CAMERA`
- ItemsViewModel instantiated at app root, passed to screens
- No fragment transactions, cleaner lifecycle

***REMOVED******REMOVED*** Data Models

***REMOVED******REMOVED******REMOVED*** ItemCategory Enum (`ml/ItemCategory.kt`)
Maps ML Kit's 5 coarse categories to app categories:
- `FASHION`, `FOOD`, `HOME_GOODS`, `PLACES`, `PLANTS`, `UNKNOWN`, `BARCODE`, `DOCUMENT`
- Method: `fromMlKitLabel(text: String)` for mapping ML Kit labels

***REMOVED******REMOVED******REMOVED*** ConfidenceLevel Enum (`items/ScannedItem.kt`)
- `LOW` (0.0-0.5), `MEDIUM` (0.5-0.75), `HIGH` (0.75-1.0)

***REMOVED******REMOVED******REMOVED*** ScanMode Enum (`camera/ScanMode.kt`)
- `OBJECT_DETECTION`, `BARCODE`, `DOCUMENT_TEXT`

***REMOVED******REMOVED*** Testing

**110 total tests** across unit and instrumented tests:

**Unit Tests** (`app/src/test/`):
- `ObjectTrackerTest.kt` - Tracking logic, confirmation, expiry
- `ObjectCandidateTest.kt` - Spatial matching (IoU, distance)
- `TrackingPipelineIntegrationTest.kt` - End-to-end tracking scenarios
- `ItemsViewModelTest.kt` - State management, de-duplication
- `DetectionResultTest.kt` - Overlay data model validation
- `PricingEngineTest.kt` - EUR price generation
- `ItemCategoryTest.kt` - ML Kit label mapping

**Instrumented Tests** (`app/src/androidTest/`):
- `ModeSwitcherInstrumentedTest.kt` - Compose UI for mode switching
- `DetectionOverlayInstrumentedTest.kt` - Bounding box rendering

**Test Frameworks**:
- JUnit 4, Robolectric, Truth assertions, MockK, Coroutines Test, Compose Testing

***REMOVED******REMOVED*** Configuration & Tuning

***REMOVED******REMOVED******REMOVED*** Tracker Configuration
Located in `CameraXManager.kt`:
```kotlin
TrackerConfig(
    minFramesToConfirm = 1,      // Frames needed for confirmation
    minConfidence = 0.25f,        // Minimum confidence threshold
    minBoxArea = 0.0f,            // Minimum normalized box area
    maxFrameGap = 5,              // Frames allowed between detections
    minMatchScore = 0.3f,         // Spatial matching threshold (IoU * 0.7 + dist * 0.3)
    expiryFrames = 30,            // Frames before candidate expires
    candidateTimeoutMs = 3000L    // Time-based expiry (3 seconds)
)
```

**Tuning Guidelines**:
- Increase `minFramesToConfirm` to reduce false positives (slower confirmation)
- Increase `minConfidence` for higher quality detections
- Increase `minBoxArea` to ignore small/distant objects
- Decrease thresholds for faster, more inclusive detection

***REMOVED******REMOVED******REMOVED*** Image Analysis Interval
In `CameraXManager.kt`:
```kotlin
val analysisIntervalMs = 800L  // Process every 800ms during scanning
```

***REMOVED******REMOVED*** Code Patterns & Conventions

***REMOVED******REMOVED******REMOVED*** State Management Pattern
```kotlin
// ViewModel
private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())
val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()

// UI
val items by viewModel.items.collectAsState()
```

***REMOVED******REMOVED******REMOVED*** ML Kit Integration Pattern
```kotlin
// Wrap ML Kit in client class
class ObjectDetectorClient {
    private val detector = ObjectDetection.getClient(options)

    suspend fun detectObjects(...): List<ScannedItem> {
        // Convert ML Kit results to domain models
    }
}
```

***REMOVED******REMOVED******REMOVED*** Camera Lifecycle Pattern
```kotlin
// Remember manager with lifecycle owner
val cameraManager = remember {
    CameraXManager(context, lifecycleOwner)
}

DisposableEffect(Unit) {
    onDispose { cameraManager.shutdown() }
}
```

***REMOVED******REMOVED*** Important Implementation Notes

***REMOVED******REMOVED******REMOVED*** 1. ML Kit Detection Modes
- **SINGLE_IMAGE_MODE**: Used for tap captures, better accuracy per frame
- **STREAM_MODE**: Used for continuous scanning, provides `trackingId` for tracking
- The app switches modes automatically based on capture type

***REMOVED******REMOVED******REMOVED*** 2. Tracking ID Sources
- **Primary**: ML Kit `trackingId` (when available in STREAM_MODE)
- **Fallback**: Generated UUID for detections without trackingId
- **Spatial Matching**: IoU + center distance when trackingId unavailable

***REMOVED******REMOVED******REMOVED*** 3. Memory Management
- ObjectTracker clears candidates on reset (mode change, scan stop)
- Candidates expire after timeout to prevent unbounded growth
- Thumbnails are Bitmap objects - ensure proper cleanup

***REMOVED******REMOVED******REMOVED*** 4. Thread Safety
- Image analysis runs on background executor
- StateFlow updates are thread-safe
- UI updates via Compose state collection (lifecycle-aware)

***REMOVED******REMOVED******REMOVED*** 5. No Persistence Layer
- All data is in-memory (ViewModel state)
- Items lost on app close (intentional for PoC)
- Future: Add Room database if persistence needed

***REMOVED******REMOVED*** Common Development Tasks

***REMOVED******REMOVED******REMOVED*** Adding a New Scan Mode
1. Add enum value to `ScanMode.kt`
2. Create ML Kit client wrapper in `ml/` (e.g., `NewScannerClient.kt`)
3. Add processing branch in `CameraXManager.processImageProxy()`
4. Update `ModeSwitcher.kt` UI
5. Add tests for new mode

***REMOVED******REMOVED******REMOVED*** Modifying Tracking Behavior
1. Adjust `TrackerConfig` in `CameraXManager.kt`
2. Run tests: `./gradlew test --tests "*ObjectTracker*"`
3. Test on device with debug logs: `adb logcat | grep ObjectTracker`
4. Check confirmation timing and duplicate prevention

***REMOVED******REMOVED******REMOVED*** Implementing Real Pricing API
1. Create `PricingRepository` interface in `ml/`
2. Implement with Retrofit/OkHttp
3. Replace `PricingEngine` calls in `ObjectDetectorClient`
4. Add error handling and fallback to mock prices
5. Update tests with mocked API responses

***REMOVED******REMOVED******REMOVED*** Adding Persistence
1. Add Room dependencies to `app/build.gradle.kts`
2. Create database entities and DAO in `items/`
3. Implement Repository pattern with local/remote sources
4. Update `ItemsViewModel` to use repository
5. Add database tests

***REMOVED******REMOVED*** Known Limitations

- **ML Kit categories**: Only 5 coarse categories (Fashion, Food, Home goods, Places, Plants)
- **No persistence**: Items cleared on app close
- **Mocked pricing**: Local price generation, no real API
- **Single module**: Will need multi-module architecture at scale
- **No DI framework**: Manual dependency injection may not scale

***REMOVED******REMOVED*** Future Architecture Considerations

When the app grows, consider:
- **Multi-module architecture**: Split into `:app`, `:feature:*`, `:core:*` modules
- **Hilt dependency injection**: For complex scoping and testing
- **Room database**: For local persistence with Paging 3
- **Backend integration**: Retrofit + Repository pattern with remote/local sources
- **Custom TensorFlow Lite model**: For fine-grained product recognition beyond ML Kit

***REMOVED******REMOVED*** Reference Documentation

- **Architecture details**: See `ARCHITECTURE.md` for comprehensive architecture documentation
- **Tracking implementation**: See `TRACKING_IMPLEMENTATION.md` for tracking system details
- **General usage**: See `README.md` for setup and usage instructions
