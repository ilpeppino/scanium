> Archived on 2025-12-20: superseded by docs/INDEX.md.
***REMOVED*** Phase 1: Test Inventory and Analysis
***REMOVED******REMOVED*** Scanium KMP Test Migration - Baseline Assessment

**Date**: 2025-12-18
**Branch**: `claude/plan-kmp-test-refactor-EugFw`
**Analyst**: Claude Code Agent

---

***REMOVED******REMOVED*** Executive Summary

**Total Test Files**: 39
**Total Test Methods**: 389
**Test Distribution**:
- KMP Common Tests (shared): 41 tests (10.5%)
- Android Unit Tests (androidApp): 314 tests (80.7%)
- Android Instrumented Tests: 24 tests (6.2%)
- Legacy Module Tests: 10 tests (2.6%)

**Critical Findings**:
1. **80% of tests are in Android-only modules** - should migrate to KMP where possible
2. **Only 10.5% in KMP commonTest** - severely under-tested for cross-platform
3. **13 test files test code that has been migrated to KMP** - duplicates/conflicts
4. **Import conflicts**: ~25 test files use legacy `com.scanium.app.*` imports

---

***REMOVED******REMOVED*** Detailed Test Inventory

***REMOVED******REMOVED******REMOVED*** 1. KMP Shared Module Tests ✅ (Already KMP)

| File | Tests | Status | Notes |
|------|-------|--------|-------|
| `shared/core-models/commonTest/.../NormalizedRectTest.kt` | 6 | ✅ Working | Geometry tests |
| `shared/core-models/commonTest/.../ImageRefTest.kt` | 4 | ✅ Working | Image reference tests |
| `shared/core-tracking/commonTest/.../ObjectTrackerGoldenVectorsTest.kt` | 2 | ✅ Working | Golden vector tests only |
| `shared/test-utils/commonTest/.../BuildersTest.kt` | 7 | ✅ Working | Test infrastructure |
| `shared/test-utils/commonTest/.../MatchersTest.kt` | 22 | ✅ Working | Test infrastructure |

**Subtotal**: 5 files, 41 tests

**Assessment**: ✅ **Good foundation but insufficient coverage**
- Need to expand ObjectTracker tests from 2 to ~20+
- Missing ItemAggregator tests entirely
- No DetectionInfo, ObjectCandidate tests

---

***REMOVED******REMOVED******REMOVED*** 2. Legacy KMP Module Tests ❌ (To Be Deleted)

| File | Tests | Migration Action | Target |
|------|-------|------------------|--------|
| `core-models/test/.../ImageRefTest.kt` | 4 | ❌ **DELETE** | Already in `shared/core-models/commonTest/` |
| `core-models/test/.../NormalizedRectTest.kt` | 6 | ❌ **DELETE** | Already in `shared/core-models/commonTest/` |
| `core-tracking/test/.../ObjectTrackerNormalizedMatchingTest.kt` | 3 | ⚠️ **MIGRATE UNIQUE TESTS** then DELETE | Merge into `shared/core-tracking/commonTest/` |

**Subtotal**: 3 files, 13 tests

**Assessment**: ❌ **Deprecated - delete after merging unique tests**

---

***REMOVED******REMOVED******REMOVED*** 3. Android App Unit Tests - **Core Business Logic** (MIGRATE TO KMP)

These tests cover business logic that has been moved to KMP and should be migrated to `commonTest`.

***REMOVED******REMOVED******REMOVED******REMOVED*** 3a. Tracking Tests → `shared/core-tracking/commonTest/`

| File | Tests | Portability | Migration Priority | Target |
|------|-------|-------------|-------------------|--------|
| `androidApp/test/.../tracking/ObjectTrackerTest.kt` | 19 | ✅ **100% Portable** | 🔴 **CRITICAL** | `shared/core-tracking/commonTest/ObjectTrackerTest.kt` |
| `androidApp/test/.../tracking/ObjectCandidateTest.kt` | 14 | ✅ **100% Portable** | 🟡 Medium | `shared/core-tracking/commonTest/ObjectCandidateTest.kt` |
| `androidApp/test/.../tracking/TrackingPipelineIntegrationTest.kt` | 8 | ✅ **100% Portable** | 🟡 Medium | `shared/core-tracking/commonTest/TrackingPipelineIntegrationTest.kt` |
| `androidApp/test/.../aggregation/ItemAggregatorTest.kt` | 15 | ✅ **100% Portable** | 🔴 **CRITICAL** | `shared/core-tracking/commonTest/ItemAggregatorTest.kt` |
| `androidApp/test/.../items/DeduplicationPipelineIntegrationTest.kt` | 11 | ✅ **100% Portable** | 🟡 Medium | `shared/core-tracking/commonTest/DeduplicationPipelineIntegrationTest.kt` |

**Subtotal**: 5 files, 67 tests

**Import Issues**:
- ❌ `import com.scanium.app.tracking.ObjectTracker` → should be `com.scanium.core.tracking.ObjectTracker`
- ❌ `import com.scanium.app.aggregation.ItemAggregator` → should be `com.scanium.core.tracking.ItemAggregator`
- ❌ Uses `RectF` → should use `NormalizedRect` directly
- ❌ Uses `toNormalizedRect()` helper → replace with `testNormalizedRect()` from test-utils

**Migration Steps**:
1. Copy files to `shared/core-tracking/commonTest/`
2. Update package from `com.scanium.app.*` to `com.scanium.core.tracking`
3. Replace `RectF(...)` with `testNormalizedRect(...)` from test-utils
4. Replace `import com.scanium.app.*` with `import com.scanium.core.*`
5. Remove `@RunWith(RobolectricTestRunner::class)` annotations
6. Update assertions to use Kotlin Test (already using `assertEquals` etc.)
7. Delete original files from androidApp/test/

***REMOVED******REMOVED******REMOVED******REMOVED*** 3b. Model Tests → `shared/core-models/commonTest/`

| File | Tests | Portability | Migration Priority | Target |
|------|-------|-------------|-------------------|--------|
| `androidApp/test/.../items/ScannedItemTest.kt` | 26 | ✅ **100% Portable** | 🔴 **CRITICAL** | `shared/core-models/commonTest/.../items/ScannedItemTest.kt` |
| `androidApp/test/.../ml/DetectionResultTest.kt` | 10 | ✅ **100% Portable** | 🟡 Medium | `shared/core-models/commonTest/.../ml/DetectionResultTest.kt` |
| `androidApp/test/.../ml/ItemCategoryTest.kt` | 17 | ✅ **100% Portable** | 🟡 Medium | `shared/core-models/commonTest/.../ml/ItemCategoryTest.kt` |
| `androidApp/test/.../camera/ScanModeTest.kt` | 7 | ✅ **100% Portable** | 🟢 Low | `shared/core-models/commonTest/.../items/ScanModeTest.kt` |

**Subtotal**: 4 files, 60 tests

**Import Issues**: Same as tracking tests

**Migration Steps**: Same as tracking tests

---

***REMOVED******REMOVED******REMOVED*** 4. Android App Unit Tests - **ViewModel Layer** (UPDATE IMPORTS)

These tests should stay in androidApp but need import updates to use KMP modules.

| File | Tests | Action | Notes |
|------|-------|--------|-------|
| `androidApp/test/.../items/ItemsViewModelTest.kt` | 38 | 🔧 **UPDATE IMPORTS** | ViewModel-specific; keep in androidApp |
| `androidApp/test/.../items/ItemsViewModelAggregationTest.kt` | 13 | 🔧 **UPDATE IMPORTS + SPLIT** | Split: aggregation logic → KMP, ViewModel interaction → stay |
| `androidApp/test/.../items/ItemsViewModelListingStatusTest.kt` | 5 | 🔧 **UPDATE IMPORTS** | Listing status is Android-specific (ViewModel) |
| `androidApp/test/.../items/ItemListingStatusTest.kt` | 5 | 🔧 **UPDATE IMPORTS** | Keep in androidApp |

**Subtotal**: 4 files, 61 tests

**Actions Required**:
1. Update all `import com.scanium.app.ml.ItemCategory` → `import com.scanium.core.models.ml.ItemCategory`
2. Update all `import com.scanium.app.items.ScannedItem` → `import com.scanium.core.models.items.ScannedItem`
3. Update all `import com.scanium.app.tracking.*` → `import com.scanium.core.tracking.*`
4. For ItemsViewModelAggregationTest: Extract pure aggregation tests to KMP, keep ViewModel orchestration tests

---

***REMOVED******REMOVED******REMOVED*** 5. Android App Unit Tests - **Platform Adapters** (KEEP IN ANDROIDAPP)

These tests are inherently Android-specific and should remain.

| File | Tests | Reason to Keep | Notes |
|------|-------|----------------|-------|
| `androidApp/test/.../camera/DetectionOverlayTransformTest.kt` | 15 | Android Canvas/View | Update imports only |
| `androidApp/test/.../camera/DocumentScanningIntegrationTest.kt` | 17 | CameraX integration | Update imports only |
| `androidApp/test/.../camera/DetectionOverlayTest.kt` (instrumented) | 12 | UI testing | Update imports only |
| `android-platform-adapters/test/.../PlatformAdaptersTest.kt` | 5 | RectF↔NormalizedRect, Bitmap↔ImageRef | Keep as-is |

**Subtotal**: 4 files, 49 tests

**Actions Required**: Update imports to KMP modules only

---

***REMOVED******REMOVED******REMOVED*** 6. Android App Unit Tests - **ML/Classification** (UPDATE IMPORTS)

| File | Tests | Action | Notes |
|------|-------|--------|-------|
| `androidApp/test/.../ml/PricingEngineTest.kt` | 19 | 🔧 **UPDATE IMPORTS** | Pricing logic; update ItemCategory imports |
| `androidApp/test/.../ml/classification/ClassificationOrchestratorTest.kt` | 6 | 🔧 **UPDATE IMPORTS** | Orchestration layer; Android-specific due to ML Kit |

**Subtotal**: 2 files, 25 tests

---

***REMOVED******REMOVED******REMOVED*** 7. Android App Unit Tests - **Domain/Config** (UPDATE IMPORTS)

| File | Tests | Action | Notes |
|------|-------|--------|-------|
| `androidApp/test/.../domain/DomainPackProviderTest.kt` | 9 | 🔧 **UPDATE IMPORTS** | Domain pack system; update ItemCategory imports |
| `androidApp/test/.../domain/category/BasicCategoryEngineTest.kt` | 13 | 🔧 **UPDATE IMPORTS** | Category engine; Android-specific (resources) |
| `androidApp/test/.../domain/category/CategoryMapperTest.kt` | 11 | 🔧 **UPDATE IMPORTS** | Mapper; update ItemCategory imports |
| `androidApp/test/.../domain/config/DomainPackTest.kt` | 7 | 🔧 **UPDATE IMPORTS** | Config tests |
| `androidApp/test/.../domain/repository/LocalDomainPackRepositoryTest.kt` | 11 | 🔧 **UPDATE IMPORTS** | Repository tests |

**Subtotal**: 5 files, 51 tests

---

***REMOVED******REMOVED******REMOVED*** 8. Android App Unit Tests - **Selling/eBay** (UPDATE IMPORTS)

| File | Tests | Action | Notes |
|------|-------|--------|-------|
| `androidApp/test/.../selling/data/EbayMarketplaceServiceTest.kt` | 1 | 🔧 **UPDATE IMPORTS** | eBay integration; Android-specific |
| `androidApp/test/.../selling/data/MockEbayApiTest.kt` | 2 | 🔧 **UPDATE IMPORTS** | Mock API tests |
| `androidApp/test/.../selling/data/MockEbayConfigManagerTest.kt` | 6 | 🔧 **UPDATE IMPORTS** | Config tests |
| `androidApp/test/.../selling/util/ListingDraftMapperTest.kt` | 1 | 🔧 **UPDATE IMPORTS** | Mapper; update ScannedItem imports |
| `androidApp/test/.../selling/util/ListingImagePreparerTest.kt` | 3 | 🔧 **UPDATE IMPORTS** | Image prep; Android-specific |

**Subtotal**: 5 files, 13 tests

---

***REMOVED******REMOVED******REMOVED*** 9. Android Instrumented Tests (KEEP, UPDATE IMPORTS)

| File | Tests | Action | Notes |
|------|-------|--------|-------|
| `androidApp/androidTest/.../camera/DetectionOverlayTest.kt` | 12 | 🔧 **UPDATE IMPORTS** | UI test; Canvas rendering |
| `androidApp/androidTest/.../items/ItemsViewModelInstrumentedTest.kt` | 4 | 🔧 **UPDATE IMPORTS** | ViewModel on-device test |
| `androidApp/androidTest/.../ui/ModeSwitcherTest.kt` | 8 | 🔧 **UPDATE IMPORTS** | Compose UI test; ScanMode |

**Subtotal**: 3 files, 24 tests

---

***REMOVED******REMOVED*** Migration Priority Matrix

***REMOVED******REMOVED******REMOVED*** 🔴 **CRITICAL Priority** (Phase 3-4)
Migrate ASAP - Core business logic already in KMP:

| Files | Tests | Effort | Risk |
|-------|-------|--------|------|
| ObjectTrackerTest.kt | 19 | High | High |
| ItemAggregatorTest.kt | 15 | High | High |
| ScannedItemTest.kt | 26 | Medium | Low |

**Total**: 3 files, 60 tests, ~6-8 hours

***REMOVED******REMOVED******REMOVED*** 🟡 **Medium Priority** (Phase 4-5)
Important but less critical:

| Files | Tests | Effort | Risk |
|-------|-------|--------|------|
| ObjectCandidateTest.kt | 14 | Medium | Medium |
| DetectionResultTest.kt | 10 | Low | Low |
| ItemCategoryTest.kt | 17 | Low | Low |
| TrackingPipelineIntegrationTest.kt | 8 | Medium | Medium |
| DeduplicationPipelineIntegrationTest.kt | 11 | Medium | Medium |
| ScanModeTest.kt | 7 | Low | Low |

**Total**: 6 files, 67 tests, ~4-6 hours

***REMOVED******REMOVED******REMOVED*** 🟢 **Low Priority** (Phase 5)
Update imports only (no migration):

| Files | Tests | Action |
|-------|-------|--------|
| 21 androidApp test files | 186 tests | Update imports: `com.scanium.app.*` → `com.scanium.core.*` |

**Total**: 21 files, 186 tests, ~2-4 hours (scripted)

---

***REMOVED******REMOVED*** Import Conflict Analysis

***REMOVED******REMOVED******REMOVED*** Broken Imports (Need Fix)

**Pattern 1: Legacy tracking imports**
```kotlin
// ❌ BROKEN (legacy)
import com.scanium.app.tracking.ObjectTracker
import com.scanium.app.tracking.DetectionInfo
import com.scanium.app.tracking.TrackerConfig

// ✅ FIXED (KMP)
import com.scanium.core.tracking.ObjectTracker
import com.scanium.core.tracking.DetectionInfo
import com.scanium.core.tracking.TrackerConfig
```

**Pattern 2: Legacy model imports**
```kotlin
// ❌ BROKEN (legacy)
import com.scanium.app.ml.ItemCategory
import com.scanium.app.items.ScannedItem
import com.scanium.app.model.NormalizedRect

// ✅ FIXED (KMP)
import com.scanium.core.models.ml.ItemCategory
import com.scanium.core.models.items.ScannedItem
import com.scanium.core.models.geometry.NormalizedRect
```

**Pattern 3: Legacy aggregation imports**
```kotlin
// ❌ BROKEN (legacy)
import com.scanium.app.aggregation.ItemAggregator

// ✅ FIXED (KMP)
import com.scanium.core.tracking.ItemAggregator
```

***REMOVED******REMOVED******REMOVED*** Files with Import Conflicts

**Critical (25 files)**:
- All 5 tracking test files
- All 4 model test files
- All 4 ViewModel test files
- 12 additional androidApp test files

**Migration Script**: See Appendix A for automated import replacement script

---

***REMOVED******REMOVED*** Portability Analysis

***REMOVED******REMOVED******REMOVED*** 100% Portable (Can Move to KMP commonTest)

**Criteria**:
- No Android framework dependencies (View, Canvas, Context, etc.)
- No Robolectric-specific code
- Tests pure business logic
- Uses portable types (NormalizedRect, ImageRef, not RectF/Bitmap)

**Files** (9 files, 127 tests):
1. ObjectTrackerTest.kt (19)
2. ObjectCandidateTest.kt (14)
3. TrackingPipelineIntegrationTest.kt (8)
4. ItemAggregatorTest.kt (15)
5. DeduplicationPipelineIntegrationTest.kt (11)
6. ScannedItemTest.kt (26)
7. DetectionResultTest.kt (10)
8. ItemCategoryTest.kt (17)
9. ScanModeTest.kt (7)

***REMOVED******REMOVED******REMOVED*** Partially Portable (Split Tests)

**ItemsViewModelAggregationTest.kt** (13 tests):
- 6 tests: Pure aggregation logic → Migrate to KMP
- 7 tests: ViewModel interaction → Keep in androidApp

***REMOVED******REMOVED******REMOVED*** Android-Specific (Keep in androidApp)

**Reasons**:
- Uses Android Views/Canvas
- Tests CameraX integration
- Tests ViewModels (StateFlow, coroutines on Android dispatcher)
- Tests Compose UI
- Tests resource loading (res/raw/)
- Uses Robolectric for Android framework mocking

**Files** (21 files, 186 tests): All others not listed above

---

***REMOVED******REMOVED*** Test Helper Migration

***REMOVED******REMOVED******REMOVED*** Current Android-Specific Helpers (To Replace)

**File**: `androidApp/src/test/.../test/TestHelpers.kt`
```kotlin
// ❌ BROKEN: Uses Android RectF
fun RectF.toNormalizedRect(width: Int, height: Int): NormalizedRect {
    return NormalizedRect(
        left / width,
        top / height,
        right / width,
        bottom / height
    )
}

fun createTestDetection(boundingBox: RectF, ...): DetectionInfo {
    // Uses RectF
}
```

***REMOVED******REMOVED******REMOVED*** New KMP Helpers (Already Created)

**File**: `shared/test-utils/src/commonMain/.../Builders.kt`
```kotlin
// ✅ PORTABLE: Pure Kotlin
fun testNormalizedRect(left: Float, top: Float, right: Float, bottom: Float): NormalizedRect

fun testDetectionInfo(
    boundingBox: NormalizedRect = testCenteredRect(),
    ...
): DetectionInfo

fun testScannedItem(...): ScannedItem
```

**Migration Pattern**:
```kotlin
// BEFORE (Android-specific)
val box = RectF(100f, 100f, 200f, 200f)
val normalizedBox = box.toNormalizedRect(1000, 1000)
val detection = createDetectionInfo(trackingId, box, 0.8f)

// AFTER (KMP-portable)
val normalizedBox = testNormalizedRect(0.1f, 0.1f, 0.2f, 0.2f)
val detection = testDetectionInfo(
    trackingId = trackingId,
    boundingBox = normalizedBox,
    confidence = 0.8f
)
```

---

***REMOVED******REMOVED*** Coverage Analysis

***REMOVED******REMOVED******REMOVED*** Current Coverage by Module

| Module | Test Files | Test Methods | Line Coverage (Est.) |
|--------|-----------|--------------|----------------------|
| `shared/core-models` | 2 | 10 | ~60% |
| `shared/core-tracking` | 1 | 2 | ~30% ⚠️ |
| `shared/test-utils` | 2 | 29 | 100% |
| `androidApp` (unit) | 28 | 314 | ~75% |
| `androidApp` (instrumented) | 3 | 24 | ~40% |
| `core-models` (legacy) | 2 | 10 | **DEPRECATED** |
| `core-tracking` (legacy) | 1 | 3 | **DEPRECATED** |

***REMOVED******REMOVED******REMOVED*** Coverage Gaps in KMP Modules

**shared/core-tracking** (CRITICAL GAPS):
- ❌ ObjectTracker: Only 2 golden vector tests (need 19 from androidApp)
- ❌ ItemAggregator: 0 tests (need 15 from androidApp)
- ❌ ObjectCandidate: 0 tests (need 14 from androidApp)
- ❌ DetectionInfo: 0 tests

**shared/core-models** (Minor Gaps):
- ⚠️ ScannedItem: 0 tests (need 26 from androidApp)
- ⚠️ ItemCategory: 0 tests (need 17 from androidApp)
- ⚠️ DetectionResult: 0 tests (need 10 from androidApp)
- ✅ NormalizedRect: 6 tests (good coverage)
- ✅ ImageRef: 4 tests (good coverage)

***REMOVED******REMOVED******REMOVED*** Target Coverage Post-Migration

| Module | Target Line Coverage | Target Test Count |
|--------|---------------------|-------------------|
| `shared/core-models` | ≥ 85% | 60+ tests |
| `shared/core-tracking` | ≥ 85% | 70+ tests |
| `shared/test-utils` | 100% | 30+ tests |

---

***REMOVED******REMOVED*** Risk Assessment

***REMOVED******REMOVED******REMOVED*** High Risk Items

1. **ObjectTracker Migration** (Risk: 🔴 High)
   - 19 tests with complex logic
   - Uses RectF extensively → Need careful conversion
   - Core business logic - any breakage is critical
   - **Mitigation**: Migrate incrementally, run tests after each batch

2. **ItemAggregator Migration** (Risk: 🔴 High)
   - 15 tests covering similarity scoring
   - Complex threshold logic
   - **Mitigation**: Keep androidApp tests until KMP tests pass 100%

3. **Import Updates** (Risk: 🟡 Medium)
   - 25 files need import changes
   - Risk of missing some imports
   - **Mitigation**: Use automated script + manual verification

***REMOVED******REMOVED******REMOVED*** Low Risk Items

1. **NormalizedRect/ImageRef** (Risk: 🟢 Low)
   - Already migrated and working
   - Simple data models

2. **Test-Utils Validation** (Risk: 🟢 Low)
   - Self-contained module
   - Well-tested (29 tests)

---

***REMOVED******REMOVED*** Next Steps Recommendations

***REMOVED******REMOVED******REMOVED*** Immediate (This Week)

1. ✅ **Phase 1 Complete**: Inventory and analysis ← YOU ARE HERE
2. 🔧 **Phase 2 Complete**: Test-utils infrastructure ← DONE
3. 🚀 **Start Phase 3**: Migrate core-models tests
   - ScannedItemTest.kt (26 tests)
   - ItemCategoryTest.kt (17 tests)
   - DetectionResultTest.kt (10 tests)
   - **Estimated**: 3-4 hours

***REMOVED******REMOVED******REMOVED*** Short-Term (Next 2-3 Days)

4. 🚀 **Phase 4**: Migrate core-tracking tests
   - ObjectTrackerTest.kt (19 tests) ← CRITICAL
   - ItemAggregatorTest.kt (15 tests) ← CRITICAL
   - ObjectCandidateTest.kt (14 tests)
   - **Estimated**: 6-8 hours

***REMOVED******REMOVED******REMOVED*** Medium-Term (Next Week)

5. 🔧 **Phase 5**: Update imports in androidApp tests
   - Automated script for 21 files
   - Manual verification
   - **Estimated**: 3-4 hours

6. 🧹 **Phase 6**: Delete legacy test files
   - Remove core-models/test/ and core-tracking/test/
   - **Estimated**: 1 hour

---

***REMOVED******REMOVED*** Appendix A: Automated Import Replacement Script

```bash
***REMOVED***!/bin/bash
***REMOVED*** Script to update imports from legacy to KMP packages

***REMOVED*** Backup first
git checkout -b test-import-updates

***REMOVED*** Define replacements
declare -A REPLACEMENTS=(
  ["com.scanium.app.ml.ItemCategory"]="com.scanium.core.models.ml.ItemCategory"
  ["com.scanium.app.items.ScannedItem"]="com.scanium.core.models.items.ScannedItem"
  ["com.scanium.app.model.NormalizedRect"]="com.scanium.core.models.geometry.NormalizedRect"
  ["com.scanium.app.model.ImageRef"]="com.scanium.core.models.image.ImageRef"
  ["com.scanium.app.tracking.ObjectTracker"]="com.scanium.core.tracking.ObjectTracker"
  ["com.scanium.app.tracking.DetectionInfo"]="com.scanium.core.tracking.DetectionInfo"
  ["com.scanium.app.tracking.TrackerConfig"]="com.scanium.core.tracking.TrackerConfig"
  ["com.scanium.app.tracking.TrackerStats"]="com.scanium.core.tracking.TrackerStats"
  ["com.scanium.app.tracking.ObjectCandidate"]="com.scanium.core.tracking.ObjectCandidate"
  ["com.scanium.app.aggregation.ItemAggregator"]="com.scanium.core.tracking.ItemAggregator"
)

***REMOVED*** Apply replacements to all androidApp test files
for old in "${!REPLACEMENTS[@]}"; do
  new="${REPLACEMENTS[$old]}"
  echo "Replacing: $old → $new"

  find androidApp/src/test -name "*.kt" -exec sed -i \
    "s|import $old|import $new|g" {} \;
done

echo "Import updates complete. Please review changes before committing."
```

---

***REMOVED******REMOVED*** Appendix B: Test Count by Category

| Category | Files | Tests | % of Total |
|----------|-------|-------|------------|
| KMP Common Tests | 5 | 41 | 10.5% |
| Business Logic (to migrate) | 9 | 127 | 32.6% |
| ViewModel Tests (update imports) | 4 | 61 | 15.7% |
| Android-Specific (keep) | 18 | 147 | 37.8% |
| Legacy (delete) | 3 | 13 | 3.3% |
| **TOTAL** | **39** | **389** | **100%** |

---

***REMOVED******REMOVED*** Conclusion

Phase 1 audit reveals:
- ✅ Clear migration path for 127 tests to KMP
- ⚠️ Critical coverage gaps in shared/core-tracking
- 🔧 25 files need import updates
- 🧹 3 legacy files to delete

**Recommended Sequence**:
1. Phase 3: Migrate model tests (53 tests, 3-4 hours)
2. Phase 4: Migrate tracking tests (74 tests, 6-8 hours) ← HIGHEST VALUE
3. Phase 5: Update androidApp imports (186 tests, 3-4 hours)
4. Phase 6: Clean up legacy files

**Total Effort**: 15-20 hours over 3-5 days

---

**End of Phase 1 Inventory**
