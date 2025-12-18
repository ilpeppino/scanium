***REMOVED*** ADR-0002: Cross-Platform “Shared Brain” (KMP-Ready) without Blocking Android

**Status:** Accepted  
**Date:** 2025-01-12  
**Deciders:** Architecture group  
**Context:** Android-first delivery with future iOS parity

---

***REMOVED******REMOVED*** Context
- We must keep Android shipping while preparing for iOS.
- Business logic (tracking, aggregation, classification contracts, domain mapping) should be shared to avoid drift; platform camera/ML must stay isolated.
- Shared modules (`shared/core-models`, `shared/core-tracking`) already exist; we need to extend them safely.

---

***REMOVED******REMOVED*** Decision
- Keep the **shared brain** in KMP modules (`shared/core-models`, `shared/core-tracking`) and add portable contracts (classification, config) there.
- Keep **platform layers thin**: Android scanning (`android-camera-camerax`, `android-ml-mlkit`, `android-platform-adapters`); UI + orchestration in `androidApp`. iOS will mirror with AVFoundation/Vision later.
- Enforce **no Android imports** in shared code (`checkPortableModules`), and keep `androidApp` as the only wiring point.
- Provide **config abstraction** (`CloudConfigProvider`) so iOS can supply the same inputs without touching Android code.

---

***REMOVED******REMOVED*** Consequences
**Positive**
- Shared contracts/models keep behavior aligned across platforms.
- Android remains unblocked; iOS can implement against the same interfaces when ready.
- Minimal surface area for platform leakage; adapters handle Bitmap/Rect edges.

**Negative**
- Some orchestration remains in Android until we migrate it with tests.
- Slight KMP compilation overhead (acceptable for scope).

---

***REMOVED******REMOVED*** Next Steps
- Adapt classification orchestration to the shared contracts and add JVM tests with mocks.
- Gradually migrate more use-cases (pricing, mapping) into shared modules once Android behavior is stable.
- Document iOS injection points (scanner adapter + classifier client) to mirror Android wiring.
