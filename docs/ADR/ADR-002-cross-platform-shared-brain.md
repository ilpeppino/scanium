# ADR-002: Cross-Platform "Shared Brain" Architecture with KMP

**Status:** Proposed
**Date:** 2025-12-18
**Deciders:** Architecture Team
**Context:** Phase 1 - Target Architecture Definition

---

## Context and Problem Statement

Scanium plans to support both Android and iOS. Code duplication between platforms leads to:
1. **Maintenance burden**: Bug fixes and features must be implemented twice
2. **Inconsistent behavior**: Android and iOS versions can diverge over time
3. **Slower development**: New features take 2x effort to ship on both platforms
4. **Testing complexity**: Test coverage must be maintained for two codebases

**Goal:** Maximize code sharing for business logic while keeping platform-specific code isolated.

**Constraints:**
- **Android First**: Must ship working Android APK immediately
- **No iOS Blockers**: Android development can't be blocked by iOS concerns
- **Native UI**: Use Jetpack Compose (Android) and SwiftUI (iOS) - no compromises
- **Build Stability**: Every commit must pass `./gradlew assembleDebug`

---

## Decision Drivers

1. **Code Reuse**: Maximize shared code for business logic (70-80% target)
2. **Android First**: iOS readiness shouldn't slow down Android development
3. **Build Performance**: Fast compilation, no heavy KMP overhead on Android
4. **Type Safety**: Strong compile-time guarantees across platforms
5. **Testability**: Shared logic should be easily testable without platform dependencies
6. **Team Velocity**: Gradual migration, not a risky "big bang" rewrite

---

## Considered Options

### Option 1: Platform-Specific Codebases (Current State)
**Approach:** Separate Android (Kotlin) and iOS (Swift) codebases with no sharing

**Pros:**
- Simple: No cross-platform tooling required
- Native: Full platform API access
- Fast builds: No additional compilation steps

**Cons:**
- ❌ **Code duplication**: ObjectTracker, aggregation, pricing logic duplicated
- ❌ **Inconsistency**: Android/iOS versions can behave differently
- ❌ **Maintenance burden**: Every bug fix needs to be ported
- ❌ **Slow feature delivery**: New features take 2x time

**Verdict:** ❌ **Rejected** - Duplication cost too high. ObjectTracker alone is 800+ lines that would need re-implementation in Swift.

---

### Option 2: Kotlin Multiplatform (KMP) for Shared Business Logic (**CHOSEN**)
**Approach:** Core domain logic (models, use cases, tracking) in KMP common code; platform-specific UI and camera/ML

```
┌─────────────────────────────────────────────────────────┐
│                 Platform-Specific Layer                  │
│  Android: Jetpack Compose + CameraX + ML Kit            │
│  iOS: SwiftUI + AVFoundation + Apple Vision (future)    │
└───────────────────────┬─────────────────────────────────┘
                        │ Adapts to/from platform types
                        ▼
┌─────────────────────────────────────────────────────────┐
│           Shared Business Logic (KMP commonMain)         │
│  • Domain Models (ScannedItem, CategoryId, Attributes)   │
│  • Use Cases (Aggregation, Classification, Pricing)      │
│  • ObjectTracker (platform-neutral tracking math)        │
│  • Repository Interfaces (data contracts)                │
└─────────────────────────────────────────────────────────┘
```

**Pros:**
- ✅ **High code reuse**: 70-80% of business logic shared
- ✅ **Type safety**: Kotlin compiler ensures correctness across platforms
- ✅ **Gradual migration**: Can move code incrementally to shared modules
- ✅ **Native UI**: Platform-specific UI (Compose/SwiftUI) keeps native feel
- ✅ **Existing investment**: `shared:core-models` and `shared:core-tracking` already set up
- ✅ **Testability**: Shared logic tested once, runs on both platforms

**Cons:**
- Slight build time increase (KMP compilation)
- Learning curve for KMP-specific patterns (expect/actual)
- Some limitations on platform APIs (must use interfaces)

**Verdict:** ✅ **CHOSEN** - Best balance of code reuse and platform flexibility. Already partially implemented.

---

### Option 3: Full Cross-Platform Framework (Flutter/React Native)
**Approach:** Use single framework for both UI and business logic

**Pros:**
- Maximum code sharing (95%+)
- Faster initial development

**Cons:**
- ❌ **UI compromises**: Not truly native look/feel
- ❌ **Performance**: Flutter/RN overhead for camera/ML scenarios
- ❌ **Rewrite cost**: Would need to rewrite entire Android app
- ❌ **Team skills**: Requires different skillset (Dart, JavaScript)
- ❌ **Platform limitations**: Hard to access latest platform features

**Verdict:** ❌ **Rejected** - Too disruptive, not aligned with "Android First" principle. Would block current development.

---

## Decision Outcome

**Chosen option: Option 2 - Kotlin Multiplatform for Shared Business Logic**

### Architecture Layers

#### **Layer 1: Platform-Specific (Android/iOS Diverge)**

**Android:**
```kotlin
// androidApp/src/main/java/com/scanium/app/

📱 Presentation Layer
├── camera/
│   ├── CameraScreen.kt           (Jetpack Compose)
│   └── DetectionOverlay.kt       (Canvas drawing)
├── items/
│   └── ItemsListScreen.kt        (Compose LazyGrid)
└── selling/
    └── SellOnEbayScreen.kt       (Compose forms)

🎥 Platform Scanning Layer
├── android-camera-camerax/
│   ├── CameraXManager.kt         (CameraX lifecycle)
│   └── FrameAnalyzer.kt          (ImageAnalysis.Analyzer)
└── android-ml-mlkit/
    ├── MlKitObjectDetector.kt    (ML Kit wrapper)
    └── MlKitBarcodeScanner.kt    (Barcode API)
```

**iOS (Future):**
```swift
// iosApp/

📱 Presentation Layer
├── Camera/
│   ├── CameraView.swift          (SwiftUI)
│   └── DetectionOverlayView.swift (Metal/Core Graphics)
├── Items/
│   └── ItemsListView.swift       (SwiftUI List)
└── Selling/
    └── SellOnEbayView.swift      (SwiftUI forms)

🎥 Platform Scanning Layer
├── Scanning/
│   ├── AVFoundationManager.swift (AVCaptureSession)
│   └── VisionDetector.swift      (Apple Vision Framework)
└── Adapters/
    └── DetectionAdapter.swift    (VNDetectedObject → RawDetection)
```

---

#### **Layer 2: Shared Business Logic (KMP commonMain)**

```kotlin
// shared:core-domain/src/commonMain/kotlin/

🧠 Domain Models
├── model/
│   ├── ScannedItem.kt            // Platform-neutral item representation
│   ├── DomainCategoryId.kt       // Fine-grained category ID
│   ├── ItemAttributes.kt         // Extracted attributes (color, brand, etc.)
│   └── PriceEstimate.kt          // Price range with confidence

🔧 Use Cases (Business Logic)
├── usecase/
│   ├── AggregateDetectionsUseCase.kt     // De-duplication + stability
│   ├── ClassifyStableItemUseCase.kt      // Cloud classification orchestration
│   ├── ApplyDomainPackMappingUseCase.kt  // Category → UI display
│   └── EstimatePriceUseCase.kt           // Pricing logic

📊 Repository Interfaces
└── repository/
    ├── ClassificationRepository.kt    // Cloud classification contract
    ├── DomainPackRepository.kt        // Category config contract
    └── PricingRepository.kt           // Pricing contract
```

**Supporting Shared Modules:**
```
shared:core-models/      (EXISTING)
├── ImageRef.kt          // Platform-neutral image reference
├── NormalizedRect.kt    // Normalized bounding box (0-1 coords)
├── ItemCategory.kt      // Category enum
└── RawDetection.kt      // ML detection output

shared:core-tracking/    (EXISTING)
├── ObjectTracker.kt     // Multi-frame tracking logic
├── ObjectCandidate.kt   // Tracking candidate state
└── TrackerConfig.kt     // Tracking configuration

shared:core-data/        (NEW)
├── repository/
│   └── CloudClassificationRepository.kt
└── mapper/
    └── GoogleVisionMapper.kt
```

---

### KMP Migration Strategy

**Phase 1: Already Complete ✅**
- `shared:core-models` and `shared:core-tracking` set up with KMP structure
- Type aliases in `core-models` and `core-tracking` Android wrappers
- Build guards prevent Android types in shared modules

**Phase 2: Use Case Extraction (Incremental)**
```
Current:                           Target:
┌────────────────────────┐        ┌────────────────────────┐
│   ItemsViewModel       │        │   ItemsViewModel       │
│  (Android-specific)    │        │  (Android-specific)    │
│                        │        │                        │
│  fun addItem() {       │        │  fun addItem() {       │
│    // aggregation      │   →    │    useCase.aggregate() │
│    // classification   │        │    useCase.classify()  │
│    // pricing          │        │    useCase.estimate()  │
│  }                     │        │  }                     │
└────────────────────────┘        └────────────────────────┘
                                           │
                                           ▼
                                  ┌────────────────────────┐
                                  │  shared:core-domain    │
                                  │  (KMP commonMain)      │
                                  │                        │
                                  │  AggregateUseCase      │
                                  │  ClassifyUseCase       │
                                  │  EstimatePriceUseCase  │
                                  └────────────────────────┘
```

**Steps:**
1. Create `shared:core-domain` module
2. Extract use case interfaces (start with smallest: `EstimatePriceUseCase`)
3. Move implementation logic from ViewModels to use cases
4. ViewModels become thin orchestrators calling use cases
5. Add tests for shared use cases (run on JVM)
6. Repeat for each use case

**Key Benefit:** Android continues working throughout migration. No "big bang" rewrite.

---

### Platform Interface Boundaries

**Problem:** How do platform-specific types cross into shared code?

**Solution:** Adapter pattern at module boundaries

**Example: Image Handling**

```kotlin
// ❌ BAD: Platform type in shared code
// shared:core-domain/ClassifyUseCase.kt
suspend fun classify(bitmap: android.graphics.Bitmap) { ... }  // Breaks iOS!

// ✅ GOOD: Platform-neutral type
// shared:core-models/ImageRef.kt
data class ImageRef(
    val jpegBytes: ByteArray,
    val width: Int,
    val height: Int
)

// Android adapter
// android-platform-adapters/ImageAdapters.kt
fun Bitmap.toImageRef(): ImageRef {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 85, stream)
    return ImageRef(stream.toByteArray(), width, height)
}

// iOS adapter (future)
// iosApp/Adapters/ImageRefAdapter.swift
extension UIImage {
    func toImageRef() -> ImageRef {
        let jpegData = self.jpegData(compressionQuality: 0.85)!
        return ImageRef(jpegBytes: jpegData, width: size.width, height: size.height)
    }
}
```

**Rule:** Shared modules can ONLY use:
- Kotlin stdlib types (String, Int, ByteArray, List, etc.)
- Custom KMP data classes (ImageRef, NormalizedRect, etc.)
- KMP-compatible libraries (kotlinx.coroutines, kotlinx.serialization)

**Enforcement:** `checkPortableModules` Gradle task fails build if Android/iOS types detected.

---

### Dependency Injection Strategy

**Current:** Manual constructor injection (no Hilt/Dagger)

**Target (KMP-compatible):**

```kotlin
// shared:core-domain/di/DomainModule.kt

interface DomainModule {
    fun provideAggregateUseCase(
        tracker: ObjectTracker,
        config: TrackerConfig
    ): AggregateDetectionsUseCase

    fun provideClassifyUseCase(
        repository: CloudClassificationRepository,
        logger: AppLogger
    ): ClassifyStableItemUseCase

    // ... other use cases
}

// Android implementation
// androidApp/src/main/java/com/scanium/app/di/

class AndroidDomainModule(
    private val context: Context,
    private val config: AppConfig
) : DomainModule {
    override fun provideAggregateUseCase(...): AggregateDetectionsUseCase {
        return AggregateDetectionsUseCase(...)
    }
}

// iOS implementation (future)
// iosApp/DI/IOSDomainModule.swift

class IOSDomainModule: DomainModule {
    func provideAggregateUseCase(...) -> AggregateDetectionsUseCase {
        return AggregateDetectionsUseCase(...)
    }
}
```

**Rationale:**
- Keep DI simple (no Hilt on Android, no heavy KMP DI framework)
- Use factory interfaces for cross-platform compatibility
- Each platform provides its own module implementation

---

### Testing Strategy

**Shared Code Tests (Run on JVM + Native)**

```kotlin
// shared:core-domain/src/commonTest/kotlin/

class AggregateDetectionsUseCaseTest {
    @Test
    fun `should aggregate similar detections into single item`() {
        // Arrange
        val tracker = ObjectTracker(config = TrackerConfig(...))
        val useCase = AggregateDetectionsUseCase(tracker)
        val detections = listOf(
            RawDetection(trackingId = "1", boundingBox = ...),
            RawDetection(trackingId = "1", boundingBox = ...)
        )

        // Act
        val result = useCase.aggregate(detections)

        // Assert
        assertEquals(1, result.size)
    }
}
```

**Run tests:**
```bash
# JVM tests (Android-compatible)
./gradlew shared:core-domain:testDebugUnitTest

# iOS tests (future)
./gradlew shared:core-domain:iosSimulatorArm64Test
```

**Platform-Specific Tests:**
```kotlin
// androidApp/src/test/java/

class ItemsViewModelTest {
    @Test
    fun `should call aggregate use case when item added`() {
        // Test Android ViewModel integration with shared use case
    }
}
```

---

## Consequences

### Positive

- ✅ **70-80% code reuse**: ObjectTracker, use cases, domain models shared
- ✅ **Consistent behavior**: Same business logic on Android and iOS
- ✅ **Faster iOS development**: When iOS starts, most logic already written
- ✅ **Single source of truth**: Bug fixes in shared code benefit both platforms
- ✅ **Better testability**: Test shared logic once on JVM (fast)
- ✅ **Type safety**: Kotlin compiler prevents divergence
- ✅ **Gradual migration**: No risky rewrites, incremental extraction

### Negative

- ⚠️ **Learning curve**: Team must learn KMP patterns (expect/actual, platform adapters)
- ⚠️ **Build time**: Slight increase for KMP compilation (~5-10%)
- ⚠️ **Abstraction cost**: Must design platform-agnostic interfaces
- ⚠️ **Debugging**: Slightly harder to debug across platform boundaries

### Risks and Mitigation

**Risk 1: Over-abstraction slows Android development**
- **Mitigation:** Start with minimal shared code (models, tracking). Don't force-share UI logic.
- **Rule:** If shared abstraction takes >2x time vs platform-specific, keep it platform-specific.

**Risk 2: iOS team can't work with Kotlin**
- **Mitigation:** Provide clear KMP → Swift interop examples
- **Documentation:** Add `docs/KMP_IOS_GUIDE.md` with usage patterns
- **Training:** KMP workshop for iOS team before iOS development starts

**Risk 3: Breaking changes in shared code break Android**
- **Mitigation:** Comprehensive tests for shared modules (171+ tests already)
- **CI:** Block PRs if shared module tests fail
- **Versioning:** Use semantic versioning for shared modules

---

## Follow-up Actions

**Phase 2-3 (Immediate):**
- [ ] Create `shared:core-domain` module with KMP structure
- [ ] Extract `EstimatePriceUseCase` (simplest, no external deps)
- [ ] Extract `AggregateDetectionsUseCase` (uses existing ObjectTracker)
- [ ] Add tests for both use cases
- [ ] Update `ItemsViewModel` to call use cases instead of inline logic

**Phase 4-5 (Cloud classification):**
- [ ] Create `shared:core-data` module
- [ ] Define `CloudClassificationRepository` interface in shared
- [ ] Implement `GoogleVisionClassifierAndroid` in androidApp
- [ ] Extract `ClassifyStableItemUseCase`

**Phase 6-7 (iOS preparation):**
- [ ] Add iOS source sets to all shared modules
- [ ] Create `iosApp/` basic structure (SwiftUI shell)
- [ ] Test KMP → Swift interop with simple use case
- [ ] Document KMP patterns for iOS team

**Documentation:**
- [ ] Write `docs/KMP_GUIDE.md` - Developer guide for working with shared modules
- [ ] Write `docs/PLATFORM_ADAPTERS.md` - How to convert platform types
- [ ] Update `docs/TESTING.md` - Testing strategy for shared code

---

## References

- [Kotlin Multiplatform Official Docs](https://kotlinlang.org/docs/multiplatform.html)
- [KMP for Mobile Guide](https://kotlinlang.org/docs/multiplatform-mobile-getting-started.html)
- [Expect/Actual Mechanism](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html)
- [KMP Samples Repository](https://github.com/Kotlin/kmm-production-sample)
- Existing shared modules: `shared:core-models`, `shared:core-tracking`

---

## Success Criteria

**Immediate (Android):**
- ✅ `./gradlew assembleDebug` still works after every change
- ✅ All 171+ tests continue to pass
- ✅ No Android-specific types in `shared:*` modules

**Mid-term (KMP readiness):**
- ✅ 70% of business logic in shared modules
- ✅ Shared modules compile for both Android + iOS targets
- ✅ CI runs shared module tests on both platforms

**Long-term (iOS launch):**
- ✅ iOS app shares 70-80% of codebase with Android
- ✅ Feature parity achieved in 50% less time than rewrite
- ✅ Bug fixes in shared code benefit both platforms
