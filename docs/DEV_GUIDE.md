# Developer Guide

## Prerequisites
- JDK 17 (Gradle toolchain auto-detects).
- Android Studio with Android SDK + emulator or physical device.
- `local.properties` with `sdk.dir=...` (copy from `local.properties.example`).

## Cloud Classification Setup (Required for Phase 9+)

Scanium uses cloud classification by default to identify items. To enable this feature:

1. **Create/update `local.properties`** in the project root:
   ```properties
   sdk.dir=/path/to/android-sdk

   # Cloud classification API configuration
   scanium.api.base.url=https://your-backend-url.com/api/v1
   scanium.api.key=your-dev-api-key
   ```

2. **Backend requirements**:
   - The backend must implement `POST /v1/classify` endpoint (see `backend/` for reference implementation)
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

## Local build & test

### With Android SDK (Workstation / Android Studio)
- `./build.sh assembleDebug` or `./gradlew assembleDebug` – build APK.
- `./gradlew installDebug` – install on a connected device/emulator.
- `./gradlew test` – JVM unit tests (fast path).
- `./gradlew connectedAndroidTest` – instrumented/Compose UI tests (device required).
- `./gradlew lint` – static checks.
- Coverage: `./gradlew koverVerify` (thresholds: shared modules ≥85%, androidApp ≥75%; HTML under `*/build/reports/kover/html` and `androidApp/build/reports/jacoco/testDebugUnitTest/html`).

### Container environments (Claude Code, Docker without Android SDK)

**⚠️ Limitation:** `./gradlew test` and `./gradlew lint` **fail without Android SDK**.

**Container-friendly validation:**
```bash
# JVM-only pre-push checks (shared modules only)
./gradlew prePushJvmCheck

# Install git pre-push hook for automatic validation
./hooks/install-hooks.sh
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

See also `docs/BUILD_STABILITY.md` for detailed verification commands and `hooks/README.md` for pre-push validation setup.

## Debugging tips
- Use Logcat filters for tags like `CameraXManager`, `ObjectDetectorClient`, `CloudClassifier`, `ItemsViewModel`.
- Detection overlays live in `androidApp/src/main/java/com/scanium/app/camera/DetectionOverlay.kt`; tweak drawing there.
- Aggregation/tracking behavior is covered by tests in `androidApp/src/test/...` and `core-tracking/src/test/...`; add golden tests when changing heuristics.
- For ML Kit analyzer crashes, enable verbose logs in the respective client classes under `androidApp/src/main/java/com/scanium/app/ml/`.
