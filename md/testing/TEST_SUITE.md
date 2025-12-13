***REMOVED*** Test Suite Documentation

This document provides a comprehensive overview of the Scanium test suite, organized by test file.

***REMOVED******REMOVED*** Table of Contents

- [ItemsViewModel Tests](***REMOVED***itemsviewmodel-tests)
- [DetectionResult Tests](***REMOVED***detectionresult-tests)
- [PricingEngine Tests](***REMOVED***pricingengine-tests)
- [ObjectCandidate Tests](***REMOVED***objectcandidate-tests)
- [ObjectTracker Tests](***REMOVED***objecttracker-tests)
- [TrackingPipeline Integration Tests](***REMOVED***trackingpipeline-integration-tests)
- [SessionDeduplicator Tests](***REMOVED***sessiondeduplicator-tests)

---

***REMOVED******REMOVED*** ItemsViewModel Tests

**File:** `app/src/test/java/com/scanium/app/items/ItemsViewModelTest.kt`

**Purpose:** Unit tests for ItemsViewModel state management and detection handling.

| Test Name | Description |
|-----------|-------------|
| `whenViewModelCreated_thenItemsListIsEmpty` | Verifies that the ViewModel initializes with an empty items list |
| `whenAddingSingleItem_thenItemAppearsInList` | Tests adding a single item to the ViewModel |
| `whenAddingMultipleItems_thenAllItemsAppear` | Verifies batch addition of multiple items |
| `whenAddingDuplicateItem_thenOnlyOneInstanceKept` | Tests ID-based deduplication (same ID items rejected) |
| `whenAddingItemsWithDuplicateIds_thenDuplicatesFiltered` | Verifies duplicate filtering in batch operations |
| `whenRemovingItem_thenItemDisappearsFromList` | Tests item removal by ID |
| `whenRemovingNonExistentItem_thenListUnchanged` | Verifies graceful handling of non-existent item removal |
| `whenClearingAllItems_thenListBecomesEmpty` | Tests clearing all items from the list |
| `whenClearingEmptyList_thenNoError` | Verifies clearing empty list doesn't cause errors |
| `whenAddingAfterClearing_thenNewItemsAppear` | Tests that items can be added after clearing |
| `whenAddingSameItemAfterRemoval_thenItemCanBeAddedAgain` | Verifies items can be re-added after removal |
| `whenAddingItemsSequentially_thenMaintainsOrder` | Tests that insertion order is preserved |
| `whenAddingEmptyList_thenNoChange` | Verifies adding empty list doesn't affect state |
| `whenAddingMixOfNewAndDuplicateItems_thenOnlyNewOnesAdded` | Tests filtering duplicates when mixing new and existing items |
| `whenAddingItemsWithDifferentConfidenceLevels_thenAllStored` | Verifies confidence values are preserved |
| `whenItemCountQueried_thenMatchesActualListSize` | Tests item count accuracy throughout operations |
| `whenAddingSimilarItemWithDifferentId_thenRejectedBySimilarityCheck` | Tests session-level similarity-based deduplication |
| `whenAddingDissimilarItems_thenBothAdded` | Verifies dissimilar items (different categories) are both added |
| `whenAddingItemsWithoutDistinguishingFeatures_thenBothAdded` | Safety check: items without thumbnails aren't falsely matched |
| `whenAddingBatchWithSimilarItems_thenOnlyUniqueOnesAdded` | Tests similarity filtering in batch operations |
| `whenAddingBatchWithInternalSimilarItems_thenFirstOccurrenceKept` | Verifies first occurrence is kept when batch contains similar items |
| `whenClearingItems_thenSessionDeduplicatorReset` | Tests that clearing items resets the deduplicator |
| `whenRemovingItem_thenCanAddSimilarItemAgain` | Verifies removed items don't block similar items |
| `whenSamePhysicalObjectDetectedWithDifferentTrackingIds_thenOnlyOneKept` | Simulates ML Kit changing tracking IDs for same object |
| `whenMultipleDifferentObjectsScanned_thenAllAdded` | Tests no false positives when scanning multiple different objects |
| `whenAddingSimilarItemsWithDifferentSizes_thenOnlyDissimilarOnesAdded` | Tests size-based similarity threshold (40% tolerance) |

---

***REMOVED******REMOVED*** DetectionResult Tests

**File:** `app/src/test/java/com/scanium/app/ml/DetectionResultTest.kt`

**Purpose:** Unit tests for DetectionResult data class.

| Test Name | Description |
|-----------|-------------|
| `whenDetectionResultCreated_thenAllFieldsAreSet` | Data class initialization with all parameters |
| `whenTrackingIdIsNull_thenDetectionResultIsValid` | Handles optional trackingId correctly |
| `whenFormattedPriceRange_thenReturnsCorrectFormat` | Price formatting with Euro symbol (€20 - €50) |
| `whenPriceRangeHasDecimals_thenRoundsToWholeNumbers` | Decimal prices rounded to integers |
| `whenPriceRangeIsZero_thenFormatsCorrectly` | Zero prices formatted correctly |
| `whenPriceRangeIsLarge_thenFormatsWithoutDecimals` | Large prices formatted without decimals |
| `whenBoundingBoxHasDimensions_thenCanAccessProperties` | Bounding box properties accessible |
| `whenConfidenceIsLow_thenStillCreatesValidResult` | Low confidence values handled |
| `whenConfidenceIsHigh_thenStillCreatesValidResult` | High confidence values handled |
| `whenMultipleDetectionsCreated_thenEachIsIndependent` | Multiple detection instances are independent |

---

***REMOVED******REMOVED*** PricingEngine Tests

**File:** `app/src/test/java/com/scanium/app/ml/PricingEngineTest.kt`

**Purpose:** Unit tests for EUR price range generation.

| Test Name | Description |
|-----------|-------------|
| `whenFashionCategory_thenPriceInExpectedRange` | Fashion items priced in 8-40 EUR range |
| `whenElectronicsCategory_thenPriceInHigherRange` | Electronics priced in 20-300 EUR range |
| `whenFoodCategory_thenPriceInLowerRange` | Food items priced in 2-10 EUR range |
| `whenPlaceCategory_thenPriceIsZero` | Places have zero price (not for sale) |
| `whenHomeGoodCategory_thenPriceInMidRange` | Home goods priced in 5-25 EUR range |
| `whenPlantCategory_thenPriceInLowRange` | Plants priced in 3-15 EUR range |
| `whenUnknownCategory_thenPriceInDefaultRange` | Unknown items use default 5-20 EUR range |
| `whenLargeBoundingBoxArea_thenPriceHigherThanSmallArea` | Larger objects generally cost more |
| `whenNullBoundingBoxArea_thenPriceStillValid` | Null area handled gracefully |
| `whenZeroBoundingBoxArea_thenPriceStillValid` | Zero area handled gracefully |
| `whenMaxBoundingBoxArea_thenPriceStillValid` | Maximum area (100%) handled |
| `whenGeneratingMultiplePrices_thenAllAreValid` | Batch price generation produces valid results |
| `whenFormattingPrice_thenCorrectEuroFormat` | Formatted prices match "€X - €Y" pattern |
| `whenFormattingPriceForPlace_thenReturnsNA` | Places return "N/A" for price |
| `whenPriceRangeGenerated_thenMinIsAtLeastHalfEuro` | Minimum price is at least 0.50 EUR |
| `whenPriceRangeGenerated_thenMaxIsAtLeastOneEuroAboveMin` | Price range has at least 1 EUR spread |
| `whenMultipleCallsForSameCategory_thenPricesVaryDueToRandomization` | Randomization produces variation |
| `whenComparingCategoryBaseRanges_thenElectronicsMostExpensive` | Electronics most expensive on average |
| `whenBoundingBoxAreaIncreases_thenSizeFactorApplied` | Size factor affects pricing |

---

***REMOVED******REMOVED*** ObjectCandidate Tests

**File:** `app/src/test/java/com/scanium/app/tracking/ObjectCandidateTest.kt`

**Purpose:** Unit tests for ObjectCandidate data class and tracking helpers.

| Test Name | Description |
|-----------|-------------|
| `create ObjectCandidate with initial values` | ObjectCandidate initializes with all values |
| `update increases seenCount and updates lastSeenFrame` | Update increments seen count and frame number |
| `update tracks maximum confidence` | Tracks and preserves maximum confidence |
| `update with higher confidence updates category and labelText` | Higher confidence updates category/label |
| `update calculates running average of box area` | Running average of bounding box area calculated |
| `getCenterPoint returns correct center coordinates` | Center point calculation is accurate |
| `distanceTo calculates Euclidean distance between centers` | Distance calculation between bounding boxes |
| `distanceTo returns zero for same bounding box` | Same position returns zero distance |
| `calculateIoU returns 1 for identical bounding boxes` | IoU is 1.0 for identical boxes |
| `calculateIoU returns 0 for non-overlapping bounding boxes` | IoU is 0.0 for non-overlapping boxes |
| `calculateIoU returns correct value for partially overlapping boxes` | IoU calculated correctly for partial overlap |
| `calculateIoU returns correct value for one box contained in another` | IoU for fully contained boxes |
| `calculateIoU with edge-touching boxes` | Edge-touching boxes have zero intersection |
| `update with multiple frames tracks progression correctly` | Multi-frame tracking maintains correct state |

---

***REMOVED******REMOVED*** ObjectTracker Tests

**File:** `app/src/test/java/com/scanium/app/tracking/ObjectTrackerTest.kt`

**Purpose:** Unit tests for ObjectTracker tracking logic.

| Test Name | Description |
|-----------|-------------|
| `first detection creates new candidate but does not confirm` | First detection tracked but not confirmed |
| `candidate confirmed after minimum frames threshold` | Confirmation after minFramesToConfirm |
| `candidate not confirmed if confidence too low` | Low confidence prevents confirmation |
| `candidate not confirmed if box area too small` | Small bounding boxes filtered out |
| `tracking with trackingId maintains same candidate` | Same trackingId keeps same candidate across frames |
| `spatial matching without trackingId works` | Spatial matching fallback works without trackingId |
| `multiple different objects tracked independently` | Multiple objects tracked simultaneously |
| `candidate expires after not being seen for threshold frames` | Expiry after expiryFrames threshold |
| `candidate not expired if seen within expiry window` | Candidates kept if seen within window |
| `reset clears all candidates` | Reset removes all tracking state |
| `confirmed candidate not confirmed again in subsequent frames` | Confirmed candidates not re-confirmed |
| `tracker handles empty detections gracefully` | Empty detection lists handled |
| `tracker tracks maximum confidence across frames` | Maximum confidence tracked |
| `category updated when higher confidence is observed` | Category refinement with higher confidence |
| `spatial matching fails for distant bounding boxes` | Distant boxes create separate candidates |
| `frame counter increments correctly` | Frame counter increments properly |
| `intermittent detection confirmed if within frame gap threshold` | Intermittent detections within gap threshold |
| `custom config affects confirmation behavior` | Custom configuration parameters work |
| `average box area calculated correctly across frames` | Average area calculation is accurate |

---

***REMOVED******REMOVED*** TrackingPipeline Integration Tests

**File:** `app/src/test/java/com/scanium/app/tracking/TrackingPipelineIntegrationTest.kt`

**Purpose:** Integration tests for complete object tracking pipeline with realistic scenarios.

| Test Name | Description |
|-----------|-------------|
| `realistic scanning scenario - single object confirmed after movement` | Single object tracked through movement and confirmed |
| `realistic scanning scenario - multiple objects confirmed independently` | Multiple objects in frame tracked independently |
| `realistic scanning scenario - object lost and found` | Object temporarily leaving and returning to frame |
| `realistic scanning scenario - object exits and expires` | Object leaving frame permanently expires |
| `realistic scanning scenario - without trackingId relies on spatial matching` | Spatial matching works without ML Kit trackingId |
| `realistic scanning scenario - category refinement over time` | ML Kit category classification refined over frames |
| `realistic scanning scenario - noise filtering` | Low-quality detections filtered out |
| `realistic scanning scenario - reset between scan sessions` | Reset between scanning sessions works correctly |

---

***REMOVED******REMOVED*** SessionDeduplicator Tests

**File:** `app/src/test/java/com/scanium/app/items/SessionDeduplicatorTest.kt`

**Purpose:** Comprehensive tests for session-level deduplication.

| Test Name | Description |
|-----------|-------------|
| `whenNewItemHasSameIdAsExisting_thenFoundAsDuplicate` | Exact ID matching finds duplicates |
| `whenNewItemHasDifferentId_thenChecksOtherSimilarityFactors` | Different IDs trigger similarity checks |
| `whenCategoriesDiffer_thenNotSimilar` | Different categories prevent matching |
| `whenCategoriesMatch_thenMayBeSimilar` | Same category enables similarity matching |
| `whenLabelsSimilar_thenIncreasesLikelihoodOfMatch` | Similar labels increase match likelihood |
| `whenSizesSimilar_thenMayBeSimilar` | Similar sizes (within 40% tolerance) may match |
| `whenSizesTooDifferent_thenNotSimilar` | Size differences exceeding 40% prevent matching |
| `whenOneItemHasNoSize_thenSizeIgnoredAndCategoryMatches` | Missing size data handled gracefully |
| `whenBothItemsLackDistinguishingFeatures_thenNotSimilar` | Safety check prevents false positives with minimal data |
| `whenOneItemHasDistinguishingFeatures_thenCanMatch` | Items with distinguishing features can match |
| `whenMultipleExistingItems_thenFindsCorrectMatch` | Correct match found among multiple items |
| `whenNoSimilarItemExists_thenReturnsNull` | Returns null when no match exists |
| `whenEmptyExistingList_thenReturnsNull` | Empty list handled gracefully |
| `whenItemCheckedMultipleTimes_thenMetadataCached` | Metadata caching optimizes repeated checks |
| `whenReset_thenClearsAllMetadata` | Reset clears metadata cache |
| `whenResetCalledMultipleTimes_thenNoError` | Multiple resets don't cause errors |
| `whenItemRemoved_thenMetadataCleared` | Item removal clears metadata |
| `whenRemovingNonExistentItem_thenNoError` | Removing non-existent items doesn't error |
| `whenSamePhysicalObjectWithDifferentTrackingIds_thenDetectedAsSimilar` | Same physical object with different IDs matched |
| `whenDifferentObjectsSameCategory_thenNotSimilar` | Different objects with same category distinguished |
| `whenObjectMovesSignificantly_thenStillConsideredSimilar` | Position changes don't prevent matching |
| `whenMultipleSimilarObjectsExist_thenFindsFirstMatch` | First match returned when multiple similar items exist |
| `whenItemsHaveMinimalData_thenHandlesGracefully` | Minimal data items handled gracefully |
| `whenConfidenceLevelsVary_thenDoesNotAffectSimilarity` | Confidence doesn't affect similarity matching |
| `whenVerySmallThumbnails_thenStillProcessesCorrectly` | Small thumbnails processed correctly |
| `whenManyExistingItems_thenPerformsEfficiently` | Performance test with 100+ items |

---

***REMOVED******REMOVED*** Test Coverage Summary

***REMOVED******REMOVED******REMOVED*** Key Testing Areas

1. **State Management** - ItemsViewModel tests verify CRUD operations and StateFlow emissions
2. **Multi-Frame Detection** - ObjectTracker ensures stable object detection over time
3. **Spatial Tracking** - ObjectTracker and ObjectCandidate test IoU and distance-based matching
4. **Deduplication** - SessionDeduplicator prevents duplicate item additions via similarity matching
5. **Pricing** - PricingEngine validates category-based and size-adjusted price generation
6. **Integration** - TrackingPipeline tests verify end-to-end scenarios

***REMOVED******REMOVED******REMOVED*** Test Statistics

- **Testing Framework**: JUnit 4 with Robolectric
- **Assertion Library**: Google Truth

***REMOVED******REMOVED******REMOVED*** Testing Patterns

- **Arrange-Act-Assert** pattern used throughout
- **Helper functions** to create test data
- **Robolectric** for Android framework dependencies
- **Coroutines testing** with `runTest` and `StandardTestDispatcher`
