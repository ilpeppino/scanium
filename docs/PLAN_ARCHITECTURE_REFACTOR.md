***REMOVED*** Architecture Refactoring Plan

**Status:** Proposed  
**Last Updated:** 2025-01-12  
**Related ADRs:** ADR-0001, ADR-0002, ADR-0003  
**Goal:** Deliver Android-first builds while standing up the shared brain and cloud-first classification.

---

***REMOVED******REMOVED*** Phase 0 – Guardrails (fast, parallel)
- Tasks: Verify Java 17 toolchain; keep `checkPortableModules` green; ensure BuildConfig reads `scanium.api.*` from `local.properties`/env (no secrets committed).
- Checkpoint commands: `./gradlew assembleDebug`, `./gradlew :shared:core-models:compileKotlinMetadata`

---

***REMOVED******REMOVED*** Phase 1 – Shared Contracts & Config (parallelizable)
- Tasks: Extend portable contracts for classification/config (`shared/core-models`); add Android `CloudConfigProvider`; add mock classifier (offline-friendly).
- Exit: Contracts compile; Android assembles unchanged in behavior.
- Checkpoint commands: `./gradlew :shared:core-models:compileKotlinMetadata`, `./gradlew assembleDebug`, `./gradlew test` (where applicable)

---

***REMOVED******REMOVED*** Phase 2 – Pipeline Hardening (can split)
- Tasks: Adapt `ClassificationOrchestrator` to shared contracts via an adapter; enforce “stable items only” uploads; add retry/latency metrics hooks; unit tests with mock classifier.
- Exit: JVM tests cover cloud + fallback; analyzer threads remain non-blocking.
- Checkpoint commands: `./gradlew test --tests "*Classification*"`, `./gradlew assembleDebug`, `./gradlew lint` (optional)

---

***REMOVED******REMOVED*** Phase 3 – Domain Pack Integration (safe increments)
- Tasks: Route classifier outputs through `DomainPackRepository` + `BasicCategoryEngine`; store active domain pack id (default `home_resale`) in shared config; optional UI badge for cloud vs fallback (feature-flagged).
- Exit: Domain mapping covered by JVM tests with fixture JSON; no regression when cloud is offline.
- Checkpoint commands: `./gradlew test --tests "*DomainPack*" --tests "*Classification*"`, `./gradlew assembleDebug`, `./gradlew lint` (optional)

---

***REMOVED******REMOVED*** Phase 4 – Backend Integration (parallel with Phase 3)
- Tasks: Backend proxy endpoint hardened for Vision (auth, rate limit, logging); client config contract stays unchanged; add mock/stub server for local tests if needed.
- Exit: Mobile can call backend when configured; defaults remain offline-friendly.
- Checkpoint commands: `./gradlew test` (with mocks), `./gradlew assembleDebug`

---

***REMOVED******REMOVED*** Phase 5 – iOS Prep (non-blocking)
- Tasks: Publish shared contracts/tracking for iOS consumption; document Vision/AVFoundation adapter to emit `RawDetection`/`ImageRef`; document Swift client using `CloudClassifierConfig`.
- Exit: Shared modules remain Android-free; iOS stubs documented; Android build unaffected.
- Checkpoint commands: `./gradlew :shared:core-models:compileKotlinMetadata`, `./gradlew assembleDebug`

---

***REMOVED******REMOVED*** Phase 6 – Observability & Validation
- Tasks: Add logging around classifier mode selection/retries/latency (no PII); expose counters for cloud usage; keep offline mode working with mocks.
- Exit: Metrics available; offline remains functional.
- Checkpoint commands: `./gradlew test`, `./gradlew assembleDebug`, `./gradlew lint` (optional)

---

***REMOVED******REMOVED*** Parallelization Notes
- Contracts/config (Phase 1) can proceed while pipeline hardening starts, as long as adapters shield current code.
- Domain pack integration (Phase 3) and backend integration (Phase 4) can run in parallel once contracts are stable.
- iOS prep is documentation/contract publishing only; schedule independently of Android feature work.

---

***REMOVED******REMOVED*** Risk Mitigations
- Keep new behavior behind adapters; avoid rewrites until tests exist.
- Use mock classifiers in unit tests to avoid network dependency.
- Maintain BuildConfig-driven config to prevent secret leakage and allow CI overrides.
