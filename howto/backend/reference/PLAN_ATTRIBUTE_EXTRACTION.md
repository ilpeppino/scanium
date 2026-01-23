# Implementation Plan: Enhanced Attribute Extraction & Marketplace Description Generation

**Version:** 1.0
**Author:** Claude (Architecture Review)
**Date:** 2026-01-03

---

## Executive Summary

This plan enhances Scanium's item classification pipeline with:

1. **Attribute extraction** (brand, model, color, material, condition) using existing Google Vision
   infrastructure
2. **Marketplace-ready description generation** via AI Assistant

**Key Finding:** The backend already has 80% of the infrastructure built. The main gaps are:

- Android `ScannedItem` model lacks an `attributes` field
- Classification response attributes are received but not persisted
- UI has no attribute display/editing
- Assistant provider is mock-only (needs real LLM)

---

## A. Current State Architecture

### Scan Pipeline Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              ANDROID APP                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  CameraXManager ──▶ ML Kit Object Detection ──▶ ObjectTracker               │
│  (camera/CameraXManager.kt:1796-1840)          (aggregation/ObjectTracker)  │
│                                                                             │
│        ▼                                                                    │
│  ItemAggregator ◀──────────────────────────────────────────────────────────│
│  (aggregation/ItemAggregator.kt)                                           │
│        │                                                                    │
│        │ Stable items (mergeCount >= 5, age >= 500ms)                      │
│        ▼                                                                    │
│  ItemClassificationCoordinator ──▶ ClassificationOrchestrator              │
│  (items/classification/ItemClassificationCoordinator.kt:71-401)            │
│        │                                                                    │
│        │ CloudCallGate filters: stability, cooldown, dedup                 │
│        ▼                                                                    │
│  CloudClassifier ──────────────────────────────────────────────────────────│
│  (ml/classification/CloudClassifier.kt:77-410)                             │
│        │                                                                    │
│        │ POST /v1/classify (multipart: image + domainPackId)               │
│        │ Headers: X-API-Key, X-Scanium-Correlation-Id                       │
│        ▼                                                                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              BACKEND                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  classifierRoutes (POST /v1/classify)                                      │
│  (modules/classifier/routes.ts:75-356)                                     │
│        │                                                                    │
│        │ Auth: API Key validation + HMAC signature                         │
│        │ Rate limit: IP, API key, device (sliding window)                  │
│        │ Cache: SHA-256 image hash (in-memory LRU)                         │
│        ▼                                                                    │
│  ClassifierService.classify()                                              │
│  (modules/classifier/service.ts:60-82)                                     │
│        │                                                                    │
│        ├──▶ GoogleVisionClassifier (LABEL_DETECTION or OBJECT_LOCALIZATION)│
│        │    (modules/classifier/providers/google-vision.ts:17-111)         │
│        │                                                                    │
│        ▼                                                                    │
│  mapSignalsToDomainCategory()                                              │
│  (modules/classifier/domain/mapper.ts:39-144)                              │
│        │                                                                    │
│        │ Token matching against DomainPack categories                      │
│        │ Returns: domainCategoryId, confidence, label, attributes          │
│        ▼                                                                    │
│  Response: { domainCategoryId, confidence, label, attributes, requestId }  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key File Locations

| Component                      | File                                                                   | Lines   |
|--------------------------------|------------------------------------------------------------------------|---------|
| Cloud Classifier (Android)     | `androidApp/.../ml/classification/CloudClassifier.kt`                  | 77-410  |
| Classification Result          | `core-models/.../ml/classification/ClassificationResult.kt`            | 20-30   |
| Classification Coordinator     | `androidApp/.../items/classification/ItemClassificationCoordinator.kt` | 71-401  |
| Cloud Call Gate                | `androidApp/.../ml/classification/CloudCallGate.kt`                    | 13-102  |
| Items State Manager            | `androidApp/.../items/state/ItemsStateManager.kt`                      | 1-600+  |
| Scanned Item Model             | `shared/core-models/.../items/ScannedItem.kt`                          | 66-128  |
| Scanned Item Entity            | `androidApp/.../items/persistence/ScannedItemEntity.kt`                | 16-200+ |
| Backend Classifier Routes      | `backend/src/modules/classifier/routes.ts`                             | 75-356  |
| Backend Classifier Service     | `backend/src/modules/classifier/service.ts`                            | 24-150  |
| Google Vision Classifier       | `backend/src/modules/classifier/providers/google-vision.ts`            | 10-111  |
| Domain Mapper                  | `backend/src/modules/classifier/domain/mapper.ts`                      | 39-144  |
| Vision Extractor               | `backend/src/modules/vision/extractor.ts`                              | 172-396 |
| Attribute Resolver             | `backend/src/modules/vision/attribute-resolver.ts`                     | 407-428 |
| Assistant Routes               | `backend/src/modules/assistant/routes.ts`                              | 135-961 |
| Assistant Repository (Android) | `androidApp/.../selling/assistant/AssistantRepository.kt`              | 50-628  |
| Domain Pack JSON               | `core-domainpack/src/main/res/raw/home_resale_domain_pack.json`        | 1-434   |

---

## B. Attribute Extraction Gap Analysis

### Desired Attributes

| Attribute     | Exists in Domain Pack | Extraction Method             | Backend Support                                                    | Android Support               |
|---------------|-----------------------|-------------------------------|--------------------------------------------------------------------|-------------------------------|
| **brand**     | Yes (lines 309-325)   | OCR                           | **PARTIAL** (VisionExtractor has OCR, attribute-resolver resolves) | **NO** (not stored)           |
| **model**     | Yes (lines 381-392)   | OCR                           | **PARTIAL** (same as brand)                                        | **NO**                        |
| **color**     | Yes (lines 327-337)   | CLIP → server-side extraction | **YES** (color-extractor.ts)                                       | **NO**                        |
| **material**  | Yes (lines 339-351)   | CLIP → label hints            | **PARTIAL** (label detection)                                      | **NO**                        |
| **size**      | Yes (lines 353-363)   | HEURISTIC                     | **NO**                                                             | **NO**                        |
| **condition** | Yes (lines 365-378)   | CLOUD                         | **NO** (requires LLM)                                              | **NO**                        |
| **year**      | Yes (lines 393-402)   | OCR                           | **PARTIAL** (OCR exists)                                           | **NO**                        |
| **sku/isbn**  | Yes (lines 406-423)   | BARCODE                       | **NO** (ML Kit on device)                                          | **YES** (barcodeValue exists) |

### Current Gaps

1. **ScannedItem model** (`shared/core-models/.../items/ScannedItem.kt:66-88`):
    - **Missing:** `attributes: Map<String, String>` field
    - **Missing:** `attributeConfidence: Map<String, Float>` field

2. **ScannedItemEntity** (`androidApp/.../items/persistence/ScannedItemEntity.kt`):
    - **Missing:** `attributes` column (JSON string or separate table)

3. **ItemClassificationCoordinator.handleClassificationResult()** (lines 309-372):
    - **Bug:** `result.attributes` is received but NOT passed to state manager

4. **Backend /v1/classify**:
    - **Gap:** Only uses LABEL_DETECTION, not full VisionExtractor with OCR/logos/colors
    - **Gap:** Domain mapper returns category.attributes (static), not extracted attributes

5. **Assistant /v1/assist/chat**:
    - **Provider:** `GroundedMockAssistantProvider` returns mock responses
    - **Need:** Real LLM provider (Claude/GPT) for description generation

---

## C. Target Design: 3-Stage Intelligence Pipeline

### Stage 1: Category Classification (EXISTING)

```
Image → GoogleVisionClassifier → Labels → DomainMapper → domainCategoryId
```

**No changes needed.** Already works with cloud classification.

### Stage 2: Attribute Extraction (NEW)

```
                                ┌──────────────────────────┐
                                │    /v1/classify/enrich   │
                                │    (new endpoint)        │
                                └───────────┬──────────────┘
                                            │
              ┌─────────────────────────────┼─────────────────────────────┐
              │                             │                             │
              ▼                             ▼                             ▼
    ┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
    │ VisionExtractor │         │ VisionExtractor │         │ VisionExtractor │
    │ (OCR: brand,    │         │ (Colors:        │         │ (Logos:         │
    │  model, year)   │         │  dominant hues) │         │  brand hints)   │
    └────────┬────────┘         └────────┬────────┘         └────────┬────────┘
              │                          │                          │
              └─────────────────────────┬┘─────────────────────────┘
                                        │
                                        ▼
                          ┌──────────────────────────┐
                          │   AttributeResolver       │
                          │   (attribute-resolver.ts) │
                          │                          │
                          │  - Brand: logo > OCR     │
                          │  - Model: OCR patterns   │
                          │  - Color: dominant %     │
                          │  - Material: labels      │
                          └───────────┬──────────────┘
                                      │
                                      ▼
                          ┌──────────────────────────┐
                          │   ResolvedAttributes     │
                          │   {                      │
                          │     brand: { value,      │
                          │              confidence, │
                          │              evidence }  │
                          │     model: ...           │
                          │     color: ...           │
                          │   }                      │
                          └──────────────────────────┘
```

**Vision Features to Use:**

| Attribute | Vision Feature                              | Normalization                                                                              |
|-----------|---------------------------------------------|--------------------------------------------------------------------------------------------|
| Brand     | LOGO_DETECTION + TEXT_DETECTION             | Brand dictionary matching (lines 52-85 of attribute-resolver.ts)                           |
| Model     | TEXT_DETECTION                              | Model regex pattern (line 105)                                                             |
| Color     | Server-side extraction (color-extractor.ts) | 11 basic colors: black, white, gray, red, orange, yellow, green, blue, purple, pink, brown |
| Material  | LABEL_DETECTION                             | Material label list (lines 353-355)                                                        |
| Year      | TEXT_DETECTION                              | 4-digit year regex: `/\b(19                                                                |20)\d{2}\b/` |

**Cost Controls:**

- Only trigger for stable items (CloudCallGate already enforces)
- Cache by image hash (already exists in classifier)
- Batch multiple images per item in single Vision call
- Rate limit: 30 enrichments/minute/API key

### Stage 3: Marketplace Description Generation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ListingDraft Schema                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  {                                                                          │
│    "itemId": "abc123",                                                     │
│    "category": "electronics_laptop",                                        │
│    "categoryLabel": "Laptop",                                               │
│    "attributes": {                                                          │
│      "brand": { "value": "Dell", "confidence": "HIGH", "source": "logo" }, │
│      "model": { "value": "XPS 15", "confidence": "MED", "source": "ocr" }, │
│      "color": { "value": "silver", "confidence": "HIGH", "source": "color"}│
│    },                                                                       │
│    "priceEstimate": { "low": 450, "high": 650, "currency": "EUR" },        │
│    "photosCount": 3,                                                        │
│    "userNotes": "Bought 2 years ago, minor scratch on lid"                 │
│  }                                                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Prompt Strategy                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  SYSTEM:                                                                    │
│  You are a marketplace listing assistant. Generate titles and descriptions  │
│  for second-hand items. Be accurate - only use information provided.        │
│  If brand/model is uncertain (LOW confidence), say "Unknown" and ask user   │
│  to confirm.                                                                │
│                                                                             │
│  OUTPUT FORMAT (JSON):                                                      │
│  {                                                                          │
│    "title": "Dell XPS 15 Laptop - Silver - Good Condition",                │
│    "bullets": [                                                             │
│      "Brand: Dell (verified from logo)",                                   │
│      "Model: XPS 15 (verified from label)",                                │
│      "Color: Silver",                                                       │
│      "Includes: Charger (if mentioned by user)"                            │
│    ],                                                                       │
│    "description": "...",                                                   │
│    "suggestedCategory": "Computers & Tablets > Laptops",                   │
│    "warnings": ["Model confidence is medium - please verify"],             │
│    "missingInfo": ["Storage capacity", "Screen size"]                      │
│  }                                                                          │
│                                                                             │
│  GUARDRAILS:                                                                │
│  - Never hallucinate brand/model not in attributes                         │
│  - If confidence=LOW, prefix with "Possibly" or mark as "Unknown"          │
│  - Always include evidence source (logo/ocr/color) in response             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Localization:**

- Default: English
- Supported (with assistantPrefs.language): Dutch (NL), German (DE), French (FR), Italian (IT)
- Prompt includes language instruction based on prefs

---

## D. PR Implementation Plan

### PR 1: Data Model - Add Attributes to ScannedItem

**Scope:** Add `attributes` field to item models across all layers.

**Files to Change:**

| File                                                | Change                                                                               |
|-----------------------------------------------------|--------------------------------------------------------------------------------------|
| `shared/core-models/.../items/ScannedItem.kt`       | Add `attributes: Map<String, ItemAttribute>? = null` field                           |
| `core-models/.../ItemAttribute.kt` (NEW)            | Create `data class ItemAttribute(value: String, confidence: Float, source: String?)` |
| `androidApp/.../persistence/ScannedItemEntity.kt`   | Add `attributesJson: String?` column, converters                                     |
| `androidApp/.../persistence/ScannedItemDatabase.kt` | Add migration from version 3 → 4                                                     |

**Risks:**

- Database migration could fail on existing installs → Add fallback to clear DB on migration failure
- Large attribute maps could bloat DB → Limit to 20 attributes max per item

**Tests:**

- Unit: `ScannedItemEntityTest.kt` - verify attributes serialization/deserialization
- Unit: Migration test - upgrade from v3 → v4 preserves existing items

**Acceptance Criteria:**

- [ ] `ScannedItem.attributes` field exists and is optional
- [ ] Entity can persist/load items with attributes
- [ ] Existing items without attributes load successfully
- [ ] Migration from v3 to v4 succeeds without data loss

---

### PR 2: Backend - Integrate VisionExtractor into /v1/classify

**Scope:** Enhance classification endpoint to extract attributes using existing VisionExtractor.

**Files to Change:**

| File                                        | Change                                                  |
|---------------------------------------------|---------------------------------------------------------|
| `backend/src/modules/classifier/routes.ts`  | Add optional `enrichAttributes=true` query param        |
| `backend/src/modules/classifier/service.ts` | Inject VisionExtractor, call when enrichAttributes=true |
| `backend/src/modules/classifier/types.ts`   | Add `EnrichedClassificationResult` with attributes      |
| `backend/src/config/index.ts`               | Add `classifier.enableAttributeEnrichment` flag         |

**Implementation:**

```typescript
// In ClassifierService.classify()
if (request.enrichAttributes && config.classifier.enableAttributeEnrichment) {
  const visionExtractor = new VisionExtractor(visionConfig);
  const facts = await visionExtractor.extractVisualFacts(
    request.requestId,
    [{ base64Data: request.buffer.toString('base64'), mimeType: request.contentType }],
    { enableOcr: true, enableLabels: true, enableLogos: true, enableColors: true }
  );

  if (facts.success && facts.facts) {
    const resolved = resolveAttributes(request.requestId, facts.facts);
    result.attributes = flattenResolvedAttributes(resolved);
    result.attributeEvidence = resolved; // Full evidence for debugging
  }
}
```

**Risks:**

- Vision API latency adds ~200-500ms → Make enrichment opt-in
- Vision API cost increases → Add per-API-key daily quota
- Logo detection has additional cost → Make configurable

**Tests:**

- Unit: `ClassifierService.test.ts` - mock VisionExtractor, verify attributes populated
- Integration: `classifier.routes.test.ts` - E2E with mock Vision provider

**Acceptance Criteria:**

- [ ] `GET /v1/classify?enrichAttributes=true` returns attributes in response
- [ ] Attributes include brand, model, color, material when detectable
- [ ] Each attribute has confidence tier (HIGH/MED/LOW) and evidence source
- [ ] Request latency < 2s (p95) with enrichment enabled
- [ ] Enrichment respects config flag and can be disabled

---

### PR 3: Android - Store and Pass Attributes Through Pipeline

**Scope:** Update Android classification pipeline to persist attributes from backend.

**Files to Change:**

| File                                                                   | Change                                                            |
|------------------------------------------------------------------------|-------------------------------------------------------------------|
| `androidApp/.../ml/classification/CloudClassifier.kt`                  | Add `enrichAttributes=true` to request, parse response attributes |
| `androidApp/.../items/classification/ItemClassificationCoordinator.kt` | Pass `result.attributes` to state manager                         |
| `androidApp/.../items/state/ItemsStateManager.kt`                      | Add `applyAttributes(itemId, attributes)` method                  |
| `androidApp/.../aggregation/ItemAggregator.kt`                         | Store attributes on AggregatedItem                                |
| `androidApp/.../items/persistence/ScannedItemRepository.kt`            | Serialize/deserialize attributes                                  |

**Key Changes in handleClassificationResult():**

```kotlin
// ItemClassificationCoordinator.kt:330+
stateManager.applyEnhancedClassification(
    aggregatedId = aggregatedItem.aggregatedId,
    category = categoryOverride,
    label = labelOverride,
    priceRange = priceRange,
    classificationConfidence = result.confidence,
    attributes = result.attributes,  // NEW: pass attributes
)
```

**Risks:**

- Attributes may be null from backend → Handle gracefully, don't overwrite existing
- Large attribute maps could cause OOM → Limit to 20 per item

**Tests:**

- Unit: `ItemClassificationCoordinatorTest.kt` - verify attributes flow to state manager
- Unit: `ItemsStateManagerTest.kt` - verify attributes persisted and loaded

**Acceptance Criteria:**

- [ ] Cloud classification returns attributes from backend
- [ ] Attributes stored in ScannedItem and persisted to database
- [ ] Attributes survive app restart (process death)
- [ ] Items without attributes from backend retain empty map (not null)

---

### PR 4: Android UI - Display Attributes in Item Details

**Scope:** Show extracted attributes in item detail view with edit capability.

**Files to Change:**

| File                                                           | Change                                                                 |
|----------------------------------------------------------------|------------------------------------------------------------------------|
| `androidApp/.../items/ItemsListScreen.kt`                      | Show attribute badges (brand, color) on list item                      |
| `androidApp/.../items/ItemDetailSheet.kt` (NEW or existing)    | Display all attributes with confidence indicators                      |
| `androidApp/.../items/components/AttributeChip.kt` (NEW)       | Reusable chip with confidence color (green=HIGH, yellow=MED, gray=LOW) |
| `androidApp/.../items/components/AttributeEditDialog.kt` (NEW) | Dialog to edit/confirm attribute value                                 |

**UI Design:**

```
┌──────────────────────────────────────────┐
│  [IMG]  Dell XPS 15 Laptop              │
│         €450 - €650                      │
│         ┌──────┐ ┌────────┐ ┌──────┐    │
│         │ Dell │ │ Silver │ │ XPS  │    │ ← Attribute chips
│         │  ✓   │ │   ✓    │ │  ?   │    │ ← Confidence icons
│         └──────┘ └────────┘ └──────┘    │
└──────────────────────────────────────────┘
```

**Chip Colors:**

- GREEN (✓): HIGH confidence - verified
- YELLOW (?): MED confidence - likely correct
- GRAY: LOW confidence - needs confirmation

**Risks:**

- Too many chips clutters UI → Show max 3 on list, all in detail sheet
- Low confidence attributes confuse users → Clear visual distinction

**Tests:**

- UI: Screenshot tests for chips at each confidence level
- Unit: AttributeChip renders correctly for all states

**Acceptance Criteria:**

- [ ] Items with attributes show up to 3 chips on list view
- [ ] Tapping item shows full attribute list in detail sheet
- [ ] Each attribute shows confidence indicator
- [ ] Low confidence attributes show "Tap to verify" prompt
- [ ] Editing attribute updates ScannedItem and persists

---

### PR 5: Android UI - "Generate Description" Action

**Scope:** Add button to generate marketplace listing using Assistant API.

**Files to Change:**

| File                                                      | Change                                                |
|-----------------------------------------------------------|-------------------------------------------------------|
| `androidApp/.../selling/assistant/AssistantRepository.kt` | Already supports `ItemContextSnapshot.attributes`     |
| `androidApp/.../items/ItemDetailSheet.kt`                 | Add "Generate Listing" button                         |
| `androidApp/.../selling/GeneratedListingScreen.kt` (NEW)  | Display generated title/description with copy buttons |
| `androidApp/.../model/ListingDraft.kt` (NEW)              | Data class for generated listing                      |

**User Flow:**

```
1. User views item detail sheet
2. Taps "Generate Listing" button
3. Loading spinner while Assistant API called
4. Generated listing appears:
   ┌─────────────────────────────────────┐
   │ Generated Listing                   │
   │                                     │
   │ Title:                              │
   │ ┌─────────────────────────────────┐ │
   │ │Dell XPS 15 Laptop - Silver     │ │
   │ │                          [Copy]│ │
   │ └─────────────────────────────────┘ │
   │                                     │
   │ Description:                        │
   │ ┌─────────────────────────────────┐ │
   │ │High-performance Dell XPS 15... │ │
   │ │                          [Copy]│ │
   │ └─────────────────────────────────┘ │
   │                                     │
   │ ⚠️ Please verify: Model number     │
   │                                     │
   │ [Use This] [Edit] [Regenerate]     │
   └─────────────────────────────────────┘
```

**Risks:**

- Backend assistant is mock → Need real LLM in PR 6
- Generation fails → Show graceful error with retry button

**Tests:**

- Unit: Mock AssistantRepository, verify request includes attributes
- UI: Screenshot test for generated listing screen

**Acceptance Criteria:**

- [ ] "Generate Listing" button visible on items with attributes
- [ ] Loading state shown during API call
- [ ] Generated title and description displayed
- [ ] Copy button copies to clipboard
- [ ] Warnings from assistant displayed to user
- [ ] Error state with retry button on failure

---

### PR 6: Backend - Real LLM Provider for Assistant

**Scope:** Replace mock assistant with real Claude/GPT integration.

**Files to Change:**

| File                                                                | Change                              |
|---------------------------------------------------------------------|-------------------------------------|
| `backend/src/modules/assistant/provider.ts`                         | Add `ClaudeAssistantProvider` class |
| `backend/src/modules/assistant/prompts/listing-generation.ts` (NEW) | System/user prompt templates        |
| `backend/src/config/index.ts`                                       | Add `assistant.provider: 'mock'     | 'claude' | 'openai'` |
| `backend/src/modules/assistant/routes.ts`                           | Switch provider based on config     |

**Claude Integration:**

```typescript
class ClaudeAssistantProvider implements AssistantProvider {
  private readonly client: Anthropic;

  async respond(request: AssistantRequest): Promise<AssistantResponse> {
    const systemPrompt = buildListingSystemPrompt(request.assistantPrefs);
    const userPrompt = buildListingUserPrompt(request.items, request.visualFacts);

    const response = await this.client.messages.create({
      model: 'claude-sonnet-4-20250514',
      max_tokens: 1024,
      system: systemPrompt,
      messages: [{ role: 'user', content: userPrompt }],
    });

    return parseAssistantResponse(response);
  }
}
```

**Guardrails in Prompt:**

```
CRITICAL RULES:
1. ONLY use brand/model if confidence >= MED. If LOW, say "Unknown brand" or "Possibly [brand]".
2. NEVER invent specifications not provided (storage, RAM, screen size).
3. If no attributes provided, generate generic description and list "Missing info".
4. Include evidence source: "Brand: Dell (verified from logo)"
5. Add "Please verify" warning for any MED confidence attributes.
```

**Risks:**

- LLM costs → Track per-request cost, enforce daily quota
- Latency → Stream response or accept 2-3s latency
- Hallucination → Strong prompt constraints + post-processing validation

**Tests:**

- Unit: Mock Claude client, verify prompt construction
- Integration: Test with real API key in staging

**Acceptance Criteria:**

- [ ] Claude provider generates coherent listing text
- [ ] Unknown attributes marked as "Unknown" not invented
- [ ] MED confidence attributes flagged for verification
- [ ] Response time < 5s (p95)
- [ ] Cost tracking logged per request

---

### PR 7: Observability - Metrics and Logging

**Scope:** Add metrics for attribute extraction and assistant performance.

**Metrics to Add:**

| Metric                                      | Type      | Labels                       |
|---------------------------------------------|-----------|------------------------------|
| `scanium_attribute_extraction_latency_ms`   | Histogram | `provider`, `feature`        |
| `scanium_attribute_extraction_success_rate` | Counter   | `provider`, `attribute_type` |
| `scanium_attribute_confidence_distribution` | Histogram | `attribute_type`             |
| `scanium_vision_api_cost_estimate`          | Counter   | `feature`                    |
| `scanium_assistant_generation_latency_ms`   | Histogram | `provider`                   |
| `scanium_assistant_cache_hit_rate`          | Gauge     | -                            |

**Files to Change:**

| File                                         | Change                |
|----------------------------------------------|-----------------------|
| `backend/src/modules/classifier/service.ts`  | Add metric recording  |
| `backend/src/modules/assistant/routes.ts`    | Add metric recording  |
| `backend/src/infra/observability/metrics.ts` | Define metric schemas |

**Logs (Structured):**

```json
{
  "level": "info",
  "msg": "Attribute extraction completed",
  "correlationId": "abc123",
  "itemId": "item_456",
  "attributesExtracted": ["brand", "color"],
  "brandConfidence": "HIGH",
  "modelConfidence": null,
  "visionLatencyMs": 423,
  "cacheHit": false
}
```

**Sensitive Data Handling:**

- NEVER log OCR text content (may contain PII)
- Log attribute values only in debug mode
- Hash image data before logging

**Acceptance Criteria:**

- [ ] Metrics exported to Prometheus endpoint
- [ ] Grafana dashboard for extraction success rate
- [ ] Alert on Vision API error rate > 5%
- [ ] No PII in production logs

---

## E. Testing Strategy

### Unit Tests (Per PR)

| PR  | Test File                              | Coverage Target           |
|-----|----------------------------------------|---------------------------|
| PR1 | `ScannedItemEntityTest.kt`             | Attribute serialization   |
| PR2 | `ClassifierService.test.ts`            | Attribute extraction flow |
| PR3 | `ItemClassificationCoordinatorTest.kt` | Attribute propagation     |
| PR4 | `AttributeChipTest.kt`                 | UI rendering              |
| PR5 | `GeneratedListingScreenTest.kt`        | Assistant integration     |
| PR6 | `ClaudeProviderTest.ts`                | Prompt construction       |
| PR7 | `MetricsTest.ts`                       | Metric recording          |

### Integration Tests

1. **End-to-End Classification with Attributes:**
    - Upload test image (IKEA bookshelf)
    - Verify response contains `brand: "IKEA"`, `color: "white"`
    - Verify confidence levels correct

2. **Process Death Persistence:**
    - Scan item with attributes
    - Kill app process
    - Relaunch
    - Verify attributes still present

3. **Description Generation:**
    - Create item with known attributes
    - Call assistant API
    - Verify generated text includes attributes
    - Verify no hallucinated specs

### Manual Test Checklist

**Before Each PR Merge:**

- [ ] Scan 5 different item types (furniture, electronics, clothing)
- [ ] Verify attributes extracted for at least 3/5 items
- [ ] Kill app, relaunch, verify attributes persist
- [ ] Generate description for item with attributes
- [ ] Verify description does not hallucinate brand/model
- [ ] Edit an attribute, verify change persists
- [ ] Check no crashes or ANRs

---

## F. Sequenced Rollout

| Week | PR  | Milestone                   |
|------|-----|-----------------------------|
| 1    | PR1 | Data model ready            |
| 1-2  | PR2 | Backend extracts attributes |
| 2    | PR3 | Android stores attributes   |
| 3    | PR4 | UI displays attributes      |
| 3-4  | PR5 | Generate description button |
| 4    | PR6 | Real LLM integration        |
| 5    | PR7 | Observability               |

**Feature Flag:**

- `cloud_attribute_enrichment_enabled` (default: false in prod)
- Roll out to 10% → 50% → 100% over 2 weeks after PR3

---

## G. Open Questions / Decisions Needed

1. **LLM Provider:** Claude vs OpenAI vs Azure OpenAI?
    - Recommendation: Claude (Anthropic) for consistency with Claude Code tooling

2. **Condition Assessment:** Requires visual inspection of wear/damage
    - Option A: Add to VisionExtractor with custom labels
    - Option B: LLM-based assessment from image
    - Recommendation: Option B in Phase 2

3. **Attribute Editing Workflow:**
    - Option A: Inline edit on chips
    - Option B: Dedicated edit sheet
    - Recommendation: Option B for cleaner UX

4. **Localization Priority:**
    - English first, then Dutch (NL market focus)?
    - Or all EU languages at once?

---

## Appendix: Existing Code References

### Backend Vision Extractor (already built)

**Location:** `backend/src/modules/vision/extractor.ts:172-396`

```typescript
class VisionExtractor {
  async extractVisualFacts(
    itemId: string,
    images: VisionImageInput[],
    options: VisionExtractorOptions
  ): Promise<VisionExtractionResult> {
    // Builds Vision API request with:
    // - TEXT_DETECTION (OCR)
    // - LABEL_DETECTION
    // - LOGO_DETECTION (optional)
    // Then extracts colors server-side
  }
}
```

### Backend Attribute Resolver (already built)

**Location:** `backend/src/modules/vision/attribute-resolver.ts:407-428`

```typescript
export function resolveAttributes(
  itemId: string,
  facts: VisualFacts
): ResolvedAttributes {
  const brand = resolveBrand(facts);     // Logo > OCR dictionary > OCR heuristic
  const model = resolveModel(facts);     // OCR with model patterns
  const { primary: color } = resolveColor(facts);  // Dominant color %
  const material = resolveMaterial(facts);  // Label hints

  return { itemId, brand, model, color, material, suggestedNextPhoto };
}
```

### Brand Dictionary (already built)

**Location:** `backend/src/modules/vision/attribute-resolver.ts:52-85`

Contains 100+ curated brands across:

- Furniture (IKEA, West Elm, Pottery Barn)
- Electronics (Apple, Samsung, Dell, Sony)
- Fashion (Nike, Adidas, North Face, Coach)
- Home goods (KitchenAid, Dyson, Le Creuset)

---

*End of Implementation Plan*
