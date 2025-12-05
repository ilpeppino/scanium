# Repository Guidelines

## Project Structure & Module Organization
- Single Android app module: `app/`.
- Kotlin sources live in `app/src/main/java/com/example/objecta/` with feature folders: `camera/` (CameraX control), `items/` (scanned item state + UI), `ml/` (object detection helpers), `navigation/` (Compose routes), and `ui/` (theme, shared components).
- Resources and layouts: `app/src/main/res/`; manifest: `app/src/main/AndroidManifest.xml`.
- Keep new screens as composables named `FeatureScreen` and route entries in `navigation/ObjectaNavGraph`.

## Build, Test, and Development Commands
```bash
./gradlew assembleDebug       # Build debug APK
./gradlew installDebug        # Deploy to connected device/emulator
./gradlew test                # JVM unit tests
./gradlew connectedAndroidTest # Instrumented + Compose UI tests (needs device)
./gradlew lint                # Android Lint across the module
```
- Use Android Studio’s “Apply Changes” for quick UI tweaks; prefer `./gradlew clean` before reproducing build issues.

## Coding Style & Naming Conventions
- Kotlin official style, 4-space indentation; prefer expression bodies for simple functions.
- Compose composables in `PascalCase` with `@Composable` at top; preview functions end with `Preview`.
- ViewModels hold `StateFlow`/`MutableStateFlow`; UI observes via `collectAsState()`. Keep side effects in `LaunchedEffect`/`DisposableEffect`.
- Filenames mirror primary class/composable (e.g., `CameraScreen.kt`, `ItemsViewModel.kt`); new resources follow lowercase_underscore.
- Run `./gradlew lint` (or Android Studio formatting) before sending changes; avoid storing secrets in code or `local.properties`.

## Testing Guidelines
- Unit tests: place in `app/src/test/java/`, using JUnit4 (`@Test`) for pure Kotlin/VM logic.
- Instrumented/Compose UI tests: place in `app/src/androidTest/java/`, use `AndroidJUnit4` and `ui-test-junit4` APIs; prefer semantics matchers over hardcoded text.
- Name tests `functionName_condition_expectedResult`; keep fixtures lightweight and deterministic.
- Run `./gradlew test` for fast checks; add `connectedAndroidTest` on UI or camera-facing changes.

## Commit & Pull Request Guidelines
- Commits in imperative mood with clear scope (e.g., `Add MLKit detector pipeline`, `Tweak CameraScreen gestures`); keep them small and logically grouped.
- PRs include: short summary, testing notes with commands run, linked issues/tickets, and screenshots or screen recordings for UI-facing changes (camera overlays, lists, dialogs).
- Call out any API/permission implications (camera usage, ML Kit model changes) in the PR description.
