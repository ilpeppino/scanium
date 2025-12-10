***REMOVED*** Scanium - Architecture Documentation

***REMOVED******REMOVED*** Overview

Scanium is a camera-first Android application that demonstrates real-time object detection and price estimation for the EU second-hand market. The app uses Google ML Kit for on-device object detection and provides a proof-of-concept for scanning physical items and estimating their resale value.

**Project Name:** Scanium (formerly ResaleVision)
**Package:** `com.scanium.app`
**Minimum SDK:** 24 (Android 7.0)
**Target SDK:** 34 (Android 14)
**Language:** Kotlin
**UI Framework:** Jetpack Compose with Material 3

---

***REMOVED******REMOVED*** Table of Contents

1. [High-Level Architecture](***REMOVED***high-level-architecture)
2. [Architectural Pattern](***REMOVED***architectural-pattern)
3. [Module Organization](***REMOVED***module-organization)
4. [Layer Architecture](***REMOVED***layer-architecture)
5. [Key Architectural Decisions](***REMOVED***key-architectural-decisions)
6. [Data Flow](***REMOVED***data-flow)
7. [Technology Stack](***REMOVED***technology-stack)
8. [Design Patterns](***REMOVED***design-patterns)
9. [Future Considerations](***REMOVED***future-considerations)

---

***REMOVED******REMOVED*** High-Level Architecture

Scanium follows a **simplified MVVM (Model-View-ViewModel)** architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│  (Jetpack Compose Screens + Navigation)                     │
│  - CameraScreen (with DetectionOverlay for visual feedback) │
│  - ItemsListScreen                                          │
│  - ItemDetailDialog                                         │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────────────────┐
│                    ViewModel Layer                          │
│  (State Management)                                         │
│  - ItemsViewModel (shared across screens)                  │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────────────────┐
│                   Domain/Business Logic                     │
│  - CameraXManager (camera lifecycle & analysis)            │
│  - CandidateTracker (multi-frame detection pipeline)       │
│  - ObjectDetectorClient (ML Kit object detection wrapper)  │
│  - BarcodeScannerClient (ML Kit barcode scanner wrapper)   │
│  - DetectionLogger (debug logging & statistics)            │
│  - PricingEngine (price generation logic)                  │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────────────────┐
│                     Data Layer                              │
│  - ScannedItem (promoted detection data model)             │
│  - DetectionResult (overlay visualization data)            │
│  - DetectionResponse (wrapper for items + results)         │
│  - DetectionCandidate (multi-frame tracking data)          │
│  - RawDetection (ML Kit raw output)                        │
│  - ItemCategory (enum with ML Kit mapping)                 │
│  - ConfidenceLevel (LOW/MEDIUM/HIGH classification)        │
│  - ScanMode (OBJECT_DETECTION/BARCODE/DOCUMENT_TEXT)       │
│  - In-memory state (StateFlow)                             │
└─────────────────────────────────────────────────────────────┘
```

---

***REMOVED******REMOVED*** Architectural Pattern

***REMOVED******REMOVED******REMOVED*** MVVM (Model-View-ViewModel)

**Why MVVM?**
- **Separation of Concerns:** UI logic is separated from business logic and data management
- **Testability:** ViewModels can be unit tested independently of Android framework
- **Lifecycle Awareness:** ViewModels survive configuration changes (rotation, etc.)
- **Compose Integration:** Perfect fit with Compose's reactive state management
- **Scalability:** Easy to extend with additional screens and features

**Implementation Details:**
- **View (UI):** Jetpack Compose screens (`CameraScreen`, `ItemsListScreen`)
- **ViewModel:** `ItemsViewModel` manages shared state across screens
- **Model:** `ScannedItem` data class, `ItemCategory` enum
- **State Management:** Kotlin `StateFlow` for reactive updates

**What problems does this solve?**
- Prevents tight coupling between UI and business logic
- Survives configuration changes without data loss
- Enables reactive UI updates when data changes
- Provides a clear contract between layers

---

***REMOVED******REMOVED*** Module Organization

***REMOVED******REMOVED******REMOVED*** Single-Module Architecture

The project uses a **single-module** structure for simplicity:

```
app/
├── src/main/
│   ├── java/com/scanium/app/
│   │   ├── MainActivity.kt                 ***REMOVED*** Entry point
│   │   ├── ScaniumApp.kt                   ***REMOVED*** Root composable
│   │   ├── camera/                         ***REMOVED*** Camera-related code
│   │   │   ├── CameraScreen.kt            ***REMOVED*** Camera UI
│   │   │   ├── CameraXManager.kt          ***REMOVED*** Camera logic
│   │   │   ├── DetectionOverlay.kt        ***REMOVED*** Visual detection overlay
│   │   │   ├── ModeSwitcher.kt            ***REMOVED*** Scan mode selector
│   │   │   └── ScanMode.kt                ***REMOVED*** Scan mode enum
│   │   ├── items/                          ***REMOVED*** Items/detection results
│   │   │   ├── ScannedItem.kt             ***REMOVED*** Data model
│   │   │   ├── ItemsViewModel.kt          ***REMOVED*** State management
│   │   │   ├── ItemsListScreen.kt         ***REMOVED*** List UI
│   │   │   └── ItemDetailDialog.kt        ***REMOVED*** Detail dialog
│   │   ├── ml/                             ***REMOVED*** Machine learning
│   │   │   ├── ObjectDetectorClient.kt    ***REMOVED*** ML Kit wrapper
│   │   │   ├── DetectionResult.kt         ***REMOVED*** Overlay data model
│   │   │   ├── DetectionResponse.kt       ***REMOVED*** Response wrapper
│   │   │   ├── ItemCategory.kt            ***REMOVED*** Category enum
│   │   │   ├── PricingEngine.kt           ***REMOVED*** Price logic
│   │   │   ├── BarcodeScannerClient.kt    ***REMOVED*** Barcode scanning
│   │   │   └── DocumentTextRecognitionClient.kt  ***REMOVED*** OCR
│   │   ├── tracking/                       ***REMOVED*** Object tracking & de-duplication
│   │   │   ├── ObjectCandidate.kt         ***REMOVED*** Candidate data class
│   │   │   └── ObjectTracker.kt           ***REMOVED*** Tracking engine
│   │   ├── navigation/                     ***REMOVED*** Navigation
│   │   │   └── NavGraph.kt                ***REMOVED*** Navigation setup
│   │   └── ui/theme/                       ***REMOVED*** Material 3 theme
│   │       ├── Color.kt
│   │       ├── Theme.kt
│   │       └── Type.kt
│   ├── AndroidManifest.xml
│   └── res/                                ***REMOVED*** Resources
└── build.gradle.kts
```

**Why Single-Module?**
- **Simplicity:** Appropriate for PoC/prototype scope
- **Fast Builds:** No multi-module compilation overhead
- **Easy Navigation:** All code in one place
- **Reduced Complexity:** No module dependency management

**Package Organization Rationale:**
- **Feature-based packages** (`camera/`, `items/`, `ml/`, `tracking/`) for logical grouping
- **Shared infrastructure** (`navigation/`, `ui/theme/`) in dedicated packages
- Each package contains related UI, logic, and models together
- Clear ownership boundaries between features
- Tracking package provides reusable de-duplication logic across scan modes

---

***REMOVED******REMOVED*** Layer Architecture

***REMOVED******REMOVED******REMOVED*** 1. UI Layer (Jetpack Compose)

**Components:**
- `CameraScreen` - Full-screen camera with gesture detection
- `ItemsListScreen` - List of detected items
- `ItemDetailDialog` - Detail view for individual items
- `ScaniumApp` - Root composable, navigation setup

**Responsibilities:**
- Render UI based on ViewModel state
- Handle user interactions (tap, long-press, navigation)
- Request runtime permissions
- Display loading/error states

**Key Technologies:**
- Jetpack Compose (declarative UI)
- Material 3 design system
- AndroidView for CameraX preview integration
- Gesture detection via `Modifier.pointerInput`

**Why Compose?**
- Modern, declarative UI paradigm
- Less boilerplate than XML layouts
- Better state management integration
- Excellent preview/tooling support
- Future-proof (Google's recommended approach)

***REMOVED******REMOVED******REMOVED*** 2. ViewModel Layer

**Components:**
- `ItemsViewModel` - Manages detected items state

**Responsibilities:**
- Hold UI state (list of scanned items)
- Provide operations (add, remove, clear items)
- Deduplication logic (tracking IDs)
- Survive configuration changes

**State Management:**
```kotlin
private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())
val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()
```

**Why StateFlow?**
- Reactive updates to UI
- Lifecycle-aware collection in Compose
- Type-safe state representation
- Built-in backpressure handling

**Shared ViewModel Pattern:**
- Single `ItemsViewModel` instance shared between screens
- Initialized at app root (`ScaniumApp`)
- Passed to screens via navigation
- Enables communication between camera and list screens

***REMOVED******REMOVED******REMOVED*** 3. Domain/Business Logic Layer

**Components:**

***REMOVED******REMOVED******REMOVED******REMOVED*** CameraXManager
- Manages CameraX lifecycle and configuration
- Binds camera preview to `PreviewView`
- Handles image analysis pipeline
- Coordinates single-shot capture vs continuous scanning
- Image preprocessing (rotation, format conversion)

**Why separate manager class?**
- Encapsulates complex CameraX setup
- Reusable across different screens
- Testable independently of UI
- Clear lifecycle management

***REMOVED******REMOVED******REMOVED******REMOVED*** ObjectDetectorClient
- Wraps Google ML Kit Object Detection API
- Configures detector for multiple objects + classification
- Supports both SINGLE_IMAGE_MODE and STREAM_MODE
- Provides `detectObjectsRaw()` for multi-frame pipeline
- Converts ML Kit results to app domain models (`ScannedItem` or `RawDetection`)
- Handles thumbnail cropping from detected bounding boxes
- Maps ML Kit categories to app categories
- Extracts label confidences for tracking

**Why wrapper pattern?**
- Isolates ML Kit dependencies
- Makes it easy to swap ML providers
- Provides domain-specific API
- Simplifies error handling

***REMOVED******REMOVED******REMOVED******REMOVED*** BarcodeScannerClient
- Wraps Google ML Kit Barcode Scanning API
- Detects multiple barcode formats (QR, EAN, UPC, etc.)
- Converts barcodes to `ScannedItem` with BARCODE category
- Extracts raw value and display value
- Generates pricing based on barcode type

**Why separate client?**
- Different use case from object detection
- Simpler API (no multi-frame tracking needed)
- Clear separation of scan modes

***REMOVED******REMOVED******REMOVED******REMOVED*** CandidateTracker
- **Core of multi-frame detection pipeline**
- Tracks detection candidates across multiple frames
- Maintains map of `trackingId -> DetectionCandidate`
- Promotes candidates to `ScannedItem` when criteria met:
  - Minimum seen count (default: 1 frame)
  - Minimum confidence (default: 0.25)
  - Optional bounding box area threshold
- Expires stale candidates (timeout: 3 seconds)
- Tracks statistics (detections, promotions, timeouts)
- Prevents duplicate promotions by removing after confirmation

**Why separate tracker?**
- Encapsulates complex multi-frame logic
- Configurable thresholds for tuning
- Reusable across different detection modes
- Testable independently
- Clear state management

***REMOVED******REMOVED******REMOVED******REMOVED*** DetectionLogger
- Centralized debug logging for detection events
- Only active in debug builds (`Log.isLoggable()` check)
- Logs raw detections with full metadata
- Logs candidate updates and promotions
- Logs frame summaries with timing
- Logs rejection reasons for tuning
- Structured logging for easy parsing

**Why centralized logger?**
- Consistent logging format
- Easy to enable/disable
- Debug builds only (no production overhead)
- Essential for threshold tuning

***REMOVED******REMOVED******REMOVED******REMOVED*** PricingEngine
- Generates mock price ranges based on category
- Takes bounding box size into account
- Adds randomization for realistic variance
- Returns EUR-formatted prices

**Why separate engine?**
- Centralizes pricing logic
- Easy to replace with real API later
- Testable business rules
- Clear pricing strategy

***REMOVED******REMOVED******REMOVED*** 4. Data Layer

**Components:**
- `ScannedItem` - Detected object data model
- `ItemCategory` - Category enum with ML Kit mapping
- In-memory state (no persistence)

**Data Models:**

```kotlin
data class ScannedItem(
    val id: String,                       // Tracking ID
    val thumbnail: Bitmap?,               // Cropped image
    val category: ItemCategory,           // Classification
    val priceRange: Pair<Double, Double>, // EUR prices
    val confidence: Float,                // Detection confidence (0.0-1.0)
    val timestamp: Long                   // Detection time
) {
    val confidenceLevel: ConfidenceLevel  // LOW/MEDIUM/HIGH
    val formattedConfidence: String       // "85%"
    val formattedPriceRange: String       // "€20 - €50"
}

data class DetectionCandidate(
    val id: String,                       // Tracking ID
    val seenCount: Int,                   // Frames observed
    val maxConfidence: Float,             // Peak confidence
    val category: ItemCategory,           // Best category
    val categoryLabel: String,            // ML Kit label text
    val lastBoundingBox: Rect?,           // Most recent box
    val thumbnail: Bitmap?,               // Most recent thumbnail
    val firstSeenTimestamp: Long,         // First detection time
    val lastSeenTimestamp: Long           // Last detection time
) {
    fun isReadyForPromotion(...): Boolean
    fun withNewObservation(...): DetectionCandidate
}

data class RawDetection(
    val trackingId: String,               // ML Kit tracking ID
    val boundingBox: Rect?,               // Object bounding box
    val labels: List<LabelWithConfidence>,// All classification labels
    val thumbnail: Bitmap?                // Cropped thumbnail
) {
    val bestLabel: LabelWithConfidence?   // Highest confidence label
    val category: ItemCategory            // Derived category
    fun getEffectiveConfidence(): Float   // Confidence with fallback
}

enum class ConfidenceLevel(val threshold: Float) {
    LOW(0.0f),
    MEDIUM(0.5f),
    HIGH(0.75f)
}

enum class ScanMode {
    OBJECT_DETECTION,
    BARCODE
}
```

**Why this structure?**
- Immutable data classes (thread-safe)
- All info needed for UI in one place
- Confidence tracking enables quality filtering
- Separate models for different pipeline stages
- Timestamp enables sorting/filtering

**No Persistence Layer:**
- PoC scope - ephemeral data
- Items lost on app close (intentional)
- Future: could add Room database or remote sync

---

***REMOVED******REMOVED*** Key Architectural Decisions

***REMOVED******REMOVED******REMOVED*** 1. No Dependency Injection Framework

**Decision:** Use manual dependency injection (constructor injection)

**Rationale:**
- **Simplicity:** PoC doesn't need Hilt/Koin complexity
- **Transparency:** Clear dependency graph
- **Learning:** Easier to understand flow
- **Performance:** No DI overhead

**Implementation:**
```kotlin
val cameraManager = remember {
    CameraXManager(context, lifecycleOwner)
}
```

**When to change:** If app grows beyond 5-10 screens or needs complex scoping

***REMOVED******REMOVED******REMOVED*** 2. Single-Activity Architecture

**Decision:** One `MainActivity` with Compose navigation

**Rationale:**
- Modern Android best practice
- Simplified lifecycle management
- Shared element transitions (future)
- Reduced memory overhead
- Compose navigation integration

**What problems does this solve?**
- No complex fragment transactions
- Single source of truth for navigation
- Easier deep linking
- Better state preservation

***REMOVED******REMOVED******REMOVED*** 3. Camera-First UX with CameraX

**Decision:** App opens directly to camera screen using CameraX

**Rationale:**
- **CameraX Benefits:**
  - Abstracts Camera2 complexity
  - Lifecycle-aware
  - Consistent behavior across devices
  - Automatic camera selection
  - Built-in image analysis pipeline

**Why camera-first?**
- Primary use case is scanning
- Reduces friction (no extra taps)
- Clear value proposition
- Modern app pattern (like Instagram, Snapchat)

**Integration with Compose:**
```kotlin
AndroidView(factory = { PreviewView(it) })
```
- Bridges imperative CameraX with declarative Compose
- Full gesture support via `Modifier.pointerInput`

***REMOVED******REMOVED******REMOVED*** 4. On-Device ML with ML Kit

**Decision:** Google ML Kit Object Detection (on-device)

**Rationale:**
- **Privacy:** No images sent to cloud
- **Performance:** Real-time processing
- **Offline:** Works without internet
- **Cost:** No API fees
- **Accuracy:** Good enough for PoC

**Configuration:**
```kotlin
ObjectDetectorOptions.Builder()
    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
    .enableMultipleObjects()
    .enableClassification()
```

**Trade-offs:**
- Limited to coarse categories (not fine-grained product recognition)
- Less accurate than cloud models
- Device-dependent performance

**Future Path:** Could add custom TensorFlow Lite model for specific product categories

***REMOVED******REMOVED******REMOVED*** 4.1 Object Tracking and De-Duplication System

**Decision:** Multi-frame tracking pipeline with ML Kit integration

**Rationale:**
- **Eliminates Duplicates:** Same physical object detected multiple times in continuous scanning no longer creates duplicate items
- **Stable Detection:** Multi-frame confirmation ensures only stable, confident objects are added
- **Robust Matching:** Dual strategy using ML Kit trackingId + spatial matching (IoU + distance)
- **Memory Efficient:** Automatic expiry of stale candidates prevents unbounded growth

**Architecture:**
```
ImageProxy → ML Kit (STREAM_MODE) → DetectionInfo[]
  ↓
ObjectTracker (processFrame)
  ├─ Match to existing candidates (trackingId or spatial)
  ├─ Update or create candidates
  ├─ Track frame counts, confidence, stability
  └─ Return newly confirmed candidates
  ↓
Convert to ScannedItems
  ↓
ItemsViewModel (ID-based de-duplication)
```

**Key Components:**

1. **ObjectCandidate** (`tracking/ObjectCandidate.kt`)
   - Tracks object across frames with metadata
   - Stores: internalId, boundingBox, seenCount, maxConfidence, category, thumbnail
   - Methods: update(), getCenterPoint(), distanceTo(), calculateIoU()

2. **ObjectTracker** (`tracking/ObjectTracker.kt`)
   - Maintains in-memory collection of candidates
   - Implements confirmation logic with configurable thresholds
   - Tracks frame-based temporal information
   - Handles candidate expiry

3. **DetectionInfo** (`tracking/ObjectTracker.kt`)
   - Raw detection metadata from ML Kit
   - Bridges ML Kit DetectedObject and ObjectTracker

**Configuration (TrackerConfig):**
```kotlin
TrackerConfig(
    minFramesToConfirm = 3,      // Require 3 frames to confirm
    minConfidence = 0.4f,         // Minimum confidence threshold
    minBoxArea = 0.001f,          // Minimum 0.1% of frame area
    maxFrameGap = 5,              // Allow 5 frames gap for matching
    minMatchScore = 0.3f,         // Spatial matching threshold
    expiryFrames = 10             // Expire after 10 frames without detection
)
```

**Matching Strategy:**
- **Primary:** Direct ML Kit trackingId matching (when available in STREAM_MODE)
- **Fallback:** Spatial matching using IoU (0.7 weight) + center distance (0.3 weight)

**Confirmation Criteria:**
```kotlin
seenCount >= minFramesToConfirm &&
maxConfidence >= minConfidence &&
averageBoxArea >= minBoxArea
```

**Integration Points:**
- **CameraXManager:** Routes OBJECT_DETECTION + STREAM mode through tracking pipeline
- **ObjectDetectorClient:** Provides `detectObjectsWithTracking()` and `candidateToScannedItem()`
- **ItemsViewModel:** Existing ID-based de-duplication works seamlessly with stable tracking IDs

**Trade-offs:**
- Slight processing overhead (IoU calculations, spatial matching)
- 3-frame delay before objects appear (ensures stability)
- Memory usage for tracking state (mitigated by expiry)

**Benefits:**
- Dramatically reduced duplicates in continuous scanning
- Improved user experience with cleaner item lists
- Tunable thresholds for different use cases
- Comprehensive logging for debugging

**Testing:**
- Unit tests: ObjectCandidateTest (13 tests), ObjectTrackerTest (22 tests)
- Integration tests: TrackingPipelineIntegrationTest (9 scenarios)
- Test coverage: Candidate lifecycle, confirmation logic, spatial matching, expiry

**Future Enhancements:**
- Color-based matching for improved spatial association
- Adaptive thresholds based on scene complexity
- Persistence of tracking state across app restarts

For detailed implementation documentation, see [TRACKING_IMPLEMENTATION.md](../features/TRACKING_IMPLEMENTATION.md).

***REMOVED******REMOVED******REMOVED*** 5. Gesture-Based Interaction

**Decision:** Tap for single capture, long-press for continuous scan

**Rationale:**
- **Intuitive:** Familiar mobile patterns
- **Efficient:** No extra UI controls needed
- **Flexible:** Single item vs batch scanning
- **Visual feedback:** Scanning indicator

**Implementation:**
```kotlin
detectTapGestures(
    onTap = { /* capture */ },
    onLongPress = { /* start scan */ },
    onPress = {
        tryAwaitRelease()
        /* stop scan */
    }
)
```

**User Mental Model:**
- Tap = take a photo
- Hold = video scan mode

***REMOVED******REMOVED******REMOVED*** 6. Reactive State Management

**Decision:** Kotlin Flow + StateFlow for state

**Rationale:**
- **Native:** Part of Kotlin coroutines
- **Efficient:** Conflated (latest value only)
- **Lifecycle-aware:** `collectAsState()` in Compose
- **Type-safe:** Compile-time guarantees

**Pattern:**
```kotlin
// ViewModel
private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())
val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()

// UI
val items by itemsViewModel.items.collectAsState()
```

**Why not LiveData?**
- Flow is Kotlin-first
- Better coroutine integration
- More operators (map, filter, etc.)
- Compose prefers Flow

***REMOVED******REMOVED******REMOVED*** 7. Navigation Architecture

**Decision:** Navigation Compose with centralized NavGraph

**Rationale:**
- **Type-safe:** Compile-time route validation
- **Declarative:** Matches Compose paradigm
- **Shared state:** Easy to pass ViewModels
- **Testable:** Can test navigation logic

**Structure:**
```kotlin
NavHost(startDestination = Routes.CAMERA) {
    composable(Routes.CAMERA) { CameraScreen(...) }
    composable(Routes.ITEMS_LIST) { ItemsListScreen(...) }
}
```

**Shared ViewModel Pattern:**
- ViewModel created at app root
- Passed to composables via parameters
- Both screens observe same state

***REMOVED******REMOVED******REMOVED*** 8. Mocked Pricing Logic

**Decision:** Local `PricingEngine` object with hardcoded ranges

**Rationale:**
- **PoC Scope:** No backend integration yet
- **Deterministic:** Easy to test
- **Extensible:** Clear interface for future API

**Design for Future:**
```kotlin
// Current
object PricingEngine {
    fun generatePriceRange(...): Pair<Double, Double>
}

// Future
interface PricingRepository {
    suspend fun getPriceEstimate(...): PriceEstimate
}
```

**Why not implement real API now?**
- Focuses on core ML/camera functionality
- Reduces complexity
- Faster iteration
- No backend dependency

***REMOVED******REMOVED******REMOVED*** 9. No Local Persistence

**Decision:** All data in memory (ViewModel state)

**Rationale:**
- **PoC Scope:** Sessions are short
- **Simplicity:** No database schema
- **Privacy:** Nothing stored on device
- **Performance:** Immediate access

**Trade-offs:**
- Items lost on app close
- Can't handle large item counts
- No historical data

**Future Options:**
- Room database for local cache
- DataStore for preferences
- Remote sync with backend

***REMOVED******REMOVED******REMOVED*** 10. Material 3 Design System

**Decision:** Use Material 3 (Material You) components

**Rationale:**
- **Modern:** Latest design language
- **Consistent:** Platform conventions
- **Accessible:** Built-in a11y features
- **Themeable:** Dynamic color support

**Benefits:**
- Professional appearance
- Less custom styling needed
- Familiar UX patterns
- Dark mode support (future)

---

***REMOVED******REMOVED*** Data Flow

***REMOVED******REMOVED******REMOVED*** Multi-Frame Detection Pipeline Flow (Scanning Mode)

```
User taps scan button
    ↓
CameraScreen → CameraXManager.startScanning(scanMode)
    ↓
CandidateTracker.clear() (reset state)
    ↓
ImageAnalysis analyzer set with 800ms interval
    ↓
Every 800ms:
    ↓
    ImageProxy captured
    ↓
    Convert to InputImage
    ↓
    ObjectDetectorClient.detectObjectsRaw()
    ↓
    ML Kit returns List<DetectedObject>
    ↓
    Convert to List<RawDetection> with:
        - trackingId (from ML Kit or UUID)
        - labels with confidences
        - bounding box
        - cropped thumbnail
    ↓
    For each RawDetection:
        ↓
        DetectionLogger.logRawDetection() [debug only]
        ↓
        Calculate effective confidence
        ↓
        CandidateTracker.processDetection()
            ↓
            Get existing candidate OR create new
            ↓
            Update: seenCount++, maxConfidence, category
            ↓
            Check promotion criteria:
                - seenCount >= minSeenCount (1)
                - maxConfidence >= minConfidence (0.25)
            ↓
            IF ready for promotion:
                - Remove from candidates map
                - Generate price via PricingEngine
                - Create ScannedItem
                - DetectionLogger.logCandidateUpdate(promoted=true)
                - Return ScannedItem
            ELSE:
                - Keep in candidates map
                - Return null
    ↓
    Collect promoted items
    ↓
    IF items promoted:
        ItemsViewModel.addItems() (deduplication)
        ↓
        StateFlow emits new list
        ↓
        UI updates item count
    ↓
    Every 10 frames:
        - CandidateTracker.cleanupExpiredCandidates()
        - DetectionLogger.logTrackerStats()
    ↓
User stops scanning
    ↓
CameraXManager.stopScanning()
    ↓
CandidateTracker.clear()
```

***REMOVED******REMOVED******REMOVED*** Barcode Scanning Flow

```
User selects Barcode mode + taps scan
    ↓
CameraXManager.startScanning(ScanMode.BARCODE)
    ↓
Every 800ms:
    ↓
    ImageProxy → InputImage
    ↓
    BarcodeScannerClient.scanBarcodes()
    ↓
    ML Kit Barcode Scanner processes
    ↓
    For each barcode:
        - Extract raw value
        - Extract format (QR, EAN, etc.)
        - Create ScannedItem with BARCODE category
        - Generate price based on format
    ↓
    Return List<ScannedItem> (no multi-frame tracking)
    ↓
    ItemsViewModel.addItems() (deduplication)
```

***REMOVED******REMOVED******REMOVED*** Navigation Flow

```
App Launch
    ↓
MainActivity.onCreate()
    ↓
ScaniumApp composable
    ↓
Create shared ItemsViewModel
    ↓
NavHost → CameraScreen (start destination)
    ↓
User taps "Items (N)" button
    ↓
navController.navigate(Routes.ITEMS_LIST)
    ↓
ItemsListScreen (same ViewModel)
    ↓
User taps back arrow
    ↓
navController.popBackStack()
    ↓
Return to CameraScreen
```

---

***REMOVED******REMOVED*** Technology Stack

***REMOVED******REMOVED******REMOVED*** Core Android

| Technology | Version | Purpose |
|------------|---------|---------|
| Kotlin | 2.0.0 | Primary language |
| Android Gradle Plugin | 8.5.0 | Build system |
| Compile SDK | 34 | Target API level |
| Min SDK | 24 | Minimum support (Android 7.0) |

***REMOVED******REMOVED******REMOVED*** UI Layer

| Technology | Version | Purpose |
|------------|---------|---------|
| Jetpack Compose BOM | 2023.10.01 | Declarative UI |
| Compose Material 3 | - | Design system |
| Material Icons Extended | - | Icon library |
| Activity Compose | 1.8.2 | Compose-Activity bridge |

***REMOVED******REMOVED******REMOVED*** Architecture Components

| Technology | Version | Purpose |
|------------|---------|---------|
| Lifecycle Runtime KTX | 2.7.0 | Lifecycle awareness |
| ViewModel Compose | 2.7.0 | State management |
| Navigation Compose | 2.7.6 | Screen navigation |

***REMOVED******REMOVED******REMOVED*** Camera & ML

| Technology | Version | Purpose |
|------------|---------|---------|
| CameraX Core | 1.3.1 | Camera abstraction |
| CameraX Camera2 | 1.3.1 | Camera2 implementation |
| CameraX Lifecycle | 1.3.1 | Lifecycle integration |
| CameraX View | 1.3.1 | PreviewView component |
| ML Kit Object Detection | 17.0.1 | On-device ML |
| Coroutines Play Services | 1.7.3 | Async ML Kit operations |

***REMOVED******REMOVED******REMOVED*** Permissions

| Technology | Version | Purpose |
|------------|---------|---------|
| Accompanist Permissions | 0.32.0 | Permission handling in Compose |

***REMOVED******REMOVED******REMOVED*** Testing

| Technology | Version | Purpose |
|------------|---------|---------|
| JUnit | 4.13.2 | Unit testing framework |
| Robolectric | 4.11.1 | Android framework in unit tests |
| Truth | 1.1.5 | Fluent assertions |
| MockK | 1.13.8 | Mocking framework |
| Coroutines Test | 1.7.3 | Coroutine testing utilities |
| AndroidX Test | 1.1.5 | Instrumentation tests |
| Espresso | 3.5.1 | UI testing |
| Compose UI Test | - | Compose testing |
| Core Testing | 2.2.0 | LiveData/Flow testing utilities |

**Test Coverage:**
- **110 total tests** (all passing ✅)
- **7 unit test files** covering:
  - CandidateTracker (multi-frame promotion logic)
  - DetectionCandidate (promotion criteria validation)
  - ItemsViewModel (state management & deduplication)
  - PricingEngine (EUR price generation)
  - ScannedItem (confidence classification)
  - ItemCategory (ML Kit label mapping)
  - FakeObjectDetector (test fixtures)
- **2 instrumented test files** covering:
  - ModeSwitcher (Compose UI interaction)
  - ItemsViewModel (integration tests)

---

***REMOVED******REMOVED*** Design Patterns

***REMOVED******REMOVED******REMOVED*** 1. Repository Pattern (Simplified)

**Where:** `ItemsViewModel` acts as lightweight repository

**Why:** Centralizes data access and business logic

**Implementation:**
```kotlin
class ItemsViewModel : ViewModel() {
    private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())
    val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()

    fun addItems(newItems: List<ScannedItem>) { /* deduplication */ }
}
```

***REMOVED******REMOVED******REMOVED*** 2. Singleton Pattern

**Where:** `PricingEngine`, ML Kit detector

**Why:** Single instance, global access, stateless operations

**Implementation:**
```kotlin
object PricingEngine {
    fun generatePriceRange(...): Pair<Double, Double>
}
```

***REMOVED******REMOVED******REMOVED*** 3. Facade Pattern

**Where:** `CameraXManager`, `ObjectDetectorClient`

**Why:** Simplify complex subsystems (CameraX, ML Kit)

**Benefits:**
- Hide implementation details
- Provide domain-specific API
- Easier to test and mock

***REMOVED******REMOVED******REMOVED*** 4. Observer Pattern

**Where:** StateFlow/Compose state observation

**Why:** Reactive UI updates

**Implementation:**
```kotlin
val items by itemsViewModel.items.collectAsState()
```

***REMOVED******REMOVED******REMOVED*** 5. Factory Pattern

**Where:** `AndroidView` factory for PreviewView

**Why:** Lazy initialization of Android views in Compose

***REMOVED******REMOVED******REMOVED*** 6. Builder Pattern

**Where:** ML Kit `ObjectDetectorOptions`, CameraX builders

**Why:** Fluent API for complex configuration

---

***REMOVED******REMOVED*** Future Considerations

***REMOVED******REMOVED******REMOVED*** Scalability Improvements

1. **Multi-Module Architecture**
   - `:app` - UI & navigation
   - `:feature:camera` - Camera feature
   - `:feature:items` - Items feature
   - `:core:ml` - ML Kit wrapper
   - `:core:data` - Data layer
   - `:core:ui` - Shared UI components

2. **Dependency Injection**
   - Add Hilt when complexity increases
   - Better testability with mock dependencies
   - Scoped dependencies (screen-level, app-level)

3. **Local Persistence**
   - Room database for item history
   - DataStore for user preferences
   - Paging 3 for large lists

4. **Remote Backend**
   - Retrofit + OkHttp for API calls
   - Real pricing API integration
   - User accounts and cloud sync
   - Repository pattern with remote/local sources

5. **Advanced ML**
   - Custom TensorFlow Lite model
   - Fine-grained product recognition
   - Brand detection
   - Condition assessment (good/fair/poor)

6. **Testing**
   - Unit tests for ViewModels
   - Compose UI tests
   - Screenshot tests
   - End-to-end integration tests

7. **Performance**
   - Image optimization (reduce memory)
   - Background ML processing
   - Result caching
   - LazyList optimizations

8. **UX Enhancements**
   - Onboarding flow
   - Settings screen
   - Item history/search
   - Price trends
   - Share functionality
   - Accessibility improvements

9. **Analytics & Monitoring**
   - Firebase Analytics
   - Crashlytics
   - Performance monitoring
   - User behavior tracking

10. **CI/CD**
    - GitHub Actions / GitLab CI
    - Automated testing
    - Release automation
    - Version management

---

***REMOVED******REMOVED*** Summary

Scanium demonstrates a **clean, pragmatic architecture** suitable for a proof-of-concept Android application. The architecture prioritizes:

- **Simplicity** - No unnecessary abstractions or frameworks
- **Modularity** - Clear separation of concerns via packages
- **Testability** - Dependency injection, pure functions
- **Maintainability** - Consistent patterns, clear naming
- **Extensibility** - Easy to add features or swap implementations
- **Modern practices** - Jetpack Compose, Kotlin Flow, CameraX, ML Kit

The single-module MVVM approach with feature-based packages provides the right balance of structure and simplicity for the current scope, while the clean interfaces and separation of concerns make it straightforward to evolve into a multi-module architecture or add backend integration when needed.

**Key Strengths:**
- Camera-first UX with intuitive gestures
- On-device ML (privacy, performance)
- Reactive state management
- Material 3 design
- Clear data flow

**Known Limitations:**
- No persistence (intentional for PoC)
- Mocked pricing
- Coarse object categories
- No backend integration
- No user accounts

This architecture serves as a solid foundation for future enhancements while remaining simple enough to understand and modify quickly.
