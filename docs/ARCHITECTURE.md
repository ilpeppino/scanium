# Architecture

---

## Current State (Verified from Code)

### Build System & Module Structure (as of Phase 0 Discovery)

**Gradle Build Configuration:**
- Root build file: `build.gradle.kts` with Kotlin 2.0.0, Android Gradle Plugin 8.5.0
- Java 17 toolchain enforced across all modules (`kotlin.jvmToolchain(17)`)
- Settings file: `settings.gradle.kts` defines 11 modules total
- Portable module check task: `checkPortableModules` validates core-models and core-tracking have no Android imports
- Subproject dependency guard: prevents modules from depending on `:androidApp` to maintain clean layering

**Module Inventory:**
1. **androidApp** (com.android.application)
   - Main Android app module with Compose UI
   - Dependencies: all core-* and android-* modules
   - Build features: Compose, BuildConfig
   - Security plugins: CycloneDX SBOM (SEC-002), OWASP Dependency-Check (SEC-003)
   - API configuration via local.properties: `scanium.api.base.url`, `scanium.api.key`

2. **core-models** (com.android.library)
   - Android library wrapping shared:core-models
   - Namespace: `com.scanium.core.models`
   - API dependency on `:shared:core-models`
   - Contains typealiases pointing to shared KMP models

3. **core-tracking** (com.android.library)
   - Android library wrapping shared:core-tracking
   - Contains typealiases: ObjectTracker, DetectionInfo, TrackerConfig, TrackerStats
   - Points to `com.scanium.core.tracking.*` in shared module

4. **shared:core-models** (Kotlin Multiplatform)
   - Path: `shared/core-models/`
   - Source sets: commonMain, androidMain, iosMain
   - Core models: ImageRef, NormalizedRect, ItemCategory, ScannedItem, RawDetection, DetectionResult
   - Package: `com.scanium.shared.core.models.*` and `com.scanium.core.models.*`

5. **shared:core-tracking** (Kotlin Multiplatform)
   - Path: `shared/core-tracking/`
   - Source sets: commonMain, androidMain, iosMain
   - Core tracking: ObjectTracker, ObjectCandidate, Logger, platform-neutral tracking math
   - Package: `com.scanium.core.tracking.*`

6. **core-domainpack** (com.android.library)
   - Domain Pack category system
   - BasicCategoryEngine for ML Kit label matching
   - DomainPackRepository, CategoryMapper
   - JSON-based category configuration

7. **core-scan** (com.android.library)
   - Namespace for future scan contracts
   - Currently minimal/stub

8. **core-contracts** (com.android.library)
   - Namespace for future domain contracts
   - Currently minimal/stub

9. **android-ml-mlkit** (com.android.library)
   - ML Kit namespace shell
   - Currently minimal

10. **android-camera-camerax** (com.android.library)
    - CameraX namespace shell
    - Currently minimal

11. **android-platform-adapters** (com.android.library)
    - Converters: Bitmap → ImageRef, Rect → NormalizedRect
    - Files: RectAdapters.kt, ImageAdapters.kt
    - Bridges Android types to portable models

**Key Dependencies (androidApp):**
- CameraX 1.3.1 (camera-core, camera-camera2, camera-lifecycle, camera-view)
- ML Kit: object-detection 17.0.1, barcode-scanning 17.2.0, text-recognition 16.0.0
- Compose BOM 2023.10.01 (UI, Material3)
- Navigation Compose 2.7.6
- OkHttp 4.12.0 (for cloud classification)
- Kotlinx Serialization 1.6.0 (for Domain Pack JSON)
- Test: JUnit, MockK, Truth, Robolectric, Coroutines Test

### Scanning Pipeline & ML Flow

**Scan Modes** (enum ScanMode in core-models):
1. OBJECT_DETECTION - Multi-object detection with ML Kit
2. BARCODE - QR code and barcode scanning
3. DOCUMENT - OCR text recognition

**Camera & Detection Flow:**
1. **Entry Point**: `MainActivity.kt` → `ScaniumApp.kt` (root Compose)
2. **Camera Screen**: `CameraScreen.kt` hosts CameraX preview and detection overlays
3. **CameraX Manager**: `CameraXManager.kt` binds camera lifecycle, routes frames to analyzers based on ScanMode
4. **Analyzers**:
   - `ObjectDetectorClient.kt` - Wraps ML Kit Object Detection API
     - Two detectors: SINGLE_IMAGE_MODE (tap capture), STREAM_MODE (continuous scan)
     - `enableMultipleObjects()`, `enableClassification()` enabled
     - Confidence threshold: 0.3f for category assignment
     - Returns `DetectionResponse(scannedItems, detectionResults)` for list + overlay
   - `BarcodeScannerClient.kt` - ML Kit barcode/QR scanning
   - `DocumentTextRecognitionClient.kt` - ML Kit OCR
5. **Detection to Items**:
   - Raw ML Kit `DetectedObject` → `RawDetection` (normalized geometry, labels, thumbnail)
   - For tracking: `DetectionInfo` extracted with trackingId, boundingBox, confidence, category
   - Thumbnail cropping: max 200x200 pixels to save memory
   - Bounding boxes normalized to 0-1 coordinates (`NormalizedRect`)

**Tracking & Aggregation:**
1. **ObjectTracker** (shared:core-tracking)
   - Location: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ObjectTracker.kt`
   - Platform-neutral Kotlin tracking logic
   - Matching strategies:
     a. Direct trackingId match (ML Kit's persistent ID)
     b. Spatial fallback: IoU (Intersection over Union) + center distance
   - Candidate management:
     - New detections create `ObjectCandidate`
     - Candidates updated across frames (bounding box, confidence, category)
     - Confirmation: `isConfirmed()` checks minFramesToConfirm, minConfidence thresholds
     - Expiry: removes stale candidates (maxFrameGap exceeded)
   - Returns newly confirmed candidates per frame

2. **ItemAggregator** (core-tracking)
   - Location: `core-tracking/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt`
   - Used by `ItemsViewModel` with `AggregationPresets.REALTIME` config
   - Merges similar ScannedItems into persistent `AggregatedItem`
   - Similarity scoring: weighted combination of spatial overlap, category match, visual similarity
   - Dynamic threshold: default 0.55 (configurable via ViewModel)

3. **ItemsViewModel** orchestration:
   - Location: `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt`
   - Flow: `addItem(ScannedItem)` → `itemAggregator.processDetection()` → update StateFlow
   - Maintains `StateFlow<List<ScannedItem>>` for UI
   - Classification orchestration via `ClassificationOrchestrator`

**Detection Overlay:**
- `DetectionOverlay.kt` renders real-time bounding boxes and labels on camera preview
- Uses `DetectionResult` objects (separate from ScannedItem for performance)

### Category Model & Assignment

**ItemCategory Enum** (shared:core-models):
- Location: `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/ml/ItemCategory.kt`
- Values: FASHION, HOME_GOOD, FOOD, PLACE, PLANT, ELECTRONICS, DOCUMENT, UNKNOWN
- Methods:
  - `fromMlKitLabel(label)` - Maps ML Kit's 5 coarse categories ("Fashion good", "Home good", etc.)
  - `fromClassifierLabel(label)` - Maps enhanced classifier labels (on-device or cloud)

**Category Assignment Flow:**
1. **ML Kit Detection** (ObjectDetectorClient):
   - ML Kit returns labels with confidence scores
   - Best label selected (highest confidence)
   - If confidence >= 0.3f: `ItemCategory.fromMlKitLabel(label)`
   - Else: UNKNOWN

2. **Domain Pack Enhancement** (optional, Track A):
   - `BasicCategoryEngine` in core-domainpack
   - Matches ML Kit labels against 23 fine-grained DomainCategory configs
   - JSON-based taxonomy: furniture_sofa, furniture_chair, electronics_laptop, etc.
   - 10 extractable attributes: brand, color, material, size, condition, etc.
   - Priority-based selection when multiple categories match
   - NOT integrated into main scanning flow yet (exists as parallel system)

3. **Cloud Classification** (disabled by default):
   - `CloudClassifier.kt` exists but requires backend config
   - POST to `{SCANIUM_API_BASE_URL}/classify` with cropped thumbnail
   - Expected response: `{domainCategoryId, confidence, label, attributes, requestId}`
   - Privacy: strips EXIF, only sends cropped item (not full frame)
   - Orchestrated via `ClassificationOrchestrator` with mode switching (ON_DEVICE vs CLOUD)
   - Not enabled in builds unless local.properties has `scanium.api.base.url` and `scanium.api.key`

**Current Limitation:**
- ML Kit provides only 5 coarse categories (Fashion, Food, Home goods, Places, Plants)
- Domain Pack fine-grained categories exist but not wired into main detection pipeline
- Cloud classification stubbed but not integrated (requires backend)

### Networking & Backend

**Current State:**
- **No real backend calls** for core scanning functionality (privacy-first, on-device)
- **Mock eBay API** (`MockEbayApi.kt`) simulates marketplace listing flow
- **Cloud classification** exists (`CloudClassifier.kt`) but disabled:
  - Requires `SCANIUM_API_BASE_URL` and `SCANIUM_API_KEY` in BuildConfig
  - OkHttp client with 10s connect/read timeouts
  - Multipart form upload: cropped JPEG thumbnail + domainPackId
  - Error handling: retryable (408, 429, 5xx) vs non-retryable (400, 401, 403, 404)
  - Response mapped to `ClassificationResult` with domainCategoryId, attributes

**Configuration:**
- API keys in `local.properties` (not committed):
  ```
  scanium.api.base.url=https://your-backend.com/api/v1
  scanium.api.key=your-dev-api-key
  ```
- BuildConfig fields injected via `androidApp/build.gradle.kts`

### Data Models & State Management

**Core Models** (shared:core-models):
1. **ScannedItem** - Detected item with thumbnail, category, price range, confidence, bounding box
2. **ImageRef** - Portable image reference (JPEG bytes + metadata)
3. **NormalizedRect** - 0-1 normalized bounding box (left, top, right, bottom)
4. **ItemCategory** - Enum for categorization
5. **RawDetection** - ML detection with trackingId, bbox, labels, thumbnail
6. **DetectionResult** - Overlay rendering data (bbox, category, confidence, trackingId)

**State Flows:**
- `ItemsViewModel._items: MutableStateFlow<List<ScannedItem>>` - Session items
- `CameraViewModel.captureResolution: StateFlow<CaptureResolution>` - Camera settings
- `ClassificationOrchestrator` manages classification mode (ON_DEVICE vs CLOUD)
- No persistence layer - all state in memory (cleared on app close)

### UI Architecture

**Navigation:**
- Navigation Compose with `NavGraph.kt`
- Routes: camera (home), items list, sell screen

**Screens:**
1. **CameraScreen** - Preview, overlays, mode switcher, shutter button
2. **ItemsListScreen** - Grid of detected items, multi-select for selling
3. **SellOnEbayScreen** - Mock listing flow with draft editing

**Theme:**
- Material 3 with Scanium branding
- Custom color scheme in `ui/theme/Color.kt`

### Testing Infrastructure

**Test Coverage: 171 tests total**
- Unit tests (JUnit 4, Truth assertions, MockK, Robolectric SDK 28)
  - ObjectTracker tests (tracking/deduplication logic)
  - ObjectCandidate tests (spatial matching)
  - ItemsViewModel tests (state management)
  - Domain Pack tests (61 tests: config loading, category matching)
  - Selling flow tests
- Instrumented tests (Compose Testing, Espresso)
  - DetectionOverlay UI tests
  - ModeSwitcher UI tests

**Test Commands:**
- `./gradlew test` - Unit tests
- `./gradlew connectedAndroidTest` - Instrumented tests (requires device)

### Documentation Inventory

**Existing Docs:**
1. `docs/ARCHITECTURE.md` (this file)
2. `docs/PRODUCT.md` - Product overview, user flows, feature flags
3. `docs/DECISIONS.md` - ADR-lite decision log
4. `docs/CI_CD.md` - GitHub Actions workflows
5. `docs/SECURITY.md` - Security practices
6. `docs/DEV_GUIDE.md` - Developer onboarding
7. `docs/INDEX.md` - Documentation index
8. `README.md` - Main project README
9. `AGENTS.md` - AI agents guide

**No existing ADR folder** - Will be created in Phase 1

### iOS & Cross-Platform Preparation

**KMP Readiness:**
- `shared:core-models` and `shared:core-tracking` already set up with KMP structure
- Source sets: commonMain, androidMain, iosMain
- iOS placeholder files exist (`IosPlaceholder.kt`)
- `android-platform-adapters` pattern established for type bridging
- Build system enforces no Android types in core modules (`checkPortableModules` task)

**iOS App Stub:**
- `iosApp/` directory exists with basic iOS project structure
- Not yet functional (placeholder)

### Build & CI/CD

**Local Build:**
- `./build.sh` script auto-detects Java 17
- `./gradlew assembleDebug` - Build APK
- `./gradlew test` - Run tests

**GitHub Actions:**
- `android-debug-apk.yml` - Builds APK artifact on push to main
- `security-cve-scan.yml` - OWASP Dependency-Check weekly + on Gradle file changes
- SBOM generation (`cyclonedxBom` task)

### Key Architectural Constraints (Current)

**Enforced:**
1. Java 17 toolchain required
2. No Android types in `core-models` or `core-tracking`
3. Modules cannot depend on `:androidApp`
4. Cloud classification disabled without explicit config
5. On-device privacy-first (no network calls by default)

**Debt/Limitations:**
1. Single-module app (androidApp contains all features)
2. No DI framework (manual constructor injection)
3. No persistence (in-memory only)
4. ML Kit limited to 5 coarse categories
5. Domain Pack not integrated into main flow
6. Cloud classification exists but not production-ready
7. No proper error boundaries or failure recovery UI

---

## Target Architecture (Android First, iOS Ready)

This section defines the future-proof, modular architecture that ships Android first with full functionality while preparing for iOS without creating build/test/compile blockers.

### Design Principles

1. **Android First, iOS Ready** - Ship working Android APK immediately; iOS follows without blocking
2. **Cloud Classification Primary** - Google Vision API as main classification path for better item categorization
3. **Responsive Pipeline** - On-device detection/tracking (fast) + async cloud classification (accurate)
4. **Shared Brain** - Core domain logic in KMP-ready modules for cross-platform parity
5. **Clean Boundaries** - Platform-specific camera/ML isolated; business logic portable
6. **No Secrets in APK** - API keys via backend proxy or secure token minting only

### Architecture Layers

#### A) Presentation Layer (Platform UI)

**Purpose:** Render UI and handle user interactions. NO business logic.

**Android Implementation:**
- **Technology:** Jetpack Compose + Material 3
- **Screens:**
  - `CameraScreen` - Preview, overlays, shutter button, mode switcher
  - `ItemsListScreen` - Grid of scanned items with status badges
  - `SellScreen` - Marketplace integration UI
- **ViewModels:**
  - `CameraViewModel` - Camera settings, capture resolution
  - `ItemsViewModel` - Session state, items list, classification orchestration
  - `ListingViewModel` - Selling flow state
- **State Management:**
  - UI observes `StateFlow<UiState>` from ViewModels
  - UI sends Intent/Action objects to ViewModels (unidirectional data flow)
  - No direct repository/use case calls from Composables

**iOS Implementation (Future):**
- **Technology:** SwiftUI + native UIKit components
- **Structure:** Mirror Android screen structure
- **State Management:** Combine publishers observing shared KMP use cases

**Rules:**
- ❌ NO Android/iOS framework types in shared use cases
- ❌ NO business logic in Composables/Views
- ✅ UI only formats and displays data from ViewModels
- ✅ All state changes via ViewModel intents

**Module Location:**
- Android: `androidApp/src/main/java/com/scanium/app/` (camera/, items/, selling/ packages)
- iOS: `iosApp/` (future)

---

#### B) Platform Scanning Layer (Platform-specific)

**Purpose:** Camera preview, frame acquisition, on-device object detection for bounding boxes and tracking.

**Key Constraint:** ML Kit (Android) and Apple Vision (iOS) are ONLY used for:
- Real-time bounding boxes (visual overlay)
- Tracking IDs (frame-to-frame object identity)
- Raw detections (geometry + coarse labels)
- **NOT for final item categorization** - that's cloud classification's job

**Android Implementation:**
```
android-camera-camerax/
├── CameraXManager.kt           # Camera lifecycle, preview, frame routing
└── FrameAnalyzer.kt            # ImageAnalysis.Analyzer contract

android-ml-mlkit/
├── MlKitObjectDetector.kt      # Wraps ML Kit Object Detection API
├── MlKitBarcodeScanner.kt      # Wraps ML Kit Barcode Scanning API
└── MlKitTextRecognizer.kt      # Wraps ML Kit Text Recognition API

androidApp/src/.../ml/
├── ObjectDetectorClient.kt     # Current: detection + tracking ID extraction
└── DetectionAdapter.kt         # Converts ML Kit DetectedObject → RawDetection
```

**Interfaces (Platform-agnostic contracts in shared modules):**
```kotlin
// shared:core-models/src/commonMain/kotlin/
interface ObjectDetector {
    suspend fun detectObjects(
        imageData: ImageData,
        mode: DetectionMode
    ): List<RawDetection>
}

data class RawDetection(
    val trackingId: String?,
    val boundingBox: NormalizedRect,
    val coarseLabel: String?,      // e.g., "Home good" from ML Kit
    val confidence: Float,
    val thumbnail: ImageRef?
)
```

**iOS Implementation (Future):**
```swift
// iosApp/Scanning/
AVFoundationCameraManager.swift   // AVCaptureSession
VisionObjectDetector.swift        // Apple Vision Framework
DetectionAdapter.swift            // Converts VNDetectedObject → RawDetection
```

**Rules:**
- ❌ NO category assignment logic here (only pass through coarse labels)
- ❌ NO cloud API calls from scanning layer
- ✅ Focus on real-time performance (<50ms per frame)
- ✅ Extract bounding boxes, trackingIds, and raw labels only
- ✅ Platform-specific code stays isolated (Android and iOS can diverge)

**Module Dependencies:**
- `android-camera-camerax` → Android SDK only
- `android-ml-mlkit` → ML Kit SDKs only
- Both adapt outputs to `shared:core-models` interfaces

---

#### C) Core Domain Layer ("Shared Brain", KMP-ready)

**Purpose:** Business logic that's identical on Android and iOS. This is the "portable brain" of the app.

**Technology:** Kotlin Multiplatform (KMP) - `commonMain` source set

**Module Structure:**
```
shared:core-domain/              # NEW MODULE (to be created)
└── src/commonMain/kotlin/
    ├── model/
    │   ├── ScannedItem.kt       # EXISTING (move from core-models)
    │   ├── AggregatedItem.kt    # EXISTING (move from core-tracking)
    │   ├── DomainCategoryId.kt  # NEW: fine-grained category ID
    │   ├── ItemAttributes.kt    # NEW: extracted attributes (color, brand, etc.)
    │   └── PriceEstimate.kt     # NEW: price range with confidence
    ├── usecase/
    │   ├── AggregateDetectionsUseCase.kt
    │   ├── ClassifyStableItemUseCase.kt
    │   ├── ApplyDomainPackMappingUseCase.kt
    │   └── EstimatePriceUseCase.kt
    └── repository/
        ├── ClassificationRepository.kt    # Interface
        ├── DomainPackRepository.kt       # EXISTING (move to shared)
        └── PricingRepository.kt          # Interface
```

**Use Cases (Business Logic):**

1. **AggregateDetectionsUseCase**
   - Input: `List<RawDetection>` from ML Kit/Vision
   - Process: De-duplication, multi-frame stability, tracking
   - Output: `List<StableDetection>` (confirmed, non-flickering items)
   - Uses: `ObjectTracker` (already in shared:core-tracking)

2. **ClassifyStableItemUseCase**
   - Input: `StableDetection` (cropped thumbnail + coarse label)
   - Process: Calls `ClassificationRepository.classifyItem()`
   - Output: `ClassificationResult` (domainCategoryId, attributes, confidence)
   - Async: Does NOT block scanning pipeline
   - Retry: Handles transient network failures

3. **ApplyDomainPackMappingUseCase**
   - Input: `domainCategoryId` (e.g., "furniture_sofa")
   - Process: Maps to UI-displayable category + icon
   - Output: `CategoryDisplay` (name, icon, itemCategory enum)
   - Uses: Domain Pack JSON config

4. **EstimatePriceUseCase**
   - Input: `DomainCategoryId + ItemAttributes`
   - Process: Applies pricing logic (could be ML model later)
   - Output: `PriceEstimate` (min/max range, currency, confidence)

**Models:**
```kotlin
// shared:core-domain/src/commonMain/kotlin/model/

data class DomainCategoryId(val value: String) {
    // Examples: "furniture_sofa", "electronics_laptop", "fashion_sneakers"
}

data class ItemAttributes(
    val brand: String? = null,
    val color: String? = null,
    val material: String? = null,
    val condition: String? = null,
    val size: String? = null,
    // ... more attributes from domain pack config
)

data class PriceEstimate(
    val minPrice: Double,
    val maxPrice: Double,
    val currency: String,
    val confidence: Float
)

data class ClassificationResult(
    val domainCategoryId: DomainCategoryId,
    val attributes: ItemAttributes,
    val confidence: Float,
    val source: ClassificationSource // CLOUD, ON_DEVICE, FALLBACK
)

enum class ClassificationSource {
    CLOUD,          // Google Vision API
    ON_DEVICE,      // ML Kit (fallback only)
    FALLBACK        // Default category when classification fails
}
```

**Rules:**
- ❌ NO Android types (Bitmap, Context, etc.)
- ❌ NO iOS types (UIImage, UIViewController, etc.)
- ❌ NO framework-specific networking (OkHttp, Alamofire)
- ✅ Use only Kotlin stdlib + KMP-compatible libraries
- ✅ All platform interactions via interfaces (injected)
- ✅ Pure functions preferred; use cases are testable

**Module Dependencies:**
- `shared:core-domain` depends on:
  - `shared:core-models` (data models)
  - `shared:core-tracking` (ObjectTracker)
  - No platform modules

---

#### D) Data Layer / Integrations (Cloud Classification)

**Purpose:** Implement repositories that interact with external services (Google Vision, pricing API, etc.)

**Cloud Classification Architecture:**

```
shared:core-data/                # NEW MODULE (to be created)
└── src/
    ├── commonMain/kotlin/
    │   ├── repository/
    │   │   └── CloudClassificationRepository.kt   # Interface
    │   ├── model/
    │   │   ├── ClassificationRequest.kt
    │   │   └── ClassificationResponse.kt
    │   └── mapper/
    │       └── GoogleVisionMapper.kt              # API response → domain model
    ├── androidMain/kotlin/
    │   └── repository/
    │       └── GoogleVisionClassifierAndroid.kt   # Android HTTP client impl
    └── iosMain/kotlin/
        └── repository/
            └── GoogleVisionClassifierIOS.kt       # iOS URLSession impl
```

**Google Vision Integration Strategy:**

⚠️ **SECURITY DECISION (see ADR-001):**
- **Chosen Approach:** Backend proxy pattern (recommended for production)
- **Alternative:** Direct Google Vision with token minting (requires extra infra)

**Backend Proxy Pattern:**
```
┌──────────┐         ┌──────────────┐         ┌──────────────────┐
│  Mobile  │────────▶│  Your Backend│────────▶│  Google Vision   │
│   App    │  HTTPS  │  (Node/Go)   │  API Key│      API         │
└──────────┘         └──────────────┘         └──────────────────┘
   No API key         - Auth user token        - Securely stored key
   in APK             - Rate limiting          - Request signing
                      - Usage tracking
```

**Repository Interface:**
```kotlin
// shared:core-data/src/commonMain/kotlin/repository/

interface CloudClassificationRepository {
    /**
     * Classify an item using cloud-based vision API.
     *
     * @param thumbnail Cropped item image (JPEG, max 200x200)
     * @param coarseLabel Optional hint from on-device detector
     * @param domainPackId Target domain pack (e.g., "home_resale")
     * @return Classification result or error
     */
    suspend fun classifyItem(
        thumbnail: ImageRef,
        coarseLabel: String?,
        domainPackId: String
    ): Result<ClassificationResult>

    /**
     * Check if cloud classification is available.
     * Returns false if no network, backend down, etc.
     */
    suspend fun isAvailable(): Boolean
}
```

**Android Implementation:**
```kotlin
// androidApp/src/main/java/com/scanium/app/data/

class GoogleVisionClassifierAndroid(
    private val httpClient: HttpClient,      // Injected (Ktor or OkHttp)
    private val config: ApiConfig,           // Injected backend URL
    private val authProvider: AuthProvider   // User token for backend
) : CloudClassificationRepository {

    override suspend fun classifyItem(
        thumbnail: ImageRef,
        coarseLabel: String?,
        domainPackId: String
    ): Result<ClassificationResult> = withContext(Dispatchers.IO) {
        try {
            // 1. Convert ImageRef to multipart form data
            val imageBytes = thumbnail.toJpegBytes()

            // 2. Call YOUR backend (not Google directly)
            val response = httpClient.post("${config.baseUrl}/api/v1/classify") {
                headers {
                    append("Authorization", "Bearer ${authProvider.getToken()}")
                }
                setBody(MultiPartFormDataContent(
                    formData {
                        append("image", imageBytes, Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                        })
                        append("domainPackId", domainPackId)
                        coarseLabel?.let { append("hint", it) }
                    }
                ))
            }

            // 3. Parse response
            when (response.status) {
                HttpStatusCode.OK -> {
                    val apiResponse = response.body<GoogleVisionApiResponse>()
                    Result.success(mapToDomainModel(apiResponse))
                }
                HttpStatusCode.TooManyRequests -> {
                    Result.failure(RateLimitException())
                }
                else -> {
                    Result.failure(ClassificationException("HTTP ${response.status}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**Backend API Contract:**

**Request:** `POST /api/v1/classify`
```json
{
  "image": "<base64_jpeg>",
  "domainPackId": "home_resale",
  "hint": "Home good"  // Optional coarse label from ML Kit
}
```

**Response:** `200 OK`
```json
{
  "domainCategoryId": "furniture_sofa",
  "confidence": 0.87,
  "attributes": {
    "color": "brown",
    "material": "leather",
    "condition": "good"
  },
  "requestId": "req_abc123",
  "latencyMs": 234
}
```

**Error Responses:**
- `429 Too Many Requests` - Rate limit exceeded
- `503 Service Unavailable` - Google Vision down
- `400 Bad Request` - Invalid image format

**Rules:**
- ❌ NO Google API keys in mobile app (APK/IPA)
- ❌ NO direct calls to Google Vision from mobile
- ✅ All cloud calls go through YOUR authenticated backend
- ✅ Backend handles rate limiting, retries, fallbacks
- ✅ Mobile only needs user auth token (JWT or similar)

**Fallback Strategy:**
```
1. Try cloud classification (Google Vision via backend)
2. If network error or timeout → use ML Kit coarse label
3. If ML Kit label unavailable → assign UNKNOWN category
4. Display confidence indicator to user ("Low confidence" badge)
```

---

#### E) Configuration Layer

**Purpose:** Centralize environment configuration for different build variants and deployment targets.

**Module:** `shared:core-config` (NEW, KMP-ready)

**Config Structure:**
```kotlin
// shared:core-config/src/commonMain/kotlin/

data class AppConfig(
    val api: ApiConfig,
    val features: FeatureFlags,
    val logging: LoggingConfig
)

data class ApiConfig(
    val baseUrl: String,                  // e.g., "https://api.scanium.com"
    val classificationProvider: String,   // "google_vision"
    val timeoutMs: Long = 10_000,
    val retryAttempts: Int = 2
)

data class FeatureFlags(
    val cloudClassificationEnabled: Boolean,
    val domainPackEnabled: Boolean,
    val debugOverlaysEnabled: Boolean,
    val mockBackendEnabled: Boolean
)

data class LoggingConfig(
    val level: LogLevel,
    val enableAnalytics: Boolean,
    val enableCrashReporting: Boolean
)

enum class LogLevel {
    ERROR, WARNING, INFO, DEBUG, VERBOSE
}
```

**Injection via Platform:**

**Android (build.gradle.kts):**
```kotlin
android {
    defaultConfig {
        // Injected from local.properties or environment
        val apiBaseUrl = localProperties.getProperty("scanium.api.base.url")
            ?: System.getenv("SCANIUM_API_BASE_URL")
            ?: "https://api.scanium.com"  // Production default

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "CLOUD_CLASSIFICATION_ENABLED", "false")
            buildConfigField("Boolean", "DEBUG_OVERLAYS_ENABLED", "true")
        }
        release {
            buildConfigField("Boolean", "CLOUD_CLASSIFICATION_ENABLED", "true")
            buildConfigField("Boolean", "DEBUG_OVERLAYS_ENABLED", "false")
        }
    }
}
```

**Usage in App:**
```kotlin
// androidApp/src/main/java/com/scanium/app/di/

object ConfigProvider {
    fun provideAppConfig(): AppConfig {
        return AppConfig(
            api = ApiConfig(
                baseUrl = BuildConfig.API_BASE_URL,
                classificationProvider = "google_vision",
                timeoutMs = 10_000
            ),
            features = FeatureFlags(
                cloudClassificationEnabled = BuildConfig.CLOUD_CLASSIFICATION_ENABLED,
                domainPackEnabled = true,
                debugOverlaysEnabled = BuildConfig.DEBUG_OVERLAYS_ENABLED,
                mockBackendEnabled = BuildConfig.DEBUG
            ),
            logging = LoggingConfig(
                level = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO,
                enableAnalytics = !BuildConfig.DEBUG,
                enableCrashReporting = true
            )
        )
    }
}
```

**Rules:**
- ❌ NO hardcoded API endpoints in code
- ❌ NO secrets committed to git
- ✅ All config injected at build time
- ✅ Different configs for debug/release builds
- ✅ Override via environment variables for CI/CD

**Configuration Sources (Priority Order):**
1. Environment variables (CI/CD, production)
2. `local.properties` (local development, gitignored)
3. `BuildConfig` defaults (fallback values)

---

#### F) Observability Layer

**Purpose:** Structured logging, metrics, and error tracking for monitoring app behavior and diagnosing issues.

**Module:** `shared:core-observability` (NEW, KMP-ready)

**Logging Interface:**
```kotlin
// shared:core-observability/src/commonMain/kotlin/

interface AppLogger {
    fun logClassificationRequest(
        itemId: String,
        source: ClassificationSource,
        timestamp: Long
    )

    fun logClassificationResult(
        itemId: String,
        domainCategoryId: String,
        confidence: Float,
        latencyMs: Long
    )

    fun logClassificationError(
        itemId: String,
        error: Throwable,
        retryAttempt: Int
    )

    fun logScanSession(
        sessionId: String,
        itemCount: Int,
        classifiedCount: Int,
        durationMs: Long
    )
}

data class ClassificationMetrics(
    val totalRequests: Int,
    val successCount: Int,
    val failureCount: Int,
    val averageLatencyMs: Long,
    val errorRate: Float
)
```

**Android Implementation:**
```kotlin
// androidApp/src/main/java/com/scanium/app/observability/

class AndroidLogger(
    private val config: LoggingConfig,
    private val analyticsClient: AnalyticsClient?  // Firebase, Amplitude, etc.
) : AppLogger {

    override fun logClassificationResult(
        itemId: String,
        domainCategoryId: String,
        confidence: Float,
        latencyMs: Long
    ) {
        // Console logging
        if (config.level >= LogLevel.INFO) {
            Log.i(TAG, "Classification: item=$itemId, category=$domainCategoryId, " +
                      "confidence=$confidence, latency=${latencyMs}ms")
        }

        // Analytics event
        if (config.enableAnalytics) {
            analyticsClient?.trackEvent("classification_success", mapOf(
                "category" to domainCategoryId,
                "confidence" to confidence,
                "latency_ms" to latencyMs
            ))
        }
    }

    override fun logClassificationError(
        itemId: String,
        error: Throwable,
        retryAttempt: Int
    ) {
        Log.e(TAG, "Classification failed: item=$itemId, retry=$retryAttempt", error)

        if (config.enableCrashReporting) {
            // Send to Crashlytics/Sentry
            crashReporter.recordException(error, mapOf(
                "item_id" to itemId,
                "retry_attempt" to retryAttempt
            ))
        }
    }
}
```

**Metrics Collection:**
```kotlin
// shared:core-observability/src/commonMain/kotlin/

class ClassificationMetricsCollector {
    private val requests = mutableListOf<ClassificationEvent>()

    fun recordRequest(event: ClassificationEvent) {
        requests.add(event)
    }

    fun getMetrics(): ClassificationMetrics {
        val successCount = requests.count { it.isSuccess }
        val avgLatency = requests.mapNotNull { it.latencyMs }.average().toLong()

        return ClassificationMetrics(
            totalRequests = requests.size,
            successCount = successCount,
            failureCount = requests.size - successCount,
            averageLatencyMs = avgLatency,
            errorRate = (requests.size - successCount).toFloat() / requests.size
        )
    }
}
```

**Debug Overlays (Debug Builds Only):**
```kotlin
// androidApp/src/main/java/com/scanium/app/debug/

@Composable
fun ClassificationDebugOverlay(
    metrics: ClassificationMetrics,
    visible: Boolean
) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(8.dp)
    ) {
        Column {
            Text("Classification Stats:", color = Color.White)
            Text("Success Rate: ${(1 - metrics.errorRate) * 100}%", color = Color.Green)
            Text("Avg Latency: ${metrics.averageLatencyMs}ms", color = Color.Yellow)
            Text("Total Requests: ${metrics.totalRequests}", color = Color.White)
        }
    }
}
```

**Rules:**
- ❌ NO logging of PII (personally identifiable information)
- ❌ NO sensitive data in logs (auth tokens, API keys)
- ✅ Structured logging with consistent format
- ✅ Different log levels for debug vs production
- ✅ Metrics aggregated locally, sent to analytics on session end
- ✅ Debug overlays only in debug builds (via FeatureFlags)

**Log Levels:**
- **VERBOSE:** Frame-by-frame detection data (debug only)
- **DEBUG:** Classification requests/responses (debug only)
- **INFO:** Session metrics, user actions
- **WARNING:** Retryable errors, fallbacks used
- **ERROR:** Critical failures, crashes

---

### Data Flow (End-to-End)

**Scanning Session Flow:**

```
1. User opens camera
   └─▶ CameraScreen renders preview (Presentation Layer)

2. CameraX streams frames
   └─▶ ObjectDetectorClient processes (Platform Scanning Layer)
       └─▶ ML Kit detects objects → RawDetection list

3. Detections aggregated
   └─▶ AggregateDetectionsUseCase (Core Domain Layer)
       └─▶ ObjectTracker confirms stable items
       └─▶ Returns StableDetection list

4. Stable items displayed in overlay
   └─▶ DetectionOverlay shows bounding boxes (Presentation Layer)

5. Async cloud classification (per stable item)
   └─▶ ClassifyStableItemUseCase (Core Domain Layer)
       └─▶ CloudClassificationRepository.classifyItem() (Data Layer)
           └─▶ HTTP POST to backend /api/v1/classify
               └─▶ Backend calls Google Vision API
               └─▶ Backend returns domainCategoryId + attributes
       └─▶ ApplyDomainPackMappingUseCase maps to UI category
       └─▶ EstimatePriceUseCase generates price estimate

6. Classified item added to session
   └─▶ ItemsViewModel updates StateFlow (Presentation Layer)
       └─▶ UI shows item in list with category + price
```

**Key Properties:**
- **Non-blocking:** Cloud classification runs async, doesn't freeze camera
- **Graceful degradation:** If cloud fails, use ML Kit coarse label
- **Responsive:** Detection overlay updates at 30+ FPS
- **Offline-capable:** Detection/tracking works without network

---

### Module Dependency Graph (Target State)

```
┌─────────────────────────────────────────────────────────────┐
│                        androidApp                           │
│  (Compose UI, ViewModels, DI, Platform Integration)         │
└──────────────────────┬──────────────────────────────────────┘
                       │
       ┌───────────────┼───────────────┐
       │               │               │
       ▼               ▼               ▼
┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│   android-  │ │   android-  │ │   android-  │
│   camera-   │ │     ml-     │ │  platform-  │
│   camerax   │ │    mlkit    │ │   adapters  │
└─────────────┘ └─────────────┘ └──────┬──────┘
                                       │
                       ┌───────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              shared:core-domain (NEW)                        │
│         (Use Cases, Business Logic, Domain Models)           │
└───────────────────────┬─────────────────────────────────────┘
                        │
       ┌────────────────┼────────────────┐
       │                │                │
       ▼                ▼                ▼
┌────────────┐   ┌────────────┐   ┌────────────┐
│  shared:   │   │  shared:   │   │  shared:   │
│   core-    │   │   core-    │   │   core-    │
│   models   │   │  tracking  │   │    data    │
│  (EXISTS)  │   │  (EXISTS)  │   │   (NEW)    │
└────────────┘   └────────────┘   └────────────┘
```

**Dependency Rules:**
1. `androidApp` can depend on all modules
2. `android-*` modules can ONLY depend on Android SDK + `shared:*` interfaces
3. `shared:*` modules can ONLY depend on other `shared:*` modules
4. `shared:*` modules CANNOT depend on `android-*` or `androidApp`
5. All cross-layer communication via interfaces

---

### Build Verification

**Gradle Tasks:**
- `./gradlew assembleDebug` - Must always succeed
- `./gradlew test` - All unit tests must pass
- `./gradlew checkPortableModules` - Validates no Android imports in shared modules
- `./gradlew checkModuleDependencies` (NEW) - Validates dependency rules

**CI Requirements:**
- Every commit must pass `assembleDebug`
- PRs blocked if tests fail
- Weekly security scans (OWASP Dependency-Check)
- SBOM generation for supply chain tracking

---

## Module overview
- **androidApp** – Compose UI, navigation, CameraX/ML Kit analyzers, view models, selling UI.
- **core-models** – Platform-neutral models (`ImageRef`, `NormalizedRect`, `ScannedItem`, `ScanMode`).
- **core-tracking** – Platform-free tracking and aggregation helpers (`ObjectTracker`, `ObjectCandidate`, presets).
- **core-domainpack** – Domain pack config/models, category engine, repository interfaces.
- **core-scan / core-contracts** – Namespaces for future scan/domain contracts (no Android deps).
- **android-camera-camerax / android-ml-mlkit** – Library shells for CameraX and ML Kit namespaces.
- **android-platform-adapters** – Converters between Android types (Bitmap/Rect/ImageProxy) and shared models.
- **shared:core-models / shared:core-tracking** – KMP-friendly modules; keep Android types out.

## Key flows (current Android implementation)
1. **Camera preview** → `CameraScreen` hosts preview/overlays and forwards frames to `CameraXManager`.
2. **Analyzer selection** → `CameraXManager` chooses analyzer per `ScanMode` (objects, barcodes, documents).
3. **ML inference** → analyzers in `ml` package wrap ML Kit clients, emit `RawDetection` with normalized geometry and optional thumbnails.
4. **Tracking & aggregation** → `ObjectTracker` + aggregation presets convert detections into stable items; IoU + trackingId dedupe protect against flicker.
5. **Item orchestration** → `ItemsViewModel` maintains session state, routes detections/classification, and exposes UI-ready `StateFlow`.
6. **UI** → Compose screens (`CameraScreen`, `ItemsListScreen`, `SellScreen`) render lists, overlays, and selling flow.
7. **Persistence/backends** → Mock selling/marketplace code lives in `selling` package; cloud classification exists but requires explicit backend config (see TODO in PRODUCT/SECURITY).

## Where to look in code (truth sources)
- `androidApp/src/main/java/com/scanium/app/MainActivity.kt` – Android entry point / Compose host.
- `androidApp/src/main/java/com/scanium/app/ScaniumApp.kt` – Root composable + navigation wiring.
- `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt` – Destinations and routes.
- `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt` – Preview, gestures, overlays.
- `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` – CameraX lifecycle and analyzer routing.
- `androidApp/src/main/java/com/scanium/app/camera/DetectionOverlay.kt` – Overlay rendering.
- `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` – Object detection pipeline.
- `androidApp/src/main/java/com/scanium/app/ml/BarcodeScannerClient.kt` – Barcode/QR analyzer.
- `androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt` – OCR analyzer.
- `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt` – Aggregation/state orchestration.
- `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt` – Cloud classification stub (disabled unless configured).
- `core-tracking/src/main/java/com/scanium/app/tracking/ObjectTracker.kt` – Tracking logic (platform-neutral math).
- `core-domainpack/src/main/java/com/scanium/app/domain/category/BasicCategoryEngine.kt` – Domain pack category selection.

## Shared brain guardrails (KMP)
- Keep shared modules free of Android types; adapt at module boundaries using `android-platform-adapters`.
- Shared logic includes: session state machine, tracking/aggregation, domain pack selection, analytics contracts.
- Platform-specific: CameraX/ML Kit analyzers, Compose/SwiftUI UI, permissions, media IO.
