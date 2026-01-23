> Archived on 2025-12-20: superseded by docs/INDEX.md.

# Testing Guide

## Test Types

- **Shared KMP unit tests**: `shared/core-models`, `shared/core-tracking` (commonMain/commonTest)
- **Android unit tests**: `androidApp/src/test` (Robolectric/Compose)
- **Instrumented tests**: `androidApp/src/androidTest` (Compose UI & Android framework)

## Quick Commands

- KMP tests: `./gradlew :shared:core-models:test :shared:core-tracking:test`
- Android unit tests: `./gradlew :androidApp:testDebugUnitTest`
- Instrumented tests: `./gradlew :androidApp:connectedDebugAndroidTest` (requires device/emulator)
- Coverage (Kover/Jacoco): `./gradlew koverVerify` (generates reports under
  `*/build/reports/kover/html` and `androidApp/build/reports/jacoco/testDebugUnitTest/html`)

## Coverage Thresholds (configured)

- shared/core-models: ≥85%
- shared/core-tracking: ≥85%
- androidApp: ≥75%

## Tips

- Use `--continue` to gather coverage even if some tests fail.
- For faster local checks, run module-scoped tasks (e.g., `:shared:core-tracking:test`).
- Instrumented tests need an emulator or physical device; ensure `adb devices` shows a target.
