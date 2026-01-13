***REMOVED*** Scanium Android Refactor/Readability/Performance Audit Report

***REMOVED******REMOVED*** A) Executive Summary
- The largest risk to performance and maintainability is the camera pipeline concentration in `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`, which mixes camera binding, analyzer control, image conversion, detection routing, diagnostics, and telemetry in a single 2.2k LOC file.
- UI surfaces central to the camera-first flow (`androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt` and `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantScreen.kt`) are extremely large composables with many state sources, increasing recomposition risk and making changes brittle.
- Hot-loop allocations in `CameraXManager.kt` (NV21 conversion + JPEG compression) and per-frame logging/telemetry introduce avoidable CPU and GC pressure during scanning.
- `androidApp/src/main/java/com/scanium/app/items/state/ItemsStateManager.kt` and `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt` are “god objects” mixing state, persistence, telemetry, and configuration concerns.
- Assistant flow complexity is split across `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt` and `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt`, with many responsibilities in each and unclear boundaries.
- Core logic in `core-tracking/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt` is large and critical, and changes there should be carefully staged with tests.
- Do not touch the camera pipeline or assistant orchestration without an architectural review and safety tests, as regressions are high impact to the scan -> recognize -> aggregate -> price -> export path.

---

***REMOVED******REMOVED*** B) Hotspot Inventory (Top 20 androidApp + large core-*)
Hotspot candidates are all files >600 LOC.

| File path | LOC | Role | Symptoms (why risky) | Suggested split (target files/modules) | Priority |
| --- | --- | --- | --- | --- | --- |
| `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` | 2218 | Camera pipeline | Mixed responsibilities: camera binding, analyzer, detection routing, image conversion, watchdog, telemetry; heavy hot-path logging | `camera/CameraSessionController.kt`, `camera/FrameAnalyzer.kt`, `camera/ImageConverter.kt`, `camera/ScanDiagnostics.kt` | P0 |
| `androidApp/src/main/java/com/scanium/app/ui/settings/DeveloperOptionsScreen.kt` | 2212 | UI/settings | Massive composable and option wiring; hard to scan and extend | `ui/settings/dev/DeveloperOptionsSections.kt`, `DeveloperOptionsState.kt` | P1 |
| `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt` | 2016 | UI/camera | UI + permissions + lifecycle + repository usage; large composable | `ui/camera/CameraControls.kt`, `ui/camera/CameraOverlays.kt`, `ui/camera/CameraScreenState.kt` | P0 |
| `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantScreen.kt` | 1964 | UI/assistant | Large composable; many state sources; side effects and UI logic intertwined | `selling/assistant/ui/AssistantChatList.kt`, `AssistantInputBar.kt`, `AssistantScreenState.kt` | P1 |
| `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt` | 1641 | ViewModel/assistant | Mixes request building, local suggestions, draft management, connectivity; hard to test | `selling/assistant/domain/AssistantUseCases.kt`, `AssistantStateReducer.kt` | P0 |
| `androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt` | 1464 | UI/items | Large composable, multiple panels and dialogs | `items/ui/ItemsListContent.kt`, `ItemsListState.kt` | P1 |
| `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt` | 1305 | Settings/data | Single file for all settings keys, migrations, flows; very wide scope | `data/settings/SettingsDataStore.kt`, `SettingsMigrations.kt`, `SettingsFlows.kt` | P1 |
| `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt` | 1270 | UI/items edit | Large composable with editing logic | `items/edit/ui/ItemEditSections.kt`, `ItemEditState.kt` | P1 |
| `androidApp/src/main/java/com/scanium/app/items/state/ItemsStateManager.kt` | 1135 | State/aggregation | State + persistence + telemetry + caching; hot path | `items/state/ItemsStateStore.kt`, `items/state/ItemsPersistence.kt`, `items/state/ItemsTelemetry.kt` | P0 |
| `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV2.kt` | 1135 | UI/items edit | Legacy screen with large composable | Deprecate or split like V3 | P2 |
| `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` | 945 | ML client | Detection, conversion, logging, metrics in one place | `ml/ObjectDetectionEngine.kt`, `ml/DetectionMapping.kt` | P1 |
| `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt` | 930 | ViewModel/items | Orchestrates multiple managers; large public surface | `items/ItemsUiFacade.kt`, keep managers but narrow API | P1 |
| `androidApp/src/main/java/com/scanium/app/items/edit/ExportAssistantSheet.kt` | 901 | UI/assistant | Large composable and UI state | `items/edit/ui/ExportAssistantContent.kt` | P2 |
| `androidApp/src/main/java/com/scanium/app/items/ItemDetailSheet.kt` | 764 | UI/items | Large composable for detail display | `items/ui/ItemDetailSections.kt` | P2 |
| `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt` | 756 | Network/assistant | Request building + error mapping + auth; large file | `selling/assistant/network/AssistantApi.kt`, `AssistantErrorMapper.kt` | P1 |
| `androidApp/src/main/java/com/scanium/app/selling/assistant/local/LocalSuggestionEngine.kt` | 740 | Assistant logic | Large heuristics & domain mapping in one file | `selling/assistant/local/SuggestionRules.kt`, `SuggestionScorers.kt` | P1 |
| `androidApp/src/main/java/com/scanium/app/assistant/AssistantScreen.kt` | 739 | UI/assistant | Duplicate name vs selling assistant; confusion | Rename or move under `assistant/ui` | P2 |
| `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt` | 722 | ML/network | Network + request building + telemetry | `ml/classification/CloudClassifierApi.kt` | P1 |
| `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsViewModel.kt` | 708 | ViewModel/settings | Many settings concerns + transformations | `ui/settings/SettingsSectionsState.kt` | P2 |
| `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantPreflight.kt` | 681 | Assistant logic | Large preflight logic, error handling | `selling/assistant/domain/PreflightPolicy.kt` | P2 |
| `core-tracking/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt` | 807 | Tracking/aggregation | Core logic, heavy branching and logging | `core-tracking/.../SimilarityScorers.kt`, `AggregationPolicies.kt` | P1 |

---

***REMOVED******REMOVED*** C) Performance Findings (evidence, impact, recommendation)

***REMOVED******REMOVED******REMOVED*** Camera pipeline: backpressure, frame closing, analyzer dispatchers
- Evidence: `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` uses `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` and `imageProxy.close()` in analyzer and in `processImageProxy` finally.
- Impact: Backpressure is configured correctly, but frame processing still does heavy work per frame and relies on coroutine completion to close proxies.
- Recommendation: Keep current backpressure strategy but reduce per-frame work (see allocation and logging findings below) and consider a dedicated “frame processor” that receives only needed data, minimizing `ImageProxy` lifetime.

***REMOVED******REMOVED******REMOVED*** Allocation hotspots in hot loops
- Evidence: `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` `ImageProxy.toBitmap()` allocates NV21 `ByteArray`, `ByteArrayOutputStream`, compresses to JPEG, then decodes a new bitmap per detection.
- Impact: High GC churn during continuous scanning; increases latency and thermal load.
- Recommendation: Reuse buffers and output streams, consider a reusable YUV-to-RGB converter with pooled byte buffers, and reduce full-frame conversion when only small crops are needed.

- Evidence: `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` allocates `android.graphics.Rect(0, 0, ...)` per frame for filtering.
- Impact: Small but frequent allocations in a hot path.
- Recommendation: Reuse a cached `Rect` or pass dimensions to avoid per-frame object creation.

***REMOVED******REMOVED******REMOVED*** Dispatchers/threads
- Evidence: `CameraXManager.kt` uses `cameraExecutor` and launches `detectionScope` on `Dispatchers.Default` for each processed frame.
- Impact: If the detection loop spikes, default dispatcher contention can cause frame drops or delayed processing.
- Recommendation: Use a bounded dispatcher or single-threaded context for the detection loop to control concurrency and avoid thread pool contention with other CPU work.

***REMOVED******REMOVED******REMOVED*** Compose performance risks
- Evidence: Very large composables in `CameraScreen.kt`, `AssistantScreen.kt`, and `DeveloperOptionsScreen.kt` with multiple `collectAsState` and side effects.
- Impact: Recomposition cost and hard-to-trace state changes; UI jank risk on lower-end devices.
- Recommendation: Split screens into smaller composables with explicit state holders, and use `derivedStateOf`/`remember` for derived values that are recomputed often.

***REMOVED******REMOVED******REMOVED*** Telemetry overhead
- Evidence: `CameraXManager.kt` creates a span for every frame via `telemetry?.beginSpan` and records timers per frame.
- Impact: High overhead if telemetry is enabled in production; potential to flood exporter or add latency.
- Recommendation: Add sampling or coarse aggregation for frame-level spans, and only enable detailed spans for debugging sessions.

***REMOVED******REMOVED******REMOVED*** Persistence overhead in scanning loop
- Evidence: `ItemsStateManager.kt` calls `persistItems` from `updateItemsState` on every update, and copies item lists for caching.
- Impact: Frequent writes during scanning can cause IO contention and latency spikes.
- Recommendation: Coalesce writes (debounce or batch), and only persist diffs or checkpoint periodically.

---

***REMOVED******REMOVED*** D) Readability & Architecture Findings

- **Responsibility mixing in camera pipeline**  
  Evidence: `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` handles camera binding, analyzer, detection routing, image conversion, diagnostics, and telemetry.  
  Recommendation: Split into clear layers: camera session management, frame analysis, ML routing, and diagnostics. Keep Android-only types (CameraX, ImageProxy) at the boundary and move pure logic into android-free modules where possible.

- **UI and data repositories mixed in composables**  
  Evidence: `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt` constructs `SettingsRepository` and `FtueRepository` inside composable scope.  
  Recommendation: Hoist state and repositories to ViewModels or DI, and keep composables focused on rendering.

- **Items state, persistence, and telemetry in a single class**  
  Evidence: `androidApp/src/main/java/com/scanium/app/items/state/ItemsStateManager.kt` handles aggregation, persistence, telemetry, and UI events.  
  Recommendation: Split into `ItemsStateStore`, `ItemsPersistence`, and `ItemsTelemetryReporter` to keep responsibilities clear and testable.

- **Settings repository too broad**  
  Evidence: `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt` contains dozens of settings, migrations, and feature flags.  
  Recommendation: Split by feature area (`settings/assistant`, `settings/camera`, `settings/dev`) with a thin aggregator.

- **Assistant flow boundaries are blurry**  
  Evidence: `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt` combines connectivity, draft building, suggestions, and request lifecycle.  
  Recommendation: Introduce domain-level use cases (request builder, suggestion engine, draft updates) and keep the ViewModel as a coordinator.

- **Naming collisions and file organization**  
  Evidence: Two `AssistantScreen.kt` files under different packages (`app/assistant` and `selling/assistant`).  
  Recommendation: Rename and reorganize by feature to avoid ambiguity and reduce mental load.

- **Documentation gaps**  
  Recommendation: Add short architecture notes for camera pipeline and item aggregation to reduce onboarding time (e.g., flow diagram + responsibility map). Keep documentation in `docs/` and focused on boundaries and invariants.

---

***REMOVED******REMOVED*** E) Test Gaps & Safety Plan

***REMOVED******REMOVED******REMOVED*** Current coverage snapshot
- Unit tests: ~110 Kotlin test files under `androidApp/src/test/java`.
- Instrumented tests: ~18 Kotlin test files under `androidApp/src/androidTest/java`.
- Core tracking has tests in `shared/core-tracking/src/commonTest/kotlin/com/scanium/core/tracking/ItemAggregatorTest.kt`.

***REMOVED******REMOVED******REMOVED*** Gaps
- Camera pipeline (`CameraXManager.kt`) lacks direct unit or instrumentation tests for analyzer throttling, frame closure behavior, and watchdog recovery.
- Large composables (`CameraScreen.kt`, `AssistantScreen.kt`, `ItemsListScreen.kt`) have limited UI coverage relative to their size and complexity.
- `ItemsStateManager.kt` has broad behavior but minimal focused tests for persistence frequency and aggregation boundary conditions.

***REMOVED******REMOVED******REMOVED*** Minimal tests to add before refactor (no code changes now)
- **Unit tests**:  
  - `ItemsStateManager` persistence batching and aggregation invariants.  
  - `ItemAggregator` similarity threshold edge cases (in shared module).
- **Instrumentation tests**:  
  - Camera pipeline smoke test verifying analyzer attach/detach and no leaked `ImageProxy`.  
  - Assistant screen UI state transitions (loading -> drafting -> done).
- **Contract/golden tests**:  
  - Assistant request/response schema tests to lock API contract.  
  - Vision overlay mapping golden tests for detection boxes.

---

***REMOVED******REMOVED*** F) Proposed Refactor Plan (Phased)

***REMOVED******REMOVED******REMOVED*** Phase 1 (1-2 days): Quick wins, low risk
- **Objective**: Improve readability and reduce recomposition/boilerplate without altering logic.
- **Files impacted**:  
  `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt`,  
  `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantScreen.kt`,  
  `androidApp/src/main/java/com/scanium/app/ui/settings/DeveloperOptionsScreen.kt`
- **Risks**: UI regressions due to missed state hoisting.
- **Suggested approach**: Extract composable sections into smaller files, introduce state holder data classes, and move repository creation to ViewModels/DI.
- **Validation commands**:  
  `./gradlew :androidApp:assembleDevDebug`  
  `./gradlew :androidApp:lint`

***REMOVED******REMOVED******REMOVED*** Phase 2 (3-5 days): Core pipeline and state separation
- **Objective**: Clarify boundaries and reduce hot-path complexity.
- **Files impacted**:  
  `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`,  
  `androidApp/src/main/java/com/scanium/app/items/state/ItemsStateManager.kt`,  
  `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt`
- **Risks**: Camera regressions, data persistence inconsistencies.
- **Suggested approach**:  
  - Split camera pipeline into session, analyzer, converter, diagnostics.  
  - Introduce persistence batching in items state.  
  - Break settings repository by feature area.
- **Validation commands**:  
  `./gradlew :androidApp:assembleDevDebug`  
  `./gradlew test`  
  `./gradlew :androidApp:lint`

***REMOVED******REMOVED******REMOVED*** Phase 3 (Larger): Architecture consolidation
- **Objective**: Reduce long-term complexity and improve module boundaries.
- **Files impacted**:  
  `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt`,  
  `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt`,  
  `core-tracking/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt`
- **Risks**: Behavioral changes in assistant logic and aggregation.
- **Suggested approach**:  
  - Introduce domain use cases for assistant request lifecycle.  
  - Isolate aggregation similarity scoring into pure Kotlin classes (android-free).  
  - Formalize interfaces across modules.
- **Validation commands**:  
  `./gradlew test`  
  `./gradlew :androidApp:assembleDevDebug`  
  `./gradlew :androidApp:lint`

---

***REMOVED******REMOVED*** G) Appendix

***REMOVED******REMOVED******REMOVED*** Commands Run
- `rg --files -g "*.kt" androidApp core-*`
- Python LOC/complexity scan for top 20 and core-* large files
- `rg -n "ImageAnalysis|ImageProxy|analyzer|setAnalyzer|STRATEGY|Backpressure" androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`
- `sed -n` views of `CameraXManager.kt`, `CameraScreen.kt`, `AssistantScreen.kt`, `AssistantViewModel.kt`, `AssistantRepository.kt`, `SettingsRepository.kt`, `ItemsStateManager.kt`, `ObjectDetectorClient.kt`, `DetectionRouter.kt`
- `rg -n "itemsStore|persist|telemetry" androidApp/src/main/java/com/scanium/app/items/state/ItemsStateManager.kt`
- `rg -n "MobileTelemetryClient|getInstance\(\)|telemetry\.send|beginSpan|PerformanceMonitor" androidApp/src/main/java/com/scanium/app`
- `rg --files -g "*Aggregator*Test*.kt"`
- Python count of unit and instrumentation tests

***REMOVED******REMOVED******REMOVED*** Metrics Gathered (Top 20 androidApp + core)
Format: `file | LOC | functions | classes/objects | @Composable | branching`
- `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt | 2218 | 50 | 26 | 0 | 153`
- `androidApp/src/main/java/com/scanium/app/ui/settings/DeveloperOptionsScreen.kt | 2212 | 23 | 4 | 22 | 111`
- `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt | 2016 | 16 | 6 | 14 | 133`
- `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantScreen.kt | 1964 | 24 | 2 | 21 | 159`
- `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt | 1641 | 37 | 31 | 0 | 158`
- `androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt | 1464 | 16 | 3 | 8 | 116`
- `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt | 1305 | 60 | 3 | 0 | 89`
- `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt | 1270 | 7 | 3 | 6 | 125`
- `androidApp/src/main/java/com/scanium/app/items/state/ItemsStateManager.kt | 1135 | 45 | 4 | 0 | 85`
- `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV2.kt | 1135 | 14 | 5 | 8 | 83`
- `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt | 945 | 15 | 18 | 0 | 77`
- `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt | 930 | 35 | 11 | 0 | 42`
- `androidApp/src/main/java/com/scanium/app/items/edit/ExportAssistantSheet.kt | 901 | 14 | 1 | 10 | 34`
- `androidApp/src/main/java/com/scanium/app/items/ItemDetailSheet.kt | 764 | 12 | 1 | 11 | 59`
- `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt | 756 | 27 | 31 | 0 | 45`
- `androidApp/src/main/java/com/scanium/app/selling/assistant/local/LocalSuggestionEngine.kt | 740 | 11 | 5 | 0 | 68`
- `androidApp/src/main/java/com/scanium/app/assistant/AssistantScreen.kt | 739 | 6 | 1 | 6 | 60`
- `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt | 722 | 11 | 11 | 0 | 64`
- `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsViewModel.kt | 708 | 49 | 6 | 0 | 16`
- `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantPreflight.kt | 681 | 12 | 10 | 0 | 41`
- `core-tracking/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt | 807 | 30 | 7 | 0 | 65`

***REMOVED******REMOVED******REMOVED*** Assumptions & Limitations
- Static analysis only; no runtime profiling or tracing was performed.
- Complexity proxies are regex-based approximations.
- Recommendations do not modify code and assume current architecture constraints (Android-only types at boundaries; shared/KMP modules remain android-free).
