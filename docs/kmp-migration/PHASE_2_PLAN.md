# KMP Migration - Phase 2 Plan: Module Conversion

## Overview

**Goal**: Convert `:core-models`, `:core-tracking`, and `:core-scan` from Android library modules to Kotlin Multiplatform (KMP) shared modules with `commonMain`, `androidMain`, and `iosMain` source sets.

**Scope**: This phase focuses exclusively on KMP module conversion and build configuration. Platform-specific adapters (iOS camera/ML) are out of scope and will be addressed in Phase 3.

**Status**: Phase 1 (Module Restructuring & Portable Types) is complete. All prerequisites met.

---

## Prerequisites (Phase 1 Completion Checklist)

All items below must be ✅ before starting Phase 2:

### ✅ Module Structure
- [x] 9-module Gradle structure established
- [x] `:core-models` – Platform-independent data models (Android-free)
- [x] `:core-tracking` – Platform-independent tracking/aggregation (Android-free)
- [x] `:core-domainpack` – Domain Pack system (Android library, ready for KMP)
- [x] `:core-scan`, `:core-contracts` – Placeholder modules
- [x] `:android-platform-adapters` – Conversion layer between Android and portable types

### ✅ Portable Types Implemented
- [x] `ImageRef` – Platform-agnostic image reference (sealed class with `ImageRef.Bytes`)
- [x] `NormalizedRect` – Portable bounding box with 0-1 coordinates
- [x] `Logger` – Platform-agnostic logging interface

### ✅ Core Models Migrated
- [x] `DetectionResult` – Uses `NormalizedRect` (removed legacy `Rect`)
- [x] `RawDetection` – Has both legacy and portable fields (transitional state)
- [x] `ScannedItem` – Uses `ImageRef` and `NormalizedRect` (still has `Uri` for compatibility)
- [x] `ObjectCandidate` – Uses `NormalizedRect` (removed `RectF`)
- [x] `ObjectTracker` – Prefers `NormalizedRect` for spatial matching

### ✅ Platform Adapters Established
- [x] `ImageAdapters.kt` – `Bitmap ↔ ImageRef` conversions
- [x] `RectAdapters.kt` – Placeholder for `Rect/RectF ↔ NormalizedRect`

### ✅ Android-Free Core Modules
- [x] `:core-models` – No Android dependencies (except `Uri` in `ScannedItem`)
- [x] `:core-tracking` – Zero Android imports (uses Logger, ImageRef, NormalizedRect)
- [x] CI builds successfully without Android SDK in core modules

### ✅ Testing & CI
- [x] 175+ tests passing (110 tracking/detection, 61 domain pack, 4+ eBay)
- [x] `./gradlew test` succeeds
- [x] GitHub Actions validates builds on every push
- [x] Android app fully functional with portable types

---

## Scope Boundaries

### ✅ In Scope (Phase 2)
1. **KMP Plugin Configuration**
   - Convert `:core-models` from `com.android.library` to `org.jetbrains.kotlin.multiplatform`
   - Convert `:core-tracking` from `com.android.library` to `org.jetbrains.kotlin.multiplatform`
   - Convert `:core-scan` from `com.android.library` to `org.jetbrains.kotlin.multiplatform`
   - Configure `commonMain`, `androidMain`, `iosMain` source sets

2. **Source Set Migration**
   - Move existing Kotlin files from `src/main/java/` to `src/commonMain/kotlin/`
   - Implement `expect`/`actual` declarations for platform-specific code
   - Update package structure to follow KMP conventions

3. **Dependency Updates**
   - Replace Android-specific dependencies with KMP-compatible alternatives
   - Configure Kotlin Coroutines for multiplatform
   - Add Kotlinx Serialization if needed

4. **Platform Actuals Implementation**
   - `Logger`: `AndroidLogger` (wrapping `android.util.Log`) and `IOSLogger` (wrapping `NSLog`/`os_log`)
   - Remove remaining Android leaks (`Uri` in `ScannedItem`, legacy fields in `RawDetection`)

5. **Build Validation**
   - Ensure `./gradlew assembleDebug` passes after each step
   - Ensure `./gradlew test` passes for all modules
   - Verify CI builds remain green
   - Validate Android app functionality (no regressions)

### ❌ Out of Scope (Deferred to Phase 3)
- iOS app target (`:iosApp`) creation
- iOS platform adapters (`IOSImageAdapters`, `IOSRectAdapters`)
- iOS camera integration (AVFoundation)
- iOS ML integration (Vision/Core ML)
- SwiftUI implementation
- iOS-specific testing infrastructure
- Cross-platform state management (shared ViewModels)
- Publishing KMP artifacts to Maven/CocoaPods

### ⚠️ Boundary Cases
- **`:core-domainpack`**: Currently an Android library. Migration is in scope for Phase 2, but depends on:
  - Removing `LocalDomainPackRepository` Android resource loading (move to platform actuals)
  - JSON parsing with Kotlinx Serialization (multiplatform-compatible)
- **`:core-contracts`**: Currently a placeholder. May remain minimal in Phase 2.

---

## Phase 2 Task Breakdown

### Task 1: Remove Remaining Android Dependencies from `:core-models`

**Objective**: Make `:core-models` 100% Android-free by removing all `android.*` imports.

**Subtasks**:
1. **Remove `Uri` from `ScannedItem`**:
   - Replace `Uri` with platform-agnostic `String` (file path or URL)
   - Update all `ScannedItem` construction sites in `:androidApp`
   - Add adapter function `Uri.toPortableString()` in `:android-platform-adapters`
   - Add adapter function `String.toAndroidUri()` for reverse conversion
   - Update tests to use string-based paths

2. **Remove legacy fields from `RawDetection`**:
   - Delete `boundingBox: Rect?` field (use only `bboxNorm: NormalizedRect?`)
   - Delete `thumbnail: Bitmap?` field (use only `thumbnailRef: ImageRef?`)
   - Update all `RawDetection` construction sites in ML Kit clients
   - Remove fallback logic in `ObjectTracker` (eliminate Rect/Bitmap handling)
   - Update tests to use only normalized types

3. **Verify Android-free status**:
   - Run `./gradlew :core-models:dependencies` – confirm zero `android.*` or `androidx.*`
   - Grep for `import android` in `core-models/src/` – expect zero matches
   - Run `./gradlew :core-models:build` – should succeed without Android SDK

**Success Criteria**:
- ✅ Zero Android imports in `:core-models`
- ✅ All tests passing (`./gradlew :core-models:test`)
- ✅ Android app builds and functions correctly
- ✅ CI build passes

**Estimated Effort**: 1 commit (small, surgical changes)

---

### Task 2: Convert `:core-models` to KMP Module

**Objective**: Migrate `:core-models` from Android library plugin to KMP plugin with `commonMain` source set.

**Subtasks**:
1. **Update `build.gradle.kts`**:
   ```kotlin
   // Before:
   plugins {
       id("com.android.library")
       id("org.jetbrains.kotlin.android")
   }

   // After:
   plugins {
       id("org.jetbrains.kotlin.multiplatform")
       id("com.android.library") // Still needed for Android target
   }

   kotlin {
       androidTarget {
           compilations.all {
               kotlinOptions {
                   jvmTarget = "17"
               }
           }
       }

       iosX64()
       iosArm64()
       iosSimulatorArm64()

       sourceSets {
           val commonMain by getting {
               dependencies {
                   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
               }
           }
           val androidMain by getting {
               dependencies {
                   // Android-specific dependencies (if any)
               }
           }
           val iosMain by creating {
               dependsOn(commonMain)
           }
       }
   }
   ```

2. **Migrate source files**:
   - Move all `.kt` files from `src/main/java/com/scanium/app/` to `src/commonMain/kotlin/com/scanium/app/`
   - Keep package structure unchanged (`com.scanium.app.model`, `com.scanium.app.ml`, etc.)
   - Verify no Android-specific code remains in migrated files

3. **Update dependent modules**:
   - Update `:core-tracking` dependencies to reference KMP `:core-models`
   - Update `:android-platform-adapters` dependencies
   - Update `:androidApp` dependencies

4. **Verify build**:
   - Run `./gradlew :core-models:build` – should compile for all targets
   - Run `./gradlew :core-models:iosX64Test` – iOS tests should pass (if any)
   - Run `./gradlew assembleDebug` – Android app still builds
   - Run `./gradlew test` – all tests pass

**Success Criteria**:
- ✅ `:core-models` builds for Android, iOS x64, iOS ARM64, iOS Simulator ARM64
- ✅ Source files in `commonMain` (no platform-specific code)
- ✅ All dependent modules build successfully
- ✅ Android app functionality unchanged
- ✅ CI build passes

**Estimated Effort**: 1-2 commits (build.gradle changes + source migration)

---

### Task 3: Convert `:core-tracking` to KMP Module with Logger Actuals

**Objective**: Migrate `:core-tracking` to KMP and implement platform-specific `Logger` actuals.

**Subtasks**:
1. **Define `expect` Logger in `commonMain`**:
   - Move `Logger.kt` interface to `src/commonMain/kotlin/com/scanium/app/tracking/Logger.kt`
   - Convert to `expect` interface:
     ```kotlin
     // commonMain/kotlin/com/scanium/app/tracking/Logger.kt
     expect interface Logger {
         fun d(tag: String, message: String)
         fun e(tag: String, message: String, throwable: Throwable? = null)
         fun i(tag: String, message: String)
         fun w(tag: String, message: String)
     }
     ```

2. **Implement `actual` Logger for Android**:
   - Create `src/androidMain/kotlin/com/scanium/app/tracking/Logger.kt`:
     ```kotlin
     actual interface Logger {
         actual fun d(tag: String, message: String)
         actual fun e(tag: String, message: String, throwable: Throwable?)
         actual fun i(tag: String, message: String)
         actual fun w(tag: String, message: String)
     }

     class AndroidLogger : Logger {
         override fun d(tag: String, message: String) {
             android.util.Log.d(tag, message)
         }
         override fun e(tag: String, message: String, throwable: Throwable?) {
             android.util.Log.e(tag, message, throwable)
         }
         override fun i(tag: String, message: String) {
             android.util.Log.i(tag, message)
         }
         override fun w(tag: String, message: String) {
             android.util.Log.w(tag, message)
         }
     }
     ```

3. **Implement `actual` Logger for iOS**:
   - Create `src/iosMain/kotlin/com/scanium/app/tracking/Logger.kt`:
     ```kotlin
     actual interface Logger {
         actual fun d(tag: String, message: String)
         actual fun e(tag: String, message: String, throwable: Throwable?)
         actual fun i(tag: String, message: String)
         actual fun w(tag: String, message: String)
     }

     class IOSLogger : Logger {
         override fun d(tag: String, message: String) {
             platform.Foundation.NSLog("[$tag] $message")
         }
         override fun e(tag: String, message: String, throwable: Throwable?) {
             val error = throwable?.let { ": ${it.message}" } ?: ""
             platform.Foundation.NSLog("[$tag] ERROR: $message$error")
         }
         override fun i(tag: String, message: String) {
             platform.Foundation.NSLog("[$tag] $message")
         }
         override fun w(tag: String, message: String) {
             platform.Foundation.NSLog("[$tag] WARNING: $message")
         }
     }
     ```

4. **Update `build.gradle.kts`** (same pattern as Task 2):
   - Replace Android plugin with KMP plugin
   - Configure `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`
   - Add `commonMain`, `androidMain`, `iosMain` source sets
   - Depend on KMP `:core-models`

5. **Migrate source files**:
   - Move all `.kt` files (except Logger) from `src/main/java/` to `src/commonMain/kotlin/`
   - Keep `ObjectTracker.kt`, `ObjectCandidate.kt`, `ItemAggregator.kt` in `commonMain`
   - Move Logger implementations to platform-specific source sets

6. **Update `:androidApp` usage**:
   - Replace `object : Logger { ... }` with `AndroidLogger()` instances
   - Update `CameraXManager.kt`, `ItemsViewModel.kt` to use `AndroidLogger`

7. **Verify build**:
   - Run `./gradlew :core-tracking:build` – compiles for all targets
   - Run `./gradlew :core-tracking:iosX64Test` – iOS tests pass
   - Run `./gradlew assembleDebug` – Android app builds
   - Run `./gradlew test` – all 110+ tracking tests pass

**Success Criteria**:
- ✅ `:core-tracking` builds for Android + iOS targets
- ✅ `Logger` implemented as `expect`/`actual`
- ✅ `ObjectTracker`, `ItemAggregator` in `commonMain` (Android-free)
- ✅ Android app uses `AndroidLogger` correctly
- ✅ All tests passing
- ✅ CI build passes

**Estimated Effort**: 2-3 commits (build.gradle, Logger actuals, source migration)

---

## Follow-Up Tasks (After First 3)

### Task 4: Convert `:core-scan` to KMP Module
- Currently a placeholder module with minimal code
- Apply same KMP conversion pattern as `:core-models`
- May remain minimal until scan contracts are defined

### Task 5: Convert `:core-domainpack` to KMP Module
- Move JSON parsing to Kotlinx Serialization (multiplatform)
- Extract `LocalDomainPackRepository` platform loading to actuals:
  - Android: Load from `res/raw/home_resale_domain_pack.json`
  - iOS: Load from bundle resources
- Keep `BasicCategoryEngine` in `commonMain`

### Task 6: Validate Full KMP Build
- Run `./gradlew build` – all modules compile for all targets
- Run `./gradlew test` – 175+ tests pass
- Run `./gradlew assembleDebug` – Android app builds
- Run `./gradlew assembleRelease` – Release build succeeds
- Verify CI pipeline remains green

---

## Non-Negotiables

### Build Stability
- **Android must remain green**: `./gradlew assembleDebug` must succeed after EVERY commit
- **Tests must pass**: `./gradlew test` must show zero failures
- **CI must validate**: GitHub Actions must build successfully on every push
- **No regressions**: Android app functionality must remain 100% intact

### Migration Discipline
- **Incremental commits**: Each task is 1-3 small, focused commits
- **Green-to-green**: Never commit broken builds
- **Test after each step**: Run `./gradlew test` before committing
- **Document blockers**: If a step fails, document why before proceeding

### Code Quality
- **No Android leaks in shared code**: Zero `android.*` imports in `commonMain`
- **Platform boundaries respected**: Adapters live in platform-specific modules
- **Minimal `expect`/`actual`**: Use only for truly platform-specific needs (Logger, resource loading)
- **Preserve behavior**: Tracking thresholds, aggregation logic unchanged

---

## Success Metrics

### Phase 2 Complete When:
1. ✅ `:core-models` is a KMP module with `commonMain` source set
2. ✅ `:core-tracking` is a KMP module with `commonMain` source set
3. ✅ `:core-scan` is a KMP module with `commonMain` source set
4. ✅ `Logger` implemented as `expect`/`actual` (Android + iOS)
5. ✅ Zero Android dependencies in `commonMain` code
6. ✅ All 175+ tests passing
7. ✅ Android app builds and functions correctly
8. ✅ CI pipeline validates builds on every push
9. ✅ iOS targets compile successfully (even without iOS app)

### Phase 2 Does NOT Require:
- iOS app implementation
- iOS camera/ML adapters
- Cross-platform state management
- Publishing artifacts
- End-to-end iOS testing

---

## Risk Mitigation

### Risk: Build Configuration Breakage
**Mitigation**:
- Test each module independently (`./gradlew :core-models:build`)
- Validate dependent modules after each change
- Keep Android plugin alongside KMP plugin during transition
- Rollback immediately if `assembleDebug` fails

### Risk: Test Failures After Migration
**Mitigation**:
- Run `./gradlew test` before and after each commit
- Compare test results (expect same 175+ passing)
- Investigate any new failures immediately
- Use golden tests to verify tracking/aggregation behavior unchanged

### Risk: Performance Regression
**Mitigation**:
- Benchmark critical paths (ObjectTracker, ItemAggregator) before/after
- Monitor camera FPS during testing
- Profile memory usage with shared models
- Optimize adapter conversions if needed

### Risk: Unexpected Android Leaks
**Mitigation**:
- Grep for `import android` after each migration
- Run `./gradlew :core-models:dependencies` to verify
- Use Gradle's `apiDump` to validate public API surface
- Review CI build logs for warnings

---

## Timeline & Dependencies

### Prerequisites (Must be ✅)
- All Phase 1 tasks complete (see checklist above)
- CI pipeline validating Android builds
- 175+ tests passing baseline
- Documentation reviewed and approved

### Task Dependencies
```
Task 1 (Remove Android deps)
  ↓
Task 2 (Convert :core-models to KMP)
  ↓
Task 3 (Convert :core-tracking to KMP + Logger actuals)
  ↓
Task 4 (Convert :core-scan to KMP)
  ↓
Task 5 (Convert :core-domainpack to KMP)
  ↓
Task 6 (Validate full KMP build)
```

### Estimated Duration
- **Task 1**: 1 commit, 1-2 hours
- **Task 2**: 2 commits, 2-3 hours
- **Task 3**: 3 commits, 3-4 hours
- **Total (first 3 tasks)**: ~6-9 hours of focused work

---

## References

### Existing Documentation
- `CLAUDE.md` – Project overview, module structure, KMP status
- `docs/kmp-migration/PLAN.md` – Original KMP migration strategy
- `docs/kmp-migration/TARGETS.md` – Top files for migration, leak inventory

### Kotlin Multiplatform Resources
- [Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [KMP Mobile Samples](https://github.com/Kotlin/kmm-production-sample)
- [Expect/Actual Mechanism](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html)

### Gradle Configuration
- [Android Library Plugin](https://developer.android.com/build/publish-library)
- [Kotlin Multiplatform Plugin](https://kotlinlang.org/docs/multiplatform-dsl-reference.html)
- [Source Set Configuration](https://kotlinlang.org/docs/multiplatform-hierarchy.html)

---

## Next Steps

1. **Review this plan** with stakeholders
2. **Confirm Phase 1 completion** (verify all ✅ prerequisites)
3. **Start Task 1**: Remove Android dependencies from `:core-models`
4. **Commit incrementally**: Green-to-green builds only
5. **Document blockers**: Update this plan if new issues arise
6. **Prepare for Phase 3**: iOS app target creation (separate plan)
