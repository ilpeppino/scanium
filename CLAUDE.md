# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Scanium** is a camera-first Android app that performs real-time object detection, barcode scanning, and document text recognition using Google ML Kit. All processing happens on-device with no cloud calls.

- **Package**: `com.scanium.app`
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Required Java**: 17 (See SETUP.md for cross-platform installation)

## Common Commands

### Building
```bash
./build.sh assembleDebug         # Build debug APK (auto-detects Java 17)
./build.sh assembleRelease       # Build release APK
./build.sh clean                 # Clean build artifacts

# Alternative: Direct Gradle (ensure Java 17 is active)
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew clean
```

**Note**: The `build.sh` script automatically finds Java 17 on macOS, Linux, and Windows (via SDKMAN, mise, or standard paths). See `SETUP.md` for detailed cross-platform setup instructions.

### Testing
```bash
./gradlew test                   # Run all unit tests
./gradlew test --console=plain   # Run unit tests with detailed output
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
```

### Running Single Tests
```bash
./gradlew test --tests "com.scanium.app.tracking.ObjectTrackerTest"
./gradlew test --tests "com.scanium.app.items.ItemsViewModelTest.testAddItems"
```

### Installation
```bash
./gradlew installDebug           # Install debug APK on connected device
adb logcat                       # View device logs
```

### Debugging ML/Tracking Issues
```bash
# Filter logs for tracking and detection
adb logcat | grep -E "ObjectTracker|CameraXManager|ObjectDetector"

# Watch for specific events
adb logcat | grep "CONFIRMED candidate"  # See when objects are confirmed
adb logcat | grep "Tracker stats"        # See tracking statistics
```

## Architecture Overview

### Pattern: Simplified MVVM
- **View**: Jetpack Compose screens (`camera/`, `items/`)
- **ViewModel**: `ItemsViewModel` (shared state across screens)
- **Model**: Data classes in `ml/`, `tracking/`, `items/`
- **State Management**: Kotlin `StateFlow` for reactive updates

### Package Structure
```
app/src/main/java/com/scanium/app/
├── camera/          # CameraX integration, scan modes, gesture handling
├── domain/          # Domain Pack system (config, repository, category engine)
│   ├── config/      # Domain Pack data models and enums
│   ├── repository/  # Domain Pack loading and caching
│   └── category/    # Category matching and mapping
├── items/           # Items list, ViewModel, data models
├── ml/              # ML Kit wrappers (object detection, barcode, OCR)
├── tracking/        # Multi-frame object tracking and de-duplication
├── navigation/      # Navigation graph
├── selling/         # eBay marketplace integration (mock)
└── ui/theme/        # Material 3 theming
```

### Feature-Based Organization
- Each package contains related UI, logic, and models together
- No dependency injection framework (manual constructor injection)
- Single-module architecture appropriate for current scope

## Key Architectural Components

### 1. Camera & Image Processing (`camera/`)

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

**UI Components** (`camera/`):
- `VerticalThresholdSlider.kt`: Confidence threshold slider
  - Slim vertical design (20dp width, 280dp height)
  - Percentage display at top in fixed position
  - Minimal design without HI/LO labels
- `ClassificationModeToggle.kt`: Cloud/On-device mode switcher
  - Clean toggle without extra labels
- Camera screen optimized for clarity with minimal overlay text

### 2. ML Kit Integration (`ml/`)

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

### 3. Object Tracking System (`tracking/`)

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

### 4. State Management (`items/`)

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

### 5. Navigation

**Single-Activity architecture** with Compose Navigation:
- `MainActivity` hosts `NavHost`
- Start destination: `Routes.CAMERA`
- ItemsViewModel instantiated at app root, passed to screens
- No fragment transactions, cleaner lifecycle

### 6. Domain Pack & Category System (`domain/`)

**NEW**: Config-driven category taxonomy for fine-grained object classification beyond ML Kit's 5 coarse categories.

**DomainPack** (`domain/config/DomainPack.kt`):
- JSON-based configuration in `res/raw/home_resale_domain_pack.json`
- Contains 23 fine-grained categories (furniture, electronics, clothing, etc.)
- Defines 10 extractable attributes (brand, color, material, size, condition, etc.)
- Each category maps to an ML Kit `ItemCategory` with matching prompts

**LocalDomainPackRepository** (`domain/repository/LocalDomainPackRepository.kt`):
- Loads and validates Domain Pack JSON from resources
- Singleton pattern with thread-safe lazy initialization
- Caches parsed configuration in memory
- Provides fallback empty pack on error
- Validates category IDs and itemCategoryName references

**BasicCategoryEngine** (`domain/category/BasicCategoryEngine.kt`):
- Implements `CategoryEngine` interface for ML Kit label matching
- Matches detected labels against DomainCategory prompts
- Uses priority-based tie-breaking for overlapping categories
- Returns scored category candidates with confidence

**CategoryMapper** (`domain/category/CategoryMapper.kt`):
- Extension functions for DomainCategory ↔ ItemCategory conversion
- Validates itemCategoryName references
- Provides safe fallback to UNKNOWN category

**DomainPackProvider** (`domain/DomainPackProvider.kt`):
- Singleton provider for app-wide access to domain system
- Initialized in `MainActivity.onCreate()`
- Provides repository and engine instances

**Integration**:
- `ScannedItem` has optional `domainCategoryId` field (non-breaking)
- Ready for future attribute extraction pipelines (CLIP, OCR, Cloud)
- Fully tested with 61 unit tests

**Configuration Example** (`res/raw/home_resale_domain_pack.json`):
- 23 fine-grained categories: sofa, chair, table, laptop, TV, phone, shoes, jacket, bag, etc.
- 10 extractable attributes: brand, color, material, size, condition, weight, dimensions, etc.
- Each category has ML Kit prompts for matching (e.g., sofa matches "sofa", "couch", "sectional")
- Priority-based disambiguation for overlapping categories

See `md/architecture/DOMAIN_PACK_ARCHITECTURE.md` for complete documentation.

## Data Models

### Core Detection Models

**ItemCategory Enum** (`ml/ItemCategory.kt`):
- Maps ML Kit's 5 coarse categories to app categories
- Values: `FASHION`, `FOOD`, `HOME_GOODS`, `PLACES`, `PLANTS`, `UNKNOWN`, `BARCODE`, `DOCUMENT`
- Method: `fromMlKitLabel(text: String)` for mapping ML Kit labels

**ConfidenceLevel Enum** (`items/ScannedItem.kt`):
- `LOW` (0.0-0.5), `MEDIUM` (0.5-0.75), `HIGH` (0.75-1.0)

**ScanMode Enum** (`camera/ScanMode.kt`):
- `OBJECT_DETECTION`, `BARCODE`, `DOCUMENT_TEXT`

**ScannedItem** (`items/ScannedItem.kt`):
- Properties: `id`, `thumbnail`, `category`, `priceRange`, `confidence`, `timestamp`, `domainCategoryId` (optional)
- Computed: `confidenceLevel`, `formattedConfidence`, `formattedPriceRange`

### Domain Pack Models

**DomainPack** (`domain/config/DomainPack.kt`):
```kotlin
@Serializable
data class DomainPack(
    val id: String,
    val version: String,
    val categories: List<DomainCategory>,
    val attributes: List<DomainAttribute>
)
```
- Helper methods: `getEnabledCategories()`, `getCategoryById()`, `getCategoriesForItemCategory()`, `getCategoriesByPriority()`

**DomainCategory** (`domain/config/DomainCategory.kt`):
```kotlin
@Serializable
data class DomainCategory(
    val id: String,              // e.g., "furniture.sofa"
    val displayName: String,     // e.g., "Sofa"
    val parentId: String?,       // e.g., "furniture"
    val itemCategoryName: String, // Maps to ItemCategory enum
    val prompts: List<String>,   // ML Kit label matching prompts
    val priority: Int? = null,   // Disambiguation priority
    val enabled: Boolean
)
```

**DomainAttribute** (`domain/config/DomainAttribute.kt`):
```kotlin
@Serializable
data class DomainAttribute(
    val id: String,
    val displayName: String,
    val type: AttributeType,           // STRING, NUMBER, ENUM, BOOLEAN
    val extractionMethod: ExtractionMethod, // OCR, CLIP, CLOUD, HEURISTIC, etc.
    val enabled: Boolean
)
```

**AttributeType Enum**: `STRING`, `NUMBER`, `ENUM`, `BOOLEAN`

**ExtractionMethod Enum**: `OCR`, `BARCODE`, `CLIP`, `CLOUD`, `HEURISTIC`, `NONE`

## Testing

**171 total tests** across unit and instrumented tests:

**Unit Tests** (`app/src/test/`):

*Tracking & Detection (original 110 tests):*
- `ObjectTrackerTest.kt` - Tracking logic, confirmation, expiry
- `ObjectCandidateTest.kt` - Spatial matching (IoU, distance)
- `TrackingPipelineIntegrationTest.kt` - End-to-end tracking scenarios
- `ItemsViewModelTest.kt` - State management, de-duplication
- `DetectionResultTest.kt` - Overlay data model validation
- `PricingEngineTest.kt` - EUR price generation
- `ItemCategoryTest.kt` - ML Kit label mapping

*Domain Pack System (61 new tests):*
- `DomainPackTest.kt` (10 tests) - DomainPack data model and helper methods
- `LocalDomainPackRepositoryTest.kt` (14 tests) - JSON loading, caching, validation
- `CategoryMapperTest.kt` (11 tests) - DomainCategory ↔ ItemCategory conversion
- `BasicCategoryEngineTest.kt` (16 tests) - ML Kit label matching, priority handling
- `DomainPackProviderTest.kt` (10 tests) - Singleton initialization, thread safety

**Instrumented Tests** (`app/src/androidTest/`):
- `ModeSwitcherInstrumentedTest.kt` - Compose UI for mode switching
- `DetectionOverlayInstrumentedTest.kt` - Bounding box rendering

**Test Frameworks**:
- JUnit 4, Robolectric (SDK 28), Truth assertions, MockK, Coroutines Test, Compose Testing, Kotlinx Serialization Test

## Configuration & Tuning

### Tracker Configuration
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

### Image Analysis Interval
In `CameraXManager.kt`:
```kotlin
val analysisIntervalMs = 800L  // Process every 800ms during scanning
```

## Code Patterns & Conventions

### State Management Pattern
```kotlin
// ViewModel
private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())
val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()

// UI
val items by viewModel.items.collectAsState()
```

### ML Kit Integration Pattern
```kotlin
// Wrap ML Kit in client class
class ObjectDetectorClient {
    private val detector = ObjectDetection.getClient(options)

    suspend fun detectObjects(...): List<ScannedItem> {
        // Convert ML Kit results to domain models
    }
}
```

### Camera Lifecycle Pattern
```kotlin
// Remember manager with lifecycle owner
val cameraManager = remember {
    CameraXManager(context, lifecycleOwner)
}

DisposableEffect(Unit) {
    onDispose { cameraManager.shutdown() }
}
```

## Important Implementation Notes

### 1. ML Kit Detection Modes
- **SINGLE_IMAGE_MODE**: Used for tap captures, better accuracy per frame
- **STREAM_MODE**: Used for continuous scanning, provides `trackingId` for tracking
- The app switches modes automatically based on capture type

### 2. Tracking ID Sources
- **Primary**: ML Kit `trackingId` (when available in STREAM_MODE)
- **Fallback**: Generated UUID for detections without trackingId
- **Spatial Matching**: IoU + center distance when trackingId unavailable

### 3. Memory Management
- ObjectTracker clears candidates on reset (mode change, scan stop)
- Candidates expire after timeout to prevent unbounded growth
- Thumbnails are Bitmap objects - ensure proper cleanup

### 4. Thread Safety
- Image analysis runs on background executor
- StateFlow updates are thread-safe
- UI updates via Compose state collection (lifecycle-aware)

### 5. No Persistence Layer
- All data is in-memory (ViewModel state)
- Items lost on app close (intentional for PoC)
- Future: Add Room database if persistence needed

## Common Development Tasks

### Adding a New Scan Mode
1. Add enum value to `ScanMode.kt`
2. Create ML Kit client wrapper in `ml/` (e.g., `NewScannerClient.kt`)
3. Add processing branch in `CameraXManager.processImageProxy()`
4. Update `ModeSwitcher.kt` UI
5. Add tests for new mode

### Modifying Tracking Behavior
1. Adjust `TrackerConfig` in `CameraXManager.kt`
2. Run tests: `./gradlew test --tests "*ObjectTracker*"`
3. Test on device with debug logs: `adb logcat | grep ObjectTracker`
4. Check confirmation timing and duplicate prevention

### Adding Domain Pack Categories
1. Edit `res/raw/home_resale_domain_pack.json`
2. Add new category with:
   - Unique `id` (e.g., "electronics.tablet")
   - `displayName` for UI
   - `itemCategoryName` mapping to ML Kit category
   - `prompts` array for label matching
   - Optional `priority` for disambiguation
3. Test with: `./gradlew test --tests "*DomainPack*"`
4. Verify loading: `adb logcat | grep DomainPack`

### Creating a New Domain Pack
1. Create JSON file in `res/raw/` (e.g., `fashion_domain_pack.json`)
2. Follow schema from `home_resale_domain_pack.json`
3. Update `LocalDomainPackRepository` to load new pack
4. Initialize in `MainActivity` with appropriate pack ID
5. Add validation tests

### Implementing Real Pricing API
1. Create `PricingRepository` interface in `ml/`
2. Implement with Retrofit/OkHttp
3. Replace `PricingEngine` calls in `ObjectDetectorClient`
4. Add error handling and fallback to mock prices
5. Update tests with mocked API responses

### Adding Persistence
1. Add Room dependencies to `app/build.gradle.kts`
2. Create database entities and DAO in `items/`
3. Implement Repository pattern with local/remote sources
4. Update `ItemsViewModel` to use repository
5. Add database tests

## Known Limitations

- **ML Kit categories**: Only 5 coarse categories (Fashion, Food, Home goods, Places, Plants)
  - ✅ **Mitigated**: Domain Pack system provides 23 fine-grained categories with extensible config
- **No persistence**: Items cleared on app close
- **Mocked pricing**: Local price generation, no real API
- **Single module**: Will need multi-module architecture at scale
- **No DI framework**: Manual dependency injection may not scale
- **Attribute extraction**: Domain Pack defines attributes but extraction not yet implemented (CLIP, OCR, Cloud pipelines pending)

## Future Architecture Considerations

When the app grows, consider:
- **Multi-module architecture**: Split into `:app`, `:feature:*`, `:core:*` modules
- **Hilt dependency injection**: For complex scoping and testing
- **Room database**: For local persistence with Paging 3
- **Backend integration**: Retrofit + Repository pattern with remote/local sources
- **Custom TensorFlow Lite model**: For fine-grained product recognition beyond ML Kit

## Reference Documentation

### Setup & Environment
- **Cross-platform setup**: See `SETUP.md` for Java 17 installation and multi-machine development workflow
- **General usage**: See `README.md` for app features and usage instructions

### Architecture
- **Architecture overview**: See `md/architecture/ARCHITECTURE.md` for comprehensive system architecture
- **Domain Pack system**: See `md/architecture/DOMAIN_PACK_ARCHITECTURE.md` for category taxonomy and config schema
- **Tracking implementation**: See `md/features/TRACKING_IMPLEMENTATION.md` for object tracking and de-duplication

### Testing & Debugging
- **Test suite**: See `md/testing/TEST_SUITE.md` for test coverage and frameworks
- **Debug guide**: See `md/debugging/DIAGNOSTIC_LOG_GUIDE.md` for debugging ML Kit issues
