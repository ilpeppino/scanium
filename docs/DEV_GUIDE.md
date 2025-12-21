***REMOVED*** Developer Guide

***REMOVED******REMOVED*** Prerequisites
- JDK 17 (Gradle toolchain auto-detects).
- Android Studio with Android SDK + emulator or physical device.
- `local.properties` with `sdk.dir=...` (copy from `local.properties.example`).

***REMOVED******REMOVED*** Cloud Classification Setup (config-driven)

Scanium uses cloud classification by default to identify items. To enable this feature:

1. **Create/update `local.properties`** in the project root:
   ```properties
   sdk.dir=/path/to/android-sdk

   ***REMOVED*** Cloud classification API configuration
   scanium.api.base.url=https://your-backend-url.com/api/v1
   scanium.api.key=your-dev-api-key
   ```

2. **Backend requirements**:
   - The backend must implement `POST /v1/classify` endpoint (see archived backend docs under `docs/_archive/2025-12/backend/` for reference)
   - Accepts multipart/form-data with `image` (JPEG) and `domainPackId` fields
   - Returns JSON with `domainCategoryId`, `confidence`, `label`, `attributes`, `requestId`

3. **Testing without backend**:
   - Leave `scanium.api.base.url` empty or unset
   - Cloud classifier will gracefully skip classification
   - Items will still appear with ML Kit detection labels

4. **CI/Production**:
   - Set environment variables: `SCANIUM_API_BASE_URL` and `SCANIUM_API_KEY`
   - Build will use env vars if `local.properties` keys are missing

**Security notes**:
- `local.properties` is gitignored by default
- Never commit API keys to version control
- Release builds require API base URL; fail-fast if missing

***REMOVED******REMOVED*** Local build & test

***REMOVED******REMOVED******REMOVED*** With Android SDK (Workstation / Android Studio)
- `./scripts/build.sh assembleDebug` or `./gradlew assembleDebug` – build APK.
- `./gradlew installDebug` – install on a connected device/emulator.
- `./gradlew test` – JVM unit tests (fast path).
- `./gradlew connectedAndroidTest` – instrumented/Compose UI tests (device required).
- `./gradlew lint` – static checks.
- Coverage: `./gradlew koverVerify` (thresholds: shared modules ≥85%, androidApp ≥75%; HTML under `*/build/reports/kover/html` and `androidApp/build/reports/jacoco/testDebugUnitTest/html`).

***REMOVED******REMOVED******REMOVED*** Container environments (Claude Code, Docker without Android SDK)

**⚠️ Limitation:** `./gradlew test` and `./gradlew lint` **fail without Android SDK**.

**Container-friendly validation:**
```bash
***REMOVED*** JVM-only pre-push checks (shared modules only)
./gradlew prePushJvmCheck

***REMOVED*** Install git pre-push hook for automatic validation
./scripts/dev/install-hooks.sh
```

**What works in containers:**
- ✅ JVM tests for shared modules: `./gradlew :shared:core-models:jvmTest :shared:core-tracking:jvmTest`
- ✅ Portability checks: `./gradlew checkPortableModules checkNoLegacyImports`
- ✅ Code editing, static analysis with `rg`/`grep`
- ✅ Git operations, documentation updates

**What requires Android SDK (use CI/workstation):**
- ❌ Building APKs: `./gradlew assembleDebug`
- ❌ Android unit tests: `./gradlew :androidApp:testDebugUnitTest`
- ❌ Lint checks: `./gradlew lint`
- ❌ Instrumented tests: `./gradlew connectedAndroidTest`

**Mobile testing workflow (container-friendly):**
1. Push changes to your branch
2. GitHub Actions builds APK automatically (see `.github/workflows/android-debug-apk.yml`)
3. Download `scanium-app-debug-apk` artifact from workflow run
4. Install APK on device for testing

See also `hooks/README.md` for pre-push validation setup.

***REMOVED******REMOVED******REMOVED*** Codex container limitations (factual)
- Android SDK/emulator are not available; JVM-only Gradle tasks are the safe path.
- Networked device access (ADB) is unavailable; use CI artifacts for APKs.
- Avoid installing system packages; rely on provided Gradle wrapper and scripts.

***REMOVED******REMOVED*** Deduplication & Detection Quality Tuning

**Viewport Alignment (WYSIWYG):**
- Ensures ML analysis sees only what user sees in Preview
- Configuration: Automatic via `ViewPort` + `UseCaseGroup` in `CameraXManager.kt`
- Logging: One-time log on startup showing viewport dimensions and edge inset

**Edge Gating (Drop Partial Objects):**
- Filters detections too close to screen edges (likely cut-off objects)
- Configuration: `CameraXManager.EDGE_INSET_MARGIN_RATIO` (default: 0.10 = 10%)
- Increase for stricter filtering, decrease to allow more edge objects
- Logging: Rate-limited (every 5 seconds) when detections are dropped

**Spatial-Temporal Deduplication:**
- Fallback merge policy for tracker ID churn
- Configuration: `SpatialTemporalMergePolicy.MergeConfig` in `shared/core-tracking`
- Presets: `DEFAULT` (balanced), `STRICT` (conservative), `LENIENT` (aggressive)
- Logging: Merge events logged via `ItemAggregator` with "SPATIAL-TEMPORAL MERGE" prefix

**Testing deduplication changes:**
```bash
***REMOVED*** Run unit tests for merge policy (Android-free)
./gradlew :shared:core-tracking:test --tests "SpatialTemporalMergePolicyTest"

***REMOVED*** Run all tracking tests
./gradlew :shared:core-tracking:test

***REMOVED*** Build and install for on-device testing
./gradlew installDebug
```

**On-device validation checklist:**
- [ ] Point camera at edge objects: verify off-screen objects don't create items
- [ ] Slowly pan camera: verify fewer duplicates appear
- [ ] Check overlay remains smooth and stable (no lag)
- [ ] Verify categories still match correctly
- [ ] Test with different lighting conditions

***REMOVED******REMOVED*** Debugging tips
- Use Logcat filters for tags like `CameraXManager`, `ObjectDetectorClient`, `CloudClassifier`, `ItemsViewModel`.
- **New viewport/filtering logs:** Search for `[VIEWPORT]`, `[CROP_RECT]`, `[EDGE_FILTER]` tags.
- **Deduplication logs:** Search for "SPATIAL-TEMPORAL MERGE" in `ItemAggregator` output.
- Detection overlays live in `androidApp/src/main/java/com/scanium/app/camera/DetectionOverlay.kt`; tweak drawing there.
- Aggregation/tracking behavior is covered by tests in `androidApp/src/test/...` and `core-tracking/src/test/...`; add golden tests when changing heuristics.
- For ML Kit analyzer crashes, enable verbose logs in the respective client classes under `androidApp/src/main/java/com/scanium/app/ml/`.
