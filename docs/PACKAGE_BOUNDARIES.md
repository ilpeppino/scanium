***REMOVED*** Package Boundaries and Module Organization

**Status:** Phase 3 - Modularity Without Build Blockers
**Last Updated:** 2025-12-18
**Goal:** Define clear package boundaries within existing modules to prepare for future Gradle module extraction

---

***REMOVED******REMOVED*** Current State: Single Module with Package Structure

All code currently lives in `:androidApp` module. We organize by **package boundaries** to enforce dependency rules without the overhead of multiple Gradle modules yet.

**Strategy:** Use package naming conventions to simulate module boundaries:
- `com.scanium.app.*` - Presentation layer (UI, ViewModels)
- `com.scanium.platform.*` - Platform-specific scanning (CameraX, ML Kit)
- `com.scanium.domain.*` - Core domain logic (NO Android imports)
- `com.scanium.data.*` - Data layer (repositories, network)
- `com.scanium.integrations.*` - External service integrations
- `com.scanium.config.*` - Configuration layer
- `com.scanium.observability.*` - Logging and metrics

---

***REMOVED******REMOVED*** Package Boundary Map

***REMOVED******REMOVED******REMOVED*** Layer 1: Presentation (com.scanium.app.*)

**Purpose:** UI and user interaction. Can depend on all other layers.

```
com.scanium.app/
├── ui/                         ***REMOVED*** Compose screens and components
│   ├── theme/                  ***REMOVED*** Material 3 theme
│   ├── components/             ***REMOVED*** Reusable Composables
│   └── navigation/             ***REMOVED*** Navigation composables
├── camera/                     ***REMOVED*** Camera screen and overlays
│   ├── CameraScreen.kt
│   ├── CameraViewModel.kt
│   └── DetectionOverlay.kt
├── items/                      ***REMOVED*** Items list screen
│   ├── ItemsListScreen.kt
│   └── ItemsViewModel.kt
├── selling/                    ***REMOVED*** Selling flow screens
│   └── ui/
└── navigation/                 ***REMOVED*** Navigation graph
    └── NavGraph.kt
```

**Dependencies:** ✅ All layers (use-cases, repositories, platform)
**Rules:**
- ❌ NO business logic in Composables
- ❌ NO direct repository/data source calls (use use-cases)
- ✅ Only observe StateFlow/State from ViewModels
- ✅ Send intents/actions to ViewModels

---

***REMOVED******REMOVED******REMOVED*** Layer 2: Platform Scanning (com.scanium.platform.*)

**Purpose:** Android-specific camera and ML Kit integration.

```
com.scanium.platform/
├── camera/                     ***REMOVED*** CameraX integration
│   ├── CameraXManager.kt       ***REMOVED*** Camera lifecycle
│   └── FrameAnalyzer.kt        ***REMOVED*** Image analysis
├── mlkit/                      ***REMOVED*** ML Kit wrappers
│   ├── ObjectDetectorClient.kt
│   ├── BarcodeScannerClient.kt
│   └── DocumentTextRecognitionClient.kt
└── adapters/                   ***REMOVED*** Type converters
    ├── ImageAdapters.kt        ***REMOVED*** Bitmap → ImageRef
    └── RectAdapters.kt         ***REMOVED*** Rect → NormalizedRect
```

**Dependencies:**
- ✅ `com.scanium.domain.*` (for interfaces and models)
- ✅ Android SDK (CameraX, ML Kit)
- ❌ NO UI layer dependencies
- ❌ NO direct data layer calls

**Rules:**
- ✅ Implement domain interfaces (ObjectDetector, etc.)
- ✅ Convert platform types to domain models at boundary
- ❌ NO business logic (tracking, aggregation) here
- ❌ NO category assignment logic (that's domain)

---

***REMOVED******REMOVED******REMOVED*** Layer 3: Core Domain (com.scanium.domain.*)

**Purpose:** Business logic, use-cases, domain models. **NO Android imports allowed.**

```
com.scanium.domain/
├── model/                      ***REMOVED*** Domain models
│   ├── ScannedItem.kt          ***REMOVED*** (May reference shared:core-models)
│   ├── DomainCategoryId.kt
│   ├── ItemAttributes.kt
│   └── PriceEstimate.kt
├── usecase/                    ***REMOVED*** Business logic
│   ├── AggregateDetectionsUseCase.kt
│   ├── ClassifyItemUseCase.kt
│   ├── EstimatePriceUseCase.kt
│   └── ApplyDomainPackMappingUseCase.kt
├── repository/                 ***REMOVED*** Repository interfaces
│   ├── ItemClassifier.kt       ***REMOVED*** Classification contract
│   ├── CategoryEngine.kt       ***REMOVED*** Category mapping contract
│   ├── PriceEstimator.kt       ***REMOVED*** Pricing contract
│   └── DomainPackRepository.kt ***REMOVED*** Category config
└── event/                      ***REMOVED*** Domain events
    └── ScanSessionEvent.kt
```

**Dependencies:**
- ✅ Kotlin stdlib only
- ✅ `shared:core-models`, `shared:core-tracking` (KMP modules)
- ❌ NO Android SDK imports
- ❌ NO platform layer dependencies
- ❌ NO UI framework dependencies

**Rules:**
- ✅ Pure Kotlin code (KMP-ready)
- ✅ Testable without Android framework
- ✅ Define interfaces for data/platform integration
- ❌ NO implementation of platform-specific logic

**Validation:** Run `checkPortableModules`-style check on this package

---

***REMOVED******REMOVED******REMOVED*** Layer 4: Data & Integrations (com.scanium.data.*, com.scanium.integrations.*)

**Purpose:** Repository implementations, network clients, external services.

```
com.scanium.data/
├── repository/                 ***REMOVED*** Repository implementations
│   ├── LocalPricingRepository.kt
│   └── DomainPackRepositoryImpl.kt
└── cache/
    └── ClassificationCache.kt

com.scanium.integrations/
└── vision/                     ***REMOVED*** Cloud classification
    ├── CloudClassifier.kt      ***REMOVED*** Implements ItemClassifier
    ├── GoogleVisionClient.kt   ***REMOVED*** HTTP client
    └── VisionApiMapper.kt      ***REMOVED*** API response → domain model
```

**Dependencies:**
- ✅ `com.scanium.domain.*` (implement interfaces)
- ✅ OkHttp, Ktor, Retrofit (network)
- ✅ Kotlinx Serialization (JSON parsing)
- ❌ NO UI layer dependencies
- ❌ NO platform layer dependencies

**Rules:**
- ✅ Implement domain repository interfaces
- ✅ Handle network errors, retries, caching
- ✅ Map external API models to domain models
- ❌ NO business logic (use-cases are in domain)

---

***REMOVED******REMOVED******REMOVED*** Layer 5: Configuration (com.scanium.config.*)

**Purpose:** App configuration, feature flags, build config.

```
com.scanium.config/
├── AppConfig.kt                ***REMOVED*** Configuration data class
├── FeatureFlags.kt             ***REMOVED*** Feature toggles
├── ApiConfig.kt                ***REMOVED*** Backend endpoints
└── ConfigProvider.kt           ***REMOVED*** Provides config from BuildConfig
```

**Dependencies:**
- ✅ BuildConfig (Android)
- ✅ `com.scanium.domain.*` (for config models)
- ❌ NO business logic
- ❌ NO UI dependencies

**Rules:**
- ✅ Read from BuildConfig, local.properties, environment
- ✅ Provide immutable config objects
- ❌ NO hardcoded secrets (use injection)

---

***REMOVED******REMOVED******REMOVED*** Layer 6: Observability (com.scanium.observability.*)

**Purpose:** Logging, metrics, analytics, crash reporting.

```
com.scanium.observability/
├── AppLogger.kt                ***REMOVED*** Logging interface
├── AndroidLogger.kt            ***REMOVED*** Android Log implementation
├── MetricsCollector.kt         ***REMOVED*** Classification metrics
└── DebugOverlay.kt             ***REMOVED*** Debug UI overlays
```

**Dependencies:**
- ✅ `com.scanium.domain.*` (for event models)
- ✅ Android Log, Firebase Analytics (platform)
- ❌ NO business logic
- ❌ NO data layer dependencies

**Rules:**
- ✅ Log domain events, not implementation details
- ✅ Structured logging (JSON or key-value)
- ❌ NO PII in logs
- ❌ NO sensitive data (tokens, keys)

---

***REMOVED******REMOVED*** Dependency Rules Matrix

| From Layer | Can Depend On | Cannot Depend On |
|------------|---------------|------------------|
| **Presentation (app.*)** | All layers | Nothing (top layer) |
| **Platform (platform.*)** | domain.*, config.*, observability.* | app.*, data.*, integrations.* |
| **Domain (domain.*)** | shared:*, Kotlin stdlib only | app.*, platform.*, data.*, integrations.*, Android SDK |
| **Data (data.*)** | domain.*, config.*, observability.* | app.*, platform.* |
| **Integrations (integrations.*)** | domain.*, config.*, observability.* | app.*, platform.*, data.* |
| **Config (config.*)** | Kotlin stdlib, BuildConfig | All other layers |
| **Observability (observability.*)** | domain.* (event models) | All other layers |

**Key Principle:** Dependencies flow **downward** (top → bottom in table).

---

***REMOVED******REMOVED*** Migration Strategy

***REMOVED******REMOVED******REMOVED*** Phase 3 (Current): Package Structure Within :androidApp

1. Create packages: `domain/`, `data/`, `integrations/`, `config/`, `observability/`
2. Define interfaces in `domain/repository/`
3. Move/create implementations in `data/`, `integrations/`
4. Update existing code to use interfaces
5. Verify: `./gradlew assembleDebug` works

**Validation:**
```bash
***REMOVED*** Check no Android imports in domain package
./gradlew checkPortableModules  ***REMOVED*** (adapt to check domain/ package)

***REMOVED*** Verify build
./gradlew assembleDebug

***REMOVED*** Verify app runs
./gradlew installDebug
```

---

***REMOVED******REMOVED******REMOVED*** Phase 4-5: Extract to Gradle Modules (Future)

Once package boundaries are stable and tested:

```
:androidApp                     (Presentation layer)
├── :core-domain                (Pure Kotlin, KMP-ready)
├── :core-data                  (Android lib, implements domain interfaces)
├── :integrations-vision        (Android lib, cloud classifier)
├── :platform-android           (CameraX + ML Kit)
└── :core-config                (Configuration)
```

**Benefits of delaying Gradle module extraction:**
- ✅ Faster iteration (no multi-module build overhead)
- ✅ Easier refactoring (move files, not modules)
- ✅ No premature optimization
- ✅ Validate boundaries before committing to modules

**When to extract:**
- Domain layer is stable (no Android imports)
- Interfaces proven with multiple implementations
- Code organization benefits outweigh build complexity

---

***REMOVED******REMOVED*** Enforcement Strategy

***REMOVED******REMOVED******REMOVED*** 1. Code Review Checklist

**For changes to `com.scanium.domain.*`:**
- [ ] No Android SDK imports (`android.*`, `androidx.*`)
- [ ] No platform types (Bitmap, Context, etc.)
- [ ] Only depends on Kotlin stdlib or shared KMP modules
- [ ] Has unit tests (no Robolectric needed)

**For changes to `com.scanium.platform.*`:**
- [ ] Implements domain interfaces (if applicable)
- [ ] Converts platform types at boundary (Bitmap → ImageRef)
- [ ] No business logic (delegates to use-cases)

**For changes to `com.scanium.app.*`:**
- [ ] No direct repository calls (uses use-cases)
- [ ] No business logic in Composables
- [ ] StateFlow/State observation only

***REMOVED******REMOVED******REMOVED*** 2. Automated Checks (Future Gradle Task)

```kotlin
// build.gradle.kts
tasks.register("checkDomainPackage") {
    description = "Validates domain package has no Android imports"
    doLast {
        val domainFiles = fileTree("src/main/java/com/scanium/domain") {
            include("**/*.kt")
        }

        domainFiles.forEach { file ->
            file.readLines().forEach { line ->
                if (line.contains("import android.") ||
                    line.contains("import androidx.")) {
                    throw GradleException(
                        "Android import found in domain package: ${file.name}:${line}\n" +
                        "Domain layer must be platform-agnostic."
                    )
                }
            }
        }

        println("✓ Domain package validation passed")
    }
}

tasks.named("check") {
    dependsOn("checkDomainPackage")
}
```

***REMOVED******REMOVED******REMOVED*** 3. Package-Private Visibility

Use package-private (`internal` in Kotlin) to enforce boundaries:

```kotlin
// domain/repository/ItemClassifier.kt
interface ItemClassifier {
    suspend fun classify(item: ScannedItem): ClassificationResult
}

// integrations/vision/CloudClassifier.kt
internal class CloudClassifier : ItemClassifier {  // internal = module-private
    // Implementation hidden from other packages
}

// DI setup exposes interface only
fun provideItemClassifier(): ItemClassifier = CloudClassifier()
```

---

***REMOVED******REMOVED*** Success Criteria

***REMOVED******REMOVED******REMOVED*** Phase 3 Complete When:

- [x] Package structure documented (this file)
- [ ] Domain interfaces defined (ItemClassifier, CategoryEngine, PriceEstimator)
- [ ] At least one implementation per interface
- [ ] Existing code refactored to use interfaces
- [ ] No Android imports in `domain/` package
- [ ] Build works: `./gradlew assembleDebug`
- [ ] App works: scanning, detection, items list functional
- [ ] Tests pass: `./gradlew test`

***REMOVED******REMOVED******REMOVED*** Ready for Phase 4 When:

- Domain package has 90%+ test coverage
- All major features use domain interfaces
- Code review process enforces package rules
- Team comfortable with package boundaries

---

***REMOVED******REMOVED*** Current Package Migration Status

***REMOVED******REMOVED******REMOVED*** ✅ Already Organized Well:
- `app/camera/` - Presentation (camera screen)
- `app/items/` - Presentation (items list)
- `app/selling/` - Presentation (selling flow)
- `app/ml/` - Platform scanning (mostly)

***REMOVED******REMOVED******REMOVED*** 📦 Needs Package Refactoring:
- `app/ml/` → `platform/mlkit/` (ML Kit clients)
- `app/data/` → `data/repository/` (if repository implementations)
- `app/model/` → `domain/model/` (if domain models)

***REMOVED******REMOVED******REMOVED*** 🆕 Needs Creation:
- `domain/usecase/` - New (extract from ViewModels)
- `domain/repository/` - New (interfaces)
- `integrations/vision/` - New (CloudClassifier)
- `config/` - New (ConfigProvider)
- `observability/` - New (AppLogger)

---

***REMOVED******REMOVED*** References

- ADR-002: Cross-platform Shared Brain (KMP strategy)
- ADR-003: Module Boundaries and Dependency Rules
- `docs/PLAN_ARCHITECTURE_REFACTOR.md` - Full refactoring roadmap
