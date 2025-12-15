# Repository Guidelines

## Project Structure & Module Organization
- Multi-module Gradle project.
- `androidApp/`: Android app (Jetpack Compose UI, CameraX, ML Kit wrappers, selling flow, navigation). Sources at `androidApp/src/main/java/com/scanium/app/` (camera, items, ml, selling, navigation, ui/theme, settings, data, media, platform). Resources: `androidApp/src/main/res`; manifest: `androidApp/src/main/AndroidManifest.xml`.
- `core-models/`: Shared models (ImageRef, NormalizedRect, ScannedItem, ScanMode, domain pack config/category). Backwards-compatible aliases in `com.scanium.app.core.*`; prefer `com.scanium.app.model` in new code.
- `core-tracking/`: Platform-free tracking and aggregation (ObjectTracker, ItemAggregator, AggregationPresets, Logger).
- `core-domainpack/`: Domain pack provider, config models, repository, category engine/mapping.
- `android-platform-adapters/`: Android adapters for Bitmap↔ImageRef and Rect/RectF↔NormalizedRect conversions.
- Library shells (`android-ml-mlkit`, `android-camera-camerax`, `core-contracts`, `core-scan`) hold namespaces; do not add `package` attributes to their manifests.
- Navigation entry: `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt`; keep new screens as composables named `FeatureScreen` and add routes there.

## Build, Test, and Development Commands
```bash
./build.sh assembleDebug      # Builds with auto-detected Java 17
./gradlew assembleDebug       # Build debug APK
./gradlew installDebug        # Deploy to connected device/emulator
./gradlew test                # JVM unit tests
./gradlew connectedAndroidTest # Instrumented + Compose UI tests (needs device)
./gradlew lint                # Android Lint across modules
```
- Use Android Studio’s “Apply Changes” for quick UI tweaks; prefer `./gradlew clean` before reproducing build issues.

## Coding Style & Naming Conventions
- Kotlin official style, 4-space indentation; prefer expression bodies for simple functions.
- Compose composables in `PascalCase` with `@Composable` at top; preview functions end with `Preview`.
- ViewModels hold `StateFlow`/`MutableStateFlow`; UI observes via `collectAsState()`. Keep side effects in `LaunchedEffect`/`DisposableEffect`.
- Filenames mirror primary class/composable (e.g., `CameraScreen.kt`, `ItemsViewModel.kt`); new resources follow lowercase_underscore.
- Prefer shared models from `com.scanium.app.model` (`ImageRef`, `NormalizedRect`, `ScannedItem`, domain config). Legacy aliases live in `com.scanium.app.core.*` for compatibility—new code should import the shared package.
- Keep Bitmaps/Rects at Android edges only; convert using `android-platform-adapters` (`Bitmap.toImageRefJpeg`, `ImageRef.Bytes.toBitmap`, `Rect/RectF.toNormalizedRect`, `NormalizedRect.toRectF/toRect`).
- Library manifests rely on Gradle `namespace`; do not set `package` attributes.
- Run `./gradlew lint` (or Android Studio formatting) before sending changes; avoid storing secrets in code or `local.properties`.

## Testing Guidelines

### Test Organization
- **Unit tests**: `androidApp/src/test/java/` (JUnit4, Truth, MockK, Coroutines Test, Robolectric)
- **Instrumented tests**: `androidApp/src/androidTest/java/` (Compose UI and Android framework)

### Current Test Coverage (all passing ✅)
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

### Test Dependencies
- JUnit 4.13.2 (test framework)
- **Robolectric 4.11.1** (Android framework in unit tests - required for `Rect`, etc.)
- Truth 1.1.5 (fluent assertions)
- MockK 1.13.8 (mocking)
- Coroutines Test 1.7.3 (coroutine testing)
- Core Testing 2.2.0 (LiveData/Flow testing)

### Testing Best Practices
- Name tests: `whenCondition_thenExpectedBehavior`
- Use `@RunWith(RobolectricTestRunner::class)` for tests using Android framework classes
- Keep fixtures lightweight and deterministic
- Run `./gradlew test` for fast unit tests
- Run `./gradlew connectedAndroidTest` for UI/instrumented tests (requires device)

## Commit & Pull Request Guidelines
- Commits in imperative mood with clear scope (e.g., `Add MLKit detector pipeline`, `Tweak CameraScreen gestures`); keep them small and logically grouped.
- PRs include: short summary, testing notes with commands run, linked issues/tickets, and screenshots or screen recordings for UI-facing changes (camera overlays, lists, dialogs).
- Call out any API/permission implications (camera usage, ML Kit model changes) in the PR description.
