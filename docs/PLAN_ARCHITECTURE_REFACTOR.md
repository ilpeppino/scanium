***REMOVED*** Architecture Refactoring Plan

**Status:** Proposed  
**Last Updated:** 2025-01-12  
**Related ADRs:** ADR-001, ADR-002, ADR-003  
**Goal:** Deliver Android-first builds while standing up the shared brain and cloud-first classification.

---

***REMOVED******REMOVED*** Phase 0 – Guardrails (fast, parallel)
- **Tasks**
  - Verify Java 17 toolchain + AGP/Kotlin versions checked in (`./gradlew help`).
  - Keep `checkPortableModules` green (no Android imports in shared modules).
  - Ensure BuildConfig values for cloud classifier pull from `local.properties`/env (no secrets committed).
- **Commands**
  - `./gradlew assembleDebug` (sanity)
  - `./gradlew :shared:core-models:compileKotlinMetadata` (shared contracts sanity)

---

***REMOVED******REMOVED*** Phase 1 – Shared Contracts & Config (parallelizable)
- **Tasks**
  - Add/extend portable contracts for classification + cloud config (shared/core-models).
  - Provide Android config provider that surfaces BuildConfig to the shared contract.
  - Add mock classifier implementation for tests (stays offline-friendly).
- **Exit Criteria**
  - Contracts compile on JVM/iOS source sets.
  - Android app still assembles with no behavior change.
- **Commands**
  - `./gradlew :shared:core-models:compileKotlinMetadata`
  - `./gradlew assembleDebug`

---

***REMOVED******REMOVED*** Phase 2 – Pipeline Hardening (can be split)
- **Tasks**
  - Wire `ClassificationOrchestrator` to the shared contracts (adapter layer) while keeping current cloud/on-device implementations.
  - Enforce “stable items only” before cloud upload; add metrics hooks for retry counts/latency.
  - Introduce fake/mock classifier in unit tests covering retry/backoff/cache.
- **Exit Criteria**
  - JVM tests cover cloud path and fallbacks with mocks.
  - Camera pipeline remains responsive (no blocking calls on analyzer threads).
- **Commands**
  - `./gradlew test --tests \"*Classification*\"`
  - `./gradlew assembleDebug`

---

***REMOVED******REMOVED*** Phase 3 – Domain Pack Integration (safe increments)
- **Tasks**
  - Route classifier outputs through `DomainPackRepository` + `BasicCategoryEngine` for fine-grained mapping.
  - Persist active domain pack id in config (defaults to `home_resale`) and expose via shared config.
  - Add thin UI badge for “cloud vs fallback” classification status (optional, behind feature flag).
- **Exit Criteria**
  - Domain mapping exercised in JVM tests with fixture JSON.
  - No regression to ML Kit coarse labels when cloud is offline.
- **Commands**
  - `./gradlew test --tests \"*DomainPack*\" \"*Classification*\"`
  - `./gradlew assembleDebug`

---

***REMOVED******REMOVED*** Phase 4 – iOS Prep (non-blocking)
- **Tasks**
  - Publish shared contracts/tracking to be consumable by iOS (Swift client stubs for classifier config + HTTP calls).
  - Document iOS platform scanning adapter (Vision/AVFoundation → RawDetection/ImageRef) mirroring Android.
  - Keep Android build green; no iOS build is required yet.
- **Exit Criteria**
  - Shared modules stay Android-free; iOS placeholders compile in common/iosMain.
  - Docs updated with injection points for Swift clients.
- **Commands**
  - `./gradlew :shared:core-models:compileKotlinMetadata`
  - (Optional) iOS compilation via KMP targets when available.

---

***REMOVED******REMOVED*** Phase 5 – Observability & Validation
- **Tasks**
  - Add lightweight logging around classifier mode selection, retries, and latency (no PII).
  - Expose counters for cloud usage to prepare for backend rate-limit tuning.
  - Keep offline mode working with mock classifier.
- **Commands**
  - `./gradlew test`
  - `./gradlew assembleDebug`

---

***REMOVED******REMOVED*** Parallelization Notes
- Contracts/config (Phase 1) can proceed while pipeline hardening begins, as long as adapters shield current code.
- Domain pack wiring (Phase 3) can run in parallel once shared contracts are stable.
- iOS prep is documentation + contract publishing only; schedule independently of Android features.

---

***REMOVED******REMOVED*** Risk Mitigations
- Keep all new behavior behind adapters; do not rewrite features until tests exist.
- Use mock classifiers in unit tests to avoid network dependency.
- Maintain BuildConfig-driven config to prevent secret leakage and to allow CI overrides.
