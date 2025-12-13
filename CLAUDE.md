***REMOVED*** CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

***REMOVED******REMOVED*** Project Overview

**Scanium** is a camera-first Android app that performs real-time object detection, barcode scanning, and document text recognition using Google ML Kit. All processing happens on-device with no cloud calls.

- **Package**: `com.scanium.app`
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Required Java**: 17 (See SETUP.md for cross-platform installation)

***REMOVED******REMOVED*** Common Commands

***REMOVED******REMOVED******REMOVED*** Building
```bash
./build.sh assembleDebug         ***REMOVED*** Build debug APK (auto-detects Java 17)
./build.sh assembleRelease       ***REMOVED*** Build release APK
./build.sh clean                 ***REMOVED*** Clean build artifacts

***REMOVED*** Alternative: Direct Gradle (ensure Java 17 is active)
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew clean
```

**Note**: The `build.sh` script automatically finds Java 17 on macOS, Linux, and Windows (via SDKMAN, mise, or standard paths). See `SETUP.md` for detailed cross-platform setup instructions.

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
├── aggregation/     ***REMOVED*** Session-level similarity-based item aggregation
├── camera/          ***REMOVED*** CameraX integration, scan modes, gesture handling
├── domain/          ***REMOVED*** Domain Pack system (config, repository, category engine)
│   ├── config/      ***REMOVED*** Domain Pack data models and enums
│   ├── repository/  ***REMOVED*** Domain Pack loading and caching
│   └── category/    ***REMOVED*** Category matching and mapping
├── items/           ***REMOVED*** Items list, ViewModel, data models
├── ml/              ***REMOVED*** ML Kit wrappers (object detection, barcode, OCR)
├── tracking/        ***REMOVED*** Multi-frame object tracking and de-duplication
├── navigation/      ***REMOVED*** Navigation graph
├── selling/         ***REMOVED*** eBay marketplace integration (mock)
│   ├── data/        ***REMOVED*** EbayApi, MockEbayApi, EbayMarketplaceService, ListingRepository
│   ├── domain/      ***REMOVED*** Listing, ListingDraft, ListingStatus, ListingCondition, ListingImage
│   ├── ui/          ***REMOVED*** SellOnEbayScreen, ListingViewModel, DebugSettingsDialog
│   └── util/        ***REMOVED*** ListingImagePreparer, ListingDraftMapper
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

**UI Components** (`camera/`):
- `VerticalThresholdSlider.kt`: Confidence threshold slider
  - Slim vertical design (20dp width, 280dp height)
  - Percentage display at top in fixed position
  - Minimal design without HI/LO labels
- `ClassificationModeToggle.kt`: Cloud/On-device mode switcher
  - Clean toggle without extra labels
- Camera screen optimized for clarity with minimal overlay text

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

**Important**: ViewModel integrates with ItemAggregator for similarity-based deduplication (see Aggregation System below).

***REMOVED******REMOVED******REMOVED*** 5. Aggregation System (`aggregation/`)

**Purpose:** Session-level similarity-based deduplication that complements frame-level tracking.

**Why Aggregation Is Needed:**

ObjectTracker handles frame-to-frame tracking but has limitations:
- ML Kit `trackingId` changes frequently for the same physical object
- Camera movement causes bounding box shifts
- Users panning slowly across objects creates new IDs

ItemAggregator solves this by merging detections based on **weighted similarity scoring** rather than exact ID match.

**Core Components:**

**ItemAggregator** (`aggregation/ItemAggregator.kt`):
- Maintains collection of `AggregatedItem` representing unique physical objects
- Compares new detections against existing items using multi-factor similarity
- Merges similar detections or creates new aggregated items
- Provides dynamic threshold adjustment for real-time tuning

**Similarity Scoring Algorithm:**

Weighted combination of 4 factors:
```kotlin
similarity = (categoryWeight * categorySimilarity) +
             (labelWeight * labelSimilarity) +
             (sizeWeight * sizeSimilarity) +
             (distanceWeight * distanceSimilarity)
```

Default weights (REALTIME preset):
- Category: 40% (most stable across frames)
- Label: 15% (can change)
- Size: 20% (normalized bounding box area)
- Distance: 25% (center point proximity)

**AggregationPresets** (`aggregation/AggregationPresets.kt`):

Six preconfigured presets for different use cases:

1. **BALANCED** (threshold 0.6):
   - Good default for most scenarios
   - Balanced precision/recall
   - Weights: category 30%, label 25%, size 20%, distance 25%

2. **STRICT** (threshold 0.75):
   - High precision, fewer merges
   - Good for dense scenes with similar objects
   - Requires category AND label match
   - Weights: category 35%, label 35%, size 15%, distance 15%

3. **LOOSE** (threshold 0.5):
   - High recall, more aggressive merging
   - Good for quick inventory counts
   - Tolerant of camera movement
   - Weights: category 35%, label 20%, size 15%, distance 30%

4. **REALTIME** (threshold 0.55) - **Currently Used**:
   - Optimized for continuous scanning with camera movement
   - Tolerant of bounding box jitter and trackingId changes
   - Good for long-press continuous scanning mode
   - Weights: category 40%, label 15%, size 20%, distance 25%

5. **LABEL_FOCUSED** (threshold 0.65):
   - Emphasizes label text similarity
   - Good for branded products with clear text
   - Requires labels to match
   - Weights: category 25%, label 45%, size 15%, distance 15%

6. **SPATIAL_FOCUSED** (threshold 0.6):
   - Emphasizes spatial proximity and size
   - Good for generic objects without clear labels
   - Stricter distance/size requirements
   - Weights: category 30%, label 10%, size 30%, distance 30%

**Integration Flow:**

```
Camera → ML Kit → ObjectTracker (frame-level)
  → ScannedItem with trackingId
  → ItemAggregator (session-level)
  → AggregatedItem (merged detections)
  → ItemsViewModel → UI
```

**Usage in ItemsViewModel:**

```kotlin
// Initialize with REALTIME preset
private val itemAggregator = ItemAggregator(
    config = AggregationPresets.REALTIME
)

// Process detections
fun addItem(item: ScannedItem) {
    val aggregatedItem = itemAggregator.processDetection(item)
    updateItemsState()
}

// Dynamic threshold adjustment
fun updateSimilarityThreshold(threshold: Float) {
    itemAggregator.updateSimilarityThreshold(threshold)
}
```

**Key Methods:**

- `processDetection(ScannedItem)`: Process single detection, returns AggregatedItem
- `processDetections(List<ScannedItem>)`: Batch processing
- `updateSimilarityThreshold(Float)`: Dynamic threshold adjustment (0.0-1.0)
- `removeItem(String)`: Remove aggregated item by ID
- `reset()`: Clear all aggregated items
- `getStats()`: Get aggregation statistics (total items, merges, etc.)

**Tuning:**

Current configuration (REALTIME preset):
- `similarityThreshold = 0.55f` - Lower = more aggressive merging
- `maxCenterDistanceRatio = 0.30f` - Max 30% of frame diagonal
- `maxSizeDifferenceRatio = 0.6f` - Max 60% size difference
- `categoryMatchRequired = true` - Category must match
- `labelMatchRequired = false` - Labels optional (can be inconsistent)

To change preset:
```kotlin
val itemAggregator = ItemAggregator(
    config = AggregationPresets.STRICT  // or LOOSE, BALANCED, etc.
)
```

***REMOVED******REMOVED******REMOVED*** 6. Navigation

**Single-Activity architecture** with Compose Navigation:
- `MainActivity` hosts `NavHost`
- Start destination: `Routes.CAMERA`
- ItemsViewModel instantiated at app root, passed to screens
- No fragment transactions, cleaner lifecycle

***REMOVED******REMOVED******REMOVED*** 6. Domain Pack & Category System (`domain/`)

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

***REMOVED******REMOVED******REMOVED*** 7. eBay Selling Integration (`selling/`)

**NEW**: Complete marketplace integration with real on-device scanning and mocked eBay API for demo and testing purposes.

**Key Components**:

**ListingImagePreparer** (`selling/util/ListingImagePreparer.kt`):
- Prepares high-quality images for web/mobile viewing
- Priority: `fullImageUri` → `thumbnail` (with scaling if needed)
- Minimum resolution: 500×500, preferred: 1600×1600
- JPEG quality: 85 (high quality, reasonable size)
- All processing on `Dispatchers.IO` (background thread)
- Comprehensive logging: resolution, file size, source, quality
- Returns sealed `PrepareResult` (Success/Failure)

**MockEbayApi** (`selling/data/MockEbayApi.kt`):
- Realistic simulation with configurable behavior
- Network delays: 400-1200ms random (configurable)
- Failure modes: NONE, NETWORK_TIMEOUT, VALIDATION_ERROR, IMAGE_TOO_SMALL, RANDOM
- Failure rate: 0.0 (never) to 1.0 (always)
- Generates mock listing IDs: `EBAY-MOCK-{timestamp}-{random}`
- Mock URLs: `https://mock.ebay.local/listing/{id}`
- Controlled via `MockEbayConfigManager` singleton

**EbayMarketplaceService** (`selling/data/EbayMarketplaceService.kt`):
- Orchestrates listing creation workflow
- Uses `ListingImagePreparer` for image processing
- Communicates with `EbayApi` (mock or real)
- Returns `ListingCreationResult` (Success/Error)
- Cleans up old cached images automatically

**ListingViewModel** (`selling/ui/ListingViewModel.kt`):
- Manages listing drafts and posting workflow
- Communicates with `ItemsViewModel` for status updates
- Tracks per-item posting status (IDLE → POSTING → SUCCESS/FAILURE)
- Prevents double-posting with disabled button state
- Logs comprehensive debugging information

**ItemsViewModel** (enhanced):
- Added `updateListingStatus()`: Updates item listing state
- Added `getListingStatus()`: Queries item listing status
- Added `getItem()`: Retrieves item by ID
- Communicates with `ListingViewModel` for status tracking

**ScannedItem** (enhanced):
- `fullImageUri`: Optional URI to high-quality image
- `listingStatus`: `ItemListingStatus` enum (NOT_LISTED, LISTING_IN_PROGRESS, LISTED_ACTIVE, LISTING_FAILED)
- `listingId`: eBay listing ID when posted
- `listingUrl`: External URL to view listing

**User Journey**:
1. Scan items (real on-device ML Kit detection)
2. Long-press item in list → enter selection mode
3. Tap to select multiple items
4. Tap "Sell on eBay" → navigate to sell screen
5. Review/edit drafts (title, price, condition)
6. Tap "Post to eBay (Mock)" → sequential posting with progress
7. Return to items list → see status badges (Listed/Posting.../Failed)
8. Tap "View" on listed items → open mock URL

**Debug Settings**:
- `MockEbayConfigManager`: Singleton config with `StateFlow`
- `DebugSettingsDialog`: UI for configuring mock behavior
- Toggle network delay simulation
- Select failure mode (radio buttons with descriptions)
- Adjust failure rate (0-100% slider)
- Reset to defaults button

**Configuration Example** (`res/raw/home_resale_domain_pack.json`):
- 23 fine-grained categories: sofa, chair, table, laptop, TV, phone, shoes, jacket, bag, etc.
- 10 extractable attributes: brand, color, material, size, condition, weight, dimensions, etc.
- Each category has ML Kit prompts for matching (e.g., sofa matches "sofa", "couch", "sectional")
- Priority-based disambiguation for overlapping categories

See `md/architecture/DOMAIN_PACK_ARCHITECTURE.md` for complete documentation.

***REMOVED******REMOVED*** Data Models

***REMOVED******REMOVED******REMOVED*** Core Detection Models

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

***REMOVED******REMOVED******REMOVED*** Domain Pack Models

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

***REMOVED******REMOVED*** Testing

**175+ total tests** across unit and instrumented tests:

**Unit Tests** (`app/src/test/`):

*Tracking & Detection (110 tests):*
- `ObjectTrackerTest.kt` - Tracking logic, confirmation, expiry
- `ObjectCandidateTest.kt` - Spatial matching (IoU, distance)
- `TrackingPipelineIntegrationTest.kt` - End-to-end tracking scenarios
- `ItemsViewModelTest.kt` - State management, de-duplication
- `DetectionResultTest.kt` - Overlay data model validation
- `PricingEngineTest.kt` - EUR price generation
- `ItemCategoryTest.kt` - ML Kit label mapping

*Domain Pack System (61 tests):*
- `DomainPackTest.kt` (10 tests) - DomainPack data model and helper methods
- `LocalDomainPackRepositoryTest.kt` (14 tests) - JSON loading, caching, validation
- `CategoryMapperTest.kt` (11 tests) - DomainCategory ↔ ItemCategory conversion
- `BasicCategoryEngineTest.kt` (16 tests) - ML Kit label matching, priority handling
- `DomainPackProviderTest.kt` (10 tests) - Singleton initialization, thread safety

*eBay Selling Integration (4+ tests):*
- `ListingImagePreparerTest.kt` - Image preparation, scaling, quality
- `MockEbayConfigManagerTest.kt` - Configuration management, state updates
- `ItemListingStatusTest.kt` - Listing status enum validation
- `ItemsViewModelListingStatusTest.kt` - Status tracking and updates

**Instrumented Tests** (`app/src/androidTest/`):
- `ModeSwitcherInstrumentedTest.kt` - Compose UI for mode switching
- `DetectionOverlayInstrumentedTest.kt` - Bounding box rendering

**Test Frameworks**:
- JUnit 4, Robolectric (SDK 28), Truth assertions, MockK, Coroutines Test, Compose Testing, Kotlinx Serialization Test

***REMOVED******REMOVED*** Configuration & Tuning

***REMOVED******REMOVED******REMOVED*** Tracker Configuration
Located in `CameraXManager.kt`:
```kotlin
TrackerConfig(
    minFramesToConfirm = 1,      // Instant confirmation (rely on aggregator for quality)
    minConfidence = 0.2f,         // Very low (20%) - aggressive detection
    minBoxArea = 0.0005f,         // Accept tiny objects (0.05% of frame)
    maxFrameGap = 8,              // Forgiving matching (allow 8 frames gap)
    minMatchScore = 0.2f,         // Low spatial matching threshold
    expiryFrames = 15             // Keep candidates 15 frames (~12 seconds at 800ms)
)
```

**Rationale for Aggressive Thresholds:**
- **Session-level deduplication**: ItemAggregator handles quality filtering
- **Responsive UX**: Instant confirmation feels more natural
- **Inclusive detection**: Low confidence catches edge cases
- **Forgiving tracking**: maxFrameGap=8 handles occlusion/movement

**Trade-offs:**
- More false positives at tracker level (filtered by aggregator)
- Potentially more noise (mitigated by 0.2f confidence minimum)
- Higher memory usage (more candidates stay active longer)

**Tuning Guidelines:**
- Increase `minFramesToConfirm` to 3 for more stable confirmations
- Increase `minConfidence` to 0.4f to reduce false positives
- Increase `minBoxArea` to 0.001f to ignore very small objects
- Decrease `expiryFrames` to 10 to reduce memory usage
- Balance: tracker permissiveness vs aggregator strictness

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
- Previously implemented Room database layer was removed to align with PoC scope (Issue 002)
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

***REMOVED******REMOVED******REMOVED*** Adding Domain Pack Categories
1. Edit `res/raw/home_resale_domain_pack.json`
2. Add new category with:
   - Unique `id` (e.g., "electronics.tablet")
   - `displayName` for UI
   - `itemCategoryName` mapping to ML Kit category
   - `prompts` array for label matching
   - Optional `priority` for disambiguation
3. Test with: `./gradlew test --tests "*DomainPack*"`
4. Verify loading: `adb logcat | grep DomainPack`

***REMOVED******REMOVED******REMOVED*** Creating a New Domain Pack
1. Create JSON file in `res/raw/` (e.g., `fashion_domain_pack.json`)
2. Follow schema from `home_resale_domain_pack.json`
3. Update `LocalDomainPackRepository` to load new pack
4. Initialize in `MainActivity` with appropriate pack ID
5. Add validation tests

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
  - ✅ **Mitigated**: Domain Pack system provides 23 fine-grained categories with extensible config
- **No persistence**: Items and listing status cleared on app close
- **Mocked pricing**: Local price generation, no real API
- **Mocked eBay**: Complete UI flow implemented, but uses mock API
  - ✅ **Ready for real integration**: Clean interface separation, just swap MockEbayApi for RealEbayApi
- **Single module**: Will need multi-module architecture at scale
- **No DI framework**: Manual dependency injection may not scale
- **Attribute extraction**: Domain Pack defines attributes but extraction not yet implemented (CLIP, OCR, Cloud pipelines pending)

***REMOVED******REMOVED*** Future Architecture Considerations

When the app grows, consider:
- **Multi-module architecture**: Split into `:app`, `:feature:*`, `:core:*` modules
- **Hilt dependency injection**: For complex scoping and testing
- **Room database**: For local persistence with Paging 3
- **Backend integration**: Retrofit + Repository pattern with remote/local sources
- **Custom TensorFlow Lite model**: For fine-grained product recognition beyond ML Kit

***REMOVED******REMOVED*** Reference Documentation

***REMOVED******REMOVED******REMOVED*** Setup & Environment
- **Cross-platform setup**: See `SETUP.md` for Java 17 installation and multi-machine development workflow
- **General usage**: See `README.md` for app features and usage instructions

***REMOVED******REMOVED******REMOVED*** Architecture
- **Architecture overview**: See `md/architecture/ARCHITECTURE.md` for comprehensive system architecture
- **Domain Pack system**: See `md/architecture/DOMAIN_PACK_ARCHITECTURE.md` for category taxonomy and config schema
- **Tracking implementation**: See `md/features/TRACKING_IMPLEMENTATION.md` for object tracking and de-duplication

***REMOVED******REMOVED******REMOVED*** Features
- **eBay Selling Integration**: See `md/features/EBAY_SELLING_INTEGRATION.md` for complete marketplace flow documentation

***REMOVED******REMOVED******REMOVED*** Testing & Debugging
- **Test suite**: See `md/testing/TEST_SUITE.md` for test coverage and frameworks
- **Debug guide**: See `md/debugging/DIAGNOSTIC_LOG_GUIDE.md` for debugging ML Kit issues
