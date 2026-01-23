> Archived on 2025-12-20: superseded by docs/INDEX.md.

# Unit Test Refactoring Plan - KMP Migration

## Scanium Project - Test Suite Alignment

**Status**: Draft
**Created**: 2025-12-18
**Branch**: `claude/plan-kmp-test-refactor-EugFw`
**Priority**: Critical

---

## Executive Summary

The KMP (Kotlin Multiplatform) migration has moved core business logic from Android-only modules (
`com.scanium.app.*`) to shared KMP modules (`com.scanium.core.*`). This has created a critical
disconnect between the test suite and the refactored codebase:

- **32 test files** across the project
- **~80+ individual test cases** in androidApp alone
- **Mixed import patterns**: Some tests use old imports, some use new KMP imports
- **Dual module system**: Legacy and KMP modules coexist, causing conflicts
- **Test failures**: Tests importing from legacy packages that have been superseded by KMP
  implementations

**Goal**: Systematically migrate all unit tests to align with the KMP architecture, ensure 100% test
coverage for shared business logic, and eliminate the legacy test duplication.

---

## Current State Analysis

### Test Distribution

| Location                           | Files | Framework             | Status        | Issue                  |
|------------------------------------|-------|-----------------------|---------------|------------------------|
| `shared/core-models/commonTest/`   | 2     | Kotlin Test           | ✅ Working     | Minimal coverage       |
| `shared/core-tracking/commonTest/` | 1     | Kotlin Test           | ✅ Working     | Golden vectors only    |
| `androidApp/src/test/`             | 20    | JUnit 4 + Robolectric | ❌ Failing     | Legacy imports         |
| `core-*/src/test/`                 | 6     | JUnit 4               | ⚠️ Deprecated | Duplicate legacy tests |
| `androidApp/src/androidTest/`      | 3     | Espresso              | ⚠️ Unknown    | UI tests               |

### Import Conflicts Detected

**Tests with LEGACY imports (need migration)**:

```kotlin
// OLD (broken after KMP migration)
import com.scanium.app.ml.ItemCategory
import com.scanium.app.tracking.ObjectTracker
import com.scanium.app.aggregation.ItemAggregator
import com.scanium.app.items.ScannedItem
import com.scanium.app.model.NormalizedRect
```

**Tests with KMP imports (correct pattern)**:

```kotlin
// NEW (KMP shared modules)
import com.scanium.core.models.ml.ItemCategory
import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.tracking.ObjectTracker
import com.scanium.core.tracking.ItemAggregator
```

**Problem**: 90% of androidApp tests still use legacy imports and will fail to compile or run
against the migrated codebase.

---

## Root Causes

### 1. **Package Structure Change**

- **Before**: All business logic in `com.scanium.app.*` (Android-only)
- **After**: Core logic moved to `com.scanium.core.*` (KMP shared)
- **Impact**: All test imports are now invalid

### 2. **Dual Module System**

- Both `/core-tracking` (legacy) and `/shared/core-tracking` (KMP) exist
- Gradle dependencies may resolve to wrong module
- Tests may accidentally compile against deprecated modules

### 3. **Insufficient KMP Test Coverage**

- Only 3 tests in `commonTest` for shared modules
- Complex logic (ObjectTracker, ItemAggregator) has ~30 tests in androidApp but only 1-2 in
  commonTest
- Risk of losing test coverage during migration

### 4. **Platform-Specific Test Dependencies**

- Tests use `RectF`, `Bitmap` (Android-specific) via Robolectric
- KMP tests must use portable types (`NormalizedRect`, `ImageRef`)
- Requires test helper migration

---

## Migration Strategy

### Guiding Principles

1. **Zero Regression**: Maintain or improve test coverage during migration
2. **KMP First**: Maximize tests in `commonTest` for cross-platform validation
3. **Platform When Necessary**: Only keep Android-specific tests in `androidUnitTest`/`androidTest`
4. **Clean Slate**: Remove legacy test files after successful migration
5. **Incremental**: Work module-by-module, verify at each step

---

## Phase Breakdown

### **PHASE 1: Audit & Categorization** ✋ (Start Here)

**Duration**: 1-2 hours
**Risk**: Low

#### Objectives

- Create comprehensive inventory of all test files
- Categorize each test as: KMP-portable, Android-specific, or UI-only
- Identify which tests cover migrated vs. legacy code
- Document dependencies between tests

#### Tasks

- [ ] **1.1** Run current test suite to capture baseline failures
  ```bash
  ./gradlew test --continue > test_baseline.log 2>&1
  ```
- [ ] **1.2** Create test inventory spreadsheet with columns:
    - File path
    - Test count
    - Imports used (legacy vs. KMP)
    - Code under test (which module)
    - Portability (Common/Android/UI)
    - Migration action (Move/Rewrite/Keep/Delete)
- [ ] **1.3** Map each legacy test to its KMP equivalent target location
    - Example: `androidApp/.../ObjectTrackerTest.kt` → `shared/core-tracking/commonTest/`
- [ ] **1.4** Identify tests requiring Robolectric (RectF, Bitmap) - these need test helpers

#### Deliverables

- `docs/test-inventory.csv` - Full test audit
- `docs/test-migration-mapping.md` - Source → Target mapping
- `test_baseline.log` - Current failure log for regression detection

#### Success Criteria

- 100% of test files categorized
- Clear migration path identified for each file
- Baseline failure count documented

---

### **PHASE 2: KMP Test Infrastructure** 🛠️

**Duration**: 2-3 hours
**Risk**: Medium

#### Objectives

- Set up robust KMP testing infrastructure
- Create portable test helpers to replace Android-specific utilities
- Establish test conventions and patterns for commonTest

#### Tasks

- [ ] **2.1** Create shared test utilities module structure:
  ```
  shared/test-utils/
  ├── src/commonMain/kotlin/com/scanium/test/
  │   ├── Builders.kt          # Factory functions for test data
  │   ├── Matchers.kt          # Custom assertions
  │   └── Fixtures.kt          # Reusable test fixtures
  ├── src/commonTest/kotlin/   # Self-tests for test utils
  └── build.gradle.kts
  ```

- [ ] **2.2** Migrate test helper functions from Android to KMP:
    - `toNormalizedRect()` → Pure Kotlin version without RectF
    - `createTestItem()` → Builder pattern for ScannedItem
    - `createTestDetection()` → Builder for DetectionInfo
    - Test image creation (replace Bitmap with ImageRef.Bytes)

- [ ] **2.3** Configure test source sets in all shared modules:
  ```kotlin
  // Ensure each shared module has:
  kotlin {
    sourceSets {
      val commonTest by getting {
        dependencies {
          implementation(kotlin("test"))
          implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
          implementation(project(":shared:test-utils"))
        }
      }
      val androidUnitTest by getting {
        dependencies {
          implementation(kotlin("test-junit"))
          implementation("org.robolectric:robolectric:4.11.1") // Only if needed
        }
      }
    }
  }
  ```

- [ ] **2.4** Create example migration guide document:
    - Before/after code snippets
    - Common pitfalls (RectF usage, etc.)
    - How to run KMP tests locally

- [ ] **2.5** Set up CI validation for KMP tests:
  ```yaml
  # .github/workflows/test-kmp.yml
  - name: Run KMP tests
    run: |
      ./gradlew :shared:core-models:test
      ./gradlew :shared:core-tracking:test
  ```

#### Deliverables

- `shared/test-utils/` module with portable test helpers
- `docs/kmp-testing-guide.md` - Migration patterns and conventions
- Updated CI workflow for KMP test validation

#### Success Criteria

- All test helper functions available in pure Kotlin
- Example test migrated successfully to commonTest
- CI runs KMP tests on every commit

---

### **PHASE 3: Core Models Tests Migration** 📦

**Duration**: 3-4 hours
**Risk**: Low

#### Objectives

- Migrate all tests for data models to `shared/core-models/commonTest/`
- Achieve comprehensive coverage for portable types
- Remove legacy model tests from androidApp

#### Current Coverage

- **KMP**: 2 files, 10 tests (NormalizedRect, ImageRef)
- **Legacy Android**: 6 files scattered across androidApp and core-models

#### Tasks

- [ ] **3.1** Migrate `NormalizedRectTest.kt`:
    - Source: `androidApp/src/test/.../model/NormalizedRectTest.kt`
    - Target: `shared/core-models/commonTest/.../geometry/`
    - **Action**: Move and update imports, remove RectF dependencies

- [ ] **3.2** Migrate `ImageRefTest.kt`:
    - Consolidate tests from both androidApp and core-models
    - Add coverage for serialization (Kotlinx Serialization)
    - Test validation logic for invalid MIME types

- [ ] **3.3** Migrate `ScannedItemTest.kt`:
    - Source: `androidApp/src/test/.../items/ScannedItemTest.kt`
    - Target: `shared/core-models/commonTest/.../items/`
    - Add tests for new KMP serialization

- [ ] **3.4** Add comprehensive `ItemCategoryTest.kt` in commonTest:
    - Enum value validation
    - Serialization round-trip tests
    - Default values and edge cases

- [ ] **3.5** Create `DetectionResultTest.kt` in commonTest:
    - Migrate from androidApp
    - Test all detection result types
    - Validation logic coverage

- [ ] **3.6** Add `RawDetectionTest.kt`:
    - Test bounding box normalization
    - Label parsing and confidence handling

- [ ] **3.7** Run model tests to verify 100% pass rate:
  ```bash
  ./gradlew :shared:core-models:allTests
  ```

- [ ] **3.8** Delete legacy test files:
    - `androidApp/src/test/.../model/NormalizedRectTest.kt`
    - `androidApp/src/test/.../model/ImageRefTest.kt`
    - `core-models/src/test/.../ImageRefTest.kt`
    - `core-models/src/test/.../NormalizedRectTest.kt`

#### Deliverables

- 10+ test files in `shared/core-models/commonTest/`
- 60+ tests covering all model types
- Zero legacy model tests remaining

#### Success Criteria

- `./gradlew :shared:core-models:test` passes 100%
- Code coverage ≥ 85% for core-models module
- All legacy model tests deleted

---

### **PHASE 4: Core Tracking Tests Migration** 🎯

**Duration**: 4-6 hours
**Risk**: High (complex business logic)

#### Objectives

- Migrate ObjectTracker, ItemAggregator, and tracking pipeline tests
- Preserve all existing test scenarios (~40+ tests)
- Add KMP-specific coverage (iOS compatibility)

#### Current Coverage

- **KMP**: 1 file, 2 golden vector tests
- **Legacy**: 3 files, ~40 tests (ObjectTracker, ItemAggregator, integration)

#### Tasks

- [ ] **4.1** Expand `ObjectTrackerTest.kt` in commonTest:
    - Source: `androidApp/src/test/.../tracking/ObjectTrackerTest.kt` (20+ tests)
    - Target: `shared/core-tracking/commonTest/.../tracking/`
    - **Critical tests to migrate**:
        - Candidate creation and confirmation thresholds
        - Spatial matching without trackingId
        - Multi-frame tracking with ID stability
        - Expiry logic after frame gaps
        - Reset behavior
        - Confidence and box area filtering
        - Category updates on higher confidence
    - Replace `RectF` with direct `NormalizedRect` construction
    - Remove Robolectric dependency

- [ ] **4.2** Create `ItemAggregatorTest.kt` in commonTest:
    - Source: `androidApp/src/test/.../aggregation/ItemAggregatorTest.kt`
    - Target: `shared/core-tracking/commonTest/.../tracking/`
    - **Critical tests**:
        - Similar detections merge correctly
        - Distinct detections remain separate
        - Similarity scoring algorithm
        - Threshold configuration (AggregationPresets)
        - Merge count tracking
        - Source detection ID tracking

- [ ] **4.3** Create `ObjectCandidateTest.kt`:
    - Migrate from androidApp
    - Test candidate state transitions
    - Frame gap handling

- [ ] **4.4** Create `DetectionInfoTest.kt`:
    - Test detection info creation
    - Validation of normalized coordinates
    - Thumbnail handling

- [ ] **4.5** Add integration test in commonTest:
    - Migrate `TrackingPipelineIntegrationTest.kt`
    - Test full pipeline: Detection → ObjectTracker → ItemAggregator
    - Multi-session scenarios

- [ ] **4.6** Add stress tests:
    - High-frequency detection streams
    - Large numbers of simultaneous objects (50+)
    - Long-running sessions (1000+ frames)

- [ ] **4.7** Run tracking tests:
  ```bash
  ./gradlew :shared:core-tracking:allTests
  ```

- [ ] **4.8** Delete legacy tracking tests:
    - `androidApp/src/test/.../tracking/ObjectTrackerTest.kt`
    - `androidApp/src/test/.../aggregation/ItemAggregatorTest.kt`
    - `androidApp/src/test/.../tracking/ObjectCandidateTest.kt`
    - `androidApp/src/test/.../tracking/TrackingPipelineIntegrationTest.kt`
    - `core-tracking/src/test/.../ObjectTrackerNormalizedMatchingTest.kt`

#### Deliverables

- 6+ test files in `shared/core-tracking/commonTest/`
- 50+ tests covering all tracking scenarios
- Integration tests validating end-to-end behavior

#### Success Criteria

- `./gradlew :shared:core-tracking:test` passes 100%
- Code coverage ≥ 80% for core-tracking module
- All critical business logic paths covered
- Performance benchmarks documented

---

### **PHASE 5: Android-Specific Test Refactoring** 🤖

**Duration**: 3-4 hours
**Risk**: Medium

#### Objectives

- Update Android-specific tests to use KMP imports
- Keep platform-specific tests in androidApp (ViewModel, Camera, ML Kit)
- Remove duplication with KMP tests

#### Current Coverage

- **androidApp tests**: 20 files, many testing already-migrated logic
- **Need to keep**: ViewModel, Camera, ML Kit adapter, Platform converters

#### Tasks

- [ ] **5.1** Update import statements in androidApp tests:
    - Script to replace all legacy imports with KMP equivalents:
      ```bash
      # Example sed script
      find androidApp/src/test -name "*.kt" -exec sed -i \
        's/import com.scanium.app.ml.ItemCategory/import com.scanium.core.models.ml.ItemCategory/g' {} \;
      ```
    - Manual verification for complex cases

- [ ] **5.2** Refactor **ItemsViewModelTest.kt** (keep in androidApp):
    - Update imports to KMP modules
    - Keep only ViewModel-specific tests (StateFlow, coroutines, repository integration)
    - Remove tests that duplicate KMP tests (aggregation logic → already in Phase 4)
    - Reduce from 40+ to ~15 focused ViewModel tests

- [ ] **5.3** Refactor **ItemsViewModelAggregationTest.kt**:
    - Split: Move aggregation logic tests to KMP
    - Keep: Only ViewModel's interaction with aggregator

- [ ] **5.4** Update **DetectionOverlayTransformTest.kt**:
    - Android-specific (uses Canvas, View coordinates)
    - Update imports only, keep in androidApp

- [ ] **5.5** Update **CameraX integration tests**:
    - `DocumentScanningIntegrationTest.kt` - Android-specific, update imports

- [ ] **5.6** Update **ML Kit wrapper tests**:
    - Tests in androidApp for ML Kit adapters
    - Update to use KMP models for input/output

- [ ] **5.7** Keep **Platform adapter tests** (android-platform-adapters):
    - `RectF ↔ NormalizedRect` conversion tests
    - `Bitmap ↔ ImageRef` conversion tests
    - These are inherently Android-specific

- [ ] **5.8** Update **Domain tests**:
    - `BasicCategoryEngineTest.kt`
    - `DomainPackProviderTest.kt`
    - `CategoryMapperTest.kt`
    - Update imports to use KMP ItemCategory

- [ ] **5.9** Update **Selling/Listing tests**:
    - `ListingDraftMapperTest.kt`
    - `EbayMarketplaceServiceTest.kt`
    - Update to use KMP models

- [ ] **5.10** Run androidApp test suite:
  ```bash
  ./gradlew :androidApp:testDebugUnitTest
  ```

#### Deliverables

- All androidApp tests using KMP imports
- Reduced test count (remove duplicates)
- Clear separation: Business logic (KMP) vs. Android integration (androidApp)

#### Success Criteria

- `./gradlew :androidApp:test` passes 100%
- No imports from legacy `com.scanium.app.tracking`, `com.scanium.app.aggregation`, etc.
- Test execution time reduced (fewer duplicate tests)

---

### **PHASE 6: Legacy Module Cleanup** 🧹

**Duration**: 2-3 hours
**Risk**: Low

#### Objectives

- Remove deprecated legacy test modules
- Clean up obsolete test files
- Update Gradle configuration to prevent accidental legacy usage

#### Tasks

- [ ] **6.1** Delete legacy test directories:
  ```bash
  rm -rf core-models/src/test/
  rm -rf core-tracking/src/test/
  rm -rf core-scan/src/test/
  rm -rf core-contracts/src/test/
  ```

- [ ] **6.2** Remove legacy modules entirely (if fully migrated):
    - Evaluate if `/core-models`, `/core-tracking` can be deleted
    - Check if androidApp still depends on them
    - If yes → create Phase 7 for app-level refactoring

- [ ] **6.3** Update `settings.gradle.kts`:
    - Remove legacy module includes if deleted
    - Comment out if deprecated but kept temporarily:
      ```kotlin
      // include(":core-tracking") // DEPRECATED: Use :shared:core-tracking
      ```

- [ ] **6.4** Add Gradle validation task:
  ```kotlin
  // In root build.gradle.kts
  tasks.register("checkNoLegacyImports") {
    doLast {
      val legacyImports = fileTree("androidApp/src") {
        include("**/*.kt")
      }.filter { file ->
        file.readText().contains("import com.scanium.app.tracking.ObjectTracker")
          || file.readText().contains("import com.scanium.app.aggregation")
      }
      if (legacyImports.isNotEmpty()) {
        throw GradleException("Found legacy imports: ${legacyImports.files}")
      }
    }
  }
  ```

- [ ] **6.5** Run full test suite:
  ```bash
  ./gradlew clean test --continue
  ```

- [ ] **6.6** Verify no tests reference deleted modules

#### Deliverables

- Legacy test directories deleted
- Gradle validation preventing legacy imports
- Clean test suite with no obsolete files

#### Success Criteria

- Zero files in deleted directories
- `./gradlew test` passes for all modules
- CI pipeline validates no legacy imports

---

### **PHASE 7: Instrumented Tests Alignment** 📱

**Duration**: 2-3 hours
**Risk**: Low

#### Objectives

- Update instrumented (UI) tests to use KMP imports
- Ensure Compose UI tests work with refactored models
- Validate end-to-end flows

#### Current Coverage

- 3 instrumented test files in `androidApp/src/androidTest/`

#### Tasks

- [ ] **7.1** Update `ItemsViewModelInstrumentedTest.kt`:
    - Change imports to KMP modules
    - Verify on-device test execution

- [ ] **7.2** Update `DetectionOverlayTest.kt`:
    - Update model imports
    - Test with real Android Canvas

- [ ] **7.3** Update `ModeSwitcherTest.kt`:
    - Compose UI test for ScanMode switching
    - Update to use KMP ScanMode enum

- [ ] **7.4** Run instrumented tests on emulator:
  ```bash
  ./gradlew :androidApp:connectedDebugAndroidTest
  ```

- [ ] **7.5** Verify CI can run instrumented tests (if configured)

#### Deliverables

- All instrumented tests updated
- Tests pass on physical device/emulator

#### Success Criteria

- `connectedAndroidTest` passes 100%
- No legacy imports in androidTest/

---

### **PHASE 8: Test Coverage & Quality Assurance** ✅

**Duration**: 3-4 hours
**Risk**: Low

#### Objectives

- Measure test coverage across all modules
- Identify and fill coverage gaps
- Establish coverage baselines for CI

#### Tasks

- [x] **8.1** Generate coverage reports:
  ```bash
  ./gradlew :shared:core-models:koverHtmlReport
  ./gradlew :shared:core-tracking:koverHtmlReport
  ./gradlew :androidApp:jacocoTestReport
  ```

- [x] **8.2** Set up Kover for KMP coverage:
  ```kotlin
  // In shared module build files
  plugins {
    id("org.jetbrains.kotlinx.kover") version "0.7.4"
  }

  koverReport {
    defaults {
      verify {
        rule {
          bound { minValue = 85 } // shared modules
        }
      }
    }
  }
  ```

- [ ] **8.3** Analyze coverage gaps:
    - Identify untested edge cases
    - Focus on critical paths (tracking, aggregation)
    - Add missing tests

- [ ] **8.4** Add property-based tests (optional):
    - Use Kotest for randomized testing
    - Example: Test tracking with random bounding boxes

- [x] **8.5** Establish coverage requirements:
    - **KMP modules**: ≥ 85% line coverage
    - **Android-specific**: ≥ 75% line coverage
    - **UI tests**: Critical paths covered

- [ ] **8.6** Document test strategy in TESTING.md:
    - What to test in commonTest vs androidTest
    - Coverage requirements per module
    - How to run tests locally and in CI

- [ ] **8.7** Update CI to enforce coverage:
  ```yaml
  - name: Check coverage
    run: ./gradlew koverVerify
  ```

#### Deliverables

- Coverage reports for all modules
- TESTING.md documentation
- CI enforcement of coverage thresholds

#### Success Criteria

- core-models: ≥ 85% coverage
- core-tracking: ≥ 80% coverage
- androidApp: ≥ 75% coverage
- All critical paths covered

---

### **PHASE 9: Documentation & CI Integration** 📚

**Duration**: 2 hours
**Risk**: Low

#### Objectives

- Document the new test architecture
- Update developer onboarding guides
- Ensure CI validates tests on every PR

#### Tasks

- [x] **9.1** Create `docs/TESTING.md`:
    - Test architecture overview
    - How to run tests locally
    - How to write new tests
    - Coverage requirements
    - Common patterns and anti-patterns

- [ ] **9.2** Update `AGENTS.md`:
    - Add KMP testing guidelines
    - Reference new test structure

- [x] **9.3** Update `README.md`:
    - Add testing section
    - Link to TESTING.md

- [ ] **9.4** Create test command cheat sheet:
  ```markdown
  ## Quick Test Commands

  # Run all KMP tests
  ./gradlew :shared:core-models:test :shared:core-tracking:test

  # Run Android unit tests
  ./gradlew :androidApp:testDebugUnitTest

  # Run instrumented tests
  ./gradlew :androidApp:connectedDebugAndroidTest

  # Run all tests with coverage
  ./gradlew test koverHtmlReport
  ```

- [ ] **9.5** Update CI workflow `.github/workflows/test.yml`:
  ```yaml
  name: Test Suite

  on: [push, pull_request]

  jobs:
    test-kmp:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v3
        - name: Set up JDK
          uses: actions/setup-java@v3
          with:
            java-version: '17'
        - name: Run KMP tests
          run: |
            ./gradlew :shared:core-models:test
            ./gradlew :shared:core-tracking:test
        - name: Check coverage
          run: ./gradlew koverVerify

    test-android:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v3
        - name: Set up JDK
          uses: actions/setup-java@v3
          with:
            java-version: '17'
        - name: Run Android unit tests
          run: ./gradlew :androidApp:testDebugUnitTest
  ```

- [ ] **9.6** Add PR checklist template:
    - `.github/pull_request_template.md` with test verification steps

#### Deliverables

- Complete TESTING.md documentation
- Updated CI workflows
- PR template with test checklist

#### Success Criteria

- CI runs all tests on every PR
- Documentation is clear and comprehensive
- New developers can run tests easily

---

## Risk Assessment & Mitigation

| Risk                                    | Probability | Impact   | Mitigation                                                          |
|-----------------------------------------|-------------|----------|---------------------------------------------------------------------|
| **Test failures during migration**      | High        | Medium   | Phase 1 captures baseline; incremental approach allows rollback     |
| **Lost test coverage**                  | Medium      | High     | Detailed mapping in Phase 1; verify coverage in Phase 8             |
| **Breaking changes to production code** | Low         | Critical | Tests validate behavior is preserved; extensive regression testing  |
| **Robolectric incompatibility**         | Medium      | Low      | Create pure Kotlin test helpers in Phase 2                          |
| **CI pipeline breaks**                  | Low         | Medium   | Update CI incrementally; keep legacy tests until migration complete |
| **Time overruns**                       | Medium      | Low      | Each phase is independently valuable; can pause between phases      |

---

## Success Metrics

### Quantitative

- ✅ **100% test migration**: All 32 test files migrated or deleted
- ✅ **Zero legacy imports**: No `com.scanium.app.tracking`, etc. in codebase
- ✅ **Coverage targets**:
    - KMP modules ≥ 85%
    - Android-specific ≥ 75%
- ✅ **Test pass rate**: 100% on `./gradlew test`
- ✅ **CI integration**: All tests run automatically on PR

### Qualitative

- ✅ **Clear separation**: Business logic tests in KMP, platform tests in androidApp
- ✅ **Developer experience**: Easy to understand where to add new tests
- ✅ **Documentation**: Comprehensive guide for testing patterns
- ✅ **Maintainability**: No duplicated test logic between legacy and KMP

---

## Timeline Estimate

| Phase                   | Duration  | Dependencies | Can Parallelize?                |
|-------------------------|-----------|--------------|---------------------------------|
| Phase 1: Audit          | 1-2 hours | None         | No                              |
| Phase 2: Infrastructure | 2-3 hours | Phase 1      | Partial                         |
| Phase 3: Core Models    | 3-4 hours | Phase 2      | No                              |
| Phase 4: Core Tracking  | 4-6 hours | Phase 2, 3   | No                              |
| Phase 5: Android Tests  | 3-4 hours | Phase 4      | Partial (after imports updated) |
| Phase 6: Cleanup        | 2-3 hours | Phase 5      | No                              |
| Phase 7: Instrumented   | 2-3 hours | Phase 5      | Yes (independent)               |
| Phase 8: Coverage       | 3-4 hours | Phase 6      | Partial                         |
| Phase 9: Documentation  | 2 hours   | Phase 8      | Yes (can start early)           |

**Total Sequential**: 22-32 hours
**With Parallelization**: 18-26 hours
**Recommended**: 3-5 working days with breaks and verification time

---

## Rollback Plan

If critical issues arise during any phase:

1. **Immediate**: Revert the branch to last stable commit
2. **Investigation**: Identify which specific test or code change caused failure
3. **Targeted fix**: Fix the specific issue without abandoning the entire phase
4. **Validation**: Re-run full test suite before continuing
5. **Documentation**: Document the issue in this plan to prevent recurrence

**Branch Strategy**:

- Commit after each phase completion
- Tag stable points: `test-migration-phase-3-stable`
- Keep legacy tests until Phase 8 verification complete

---

## Next Steps

### Immediate Actions (Before Starting Phase 1)

1. ✅ Review this plan with team
2. ⏸️ Get approval to proceed
3. ⏸️ Create backup branch: `git branch backup-before-test-migration`
4. ⏸️ Ensure CI is stable before starting
5. ⏸️ Allocate dedicated time (minimize context switching)

### Communication

- Daily progress updates in standup/Slack
- Document blockers immediately in this plan
- Share learnings (especially tricky migrations) in team wiki

---

## Appendix

### A. Key File Locations

**KMP Test Targets**:

- `shared/core-models/src/commonTest/kotlin/com/scanium/core/models/`
- `shared/core-tracking/src/commonTest/kotlin/com/scanium/core/tracking/`

**Android Test Locations**:

- `androidApp/src/test/java/com/scanium/app/` (unit tests)
- `androidApp/src/androidTest/java/com/scanium/app/` (instrumented tests)

**Legacy (To Be Deleted)**:

- `core-models/src/test/`
- `core-tracking/src/test/`

### B. Import Mapping Reference

| Old (Legacy)                                 | New (KMP)                                         |
|----------------------------------------------|---------------------------------------------------|
| `com.scanium.app.ml.ItemCategory`            | `com.scanium.core.models.ml.ItemCategory`         |
| `com.scanium.app.items.ScannedItem`          | `com.scanium.core.models.items.ScannedItem`       |
| `com.scanium.app.model.NormalizedRect`       | `com.scanium.core.models.geometry.NormalizedRect` |
| `com.scanium.app.model.ImageRef`             | `com.scanium.core.models.image.ImageRef`          |
| `com.scanium.app.tracking.ObjectTracker`     | `com.scanium.core.tracking.ObjectTracker`         |
| `com.scanium.app.tracking.DetectionInfo`     | `com.scanium.core.tracking.DetectionInfo`         |
| `com.scanium.app.aggregation.ItemAggregator` | `com.scanium.core.tracking.ItemAggregator`        |

### C. Test Helper Migration Examples

**Before (Android-specific with RectF)**:

```kotlin
private fun createTestDetection(
    id: String,
    boundingBox: RectF,
    confidence: Float
): DetectionInfo {
    val normalizedBox = boundingBox.toNormalizedRect(1000, 1000)
    return DetectionInfo(
        trackingId = id,
        boundingBox = normalizedBox,
        confidence = confidence,
        category = ItemCategory.FASHION,
        labelText = "Test"
    )
}
```

**After (KMP-portable)**:

```kotlin
private fun createTestDetection(
    id: String,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    confidence: Float
): DetectionInfo {
    val normalizedBox = NormalizedRect(left, top, right, bottom)
    return DetectionInfo(
        trackingId = id,
        boundingBox = normalizedBox,
        confidence = confidence,
        category = ItemCategory.FASHION,
        labelText = "Test",
        thumbnail = null,
        normalizedBoxArea = normalizedBox.area
    )
}
```

---

**End of Plan**
