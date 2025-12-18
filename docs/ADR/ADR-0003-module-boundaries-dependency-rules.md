***REMOVED*** ADR-0003: Module Boundaries and Dependency Rules

**Status:** Accepted  
**Date:** 2025-01-12  
**Deciders:** Architecture group  
**Context:** Protect build stability while adding cloud + KMP contracts

---

***REMOVED******REMOVED*** Context
We have multiple modules (androidApp, android-*, shared/core-*, core-domainpack, shell modules). Without rules, platform code can leak into shared code or platform modules can tangle, risking `./gradlew assembleDebug`.

---

***REMOVED******REMOVED*** Decision
Adopt clear dependency rules and keep enforcement lightweight:
1. **Shared-first contracts:** `shared/core-models` and `shared/core-tracking` stay Android-free (checked by `checkPortableModules`). New contracts (classification/config) live here.
2. **Platform isolation:** `android-ml-mlkit`, `android-camera-camerax`, `android-platform-adapters` do not depend on each other (adapters allowed as a leaf helper) and never depend on `androidApp`.
3. **App-only wiring:** `androidApp` is the sole integration point; no other module may depend on it.
4. **Domain pack neutrality:** `core-domainpack` may depend on shared models but not on platform code.
5. **Shell modules stay light:** `core-contracts`/`core-scan` remain contract-only if used; no Android types.

Enforcement today: `settings.gradle.kts` blocks dependencies on `:androidApp`; `checkPortableModules` blocks Android imports in shared modules; code review for new deps. Future: Gradle dependency validation when the graph grows.

---

***REMOVED******REMOVED*** Consequences
**Positive**
- Prevents platform leakage and circular deps.
- Keeps shared modules consumable by iOS without changes.
- Platform modules can evolve independently (swap MLKit/Vision) without touching shared code.

**Negative**
- Slight overhead when adding new deps; adapters may be needed to cross layers cleanly.

---

***REMOVED******REMOVED*** Notes for Future Enforcement
- If new shared modules appear (e.g., shared/core-domain), they follow the same rules: depend only on shared modules, never on Android or UI.
- Add automated dependency validation once more modules land to keep rules machine-checked.
