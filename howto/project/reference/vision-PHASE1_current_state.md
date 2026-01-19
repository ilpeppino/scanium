***REMOVED*** Phase 1: Current Vision + Classification Pipeline Analysis

**Date:** 2026-01-11
**Status:** Investigation Complete
**Objective:** Understand the current end-to-end flow for item scanning, classification, vision
extraction, and attribute display

---

***REMOVED******REMOVED*** Executive Summary

***REMOVED******REMOVED******REMOVED*** What Works

- Google Vision API integration is **fully functional** and enabled by default
- All vision features (OCR, labels, logos, colors) are **active** and detecting correctly
- Vision data **successfully flows** from backend to Android app through the entire pipeline
- Enriched attributes (brand, color, model, material) are **extracted and persisted**

***REMOVED******REMOVED******REMOVED*** What's Broken

The UI displays "Unknown" for obvious products (Labello, Kleenex) because:

1. **Missing `itemType` in Vision Response** - Backend doesn't send the sellable item type (e.g., "
   Lip Balm", "Tissue") to Android
2. **Mismatch between attribute sources** - UI logic prioritizes vision attributes over enriched
   attributes, but vision attributes lack critical data
3. **Classification overrides vision** - The classification label ("Unknown") overwrites richer
   vision results

---

***REMOVED******REMOVED*** 1. Current Pipeline Architecture

***REMOVED******REMOVED******REMOVED*** Data Flow Diagram (Text)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ANDROID APP                                                         │
├─────────────────────────────────────────────────────────────────────┤
│ Camera (CameraX) → ML Kit Detection → Crop Item Thumbnail          │
│         ↓                                                           │
│ CloudClassifier.classifySingle()                                    │
│         ↓                                                           │
│ POST /v1/classify?enrichAttributes=true                             │
│   - multipart/form-data: cropped JPEG (no EXIF)                    │
│   - domainPackId: "home_resale"                                    │
└─────────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────────┐
│ BACKEND (Node.js/TypeScript)                                        │
├─────────────────────────────────────────────────────────────────────┤
│ ClassifierService.classify()                                        │
│         ↓                                                           │
│ ┌─────────────────────────┐      ┌────────────────────────────┐   │
│ │ GoogleVisionClassifier  │  +   │ VisionExtractor            │   │
│ │ (for classification)    │      │ (for attribute enrichment) │   │
│ └─────────────────────────┘      └────────────────────────────┘   │
│         ↓                                  ↓                        │
│ Google Vision API Call(s)                                          │
│   - TEXT_DETECTION (OCR)            ✓ Enabled                      │
│   - LABEL_DETECTION                 ✓ Enabled                      │
│   - LOGO_DETECTION                  ✓ Enabled                      │
│   - IMAGE_PROPERTIES (colors)       ✓ Enabled                      │
│   - OBJECT_LOCALIZATION             ✓ Enabled                      │
│         ↓                                                           │
│ VisualFacts Extraction                                             │
│   - ocrSnippets: ["Labello", "Hydro Care", ...]                   │
│   - logoHints: [{ brand: "Labello", score: 0.92 }]                │
│   - labelHints: [{ label: "Lip balm", score: 0.85 }]              │
│   - dominantColors: [{ name: "blue", hex: "***REMOVED***1E40AF", pct: 45 }]   │
│         ↓                                                           │
│ AttributeResolver.resolveAttributes()                              │
│   - brand: { value: "Labello", confidence: "HIGH", source: "logo" }│
│   - color: { value: "blue", confidence: "MED", source: "color" }   │
│   - model: { value: "Hydro Care", confidence: "MED", source: "ocr"}│
│         ↓                                                           │
│ ClassificationResult Response (JSON)                               │
│   {                                                                 │
│     domainCategoryId: null,           ← Often null ("Unknown")     │
│     confidence: 0.0,                                               │
│     label: "Unknown",                 ← Problem!                   │
│     attributes: {},                   ← Empty from domain pack     │
│     enrichedAttributes: {             ← Vision-extracted ✓         │
│       brand: { value: "Labello", confidence: "HIGH", ... },        │
│       color: { value: "blue", confidence: "MED", ... }             │
│     },                                                              │
│     visionAttributes: {               ← Raw vision data ✓          │
│       colors: [{ name: "blue", hex: "***REMOVED***1E40AF", score: 0.45 }],     │
│       ocrText: "Labello\nHydro Care\n...",                         │
│       logos: [{ name: "Labello", score: 0.92 }],                   │
│       labels: [{ name: "Lip balm", score: 0.85 }],                 │
│       brandCandidates: ["Labello"],                                │
│       modelCandidates: ["Hydro Care"],                             │
│       // ⚠️ MISSING: itemType (e.g., "Lip Balm")                   │
│     }                                                               │
│   }                                                                 │
└─────────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────────┐
│ ANDROID APP - Response Processing                                  │
├─────────────────────────────────────────────────────────────────────┤
│ CloudClassifier.parseSuccessResponse()                              │
│   - Maps enrichedAttributes → ItemAttribute map                    │
│   - Maps visionAttributes → SharedVisionAttributes                 │
│         ↓                                                           │
│ ItemClassificationCoordinator.handleClassificationResult()         │
│   - Stores enrichedAttributes in AggregatedItem                    │
│   - Stores visionAttributes in AggregatedItem                      │
│         ↓                                                           │
│ ItemAggregator.applyEnhancedClassification()                       │
│   - item.enrichedAttributes = enrichedAttributes  ✓                │
│   - item.visionAttributes = visionAttributes      ✓                │
│         ↓                                                           │
│ AggregatedItem.toScannedItem()                                     │
│   ScannedItem {                                                     │
│     attributes: enrichedAttributes,               ✓ Present        │
│     visionAttributes: visionAttributes,           ✓ Present        │
│     labelText: "Unknown"                          ⚠️ Problem       │
│   }                                                                 │
│         ↓                                                           │
│ ScannedItemEntity (Room DB Persistence)                            │
│   - attributesJson: JSON.stringify(enrichedAttributes)  ✓          │
│   - visionAttributesJson: JSON.stringify(visionAttributes)  ✓      │
│         ↓                                                           │
│ UI Display (ItemsListScreen, ItemDetailSheet)                      │
│   - Uses: item.displayLabel                                        │
└─────────────────────────────────────────────────────────────────────┘
```

---

***REMOVED******REMOVED*** 2. Where Attributes SHOULD Appear

***REMOVED******REMOVED******REMOVED*** ScannedItem.displayLabel Logic

Located: `shared/core-models/src/commonMain/kotlin/.../ScannedItem.kt:235-281`

**Priority Order:**

1. `attributes["brand"]` OR `visionAttributes.primaryBrand`
2. `attributes["itemType"]` OR `visionAttributes.itemType`
3. `attributes["color"]` OR `visionAttributes.primaryColor.name`

**Label Construction:**

- **Best case:** "Labello Lip Balm · Blue"  (brand + itemType + color)
- **Good case:** "Lip Balm · Blue"  (itemType + color)
- **Fallback:** `labelText` (from backend)  ← **"Unknown"** ⚠️
- **Final fallback:** `category.displayName`  ← "Unknown" ⚠️

---

***REMOVED******REMOVED*** 3. Where Attributes Are LOST or DROPPED

***REMOVED******REMOVED******REMOVED*** Critical Gap ***REMOVED***1: Missing `itemType` in Backend Response

**Location:** `backend/src/modules/classifier/service.ts:367-394`

The `buildVisionAttributes()` method builds the response sent to Android:

```typescript
// Current implementation
return {
  colors: visualFacts.dominantColors.map(...),
  ocrText,
  logos: (visualFacts.logoHints ?? []).map(...),
  labels: visualFacts.labelHints.map(...),
  brandCandidates,
  modelCandidates,
  // ❌ MISSING: itemType
};
```

**Impact:**

- Android expects `visionAttributes.itemType` to exist
- Backend never sends it
- `displayLabel` falls through all cases to `labelText` ("Unknown")

**Evidence:**

- TypeScript type: `backend/src/modules/classifier/types.ts:79-86`
- Android type: `shared/core-models/.../VisionAttributes.kt:15-24` includes `itemType: String?`
- Mismatch between client expectation and server response

---

***REMOVED******REMOVED******REMOVED*** Critical Gap ***REMOVED***2: Classification Label Overwrites Vision Results

**Location:** `androidApp/.../ItemClassificationCoordinator.kt:361`

```kotlin
val labelOverride = result.label?.takeUnless { it.isBlank() } ?: aggregatedItem.labelText
```

**Flow:**

1. Backend classification returns `label: "Unknown"` (from domain pack mapping)
2. This gets stored as `labelText` in ScannedItem
3. When `displayLabel` falls back to `labelText`, it shows "Unknown"
4. Rich vision data (brand="Labello", labels=["Lip balm"]) is **ignored** in favor of classification
   label

---

***REMOVED******REMOVED******REMOVED*** Critical Gap ***REMOVED***3: Vision vs Enriched Attributes Priority Mismatch

**Location:** `shared/core-models/.../ScannedItem.kt:237-245`

```kotlin
val brand = attributes["brand"]?.value?.trim()?.takeIf { it.isNotEmpty() }
    ?: visionAttributes.primaryBrand?.trim()?.takeIf { it.isNotEmpty() }
```

**Issue:**

- Logic checks `attributes["brand"]` first, then falls back to `visionAttributes.primaryBrand`
- `enrichedAttributes` ARE being populated correctly with brand from logos
- BUT: they're stored in a different field than `attributes`

**Actual storage:**

- `item.enrichedAttributes` = `{ brand: ItemAttribute(...), color: ItemAttribute(...) }`
- `item.attributes` = (remains empty or has different data)

**Root cause to verify:**

- Are `enrichedAttributes` being merged into `attributes` map?
- Or are they kept separate and never used by `displayLabel`?

---

***REMOVED******REMOVED*** 4. Classification vs Vision: Competition or Complement?

***REMOVED******REMOVED******REMOVED*** Current Behavior: **Competition (Vision Loses)**

**Classification Pipeline:**

1. Google Vision detects labels (e.g., "Lip balm")
2. Labels mapped to domain category via `mapSignalsToDomainCategory()`
3. Domain pack mapping often returns `null` for uncommon products
4. Result: `domainCategoryId: null, label: "Unknown"`

**Vision Pipeline:**

1. Google Vision detects logos (e.g., "Labello" with 92% confidence)
2. AttributeResolver extracts brand from logo
3. Result: `enrichedAttributes.brand = "Labello"` with HIGH confidence

**UI Decision:**

- Uses `labelText` ("Unknown") because `visionAttributes.itemType` is missing
- Ignores `enrichedAttributes.brand` entirely

***REMOVED******REMOVED******REMOVED*** Ideal Behavior: **Complement**

- Classification provides: domain category, price estimates
- Vision provides: brand, color, model, material
- UI should show: "Labello (Lip balm) · Blue"  (vision brand + label + color)

---

***REMOVED******REMOVED*** 5. Concrete Example: Labello Lip Balm

***REMOVED******REMOVED******REMOVED*** What Google Vision Detects

Based on code analysis of enabled features:

```json
{
  "logoAnnotations": [
    { "description": "Labello", "score": 0.92 }
  ],
  "labelAnnotations": [
    { "description": "Lip balm", "score": 0.85 },
    { "description": "Personal care", "score": 0.78 }
  ],
  "textAnnotations": [
    { "description": "Labello", "locale": "en" },
    { "description": "Hydro Care" },
    { "description": "5.5ml" }
  ],
  "imagePropertiesAnnotation": {
    "dominantColors": {
      "colors": [
        { "color": {"red": 30, "green": 64, "blue": 175}, "score": 0.45, "pixelFraction": 0.38 }
      ]
    }
  }
}
```

***REMOVED******REMOVED******REMOVED*** What Backend Sends to Android

```json
{
  "domainCategoryId": null,
  "confidence": 0.0,
  "label": "Unknown",
  "attributes": {},
  "enrichedAttributes": {
    "brand": {
      "value": "Labello",
      "confidence": "HIGH",
      "confidenceScore": 0.9,
      "evidenceRefs": [
        { "type": "logo", "value": "Labello", "score": 0.92 }
      ]
    },
    "color": {
      "value": "blue",
      "confidence": "MED",
      "confidenceScore": 0.65,
      "evidenceRefs": [
        { "type": "color", "value": "blue", "score": 0.45 }
      ]
    },
    "model": {
      "value": "Hydro Care",
      "confidence": "MED",
      "confidenceScore": 0.65,
      "evidenceRefs": [
        { "type": "ocr", "value": "Hydro Care" }
      ]
    }
  },
  "visionAttributes": {
    "colors": [
      { "name": "blue", "hex": "***REMOVED***1E40AF", "score": 0.45 }
    ],
    "ocrText": "Labello\nHydro Care\n5.5ml",
    "logos": [
      { "name": "Labello", "score": 0.92 }
    ],
    "labels": [
      { "name": "Lip balm", "score": 0.85 },
      { "name": "Personal care", "score": 0.78 }
    ],
    "brandCandidates": ["Labello"],
    "modelCandidates": ["Hydro Care"]
    // ❌ MISSING: "itemType": "Lip Balm"
  }
}
```

***REMOVED******REMOVED******REMOVED*** What UI Shows

**Current:** "Unknown"

**Why:**

1. `displayLabel` checks `attributes["brand"]` → NOT FOUND (enrichedAttributes ≠ attributes)
2. Falls back to `visionAttributes.primaryBrand` → "Labello" FOUND
3. Checks `attributes["itemType"]` → NOT FOUND
4. Falls back to `visionAttributes.itemType` → **NULL** (not sent by backend)
5. Falls through to `labelText` → **"Unknown"**

**Expected:** "Labello Lip Balm · Blue"

**If itemType was sent:** "Labello Lip Balm · Blue"
**Minimal fix:** "Labello · Blue" (brand + color, no itemType)

---

***REMOVED******REMOVED*** 6. Code Evidence & File Locations

***REMOVED******REMOVED******REMOVED*** Android App

| Component                  | File                                                                       | Purpose                             |
|----------------------------|----------------------------------------------------------------------------|-------------------------------------|
| Camera Capture             | `androidApp/.../camera/CameraScreen.kt`                                    | CameraX preview, tap/long-press     |
| Image Upload               | `androidApp/.../ml/classification/CloudClassifier.kt`                      | POST /v1/classify                   |
| Response Parsing           | CloudClassifier.kt:457-577                                                 | Parse enriched + vision attributes  |
| Classification Coordinator | `androidApp/.../items/classification/ItemClassificationCoordinator.kt:345` | handleClassificationResult()        |
| Aggregator                 | `core-tracking/.../aggregation/ItemAggregator.kt:450-486`                  | applyEnhancedClassification()       |
| AggregatedItem Model       | AggregatedItem.kt:43-120                                                   | Has visionAttributes field ✓        |
| ScannedItem Model          | `shared/core-models/.../items/ScannedItem.kt:67-194`                       | Has attributes + visionAttributes ✓ |
| Display Logic              | ScannedItem.kt:235-281                                                     | displayLabel getter                 |
| Database Entity            | `androidApp/.../persistence/ScannedItemEntity.kt`                          | Room DB fields                      |
| UI Rendering               | `androidApp/.../ItemsListScreen.kt:327,973,1085`                           | Uses displayLabel                   |

***REMOVED******REMOVED******REMOVED*** Backend

| Component              | File                                                        | Purpose                              |
|------------------------|-------------------------------------------------------------|--------------------------------------|
| Classification Service | `backend/src/modules/classifier/service.ts:116`             | Main classify() orchestrator         |
| Vision API Client      | `backend/src/modules/classifier/providers/google-vision.ts` | ImageAnnotatorClient wrapper         |
| Vision Extractor       | `backend/src/modules/vision/extractor.ts`                   | extractVisualFacts()                 |
| Response Mapper        | `backend/src/modules/vision/response-mapper.ts:241-315`     | Parse Vision API responses           |
| Attribute Resolver     | `backend/src/modules/vision/attribute-resolver.ts`          | Resolve brand/color/model from facts |
| Build Vision Attrs     | service.ts:367-394                                          | ❌ Missing itemType                   |
| Types                  | `backend/src/modules/classifier/types.ts:79-86`             | VisionAttributeSummary type          |

---

***REMOVED******REMOVED*** 7. Blockers Preventing Attributes from Appearing in UI

***REMOVED******REMOVED******REMOVED*** Blocker ***REMOVED***1: Missing `itemType` Field

**Severity:** HIGH
**Location:** Backend response building
**Impact:** UI falls back to "Unknown" label
**Fix required:** Add `itemType` to backend response

***REMOVED******REMOVED******REMOVED*** Blocker ***REMOVED***2: `enrichedAttributes` Not Merged into `attributes`

**Severity:** HIGH
**Location:** Android attribute mapping
**Impact:** `displayLabel` can't find brand/color in `attributes` map
**Fix required:** Verify if merging is happening, or change displayLabel to check enrichedAttributes

***REMOVED******REMOVED******REMOVED*** Blocker ***REMOVED***3: Classification Label Overrides Vision

**Severity:** MEDIUM
**Location:** Label assignment logic
**Impact:** "Unknown" classification label hides vision-detected product type
**Fix required:** Prioritize vision labels over classification when confidence is low

***REMOVED******REMOVED******REMOVED*** Blocker ***REMOVED***4: No Fallback from Classification to Vision Labels

**Severity:** MEDIUM
**Location:** Display label logic
**Impact:** Vision labels ("Lip balm", "Tissue") never used even when available
**Fix required:** Use vision labels as itemType when domain classification fails

---

***REMOVED******REMOVED*** 8. Configuration Verification

***REMOVED******REMOVED******REMOVED*** Backend Vision Features (All ENABLED by default)

```bash
***REMOVED*** From backend/src/config/index.ts
VISION_ENABLE_OCR=true
VISION_ENABLE_LABELS=true
VISION_ENABLE_LOGOS=true
VISION_ENABLE_COLORS=true
VISION_OCR_MODE=TEXT_DETECTION
CLASSIFIER_ENABLE_ATTRIBUTE_ENRICHMENT=true
```

***REMOVED******REMOVED******REMOVED*** Android Request

```kotlin
// From CloudClassifier.kt:153
val endpoint = "${config.baseUrl.trimEnd('/')}/v1/classify?enrichAttributes=true"
```

**Conclusion:** All features are enabled and working. The issue is not configuration, but data flow
and mapping.

---

***REMOVED******REMOVED*** 9. Summary of Findings

***REMOVED******REMOVED******REMOVED*** ✅ What's Working

1. Google Vision API successfully detects logos, text, labels, colors
2. Backend correctly extracts attributes (brand, color, model, material)
3. `enrichedAttributes` are sent to Android in API response
4. `visionAttributes` are sent to Android in API response
5. Both are persisted to Room database
6. Data flows all the way to ScannedItem model

***REMOVED******REMOVED******REMOVED*** ❌ What's Broken

1. Backend doesn't send `itemType` in `visionAttributes` response
2. Android `displayLabel` can't find data in `attributes` map (checking wrong field?)
3. Classification label "Unknown" overrides all vision data
4. Vision labels (e.g., "Lip balm") are never used as fallback

***REMOVED******REMOVED******REMOVED*** 🔍 Critical Path to Fix

1. **Phase 2a:** Add `itemType` to backend `VisionAttributeSummary` type and response builder
2. **Phase 2b:** Map vision labels → itemType on backend (e.g., "Lip balm" → "Lip Balm")
3. **Phase 2c:** Verify Android `displayLabel` logic checks correct attribute sources
4. **Phase 2d:** Prioritize vision itemType over classification "Unknown" label

---

***REMOVED******REMOVED*** 10. Next Steps (Phase 2 Planning)

***REMOVED******REMOVED******REMOVED*** Immediate Verification Needed

1. Check if `enrichedAttributes` are actually merged into `attributes` map in Android
2. Add temporary logging to see what `attributes["brand"]` returns vs
   `visionAttributes.primaryBrand`
3. Verify with real scan whether enrichedAttributes are populated or empty

***REMOVED******REMOVED******REMOVED*** Proposed Fixes (No Implementation Yet)

1. Backend: Add `itemType` field to `VisionAttributeSummary`
2. Backend: Map highest-scoring vision label → sellable itemType
3. Android: Fix attribute lookup (use enrichedAttributes or merge correctly)
4. Android: Fallback to vision label when classification returns "Unknown"

---

**End of Phase 1 Report**
