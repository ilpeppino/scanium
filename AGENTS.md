***REMOVED*** Repository Guidelines

***REMOVED******REMOVED*** Project Structure & Module Organization

***REMOVED******REMOVED******REMOVED*** Android Application
- Multi-module Gradle project with Hilt DI.
- `androidApp/`: Android app (Jetpack Compose UI, CameraX, ML Kit wrappers, AI Assistant, selling flow, navigation).
  - Main packages at `androidApp/src/main/java/com/scanium/app/`:
    - `camera/`: CameraX integration, detection overlay, camera UI
    - `items/`: Item list, details, editing, persistence
    - `ml/`: ML Kit clients (object detection, barcode, OCR), pricing engine
    - `assistant/`: AI Assistant (Claude/OpenAI integration, multimodal input)
    - `classification/`: Classifier providers (Mock, NoOp)
    - `selling/`: eBay marketplace integration (flavor-gated)
    - `ftue/`: First Time User Experience, onboarding tours
    - `voice/`: Voice control, speech recognition, state machine
    - `audio/`: Sound effects, audio feedback
    - `telemetry/`: OpenTelemetry OTLP export (logs, traces, metrics)
    - `diagnostics/`: System health checks, backend connectivity status
    - `settings/`: Settings screens, developer options
    - `ui/`: Shared UI components, Material 3 theme
    - `navigation/`: Navigation graph (NavGraph.kt)
    - `di/`: Hilt dependency injection modules
  - Resources: `androidApp/src/main/res`
  - Manifest: `androidApp/src/main/AndroidManifest.xml`

***REMOVED******REMOVED******REMOVED*** Shared Kotlin Modules (KMP-ready)
- `shared/core-models/`: Portable models (ImageRef, NormalizedRect, ScannedItem, ScanMode, domain config)
- `shared/core-tracking/`: Platform-free tracking/aggregation (ObjectTracker, ItemAggregator)
- `shared/core-export/`: Export models and mappers (CSV, ZIP)
- `shared/test-utils/`: Shared test helpers

***REMOVED******REMOVED******REMOVED*** Android Wrappers & Adapters
- `core-models/`: Android wrapper for shared models (typealiases, backwards-compatible)
- `core-tracking/`: Android wrapper for shared tracking logic
- `core-domainpack/`: Domain pack provider, repository, category engine/mapping
- `android-platform-adapters/`: Bitmap↔ImageRef and Rect/RectF↔NormalizedRect conversions
- Library shells (`android-ml-mlkit`, `android-camera-camerax`, `core-contracts`, `core-scan`): Hold namespaces only

***REMOVED******REMOVED******REMOVED*** Backend Services
- `backend/`: Fastify + TypeScript + Prisma + PostgreSQL backend
  - `src/index.ts` or `src/main.ts`: Server entry point
  - `src/routes/`: API endpoints (items, auth, health)
  - `src/services/`: Business logic layer
  - `src/modules/`: Feature modules (classifier, etc.)
  - `prisma/schema.prisma`: Database schema
  - `prisma/migrations/`: Version-controlled schema changes
  - `docker-compose.yml`: PostgreSQL container

***REMOVED******REMOVED******REMOVED*** Observability Stack
- `monitoring/`: LGTM observability stack (Grafana, Loki, Tempo, Mimir, Alloy)
  - `docker-compose.yml`: All monitoring services
  - `grafana/`: Dashboards and datasource provisioning
  - `alloy/alloy.hcl`: OTLP routing configuration
  - `loki/`, `tempo/`, `mimir/`: Backend storage configs

***REMOVED******REMOVED******REMOVED*** Development Scripts
- `scripts/backend/start-dev.sh`: Start backend + PostgreSQL + ngrok + monitoring
- `scripts/backend/stop-dev.sh`: Stop services
- `scripts/monitoring/`: Monitoring stack management
- `scripts/build.sh`: Android build with Java 17 auto-detection

***REMOVED******REMOVED*** Build, Test, and Development Commands

***REMOVED******REMOVED******REMOVED*** Android
```bash
./scripts/build.sh assembleDebug      ***REMOVED*** Builds with auto-detected Java 17
./gradlew assembleDebug               ***REMOVED*** Build debug APK
./gradlew installDebug                ***REMOVED*** Deploy to connected device/emulator
./gradlew test                        ***REMOVED*** JVM unit tests
./gradlew connectedAndroidTest        ***REMOVED*** Instrumented + Compose UI tests (needs device)
./gradlew lint                        ***REMOVED*** Android Lint across modules
./gradlew prePushJvmCheck             ***REMOVED*** Fast pre-push validation (JVM tests + portability)
```
- Use Android Studio's "Apply Changes" for quick UI tweaks; prefer `./gradlew clean` before reproducing build issues.

***REMOVED******REMOVED******REMOVED*** Backend & Observability
```bash
scripts/backend/start-dev.sh          ***REMOVED*** Start backend + PostgreSQL + ngrok + monitoring
scripts/backend/start-dev.sh --no-monitoring  ***REMOVED*** Backend only
scripts/backend/stop-dev.sh           ***REMOVED*** Stop backend + PostgreSQL
scripts/backend/stop-dev.sh --with-monitoring ***REMOVED*** Stop everything
scripts/monitoring/print-urls.sh      ***REMOVED*** View monitoring URLs and health status
cd backend && npm install             ***REMOVED*** Install dependencies
cd backend && npm run dev             ***REMOVED*** Run backend in dev mode
cd backend && npm test                ***REMOVED*** Run backend tests
cd backend && npm run prisma:migrate  ***REMOVED*** Run database migrations
cd backend && npm run typecheck       ***REMOVED*** TypeScript type checking
```

***REMOVED******REMOVED*** Coding Style & Naming Conventions
- Kotlin official style, 4-space indentation; prefer expression bodies for simple functions.
- Compose composables in `PascalCase` with `@Composable` at top; preview functions end with `Preview`.
- ViewModels hold `StateFlow`/`MutableStateFlow`; UI observes via `collectAsState()`. Keep side effects in `LaunchedEffect`/`DisposableEffect`.
- Filenames mirror primary class/composable (e.g., `CameraScreen.kt`, `ItemsViewModel.kt`); new resources follow lowercase_underscore.
- Prefer shared models from `com.scanium.app.model` (`ImageRef`, `NormalizedRect`, `ScannedItem`, domain config). Legacy aliases live in `com.scanium.app.core.*` for compatibility—new code should import the shared package.
- Keep Bitmaps/Rects at Android edges only; convert using `android-platform-adapters` (`Bitmap.toImageRefJpeg`, `ImageRef.Bytes.toBitmap`, `Rect/RectF.toNormalizedRect`, `NormalizedRect.toRectF/toRect`). Portable models (RawDetection, DetectionResult) should remain android-free.
- Library manifests rely on Gradle `namespace`; do not set `package` attributes.
- Run `./gradlew lint` (or Android Studio formatting) before sending changes; avoid storing secrets in code or `local.properties`.

***REMOVED******REMOVED*** Testing Guidelines

***REMOVED******REMOVED******REMOVED*** Test Organization
- **Unit tests**: `androidApp/src/test/java/` (JUnit4, Truth, MockK, Coroutines Test, Robolectric)
- **Instrumented tests**: `androidApp/src/androidTest/java/` (Compose UI and Android framework)

***REMOVED******REMOVED******REMOVED*** Current Test Coverage (all passing ✅)
- **Unit tests** focus on:
  - Tracking & aggregation: `ObjectTrackerTest.kt`, `ObjectCandidateTest.kt`, `TrackingPipelineIntegrationTest.kt`, `ItemAggregatorTest.kt`
  - Items & deduplication: `ItemsViewModelTest.kt`, `ItemsViewModelAggregationTest.kt`, `ItemsViewModelListingStatusTest.kt`, `DeduplicationPipelineIntegrationTest.kt`, `ScannedItemTest.kt`
  - Domain pack: `DomainPackTest.kt`, `DomainPackProviderTest.kt`, `CategoryMapperTest.kt`, `BasicCategoryEngineTest.kt`
  - ML & pricing: `PricingEngineTest.kt`, `ClassificationOrchestratorTest.kt`, `DetectionResultTest.kt`, `ScanModeTest.kt`, `DocumentScanningIntegrationTest.kt`
  - Selling flow: `ListingImagePreparerTest.kt`, `ListingDraftMapperTest.kt`, `EbayMarketplaceServiceTest.kt`, `MockEbayApiTest.kt`
  - Models/adapters: `ImageRefTest.kt`, `NormalizedRectTest.kt`, `PlatformAdaptersTest.kt`

- **Instrumented tests**:
  - `ModeSwitcherTest.kt` and `DetectionOverlayTest.kt` - Compose UI interaction
  - `ItemsViewModelInstrumentedTest.kt` - Integration tests

***REMOVED******REMOVED******REMOVED*** Test Dependencies
- JUnit 4.13.2 (test framework)
- **Robolectric 4.11.1** (Android framework in unit tests - required for `Rect`, etc.)
- Truth 1.1.5 (fluent assertions)
- MockK 1.13.8 (mocking)
- Coroutines Test 1.7.3 (coroutine testing)
- Core Testing 2.2.0 (LiveData/Flow testing)

***REMOVED******REMOVED******REMOVED*** Testing Best Practices
- Name tests: `whenCondition_thenExpectedBehavior`
- Use `@RunWith(RobolectricTestRunner::class)` for tests using Android framework classes
- Keep fixtures lightweight and deterministic
- Run `./gradlew test` for fast unit tests
- Run `./gradlew connectedAndroidTest` for UI/instrumented tests (requires device)

***REMOVED******REMOVED*** Commit & Pull Request Guidelines
- Commits in imperative mood with clear scope (e.g., `Add MLKit detector pipeline`, `Tweak CameraScreen gestures`); keep them small and logically grouped.
- PRs include: short summary, testing notes with commands run, linked issues/tickets, and screenshots or screen recordings for UI-facing changes (camera overlays, lists, dialogs).
- Call out any API/permission implications (camera usage, ML Kit model changes) in the PR description.
