# Architecture

Single source of truth for how Scanium is structured and how we evolve it. Goal: ship Android first, keep builds green, keep cloud classification optional/configurable, and grow a shared “brain” that iOS can consume without blocking Android.

---

## Current State (from repo inspection)
- **Build/tooling:** Java 17 toolchain; AGP 8.5.0; Kotlin 2.0.0 + Compose compiler 2.0.0; Compose BOM 2024.05.00 in `androidApp`; KSP 2.0.0-1.0.24. `./gradlew assembleDebug` is the main gate; SBOM + OWASP checks in `androidApp`.
- **Modules:**
  - Platform UI: `androidApp/` (Compose, navigation, view models).
  - Platform scanning: `android-camera-camerax` (CameraX), `android-ml-mlkit` (ML Kit analyzers), `android-platform-adapters` (Bitmap/Rect adapters).
  - Shared brain: `shared/core-models` (ImageRef, NormalizedRect, RawDetection, DetectionResult, ItemCategory + classification/config contracts), `shared/core-tracking` (ObjectTracker, math).
  - Domain taxonomy: `core-domainpack` (DomainPackRepository, BasicCategoryEngine, JSON config).
  - Shell namespaces: `core-contracts`, `core-scan`, `shared:test-utils` (test helpers for shared modules).
  - Android wrappers: `core-models`, `core-tracking` (typealiases to shared KMP).
- **Pipeline today:** CameraX → ML Kit detection → adapters to `RawDetection` → `ObjectTracker` + `ItemAggregator` → `ClassificationOrchestrator` (cloud/offline paths) → UI state in view models. Cloud classifier is config-driven via `BuildConfig`/`local.properties`; on-device labels act as fallback when unset.

---

## Target Architecture (layers)
- **Presentation (platform-specific):** Compose UI (Android), future SwiftUI (iOS). Pure UI + state wiring only.
- **Platform Scanning Layer:** Camera + on-device detectors; emits portable `RawDetection` + thumbnails. Android = CameraX/ML Kit; iOS (future) = AVFoundation/Apple Vision.
- **Shared Brain (portable/KMP-ready):** Models, tracking/aggregation, classification/config contracts, domain mapping. No Android types allowed.
- **Integration:** `androidApp` wires platform scanning to shared brain and domain pack; iOS will mirror the same contracts later.

Mermaid (layered view):
```mermaid
flowchart TD
    UI[Presentation: Compose/SwiftUI] --> VM[ViewModels]
    VM --> Platform[Platform Scanning Layer<br/>CameraX+ML Kit / AVFoundation+Vision]
    Platform --> Adapters[Platform Adapters<br/>Bitmap/Rect -> ImageRef/NormalizedRect]
    Adapters --> Tracking[Shared Tracking & Aggregation]
    Tracking --> Classify[Classification Orchestrator]
    Classify --> Cloud[Cloud Classifier (backend proxy -> Google Vision)]
    Classify --> Fallback[On-device labels (fallback)]
    Classify --> Domain[Domain Pack Mapping]
    Domain --> VM
```

---

## Data Flow (stable items only for cloud)
1. Camera frame → ML Kit detector → `RawDetection` (normalized bbox, coarse label, thumbnail).
2. `ObjectTracker` + `ItemAggregator` merge detections; only **stable items** (confirmed + thumbnail) are eligible for cloud upload.
3. `ClassificationOrchestrator` (bounded concurrency=2, retries) decides mode:
   - `CLOUD`: send thumbnail to backend proxy (Google Vision), async.
   - `ON_DEVICE`/`FALLBACK`: use coarse labels when cloud unavailable/unconfigured.
4. `DomainPackRepository` + `BasicCategoryEngine` map classifier output to domain categories/attributes.
5. View models push updated UI state (overlays, item list, selling flow).

Mermaid (pipeline):
```mermaid
flowchart LR
    Frames[Camera Frames] --> Detect[On-device Detection (ML Kit)]
    Detect --> Raw[RawDetection]
    Raw --> Track[ObjectTracker]
    Track --> Agg[ItemAggregator]
    Agg -- stable items only --> Queue[ClassificationOrchestrator]
    Queue -->|cloud| Cloud[Backend Proxy -> Google Vision]
    Queue -->|fallback| OnDevice[Coarse labels]
    Cloud --> Map[Domain Pack Mapping]
    OnDevice --> Map
    Map --> VM[ViewModels/StateFlow]
    VM --> UI[Compose UI]
```

---

## Module/Package Boundaries & Dependency Rules
- Shared modules (`shared/*`) are Android-free; enforced by `checkPortableModules`.
- Platform modules (`android-*`) do not depend on each other except adapters can be a leaf helper; none depend on `androidApp`.
- `androidApp` is the only integration point (wires UI + platform + shared).
- `core-domainpack` depends on shared models but not on platform code.
- Shell modules (`core-contracts`, `core-scan`) stay lightweight; no Android types.

## Security posture (concise)
- Network + classification defaults keep processing on-device; cloud classification only activates when `SCANIUM_API_BASE_URL`/`SCANIUM_API_KEY` are set (via `local.properties` or environment). See `androidApp/build.gradle.kts` BuildConfig entries.
- OWASP Dependency-Check and CycloneDX SBOM run from `androidApp` (see Gradle plugins) and are exercised via `security-cve-scan.yml`.
- Android network security config lives at `androidApp/src/main/res/xml/network_security_config.xml`; release builds enable R8/ProGuard per `proguard-rules.pro`.

---

## Cloud Classification Flow (Google Vision via backend proxy)
- **Trigger:** Only stable aggregated items with thumbnails.
- **Config:** `CloudClassifierConfig` + `CloudConfigProvider` (Android impl reads BuildConfig from `local.properties`/env: `scanium.api.base.url`, `scanium.api.key`). No secrets in source.
- **Transport:** OkHttp multipart JPEG upload to backend proxy; timeouts 10s/10s; retries on 408/429/5xx; EXIF stripped via re-encode.
- **Backend:** Holds Google credentials, rate limits, logs, maps Vision output to domain categories.
- **Fallback:** When config missing or network down, orchestrator uses on-device labels; results marked as fallback.
- **Testing:** Mock classifier for JVM tests; cloud path optional/gated by env.

---

## Build Guardrails
- Java 17 toolchain (root + androidApp).
- Commands: `./gradlew assembleDebug` (must stay green), `./gradlew test` (fast, offline), `./gradlew connectedAndroidTest` (device-only), `./gradlew lint` (optional/CI).
- Security/lint: CycloneDX + OWASP Dependency Check active in `androidApp`.

---

## Cross-Platform Readiness (iOS Prep)
- Contracts and models live in shared modules; no Android imports.
- Future iOS will implement:
  - Platform scanning adapter (Vision/AVFoundation → `RawDetection`/`ImageRef`).
  - Cloud classifier client using the same `CloudClassifierConfig`.
- Android remains unblocked; shared code already compiles for Android; iOS targets can be added later without touching Android.

---

## Roadmap (high level)
- Harden shared contracts and config (done).
- Adapt orchestrator to shared contracts; add mocks/tests.
- Route classifier outputs through domain pack mapping and surface status in UI.
- Add iOS clients against the same contracts once Android path is stable.
