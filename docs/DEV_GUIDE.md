# Developer Guide

## Prerequisites
- JDK 17 (Gradle toolchain auto-detects).
- Android Studio with Android SDK + emulator or physical device.
- `local.properties` with `sdk.dir=...` (copy from `local.properties.example`).

## Local build & test
- `./build.sh assembleDebug` or `./gradlew assembleDebug` – build APK.
- `./gradlew installDebug` – install on a connected device/emulator.
- `./gradlew test` – JVM unit tests (fast path).
- `./gradlew connectedAndroidTest` – instrumented/Compose UI tests (device required).
- `./gradlew lint` – static checks.

## Debugging tips
- Use Logcat filters for tags like `CameraXManager`, `ObjectDetectorClient`, `CloudClassifier`, `ItemsViewModel`.
- Detection overlays live in `androidApp/src/main/java/com/scanium/app/camera/DetectionOverlay.kt`; tweak drawing there.
- Aggregation/tracking behavior is covered by tests in `androidApp/src/test/...` and `core-tracking/src/test/...`; add golden tests when changing heuristics.
- For ML Kit analyzer crashes, enable verbose logs in the respective client classes under `androidApp/src/main/java/com/scanium/app/ml/`.

## Do/Don't in the Codex container
- Do rely on GitHub Actions artifacts for APKs; local Android SDK is not installed here.
- Do run `rg`/static analysis, edit code, and keep changes small.
- Don't attempt to install Android Studio/SDK in the container; builds requiring them should run on your workstation or CI.
