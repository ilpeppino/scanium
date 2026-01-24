# RCA: Items Labelled as "Item" / No Attribute Enrichment

**Date**: 2026-01-24
**Status**: Identified
**Severity**: High (core user experience impact)

## Problem Statement

Detected items show generic labels ("Item", "Unknown", or coarse category names like "Electronics")
instead of domain-pack-classified names. Attributes (brand, color, OCR) never appear in item list
details despite the domain pack system (brands, strings) being implemented.

## Impact

- Items appear generic in the list view, defeating the purpose of smart classification
- Domain pack's 75 categories and 10 attributes are unused during detection
- Multi-hypothesis results lose their attribute data on item creation
- Vision enrichment pipeline never runs for detection-flow items

## Root Causes

### RC1: Hypothesis Attributes Are Discarded

**Location**: `androidApp/.../items/ItemsViewModel.kt:1121`

When cloud multi-hypothesis classification succeeds and returns a `ClassificationHypothesis` with
populated `attributes` (brand, color, etc.), those attributes are discarded:

```kotlin
val item = ScannedItem(
    labelText = hypothesis?.categoryName ?: rawDetection.onDeviceLabel,  // OK
    attributes = emptyMap()  // BUG: hypothesis.attributes IGNORED
)
```

The `ClassificationHypothesis` data class has `val attributes: Map<String, String>` but the value
is never passed through to the ScannedItem.

### RC2: Vision Insights Never Triggered After Detection

**Location**: `androidApp/.../items/ItemsUiFacade.kt:219` vs `:235`

Two paths exist for adding items:
- `addItem()` - used by detection pipeline - does NOT trigger vision insights
- `addItemsWithVisionPrefill()` - used by multi-object capture - DOES trigger enrichment

`createItemFromDetection()` calls `facade.addItem(item)`, which simply adds to state. No enrichment
is triggered. The bitmap is then recycled in the `finally` block, making it unavailable for later use.

The `displayLabel` property in `ScannedItem.kt:233` tries to build a rich label from
`visionAttributes.primaryBrand`, `visionAttributes.itemType`, `visionAttributes.primaryColor` but
these are never populated for detection-flow items.

### RC3: Domain Pack Classification Not Wired Into Detection

**Location**: `core-domainpack/.../category/BasicCategoryEngine.kt`

The `BasicCategoryEngine.selectCategory()` and `getCandidateCategories()` methods can map ML Kit
labels to fine-grained domain categories (75 categories). However, these are never called during
item creation.

The only usage is in `ListingTitleBuilder.buildTitle()` for eBay listing generation - not during
detection.

### RC4: Fallback Chain Produces Generic Labels

**Location**: `shared/core-models/.../items/ScannedItem.kt:233-280`

The `displayLabel` property fallback chain:
1. `brand + itemType + color` from visionAttributes - empty (never populated)
2. `labelText` - ML Kit's raw label ("Unknown", "Object", or coarse category)
3. `category.displayName` - e.g., "Home good", "Electronics"

When ML Kit doesn't provide a high-confidence label, `onDeviceLabel` is set to "Unknown"
(`CameraFrameAnalyzer.kt:445-636`), which falls through to `category.displayName`.

### RC5: Category Mapping TODO Left Unimplemented

**Location**: `androidApp/.../items/ItemsViewModel.kt:1112-1113`

```kotlin
category = hypothesis?.let {
    // TODO Phase 2: Map domainCategoryId to ItemCategory properly
    rawDetection.onDeviceCategory  // Always uses ML Kit category
} ?: rawDetection.onDeviceCategory,
```

Even when a hypothesis is confirmed with a `categoryId` like `"furniture_sofa"`, the item still
gets the coarse ML Kit category.

## Data Flow (Current vs Expected)

```
CURRENT:
ML Kit -> ObjectTracker -> RawDetection(onDeviceLabel="Unknown")
  -> triggerMultiHypothesisClassification()
    -> CloudClassifier returns hypothesis{categoryName="Sofa", attributes={brand:"IKEA"}}
  -> createItemFromDetection()
    -> ScannedItem(labelText="Sofa", attributes=emptyMap(), visionAttributes=EMPTY)
    -> facade.addItem()  <- NO enrichment triggered
    -> displayLabel = "Sofa" (if cloud worked) or category.displayName (if not)
    -> User sees: "Home good" or "Unknown" (if cloud fails)

EXPECTED:
ML Kit -> ObjectTracker -> RawDetection
  -> triggerMultiHypothesisClassification()
    -> hypothesis with attributes
  -> createItemFromDetection()
    -> ScannedItem(labelText="Sofa", attributes={brand:"IKEA", color:"White"})
    -> facade.addItem() + extractVisionInsights() triggered
    -> displayLabel = "IKEA Sofa . White"
```

## Fixes

| # | Issue | Location | Fix |
|---|-------|----------|-----|
| 1 | hypothesis.attributes discarded | ItemsViewModel.kt:1121 | Pass `hypothesis?.attributes` converted to ItemAttribute map |
| 2 | No vision enrichment after detection | ItemsViewModel.kt:1125 | Call extractVisionInsights() after addItem() using thumbnail URI |
| 3 | Domain pack unused | ItemsViewModel.kt:1100 | Call BasicCategoryEngine.selectCategory() when hypothesis is null |
| 4 | Category not mapped from hypothesis | ItemsViewModel.kt:1112 | Map hypothesis.categoryId -> domain category -> ItemCategory |
| 5 | Bitmap recycled before enrichment | ItemsViewModel.kt:1130 | Save thumbnail URI before recycling, pass to enrichment |

## Related Files

- `androidApp/.../items/ItemsViewModel.kt` - Item creation from detection
- `androidApp/.../items/ItemsUiFacade.kt` - Facade with addItem vs addItemsWithVisionPrefill
- `androidApp/.../ml/VisionInsightsPrefiller.kt` - Vision enrichment pipeline
- `shared/core-models/.../items/ScannedItem.kt` - displayLabel property
- `core-domainpack/.../category/BasicCategoryEngine.kt` - Domain pack category engine
- `core-domainpack/.../DomainPackProvider.kt` - Singleton provider
- `androidApp/.../classification/hypothesis/ClassificationHypothesis.kt` - Hypothesis model
- `androidApp/.../camera/CameraFrameAnalyzer.kt` - Detection to RawDetection mapping
