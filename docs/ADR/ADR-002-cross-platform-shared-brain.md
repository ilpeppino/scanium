# ADR-002: Cross-Platform “Shared Brain” (KMP-Ready) without Blocking Android

**Status:** Accepted  
**Date:** 2025-01-12  
**Deciders:** Architecture group  
**Context:** Android-first shipping, iOS preparation

---

## Context
We must keep Android shipping while preparing for iOS. Business logic (tracking, aggregation, classification contracts, domain mapping) should be shared to avoid divergence, but platform-specific camera/ML must stay isolated. Current shared modules (`shared/core-models`, `shared/core-tracking`) already exist; we need to extend them with portable contracts without introducing iOS build blockers.

---

## Decision
- Keep **shared brain** in existing KMP modules:
  - `shared/core-models`: models plus portable contracts for classification/config.
  - `shared/core-tracking`: tracking/aggregation math.
- Keep **platform layers thin and isolated**:
  - Android scanning: `android-camera-camerax`, `android-ml-mlkit`, `android-platform-adapters`.
  - UI + orchestration stays in `androidApp` (Compose + ViewModels).
- Add **configuration abstraction** (`CloudConfigProvider`) so iOS can supply the same inputs without touching Android code.
- Continue enforcing no Android imports in shared modules (`checkPortableModules`).

---

## Alternatives Considered
- **Full cross-platform UI (Flutter/RN)** — Rejected; would block Android, compromise camera/ML performance.
- **Separate codebases with no sharing** — Rejected; duplication and divergence risk.
- **New shared modules before stabilizing Android** — Rejected for now; reuse existing shared modules to avoid churn and keep builds stable.

---

## Consequences
**Positive**
- Shared contracts and models keep behavior aligned across platforms.
- Android remains unblocked; iOS can implement against the same interfaces later.
- Minimal surface area for platform leakage; adapters handle Bitmap/Rect conversions.

**Negative**
- Some duplication remains in UI orchestration until we gradually migrate more logic into shared modules.
- KMP compilation overhead (small, acceptable for current scope).

**Next Steps**
- Keep adding portable contracts (classification, pricing, config) to `shared/core-models`.
- Move orchestration/use-cases to shared modules once Android behavior is stable and covered by tests.
- Document iOS injection points (scanner adapter + classifier client) to mirror Android wiring.
