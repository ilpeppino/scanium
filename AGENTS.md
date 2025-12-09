***REMOVED*** Repository Guidelines

***REMOVED******REMOVED*** Project Structure & Module Organization
- Single Android app module: `app/`.
- Kotlin sources live in `app/src/main/java/com/example/scanium/` with feature folders: `camera/` (CameraX control), `items/` (scanned item state + UI), `ml/` (object detection helpers), `navigation/` (Compose routes), and `ui/` (theme, shared components).
- Resources and layouts: `app/src/main/res/`; manifest: `app/src/main/AndroidManifest.xml`.
- Keep new screens as composables named `FeatureScreen` and route entries in `navigation/ScaniumNavGraph`.

***REMOVED******REMOVED*** Build, Test, and Development Commands
```bash
./gradlew assembleDebug       ***REMOVED*** Build debug APK
./gradlew installDebug        ***REMOVED*** Deploy to connected device/emulator
./gradlew test                ***REMOVED*** JVM unit tests
./gradlew connectedAndroidTest ***REMOVED*** Instrumented + Compose UI tests (needs device)
./gradlew lint                ***REMOVED*** Android Lint across the module
```
- Use Android Studio’s “Apply Changes” for quick UI tweaks; prefer `./gradlew clean` before reproducing build issues.

***REMOVED******REMOVED*** Coding Style & Naming Conventions
- Kotlin official style, 4-space indentation; prefer expression bodies for simple functions.
- Compose composables in `PascalCase` with `@Composable` at top; preview functions end with `Preview`.
- ViewModels hold `StateFlow`/`MutableStateFlow`; UI observes via `collectAsState()`. Keep side effects in `LaunchedEffect`/`DisposableEffect`.
- Filenames mirror primary class/composable (e.g., `CameraScreen.kt`, `ItemsViewModel.kt`); new resources follow lowercase_underscore.
- Run `./gradlew lint` (or Android Studio formatting) before sending changes; avoid storing secrets in code or `local.properties`.

***REMOVED******REMOVED*** Testing Guidelines

***REMOVED******REMOVED******REMOVED*** Test Organization
- **Unit tests**: `app/src/test/java/` - Pure Kotlin/JVM logic with JUnit4
- **Instrumented tests**: `app/src/androidTest/java/` - UI and Android framework tests

***REMOVED******REMOVED******REMOVED*** Current Test Coverage (110 tests - all passing ✅)
- **Unit tests** (7 files):
  - `CandidateTrackerTest.kt` - Multi-frame detection pipeline (20 tests)
  - `DetectionCandidateTest.kt` - Promotion criteria validation (16 tests)
  - `ItemsViewModelTest.kt` - State management & deduplication (18 tests)
  - `PricingEngineTest.kt` - EUR price generation
  - `ScannedItemTest.kt` - Confidence level classification
  - `ItemCategoryTest.kt` - ML Kit label mapping
  - `FakeObjectDetector.kt` - Test fixtures

- **Instrumented tests** (2 files):
  - `ModeSwitcherTest.kt` - Compose UI interaction
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
