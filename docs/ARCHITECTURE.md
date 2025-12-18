***REMOVED*** Architecture

This document is the single source of truth for how Scanium is structured and how we will evolve it. The goals are Android-first delivery, cloud classification as the primary categorization path, and a shared “brain” that can be reused by iOS without blocking Android builds.

---

***REMOVED******REMOVED*** Principles
- Android-first: `./gradlew assembleDebug` must stay green at every step.
- Shared brain: business logic, contracts, and models live in KMP-ready modules; platform layers stay thin.
- Cloud-primary classification: Google Vision (via backend) is the canonical classifier; on-device labels are a fallback.
- Responsive pipeline: detection/tracking/aggregation remain on-device; cloud classification runs asynchronously for stable items only.
- No secrets in the app: endpoints/keys come from `local.properties` or environment, never committed.
- Safe increments: additive scaffolding first, then migrations guarded by tests.

---

***REMOVED******REMOVED*** Build Guardrails
- Toolchain: Java 17 enforced via Gradle toolchains (root + androidApp).
- Android Gradle Plugin 8.5.0, Kotlin 2.0.x, Compose BOM 2023.10.
- Build commands:
  - `./gradlew assembleDebug` – primary build gate
  - `./gradlew test` – JVM/unit tests (runs fast, offline)
  - `./gradlew connectedAndroidTest` – instrumented/Compose UI (optional, needs device)
- Lint/security: CycloneDX SBOM + OWASP Dependency Check wired in `androidApp`.
- Sandbox check: `checkPortableModules` keeps shared modules free of Android imports.

---

***REMOVED******REMOVED*** Module Map & Boundaries

**Platform UI (Android)**
- `androidApp/` – Compose UI, navigation (`navigation/NavGraph.kt`), view models, feature glue.

**Platform Scanning Layer (Android)**
- `android-camera-camerax/` – Camera lifecycle, frame acquisition.
- `android-ml-mlkit/` – ML Kit object/barcode/text wrappers.
- `android-platform-adapters/` – Bitmap/Rect ↔ `ImageRef`/`NormalizedRect` conversions.

**Shared Brain (portable, KMP-ready)**
- `shared/core-models/` – Portable models (`ImageRef`, `NormalizedRect`, `RawDetection`, `DetectionResult`, `ItemCategory`) and new classification/domain contracts (see `classification/` and `config/` packages).
- `shared/core-tracking/` – Platform-neutral tracking (`ObjectTracker`, `ObjectCandidate`, `AggregationPresets` math).

**Domain Pack & Mapping**
- `core-domainpack/` – Domain taxonomy, mapping, and repository (`DomainPackRepository`, `BasicCategoryEngine`, JSON config in `res/raw/home_resale_domain_pack.json`).

**Shell Namespaces (kept lightweight)**
- `core-contracts/`, `core-scan/` – Reserved for future shared contracts; kept Android-plugin but without platform dependencies to avoid build churn.

**Dependency Rules**
- Shared modules (`shared/*`) must not import Android types.
- Platform modules (`android-*`) must not depend on `androidApp`.
- UI layer depends on contracts/use cases, not on platform ML/Camera directly.
- Cross-module flow: `androidApp` → platform adapters/detectors → shared tracking/aggregation → classification contracts.

---

***REMOVED******REMOVED*** Scanning & Classification Pipeline
1. **Frame capture (Android only):** CameraX (`android-camera-camerax`) feeds frames to ML Kit analyzers (`android-ml-mlkit`).
2. **Detection:** ML Kit detections are converted to `RawDetection` (`NormalizedRect` + coarse labels + optional thumbnail) via adapters in `androidApp/ml`.
3. **Tracking & aggregation:** `shared/core-tracking`’s `ObjectTracker` and `ItemAggregator` merge detections over time, producing stable `AggregatedItem` instances. Only stable items (confirmed + thumbnail present) are eligible for cloud classification.
4. **Classification orchestration:** `ClassificationOrchestrator` (androidApp) enforces a bounded queue (max concurrency 2), retry with backoff, caching, and status tracking.
5. **Primary path – cloud:** `CloudClassifier` posts cropped thumbnails to the backend proxy for Google Vision. Configured via BuildConfig (`SCANIUM_API_BASE_URL`, `SCANIUM_API_KEY`), now also exposed through `CloudClassifierConfig`/`CloudConfigProvider` (see `shared/core-models/classification` and `androidApp/config`).
6. **Fallback – on-device:** When cloud is unavailable or not configured, the orchestrator can use on-device labels (ML Kit coarse categories) to keep UI responsive; results are marked as fallback.
7. **Domain mapping:** `DomainPackRepository` + `BasicCategoryEngine` translate classifier outputs to domain categories/attributes for UI and selling flows.
8. **UI update:** View models push `StateFlow` updates to Compose screens (camera overlay, item list, selling flow).

---

***REMOVED******REMOVED*** Cloud Classification (Google Vision via Backend)
- **Contract:** Portable interfaces in `shared/core-models/classification/ClassifierContracts.kt` define `Classifier`, `ClassificationResult`, `ClassificationMode`, and `CloudClassifierConfig`.
- **Android implementation:** `CloudClassifier` (androidApp) uses OkHttp with 10s connect/read timeouts, multipart upload of JPEG thumbnails, and retries for 408/429/5xx. `CloudConfigProvider` reads BuildConfig to populate `CloudClassifierConfig` without exposing secrets in code.
- **Security:** No Google Vision keys in the app. Backend proxy handles auth, rate limiting, logging, and Vision API calls.
- **Performance:** Only stable aggregated items are uploaded; thumbnails are re-encoded to strip EXIF. Async pipeline keeps camera thread hot.
- **Testing:** Provide a mock classifier implementing the shared `Classifier` interface for JVM tests; cloud calls can be faked without network.

---

***REMOVED******REMOVED*** Cross-Platform Readiness (iOS Prep)
- Shared contracts/models already live in `shared/core-models` and `shared/core-tracking`. New classification/config contracts extend the same shared space to avoid Android coupling.
- Platform scanning layers remain separate:
  - Android: ML Kit + CameraX (current)
  - iOS (future): AVFoundation + Apple Vision → adapters that emit `RawDetection`/`ImageRef` into the same tracking + classification use cases.
- Networking parity: iOS client will call the same backend proxy using the shared `CloudClassifierConfig` contract; Swift implementation can be added without touching Android.
- No iOS build blockers: All new interfaces are platform-agnostic and reside in existing shared modules; Android continues to compile unaffected.

---

***REMOVED******REMOVED*** Testing Strategy
- **Unit (JVM):** Tracking, aggregation, domain pack mapping, classification orchestrator logic; use mock classifier implementations.
- **Instrumented (optional):** Compose UI flows (`ModeSwitcherTest`, `DetectionOverlayTest`) remain minimal to avoid CI flakiness.
- **Commands:** `./gradlew test` for fast coverage; `./gradlew connectedAndroidTest` when a device is available.
- **Stability:** Tests must not require cloud credentials; mocks default to offline mode.

---

***REMOVED******REMOVED*** Configuration & Secrets
- `local.properties` (ignored by git) or environment variables provide:
  - `scanium.api.base.url`
  - `scanium.api.key`
- `androidApp/build.gradle.kts` injects values into `BuildConfig`. `CloudConfigProvider` surfaces them as a typed `CloudClassifierConfig` for app code. Empty values skip cloud calls gracefully.
- Never commit API keys. CI should supply env vars; local dev uses `local.properties.example` as a template.

---

***REMOVED******REMOVED*** Roadmap (high level)
- Solidify shared contracts (done in this pass).
- Move classification orchestration to shared (KMP) once Android path is stable.
- Add iOS implementations for scanning + classifier config using the same contracts.
- Incrementally migrate business logic (aggregation, pricing) into shared modules to maximize parity.
