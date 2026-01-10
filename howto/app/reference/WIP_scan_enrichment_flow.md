***REMOVED*** Scan Enrichment Flow - Implementation Complete

***REMOVED******REMOVED*** Executive Summary

This document traces the end-to-end scan flow and documents the fixes implemented to make vision enrichment (brand/text/logo/color) work correctly in the UI.

***REMOVED******REMOVED*** Changes Implemented

***REMOVED******REMOVED******REMOVED*** 1. Fixed ID Mismatch Bug (Critical)
- **File**: `ItemsViewModel.kt`
- **Issue**: `addItemsWithVisionPrefill` was passing `item.id` to vision extraction, but after aggregation the items got a different `aggregatedId`
- **Fix**: Changed to use `stateManager.addItemsSync()` which processes items synchronously and returns `AggregatedItem` objects with the correct `aggregatedId`

***REMOVED******REMOVED******REMOVED*** 2. Added itemType Field to Backend
- **File**: `backend/src/modules/vision/routes.ts`
- **Added**: `deriveItemType()` function that maps Vision API labels to sellable nouns (e.g., "lip balm", "T-shirt", "Tissue Box")
- **Response**: Now includes `itemType` field alongside `categoryHint`

***REMOVED******REMOVED******REMOVED*** 3. Updated displayLabel in ScannedItem
- **File**: `shared/core-models/.../ScannedItem.kt`
- **Format**: `{brand} {itemType} · {color}` with fallback logic
- **Priority**: brand+itemType+color > itemType+color > brand+itemType > itemType > labelText > category

***REMOVED******REMOVED******REMOVED*** 4. Added itemType to VisionAttributes Model
- **File**: `shared/core-models/.../VisionAttributes.kt`
- **Added**: `itemType: String?` field

***REMOVED******REMOVED******REMOVED*** 5. Updated Android API Client
- **File**: `VisionInsightsRepository.kt`
- **Added**: `itemType` parsing from backend response
- **Cleaned**: Replaced verbose DIAG logs with clean SCAN_ENRICH logs

***REMOVED******REMOVED******REMOVED*** 6. Added Synchronous Item Addition
- **File**: `ItemsStateManager.kt`
- **Added**: `addItemsSync()` method that processes items through the aggregator synchronously

***REMOVED******REMOVED*** Current Flow (Pre-Fix)

```
┌─────────────────┐
│  CameraScreen   │
│   Shutter Tap   │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────┐
│  CameraXManager             │
│  captureSingleFrame()       │ ← CameraX Preview (1280x720)
│  detectObjects() → Items    │
│  captureHighResImage()      │ ← High-res capture
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  ItemsViewModel                     │
│  addItemsWithVisionPrefill()       │
│  ✓ Attaches fullImageUri to items  │
└────────┬────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────┐
│  ItemsStateManager.addItems()                            │
│  → itemAggregator.processDetections()                    │
│  → Items get NEW aggregatedId (different from item.id!)  │  ← BUG SOURCE
└────────┬─────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────┐
│  VisionInsightsPrefiller.extractAndApply()               │
│  Called with item.id (WRONG - should be aggregatedId)    │  ← BUG
└────────┬─────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────┐
│  ItemsStateManager.applyVisionInsights()                 │
│  Looks up by aggregatedId                                │
│  ❌ ITEM NOT FOUND because id ≠ aggregatedId             │  ← FAILURE
└──────────────────────────────────────────────────────────┘
```

***REMOVED******REMOVED*** Root Causes Identified

***REMOVED******REMOVED******REMOVED*** 1. **ID Mismatch (CRITICAL)**

**Location**: `ItemsViewModel.addItemsWithVisionPrefill()` line 275

**Problem**: The code passes `item.id` to vision extraction, but after items go through the aggregator, they get a new `aggregatedId`. The `applyVisionInsights` method looks up items by `aggregatedId`, so the lookup fails.

```kotlin
// Current (BROKEN):
visionInsightsPrefiller.extractAndApply(
    ...
    itemId = item.id,  // ← Session ID, not aggregatedId!
    ...
)

// ItemsStateManager.applyVisionInsights() does:
val existingItem = allItems.find { it.aggregatedId == aggregatedId }
// ↑ This fails because aggregatedId != item.id
```

**Fix**: After calling `stateManager.addItems(newItems)`, retrieve the aggregated items and use their `aggregatedId` for vision extraction.

***REMOVED******REMOVED******REMOVED*** 2. **Missing itemType Field**

**Problem**: The backend `/v1/vision/insights` endpoint returns `categoryHint` (coarse category like "cosmetics") but not a sellable item type like "lip balm" or "T-shirt".

**Fix**: Add `itemType` inference in the backend from Vision API labels, or derive it client-side from labelHints.

***REMOVED******REMOVED******REMOVED*** 3. **Label Not Using Brand/Color**

**Problem**: `ScannedItem.displayLabel` only uses `labelText` or `category.displayName`. It doesn't incorporate vision attributes (brand, itemType, color) as required by the tactical flow.

**Fix**: Update `displayLabel` to prioritize: `{brand} {itemType} · {color}` format.

***REMOVED******REMOVED*** Backend Analysis

***REMOVED******REMOVED******REMOVED*** `/v1/vision/insights` Endpoint (WORKING)

**Location**: `backend/src/modules/vision/routes.ts`

**Response**:
```json
{
  "success": true,
  "ocrSnippets": ["text1", "text2"],
  "logoHints": [{"name": "Brand", "confidence": 0.95}],
  "dominantColors": [{"name": "blue", "hex": "***REMOVED***1E40AF", "pct": 45}],
  "labelHints": ["Label1", "Label2"],
  "suggestedLabel": "Brand Model",
  "categoryHint": "cosmetics"
}
```

**What's enabled**:
- TEXT_DETECTION (OCR) ✓
- LOGO_DETECTION (brand) ✓
- LABEL_DETECTION (categories) ✓
- IMAGE_PROPERTIES (colors) ✓

**Missing**:
- `itemType` - sellable noun like "lip balm", "T-shirt"

***REMOVED******REMOVED******REMOVED*** Backend Fix Needed

Add `itemType` inference from Vision labels. Use the first specific label as itemType (e.g., "lipstick" from cosmetics, "shirt" from clothing).

***REMOVED******REMOVED*** Android Analysis

***REMOVED******REMOVED******REMOVED*** Components Overview

| Component | Purpose | Status |
|-----------|---------|--------|
| `LocalVisionExtractor` | On-device OCR + colors (Layer A) | ✓ Working |
| `VisionInsightsRepository` | Cloud API calls (Layer B) | ✓ Working |
| `VisionInsightsPrefiller` | Orchestrates both layers | ✓ Working |
| `ItemsStateManager.applyVisionInsights()` | Applies results to items | ✓ Working |
| `ItemsViewModel.addItemsWithVisionPrefill()` | Triggers extraction | ❌ Wrong ID |

***REMOVED******REMOVED******REMOVED*** Data Models

**VisionAttributes** (shared/core-models):
```kotlin
data class VisionAttributes(
    val colors: List<VisionColor>,
    val ocrText: String?,
    val logos: List<VisionLogo>,
    val labels: List<VisionLabel>,
    val brandCandidates: List<String>,
    val modelCandidates: List<String>,
)
```

**ScannedItem** includes:
- `visionAttributes: VisionAttributes`
- `attributes: Map<String, ItemAttribute>`
- `detectedAttributes: Map<String, ItemAttribute>`

***REMOVED******REMOVED******REMOVED*** UI Components

**ItemsListScreen**: Shows `item.displayLabel` which doesn't use vision data.

**EditItemsScreen**: Has `VisionAttributesDisplay` that shows brand/colors but only when item has vision data populated.

***REMOVED******REMOVED*** Implementation Plan

***REMOVED******REMOVED******REMOVED*** Phase 1: Fix ID Mismatch (Critical)

1. In `addItemsWithVisionPrefill()`:
   - Call `stateManager.addItems(newItems)` first
   - Get the resulting aggregated items
   - Match original items to aggregated items by similarity
   - Use `aggregatedId` for vision extraction

***REMOVED******REMOVED******REMOVED*** Phase 2: Backend - Add itemType

1. In `/v1/vision/insights`:
   - Add `itemType` field to response
   - Derive from labelHints (first specific label)
   - Map generic labels to sellable nouns

***REMOVED******REMOVED******REMOVED*** Phase 3: Android - Smart Label Formatting

1. Update `ScannedItem.displayLabel`:
   ```
   Priority:
   1. brand + itemType + color → "{brand} {itemType} · {color}"
   2. itemType + color → "{itemType} · {color}"
   3. itemType only → "{itemType}"
   4. suggestedLabel → as-is
   5. fallback → coarse category
   ```

***REMOVED******REMOVED******REMOVED*** Phase 4: UI Updates

1. Ensure `VisionAttributesDisplay` shows in EditItemsScreen
2. Add "Detected" badge to each attribute
3. Ensure attributes are editable (source switches to USER on edit)

***REMOVED******REMOVED*** Test Cases

1. **Scan clear product (Labello)**:
   - Expected: Label shows "Labello Lip Balm · Blue" (or similar)
   - Brand: "Labello" from logo detection
   - ItemType: "Lip Balm" from labels
   - Color: "Blue" from dominant colors

2. **Scan product without logo (generic box)**:
   - Expected: Label shows "Box · White" (from labels + colors)
   - No brand (acceptable)

3. **Network failure**:
   - Expected: Local extraction still works (OCR + colors)
   - Item exists, user can edit manually

***REMOVED******REMOVED*** Files to Modify

***REMOVED******REMOVED******REMOVED*** Android
- `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt`
- `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/items/ScannedItem.kt`
- `androidApp/src/main/java/com/scanium/app/ml/VisionInsightsRepository.kt`

***REMOVED******REMOVED******REMOVED*** Backend
- `backend/src/modules/vision/routes.ts`

***REMOVED******REMOVED*** Observability

***REMOVED******REMOVED******REMOVED*** Backend Logs (already in place)
- `Vision insights extracted` with counts
- `Vision extraction failed` with error codes

***REMOVED******REMOVED******REMOVED*** Android Logs (to add)
- `Scan enrichment started for item {aggregatedId}`
- `Local extraction completed: OCR={success}, Colors={count}`
- `Cloud extraction completed: Brand={detected}, ItemType={inferred}`
- `Enrichment applied to item {aggregatedId}`
