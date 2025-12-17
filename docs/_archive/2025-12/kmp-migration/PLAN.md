# KMP Migration Plan (Keep Android Green)

## Target Module Layout
- **shared:core** – pure Kotlin models (ScannedItem equivalent without Bitmap/RectF), math helpers, time utilities.
- **shared:tracking** – tracking/aggregation logic (ObjectTracker/ObjectCandidate analogs) depending only on `shared:core` models and geometry abstractions (e.g., `NormalizedRect`).
- **shared:domain** – domain pack contracts, pricing rules, category mapping; no platform I/O.
- **shared:analytics** – interfaces/events; platform provides implementations.
- **androidApp** – current UI, CameraX/ML Kit analyzers, media utilities.
- **androidPlatform** (new library module) – adapters from Android types (Bitmap/Rect/RectF/ImageProxy) into shared models and bridge implementations for ML/analytics.
- **iosScaffold** – placeholder for future Swift package consuming shared artifacts; thin adapters for AVFoundation/Vision/CoreML.

## Incremental “Green Build” Steps (commit-by-commit)
1. **Baseline documentation + guards**: add KMP rules to docs (this file) and a lint/checklist; ensure `./gradlew assembleDebug` passes.
2. **Introduce shared geometry/models**: add `NormalizedRect`, `ImageRef`, platform-neutral `ScannedItemData` to `shared:core` (no Android deps); add mapper stubs in `androidPlatform` converting `Bitmap/RectF` to shared shapes. Android build stays green via adapters but uses existing models internally.
3. **Extract tracking math**: move IoU/distance/averaging logic from `ObjectCandidate/ObjectTracker` into `shared:tracking` using shared models; keep Android tracker delegating to shared functions while still emitting Android `ScannedItem` for UI.
4. **Port aggregation/state**: introduce shared aggregation pipeline using `shared` models; Android `ItemsViewModel` wraps it via adapters; keep Compose-facing types unchanged for now.
5. **Move domain pack logic**: migrate `BasicCategoryEngine`, `CategoryMapper`, and pack parsing to `shared:domain`; Android `LocalDomainPackRepository` implements platform loader and feeds shared engine.
6. **Analytics/contracts**: define shared analytics interfaces/events; Android wires existing logging to implementations; ensure shared code only depends on interfaces.
7. **Replace Android types in models**: update Android layer to map between Compose/UI models and shared `ScannedItemData`; progressively shrink Bitmap/RectF presence to platform modules only. Confirm `./gradlew assembleDebug` and `./gradlew test` after each step.
8. **Add iOS scaffold**: create minimal Swift package binding to shared framework, with stub camera/ML adapters. Validate shared Gradle publishing and keep Android build green.

## Rules and Constraints
- Shared modules **must not** import `android.*`, CameraX, ML Kit, or Compose; only pure Kotlin/JVM libs.
- Platform modules adapt to shared models at boundaries (image refs, rectangles, timestamps); shared code remains deterministic and unit-testable.
- Behavior parity is required: aggregation/tracking thresholds stay the same; any changes require updated golden tests.
- Android build (`./gradlew assembleDebug`) must succeed after each commit; shared code addition must not break existing UI.

## Risks & Mitigations
- **SDK/Gradle breakage**: Creating new modules may impact build scripts. Mitigate by adding modules incrementally and running `./gradlew assembleDebug` + `./gradlew test` every step.
- **Model drift**: Divergence between Android models and shared models could introduce bugs. Mitigate with adapter mappers and golden tests covering tracker/aggregator outputs.
- **Performance regressions**: Extra conversions could hurt camera FPS. Mitigate with lightweight data classes, lazy thumbnail handling, and benchmarks in tracking tests.
- **Parallel platform differences**: iOS classifiers/camera may differ. Mitigate by keeping shared contracts narrow (geometry + classification results) and allowing platform-specific capability flags.
