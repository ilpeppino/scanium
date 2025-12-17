# KMP Core Tracking Module

This document describes the `shared/core-tracking` Kotlin Multiplatform module and the migration plan for moving platform-independent tracking and aggregation logic from the existing Android library `core-tracking` module.

## Overview

The `shared/core-tracking` module contains the core business logic for:
- **Multi-frame object tracking** - Tracking objects across video frames using spatial matching
- **Session-level aggregation** - Deduplicating and merging similar items using similarity scoring
- **Platform-agnostic logging** - Logging interface with platform-specific implementations

This module depends **only** on `shared:core-models` and has no other internal dependencies.

## Module Configuration

**Type**: Kotlin Multiplatform Library
**Plugin**: `kotlin("multiplatform")`
**Package**: `com.scanium.core.tracking`
**Namespace (Android)**: `com.scanium.core.tracking`

**Source Sets**:
- `commonMain` - Shared tracking logic, aggregation, interfaces
- `commonTest` - Shared tests
- `androidMain` - AndroidLogger implementation
- `androidUnitTest` - Android unit tests
- `iosMain` - IOSLogger implementation
- `iosTest` - iOS tests

**Dependencies**:
- `shared:core-models` - For ImageRef, NormalizedRect, ScannedItem, etc.
- Kotlinx Coroutines Core (for async operations)
- Kotlinx Serialization JSON (for data serialization)
- Kotlin Test (for testing)

## Files to Migrate

The following files from `core-tracking/src/main/java/` will be migrated to `shared/core-tracking/src/commonMain/kotlin/`:

### Tracking System

#### `Logger.kt`
**Source**: `core-tracking/src/main/java/com/scanium/app/tracking/Logger.kt`
**Target**: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/Logger.kt`

Platform-agnostic logging interface.

```kotlin
interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String)

    companion object {
        val NONE: Logger  // No-op logger
    }
}
```

**Migration Notes**:
- Already platform-independent ✅
- Interface stays in `commonMain`
- Will use **expect/actual** pattern for platform implementations

**Platform Implementations** (New):
- **androidMain**: `AndroidLogger` wrapping `android.util.Log`
- **iosMain**: `IOSLogger` wrapping `NSLog` or `os_log`

**Migration Plan**:
1. Move interface to `commonMain`
2. Create `expect fun createLogger(): Logger` in `commonMain`
3. Create `actual fun createLogger(): Logger` in `androidMain` returning `AndroidLogger`
4. Create `actual fun createLogger(): Logger` in `iosMain` returning `IOSLogger`

---

#### `ObjectCandidate.kt`
**Source**: `core-tracking/src/main/java/com/scanium/app/tracking/ObjectCandidate.kt`
**Target**: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ObjectCandidate.kt`

Candidate state during multi-frame tracking.

**Migration Notes**:
- Uses `NormalizedRect` from `core-models` ✅
- Already Android-free (no android.* imports)
- Ready for `commonMain`

**Dependencies**:
- `NormalizedRect` (from `shared:core-models`)
- `ItemCategory` (from `shared:core-models`)

---

#### `ObjectTracker.kt`
**Source**: `core-tracking/src/main/java/com/scanium/app/tracking/ObjectTracker.kt`
**Target**: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ObjectTracker.kt`

Multi-frame tracking with spatial matching using IoU (Intersection over Union) and distance metrics.

**Key Features**:
- Tracks objects across frames using `trackingId` (from ML Kit)
- Fallback to spatial matching using `NormalizedRect` IoU + center distance
- Confirms candidates after minimum frame threshold
- Expires stale candidates
- Uses `Logger` for debugging

**Migration Notes**:
- Uses `NormalizedRect` for spatial matching ✅
- Uses `ImageRef` for thumbnails ✅
- Uses `Logger` interface ✅
- Already Android-free
- Ready for `commonMain`

**Dependencies**:
- `NormalizedRect`, `ImageRef`, `ItemCategory` (from `shared:core-models`)
- `Logger` (from this module)
- `ObjectCandidate` (from this module)

---

#### Supporting Files

**TrackerConfig.kt**
- Tunable thresholds for tracking
- Pure Kotlin data class
- Ready for `commonMain`

**DetectionInfo.kt**
- Input to tracker (frame-level detection info)
- Uses `NormalizedRect` and `ImageRef`
- Ready for `commonMain`

---

### Aggregation System

#### `ItemAggregator.kt`
**Source**: `core-tracking/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt`
**Target**: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/aggregation/ItemAggregator.kt`

Session-level similarity-based deduplication and merging.

**Key Features**:
- Merges similar items based on weighted scoring
- Scoring factors: category (40%), label (15%), size (20%), distance (25%)
- Configurable similarity threshold
- Uses `Logger` for debugging

**Migration Notes**:
- Uses `ScannedItem` from `core-models` ✅
- Uses `Logger` interface ✅
- Already Android-free
- Ready for `commonMain`

**Dependencies**:
- `ScannedItem`, `NormalizedRect` (from `shared:core-models`)
- `Logger` (from this module)

---

#### `AggregationPresets.kt`
**Source**: `core-tracking/src/main/java/com/scanium/app/aggregation/AggregationPresets.kt`
**Target**: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/aggregation/AggregationPresets.kt`

Configuration presets for aggregation (REALTIME, CONSERVATIVE, etc.).

**Migration Notes**:
- Pure Kotlin object with config presets
- No dependencies
- Ready for `commonMain`

---

#### `AggregatedItem.kt`
**Source**: `core-tracking/src/main/java/com/scanium/app/aggregation/AggregatedItem.kt`
**Target**: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/aggregation/AggregatedItem.kt`

Merged detection result with confidence and metadata.

**Migration Notes**:
- Uses `ScannedItem` from `core-models`
- Pure Kotlin data class
- Ready for `commonMain`

---

## Tests to Migrate

### Existing Tests

#### `ObjectTrackerNormalizedMatchingTest.kt`
**Source**: `core-tracking/src/test/java/com/scanium/app/tracking/ObjectTrackerNormalizedMatchingTest.kt`
**Target**: `shared/core-tracking/src/commonTest/kotlin/com/scanium/core/tracking/ObjectTrackerTest.kt`

Tests for ObjectTracker spatial matching logic.

**Migration Notes**:
- Currently uses JUnit 4
- Needs migration to `kotlin.test` (platform-agnostic)
- All test logic is platform-independent

**Test Coverage**:
- Spatial matching with `NormalizedRect`
- IoU calculations
- Candidate confirmation
- Expiry logic
- Duplicate handling

---

## Migration Checklist

### Phase 1: Pre-Migration Cleanup ✅ (This PR)
- [x] Create KMP module skeleton
- [x] Define source sets (commonMain, androidMain, iosMain)
- [x] Add minimal Gradle configuration
- [x] Add dependency on `shared:core-models`
- [x] Add placeholder files
- [x] Document migration plan

### Phase 2: Prepare expect/actual for Logger (Next PR)
- [ ] Define `expect fun createLogger(): Logger` in `commonMain`
- [ ] Implement `actual fun createLogger()` in `androidMain` → `AndroidLogger`
- [ ] Implement `actual fun createLogger()` in `iosMain` → `IOSLogger`
- [ ] Test logger on both platforms

### Phase 3: Move Tracking Code (Future PR)
- [ ] Move `Logger.kt` interface → `commonMain`
- [ ] Move `ObjectCandidate.kt` → `commonMain`
- [ ] Move `TrackerConfig.kt` → `commonMain`
- [ ] Move `DetectionInfo.kt` → `commonMain`
- [ ] Move `ObjectTracker.kt` → `commonMain`
- [ ] Verify no android.* imports remain

### Phase 4: Move Aggregation Code (Future PR)
- [ ] Move `ItemAggregator.kt` → `commonMain`
- [ ] Move `AggregationPresets.kt` → `commonMain`
- [ ] Move `AggregatedItem.kt` → `commonMain`
- [ ] Verify no android.* imports remain

### Phase 5: Move Tests (Future PR)
- [ ] Migrate tests to `kotlin.test` framework
- [ ] Move tests → `commonTest`
- [ ] Add platform-specific tests if needed
- [ ] Verify all tests pass on JVM and Android

### Phase 6: Update Dependencies (Future PR)
- [ ] Update `androidApp` to depend on `shared:core-tracking`
- [ ] Remove references to old `core-tracking` Android library
- [ ] Remove old `core-tracking` Android library module

---

## Strict Rules for commonMain

### ✅ ALLOWED
- Kotlin stdlib (`kotlin.*`)
- Kotlin Coroutines (`kotlinx.coroutines.*`)
- Kotlinx Serialization (`kotlinx.serialization.*`)
- `shared:core-models` module
- Pure Kotlin: data classes, interfaces, objects, functions
- `expect/actual` for platform-specific implementations

### ❌ FORBIDDEN
- `android.*` - Any Android SDK classes
- `androidx.*` - Any AndroidX library classes
- `android.util.Log` - Use `Logger` interface with expect/actual
- `java.util.*` except standard collections
- CameraX, ML Kit, Compose - Platform-specific, stays in `androidApp`

### expect/actual Pattern

**Logger Example**:

```kotlin
// commonMain/kotlin/.../Logger.kt
expect fun createLogger(): Logger

// androidMain/kotlin/.../AndroidLogger.kt
actual fun createLogger(): Logger = AndroidLogger()

class AndroidLogger : Logger {
    override fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
    }
    // ...
}

// iosMain/kotlin/.../IOSLogger.kt
actual fun createLogger(): Logger = IOSLogger()

class IOSLogger : Logger {
    override fun d(tag: String, message: String) {
        platform.Foundation.NSLog("%@: %@", tag, message)
    }
    // ...
}
```

---

## Package Structure

Target package structure in `commonMain`:

```
com.scanium.core.tracking/
├── Logger.kt                      # Platform-agnostic interface
├── LoggerFactory.kt               # expect fun createLogger()
├── ObjectCandidate.kt             # Candidate state
├── ObjectTracker.kt               # Multi-frame tracker
├── TrackerConfig.kt               # Config data class
├── DetectionInfo.kt               # Input data
└── aggregation/
    ├── ItemAggregator.kt          # Session-level dedup
    ├── AggregationPresets.kt      # Config presets
    └── AggregatedItem.kt          # Merged result
```

---

## Dependencies

### This Module Depends On
- `shared:core-models` - For ImageRef, NormalizedRect, ScannedItem, ItemCategory
- Kotlin stdlib
- Kotlinx Coroutines Core
- Kotlinx Serialization JSON

### Modules That Will Depend On This
- `androidApp` - Uses ObjectTracker and ItemAggregator
- Future: `iosApp` - Will use same tracking logic

---

## Build Verification

### Local Build (No Android SDK required for commonMain)
```bash
./gradlew :shared:core-tracking:compileKotlinCommonMain
./gradlew :shared:core-tracking:compileKotlinAndroidDebug
```

### Testing
```bash
./gradlew :shared:core-tracking:cleanAllTests :shared:core-tracking:allTests
```

### Dependency Verification
```bash
# Verify depends on shared:core-models only
./gradlew :shared:core-tracking:dependencies
```

### CI Pipeline
- Verify `commonMain` builds without Android SDK
- Run tests on JVM (commonTest)
- Build Android target
- Build iOS targets (future)

---

## Key Algorithms

### ObjectTracker Spatial Matching
1. **IoU (Intersection over Union)**:
   - Measures bounding box overlap (0.0 = no overlap, 1.0 = perfect match)
   - Threshold: configurable (default 0.3)

2. **Center Distance**:
   - Euclidean distance between box centers (normalized coordinates)
   - Threshold: configurable (default 0.15)

3. **Combined Scoring**:
   - Match if: `IoU >= threshold OR centerDistance <= threshold`
   - Prefers tracking ID from ML Kit when available

### ItemAggregator Similarity Scoring
**Weighted Scoring**:
- Category match: 40%
- Label similarity: 15% (Levenshtein distance)
- Size similarity: 20% (bounding box area ratio)
- Spatial distance: 25% (center distance)

**Threshold**: 0.55 (REALTIME preset) - Lower = more permissive

---

## Timeline

1. **This PR**: KMP skeleton only (no code moved)
2. **Next PR**: Implement expect/actual Logger pattern
3. **Future PR**: Move tracking code to `shared/core-tracking`
4. **Future PR**: Move aggregation code
5. **Future PR**: Move tests to `commonTest`
6. **Future PR**: Update dependencies, remove old module

---

## References

- **KMP Structure**: `docs/kmp-migration/KMP_STRUCTURE.md`
- **KMP Core Models**: `docs/kmp-migration/KMP_CORE_MODELS.md`
- **Migration Plan**: `docs/kmp-migration/PLAN.md`
- **Phase 2 Plan**: `docs/kmp-migration/PHASE_2_PLAN.md`
- **Architecture**: `md/architecture/ARCHITECTURE.md`
- **Tracking Deep-Dive**: `md/features/TRACKING_IMPLEMENTATION.md`

---

**Status**: Phase 1 (Skeleton) - In Progress
**Last Updated**: 2025-12-17
**Next**: Implement expect/actual Logger pattern
