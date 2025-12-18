***REMOVED*** ADR-003: Module Boundaries and Dependency Rules

**Status:** Proposed
**Date:** 2025-12-18
**Deciders:** Architecture Team
**Context:** Phase 1 - Target Architecture Definition

---

***REMOVED******REMOVED*** Context and Problem Statement

As Scanium grows from a single-module Android app to a multi-platform (Android + iOS) architecture with cloud integration, we need clear module boundaries and dependency rules to prevent:

1. **Circular dependencies** - Module A depends on B, B depends on A (compile errors)
2. **Layer violations** - UI code directly calling platform APIs (breaks abstraction)
3. **Platform leakage** - Android types in shared KMP modules (breaks iOS compilation)
4. **Unmaintainable structure** - Everything depends on everything (spaghetti)

**Current State:**
- 11 modules total
- Basic portability checks (`checkPortableModules` task)
- Subproject dependency guard (prevents modules from depending on `:androidApp`)
- But no comprehensive dependency validation

**Goal:** Define and enforce strict module boundaries that:
- Support Android-first development (no iOS blockers)
- Enable gradual KMP migration (shared modules expand over time)
- Prevent architectural erosion (rules enforced at build time)
- Keep build stable (`./gradlew assembleDebug` always works)

---

***REMOVED******REMOVED*** Decision Drivers

1. **Build Stability**: Dependencies must never create cycles or break compilation
2. **Layer Isolation**: Presentation → Domain → Data flow must be unidirectional
3. **Platform Safety**: Shared modules cannot contain Android/iOS-specific types
4. **Gradual Migration**: Rules must allow incremental refactoring, not require "big bang"
5. **Developer Experience**: Clear error messages when rules violated
6. **Tooling**: Automated validation in CI/CD pipeline

---

***REMOVED******REMOVED*** Considered Options

***REMOVED******REMOVED******REMOVED*** Option 1: No Formal Boundaries (Current State)
**Approach:** Trust developers to follow conventions, no automated enforcement

**Pros:**
- Zero setup cost
- Maximum flexibility

**Cons:**
- ❌ **Accidental violations**: Easy to add wrong dependencies
- ❌ **Technical debt accumulates**: No way to detect erosion
- ❌ **Refactoring risk**: Changes can break assumptions
- ❌ **Onboarding friction**: New developers don't know rules

**Verdict:** ❌ **Rejected** - Already caused issues. Need enforcement.

---

***REMOVED******REMOVED******REMOVED*** Option 2: Strict Layer Architecture with Gradle Dependency Validation (**CHOSEN**)
**Approach:** Define clear layers (Presentation, Domain, Data, Platform) with automated Gradle checks

**Layers:**
```
┌─────────────────────────────────────────────────────┐
│         Presentation Layer (androidApp)             │  ← Can depend on all layers
│   (Compose UI, ViewModels, Navigation)              │
└────────────────────┬────────────────────────────────┘
                     │
     ┌───────────────┼───────────────┐
     │               │               │
     ▼               ▼               ▼
┌──────────┐   ┌──────────┐   ┌──────────┐
│ android- │   │ android- │   │ android- │            Platform Layer
│ camera-  │   │   ml-    │   │ platform-│            ← Only depends on Android SDK + shared:* interfaces
│ camerax  │   │  mlkit   │   │ adapters │
└────┬─────┘   └────┬─────┘   └────┬─────┘
     │              │              │
     └──────────────┼──────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────┐
│          Domain Layer (shared:core-domain)           │  ← Core business logic (KMP)
│  (Use Cases, Domain Models, Repository Interfaces)   │
└────────────────────┬────────────────────────────────┘
                     │
     ┌───────────────┼───────────────┐
     │               │               │
     ▼               ▼               ▼
┌──────────┐   ┌──────────┐   ┌──────────┐
│ shared:  │   │ shared:  │   │ shared:  │            Data/Foundation Layer
│  core-   │   │  core-   │   │  core-   │            ← Pure Kotlin, no platform deps
│  models  │   │ tracking │   │   data   │
└──────────┘   └──────────┘   └──────────┘
```

**Pros:**
- ✅ **Automated enforcement**: Build fails if rules violated
- ✅ **Clear boundaries**: Each layer has well-defined responsibilities
- ✅ **Gradual migration**: Can add rules incrementally
- ✅ **Fast feedback**: Developers see errors immediately
- ✅ **Documentation**: Dependency graph IS the architecture

**Cons:**
- Initial setup cost (write Gradle validation tasks)
- Some false positives may need exceptions (documented)

**Verdict:** ✅ **CHOSEN** - Best balance of safety and flexibility.

---

***REMOVED******REMOVED******REMOVED*** Option 3: Separate Git Repositories per Layer
**Approach:** Split layers into separate Git repos with published artifacts

**Pros:**
- Strongest isolation
- Versioned dependencies

**Cons:**
- ❌ **Massive overhead**: Separate CI/CD for each repo
- ❌ **Slow iteration**: Changes require version bumps, publishing
- ❌ **Complexity**: Multi-repo coordination is hard
- ❌ **Overkill**: Not needed for current team size (<10 devs)

**Verdict:** ❌ **Rejected** - Too complex for current scale. Revisit if team grows to 20+ devs.

---

***REMOVED******REMOVED*** Decision Outcome

**Chosen option: Option 2 - Strict Layer Architecture with Gradle Validation**

---

***REMOVED******REMOVED******REMOVED*** Module Dependency Rules

***REMOVED******REMOVED******REMOVED******REMOVED*** **Rule 1: Presentation Layer Can Depend on Everything**

```gradle
// ✅ ALLOWED
:androidApp → :android-camera-camerax
:androidApp → :android-ml-mlkit
:androidApp → :android-platform-adapters
:androidApp → :shared:core-domain
:androidApp → :shared:core-models
:androidApp → :shared:core-tracking
:androidApp → :shared:core-data
:androidApp → :core-domainpack
```

**Rationale:** Presentation layer is the integration point. It wires everything together.

---

***REMOVED******REMOVED******REMOVED******REMOVED*** **Rule 2: Platform Modules Cannot Depend on Each Other**

```gradle
// ❌ FORBIDDEN
:android-camera-camerax → :android-ml-mlkit
:android-ml-mlkit → :android-platform-adapters

// ✅ ALLOWED
:android-camera-camerax → :shared:core-models  (for ImageRef interface)
:android-ml-mlkit → :shared:core-models        (for RawDetection interface)
:android-platform-adapters → :shared:core-models  (for type conversions)
```

**Rationale:** Platform modules should be isolated "leaf" nodes. They adapt platform APIs to shared interfaces.

**Exception:** `android-platform-adapters` is special - it provides converters between Android types and shared models. Other Android modules can depend on it:
```gradle
// ✅ ALLOWED (exception)
:android-camera-camerax → :android-platform-adapters
:android-ml-mlkit → :android-platform-adapters
```

---

***REMOVED******REMOVED******REMOVED******REMOVED*** **Rule 3: Shared Modules Cannot Depend on Platform Modules**

```gradle
// ❌ FORBIDDEN
:shared:core-domain → :android-camera-camerax  ❌
:shared:core-models → :androidApp             ❌
:shared:core-tracking → :android-ml-mlkit     ❌

// ✅ ALLOWED
:shared:core-domain → :shared:core-models
:shared:core-domain → :shared:core-tracking
:shared:core-data → :shared:core-models
```

**Rationale:** Shared modules must be platform-agnostic to work on iOS. Android dependencies break iOS compilation.

**Enforcement:**
1. `checkPortableModules` task scans for `import android.*` or `import androidx.*`
2. Gradle dependency validation blocks platform module dependencies at build time

---

***REMOVED******REMOVED******REMOVED******REMOVED*** **Rule 4: Dependency Direction is Always Downward**

```
Presentation (androidApp)
    ↓ can depend on
Platform (android-*) & Domain (shared:core-domain)
    ↓ can depend on
Foundation (shared:core-models, shared:core-tracking, shared:core-data)
    ↓ can depend on
Kotlin stdlib only
```

**Anti-pattern:**
```gradle
// ❌ FORBIDDEN - Foundation cannot depend upward
:shared:core-models → :shared:core-domain  ❌
:shared:core-tracking → :androidApp        ❌
```

**Rationale:** Dependencies flow down the abstraction ladder. Foundation has no idea about domain logic or UI.

---

***REMOVED******REMOVED******REMOVED******REMOVED*** **Rule 5: Core-* Android Wrappers Are Deprecated (Phase Out)**

```gradle
// Current (legacy):
:core-models (Android lib) → :shared:core-models
:core-tracking (Android lib) → :shared:core-tracking

// Target (after migration):
:androidApp → :shared:core-models  (direct)
:androidApp → :shared:core-tracking  (direct)
```

**Migration Path:**
1. Phase 2-4: Keep `:core-*` wrappers with type aliases for compatibility
2. Phase 5-6: Update androidApp imports to use `shared:*` directly
3. Phase 7: Remove `:core-*` wrapper modules entirely

**Rationale:** Extra wrapper layer adds confusion. Migrate to direct `shared:*` dependencies.

---

***REMOVED******REMOVED******REMOVED*** Module Responsibility Matrix

| Module | Responsibility | Can Depend On | Cannot Depend On |
|--------|---------------|---------------|------------------|
| **androidApp** | Compose UI, ViewModels, DI, Integration | All modules | None (top layer) |
| **android-camera-camerax** | CameraX wrapper, frame acquisition | `shared:core-models`, `android-platform-adapters` | Other `android-*`, `androidApp`, `shared:core-domain` |
| **android-ml-mlkit** | ML Kit wrapper, detection | `shared:core-models`, `android-platform-adapters` | Other `android-*`, `androidApp`, `shared:core-domain` |
| **android-platform-adapters** | Type converters (Bitmap→ImageRef) | `shared:core-models` | All other modules |
| **shared:core-domain** | Use cases, business logic, interfaces | `shared:core-models`, `shared:core-tracking`, `shared:core-data` | Any `android-*`, `androidApp` |
| **shared:core-models** | Data models (ScannedItem, ImageRef, etc.) | Kotlin stdlib only | All other modules |
| **shared:core-tracking** | ObjectTracker, tracking math | `shared:core-models` | All other modules |
| **shared:core-data** | Repository implementations, API clients | `shared:core-models`, `shared:core-domain` (interfaces) | Any `android-*`, `androidApp` |
| **core-domainpack** | Domain Pack config (legacy, Android-only) | `shared:core-models` | All `android-*` |

---

***REMOVED******REMOVED******REMOVED*** Gradle Validation Implementation

**New Task: `checkModuleDependencies`**

```kotlin
// build.gradle.kts (root)

tasks.register("checkModuleDependencies") {
    description = "Validates module dependencies follow architectural rules"
    group = "verification"

    doLast {
        val violations = mutableListOf<String>()

        // Rule 1: Platform modules cannot depend on each other
        val platformModules = listOf("android-camera-camerax", "android-ml-mlkit")
        platformModules.forEach { source ->
            platformModules.forEach { target ->
                if (source != target && hasDependency(source, target)) {
                    violations.add("$source → $target: Platform modules cannot depend on each other")
                }
            }
        }

        // Rule 2: Shared modules cannot depend on platform modules
        val sharedModules = listOf("shared:core-models", "shared:core-tracking", "shared:core-domain", "shared:core-data")
        val androidModules = listOf("androidApp", "android-camera-camerax", "android-ml-mlkit", "android-platform-adapters")

        sharedModules.forEach { shared ->
            androidModules.forEach { android ->
                if (hasDependency(shared, android)) {
                    violations.add("$shared → $android: Shared modules cannot depend on Android modules")
                }
            }
        }

        // Rule 3: Foundation cannot depend upward
        val foundationModules = listOf("shared:core-models", "shared:core-tracking")
        val domainModules = listOf("shared:core-domain", "androidApp")

        foundationModules.forEach { foundation ->
            domainModules.forEach { domain ->
                if (hasDependency(foundation, domain)) {
                    violations.add("$foundation → $domain: Foundation cannot depend on domain/presentation")
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Module dependency violations detected:\n" +
                violations.joinToString("\n  • ", prefix = "  • ") +
                "\n\nSee docs/ADR/ADR-003-module-boundaries-and-dependency-rules.md for rules."
            )
        }

        println("✓ Module dependency validation passed")
    }
}

// Helper function
fun hasDependency(sourceModule: String, targetModule: String): Boolean {
    val source = project.findProject(":$sourceModule") ?: return false
    return source.configurations.any { config ->
        config.dependencies.any { dep ->
            dep is ProjectDependency && dep.dependencyProject.path == ":$targetModule"
        }
    }
}
```

**Run validation:**
```bash
./gradlew checkModuleDependencies

***REMOVED*** Add to test task for automatic validation
./gradlew test  ***REMOVED*** Now includes dependency check
```

---

***REMOVED******REMOVED******REMOVED*** Exception Handling

Some dependencies may seem like violations but are intentional. Document these:

**Documented Exceptions:**

1. **`android-platform-adapters` as utility**
   ```gradle
   // Exception: Platform modules can depend on android-platform-adapters
   :android-camera-camerax → :android-platform-adapters  ✅
   :android-ml-mlkit → :android-platform-adapters        ✅
   ```
   **Reason:** Adapters provide shared conversion utilities. Not a layer violation.

2. **Legacy `core-*` wrappers during migration**
   ```gradle
   // Exception: Temporary during migration (remove by Phase 7)
   :androidApp → :core-models → :shared:core-models  ✅ (temporary)
   ```
   **Reason:** Backward compatibility during gradual migration.

**How to Add Exception:**
```kotlin
// build.gradle.kts

val allowedExceptions = mapOf(
    "android-camera-camerax" to listOf("android-platform-adapters"),
    "android-ml-mlkit" to listOf("android-platform-adapters")
)

// Validation checks allowedExceptions before flagging violations
```

---

***REMOVED******REMOVED*** Consequences

***REMOVED******REMOVED******REMOVED*** Positive

- ✅ **Prevents architectural erosion**: Rules stop accidental violations
- ✅ **Fast feedback**: Build fails immediately on bad dependency
- ✅ **Self-documenting**: Dependency graph IS the architecture
- ✅ **Onboarding aid**: New developers see clear structure
- ✅ **Refactoring safety**: Changes that break architecture fail fast
- ✅ **iOS-ready**: Enforces platform isolation needed for KMP

***REMOVED******REMOVED******REMOVED*** Negative

- ⚠️ **Initial setup cost**: Write validation task (~2-3 hours)
- ⚠️ **Occasional friction**: Valid changes might be blocked, need exception
- ⚠️ **Maintenance**: Rules need updates as architecture evolves

***REMOVED******REMOVED******REMOVED*** Risks and Mitigation

**Risk 1: False positives block legitimate work**
- **Mitigation:** Exception mechanism (`allowedExceptions` map)
- **Process:** Violations require ADR update documenting why exception is needed

**Risk 2: Developers disable checks to "move fast"**
- **Mitigation:** Make checks FAST (<1 second). Don't add to critical path.
- **Process:** CI blocks merges if checks disabled

**Risk 3: Rules become outdated as architecture evolves**
- **Mitigation:** Review rules quarterly
- **Process:** Every ADR that changes architecture updates this ADR

---

***REMOVED******REMOVED*** Follow-up Actions

**Phase 1 (Immediate):**
- [x] Document rules in this ADR
- [ ] Implement `checkModuleDependencies` Gradle task
- [ ] Add task to CI pipeline (GitHub Actions)
- [ ] Run task locally and fix any existing violations

**Phase 2-3 (During refactoring):**
- [ ] Run `checkModuleDependencies` after each module creation
- [ ] Update validation rules as new modules added
- [ ] Document exceptions when needed

**Phase 4-7 (Ongoing):**
- [ ] Quarterly review of dependency rules
- [ ] Remove `core-*` wrapper exceptions after migration complete
- [ ] Add visualizer: Generate module dependency graph image for docs

**Documentation:**
- [ ] Add dependency graph diagram to `docs/ARCHITECTURE.md`
- [ ] Create `docs/MODULE_GUIDE.md` - Where to put new code
- [ ] Update `CONTRIBUTING.md` with architecture rules

---

***REMOVED******REMOVED*** Validation Commands

**Check all rules:**
```bash
./gradlew checkModuleDependencies
```

**Check portable modules (no Android imports):**
```bash
./gradlew checkPortableModules
```

**Run full validation suite:**
```bash
./gradlew clean assembleDebug test checkPortableModules checkModuleDependencies
```

**CI Pipeline (GitHub Actions):**
```yaml
- name: Validate Architecture
  run: |
    ./gradlew checkPortableModules
    ./gradlew checkModuleDependencies
```

---

***REMOVED******REMOVED*** References

- [Gradle Dependency Management](https://docs.gradle.org/current/userguide/dependency_management.html)
- [Clean Architecture (Robert C. Martin)](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Modular Android Architecture](https://developer.android.com/topic/modularization)
- Existing validation: `build.gradle.kts` (root) - `checkPortableModules` task
- Related: ADR-002 (Cross-platform Shared Brain) - KMP portability requirements

---

***REMOVED******REMOVED*** Appendix: Module Dependency Graph (Current State)

```
androidApp
├── core-models → shared:core-models
├── core-tracking → shared:core-tracking
├── core-domainpack
├── core-scan
├── core-contracts
├── android-ml-mlkit
├── android-camera-camerax
└── android-platform-adapters

android-camera-camerax
└── (Android SDK only)

android-ml-mlkit
└── (Android SDK only)

android-platform-adapters
└── shared:core-models

shared:core-models
└── (Kotlin stdlib only)

shared:core-tracking
└── shared:core-models

core-domainpack
└── shared:core-models
```

***REMOVED******REMOVED*** Appendix: Module Dependency Graph (Target State - Phase 7)

```
androidApp
├── android-camera-camerax
├── android-ml-mlkit
├── android-platform-adapters
├── shared:core-domain (NEW)
├── shared:core-models
├── shared:core-tracking
├── shared:core-data (NEW)
└── shared:core-config (NEW)

android-camera-camerax
├── android-platform-adapters
└── shared:core-models

android-ml-mlkit
├── android-platform-adapters
└── shared:core-models

android-platform-adapters
└── shared:core-models

shared:core-domain (NEW)
├── shared:core-models
├── shared:core-tracking
└── shared:core-data

shared:core-data (NEW)
└── shared:core-models

shared:core-tracking
└── shared:core-models

shared:core-models
└── (Kotlin stdlib only)

shared:core-config (NEW)
└── (Kotlin stdlib only)

***REMOVED*** REMOVED: core-models, core-tracking (Android wrappers deprecated)
***REMOVED*** MOVED TO SHARED: core-domainpack → shared:core-domain
```
