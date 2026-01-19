> Archived on 2025-12-20: superseded by docs/DECISIONS.md.

***REMOVED*** ADR-003: Module Boundaries and Dependency Rules

**Status:** Accepted  
**Date:** 2025-01-12  
**Deciders:** Architecture group  
**Context:** Protect build stability while adding cloud + iOS-ready contracts

---

***REMOVED******REMOVED*** Context

The repo already has multiple modules (androidApp, android-*, shared/core-*, core-domainpack,
core-contracts, core-scan). Without explicit rules, platform code could leak into shared modules or
platform modules could become intertwined, risking `./gradlew assembleDebug`.

---

***REMOVED******REMOVED*** Decision

Adopt a strict but incremental dependency policy:

1. **Shared-first contracts**
    - Portable code lives in `shared/core-models` and `shared/core-tracking` (no Android imports,
      verified by `checkPortableModules`).
    - New contracts (classification/config) are added here to avoid future iOS blockers.

2. **Platform isolation**
    - `android-ml-mlkit`, `android-camera-camerax`, and `android-platform-adapters` must not depend
      on each other (except adapters are allowed as a helper leaf). None may depend on `androidApp`.

3. **App integration only at the top**
    - `androidApp` is the only module that wires UI + platform + shared logic together. No other
      module may depend on it.

4. **Domain pack stays neutral**
    - `core-domainpack` exposes category/config logic without Android types; it may depend on shared
      models but not on platform modules.

5. **Shell modules stay empty unless contract-only**
    - `core-contracts`/`core-scan` remain lightweight; if used, they must stay Android-free to
      remain safe for KMP migration later.

**Enforcement**

- `settings.gradle.kts` already blocks modules from depending on `:androidApp`.
- `checkPortableModules` scans shared modules for `android.*` imports.
- Build reviews + CI linting verify new dependencies; future work may add Gradle dependency rules
  when module graph expands.

---

***REMOVED******REMOVED*** Consequences

**Positive**

- Clear layering prevents platform leakage and circular deps.
- Shared modules remain usable by iOS without extra work.
- Platform modules can evolve independently (swap MLKit/Vision implementations) without rippling
  through shared code.

**Negative**

- More upfront discipline when adding dependencies; some adapters may be needed to cross layers
  cleanly.

---

***REMOVED******REMOVED*** Notes for Future Enforcement

- If additional shared modules are added (e.g., `shared/core-domain`), they must follow the same
  rules: depend only on other shared modules, never on Android or UI.
- Introduce automated dependency validation once new modules appear to keep rules machine-checked.
