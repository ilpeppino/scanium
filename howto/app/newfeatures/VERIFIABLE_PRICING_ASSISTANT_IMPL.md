# Verifiable Pricing Assistant — Implementation Plan

## Status
Planned | See also: [Architecture & UX](VERIFIABLE_PRICING_ASSISTANT.md)

---

## 1. Current State Summary

### Backend (substantial foundation)

| File | Purpose |
|------|---------|
| `backend/src/modules/pricing/service-v4.ts` | Full pipeline: fetch listings → filter → AI normalize → aggregate → cache |
| `backend/src/modules/pricing/routes-v4.ts` | `POST /v1/pricing/v4` endpoint |
| `backend/src/modules/pricing/normalization/ai-clusterer.ts` | OpenAI-based listing classification |
| `backend/src/modules/pricing/normalization/aggregator.ts` | Statistical price aggregation (p25/median/p75) |
| `backend/src/modules/pricing/normalization/filters.ts` | Deterministic listing filters |
| `backend/src/modules/pricing/adapters/ebay-adapter.ts` | eBay Browse API with OAuth |
| `backend/src/modules/pricing/adapters/marktplaats-adapter.ts` | Marktplaats RSS feed search |
| `backend/src/modules/pricing/types-v4.ts` | V4 request/response types |
| `backend/src/modules/pricing/prompts-v4.ts` | AI normalization prompts |
| `backend/src/modules/pricing/index.ts` | Module registration and exports |

In-memory cache with configurable TTL (default 24h). V3 fallback when marketplace adapters fail.

### Android (partial)

| File | Purpose | Key Lines |
|------|---------|-----------|
| `androidApp/.../pricing/PricingV4Api.kt` | API client (brand/productType/model/condition/countryCode) | Request DTOs: 16-86, `estimatePrice()`: 92-185 |
| `androidApp/.../pricing/PricingV4Repository.kt` | V4 repository wrapping API | `estimatePrice()`: 28-71, DTO converters: 74-119 |
| `androidApp/.../pricing/PricingUiState.kt` | State machine: Idle→InsufficientData→Ready→Loading→Success/Error | States: 1-62, `PricingInputs`: 27-54 |
| `androidApp/.../items/edit/PriceEstimateCard.kt` | Price range, confidence, sample listings, "Use Price" button | Main composable: 38-80, ReadyState: 83-99 |
| `androidApp/.../items/edit/ItemEditState.kt` | Edit state with `pricingInsights`, `pricingUiState`, `lastPricingInputs` | Fields: 61-63, computed `pricingInputs`: 65-72 |
| `androidApp/.../items/edit/EditItemScreenV3.kt` | AI button opens ExportAssistantSheet (NOT pricing) | AI button: 363-421, onClick: 365-390, trigger: line 386 |
| `androidApp/.../items/edit/ExportAssistantSheet.kt` | Listing generation bottom sheet (current AI button target) | Main sheet: 82-100 |
| `androidApp/.../items/edit/ExportAssistantViewModel.kt` | Export assistant state management | State sealed class: 54-80 |
| `androidApp/.../config/FeatureFlags.kt` | Build-time feature gating | `allowPricingV3`: 48-49, `allowPricingV4`: 56-57, `allowAiAssistant`: 40-41 |
| `androidApp/.../di/PricingModule.kt` | Hilt DI for pricing APIs/repos | V3 provides: 22-45, V4 provides: 47-70 |

### Shared Models (KMP)

| File | Purpose |
|------|---------|
| `shared/core-models/.../pricing/PricingV4Models.kt` | `PricingV4Request` (6-14), `PricingV4Response` (17-22), `PricingV4Insights` (25-35), `MarketplaceSource` (38-44), `PricingV4SampleListing` (47-54) |
| `shared/core-models/.../assistant/AssistantModels.kt` | `PricingInsights` (271-296), `PricingConfidence` enum, `SampleListing` (254-265) |

### What's Missing

| Gap | Backend | Android |
|-----|---------|---------|
| Variant attribute schemas per product type | No endpoint | No UI |
| Variant attributes in V4 request | Not accepted | Not sent |
| Completeness data (charger, box, etc.) | Not accepted | No UI |
| EAN/UPC identifier in search | Not used | Not sent |
| Pricing Assistant wizard sheet | N/A | Doesn't exist |
| AI button → pricing flow | N/A | Triggers export (`line 386`), not pricing |

---

## 2. Proposed Architecture

```
AI Button tap (EditItemScreenV3.kt:365)
  → PricingAssistantSheet (new, 5-step wizard)
    Step 0: Context explanation
    Step 1: Variant attributes (dynamic from schema endpoint)
    Step 2: Completeness multi-select
    Step 3: Identifier (if barcode detected)
    Step 4: Confirm & submit
  → POST /v1/pricing/v4 (extended with variantAttributes, completeness, identifier)
  → Backend appends variant text to search queries (service-v4.ts:240)
  → Existing V4 pipeline (fetch → filter → normalize → aggregate)
  → PriceEstimateCard (reused inline in sheet)
```

New backend endpoint: `GET /v1/pricing/variant-schema?productType=X`

---

## 3. Implementation Steps

### PR 1: Backend — Extend V4 with Variant Attributes

#### Step 1: Create variant schema definitions
**Create** `backend/src/modules/pricing/variant-schemas.ts`

```typescript
interface VariantField {
  key: string;
  label: string;
  type: 'select' | 'text';
  options?: string[];
  required: boolean;
}

interface VariantSchema {
  fields: VariantField[];
  completenessOptions: string[];
}
```

- `VARIANT_SCHEMAS` map keyed by productType pattern
- Start with ~10 categories: laptop, smartphone, tablet, console, camera, headphones, watch, clothing, shoes, generic
- `getVariantSchema(productType)` lookup with fuzzy matching
- Generic fallback with no variant fields

#### Step 2: Create variant schema route
**Create** `backend/src/modules/pricing/routes-variant-schema.ts`

- `GET /v1/pricing/variant-schema?productType=X`
- Returns `{ fields, completenessOptions }` or empty defaults
- API key auth (same pattern as `routes-v4.ts`)

#### Step 3: Extend V4 request types
**Modify** `backend/src/modules/pricing/types-v4.ts`

Add to `PricingV4Request`:
```typescript
variantAttributes?: Record<string, string>;
completeness?: string[];
identifier?: string;
```

Add same fields to `PricingV4CacheKeyComponents`.

#### Step 4: Integrate variant attributes into search & cache
**Modify** `backend/src/modules/pricing/service-v4.ts`

- `buildListingQuery()` (~line 240): append variant attribute values to search text
  - Example: `"MacBook Pro"` → `"MacBook Pro 256GB 16GB"`
- `buildCacheKey()` (~line 228): include new fields in hash
- If `identifier` provided: secondary search query using just the EAN/UPC
- Pass completeness info to AI clusterer for relevance scoring

#### Step 5: Register route
**Modify** `backend/src/modules/pricing/index.ts` — add variant-schema route registration

#### Step 6: Tests
- **Create** `backend/src/modules/pricing/variant-schemas.test.ts` — schema lookup, fuzzy matching, default fallback
- **Modify** `backend/src/modules/pricing/service-v4.test.ts` — variant attributes in query, cache key differentiation

---

### PR 2: Android — Pricing Assistant Sheet

#### Step 1: Add variant schema API client
**Create** `androidApp/src/main/java/com/scanium/app/pricing/VariantSchemaApi.kt`

```kotlin
data class VariantField(
    val key: String,
    val label: String,
    val type: String, // "select" | "text"
    val options: List<String>? = null,
    val required: Boolean = false
)

data class VariantSchema(
    val fields: List<VariantField>,
    val completenessOptions: List<String>
)
```

- `suspend fun getSchema(endpoint: String, productType: String, apiKey: String): VariantSchema?`

#### Step 2: Extend PricingV4Request
**Modify** `androidApp/src/main/java/com/scanium/app/pricing/PricingV4Api.kt` (request DTO, ~line 16-86)

Add:
```kotlin
val variantAttributes: Map<String, String> = emptyMap()
val completeness: List<String> = emptyList()
val identifier: String? = null
```

Also update `shared/core-models/.../pricing/PricingV4Models.kt` (`PricingV4Request`, line 6-14) with matching fields.

#### Step 3: Create PricingAssistantViewModel
**Create** `androidApp/src/main/java/com/scanium/app/items/edit/PricingAssistantViewModel.kt`

- `@HiltViewModel` with `StateFlow<PricingAssistantState>`
- State: `currentStep` (0-4), `variantSchema`, `variantValues`, `completenessValues`, `identifier`, `pricingResult`, `isLoading`
- Actions: `loadSchema()`, `nextStep()`, `prevStep()`, `submit()`
- Smart step skipping (skip variant step if schema has no fields, skip identifier if none detected)
- Calls existing `PricingV4Repository` (line 28-71) for the final estimate

#### Step 4: Create PricingAssistantSheet composable
**Create** `androidApp/src/main/java/com/scanium/app/items/edit/PricingAssistantSheet.kt`

Follow the `ExportAssistantSheet.kt` pattern (line 82-100):
- `ModalBottomSheet` with `skipPartiallyExpanded = true`
- Step indicator (dots) at top
- Step 0: Trust explanation text + Continue
- Step 1: Dynamic form from `VariantSchema.fields` (dropdowns for select, text for text)
- Step 2: Completeness chips (multi-select)
- Step 3: Identifier display/input (optional)
- Step 4: Summary + "Get Price Estimate" CTA
- Result: embed `PriceEstimateCard` inline with "Use Price" action
- Back/Next navigation at bottom

#### Step 5: Wire AI button
**Modify** `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt`

- At line ~385: when AI button tapped and pricing prerequisites met (brand + productType + model), open `PricingAssistantSheet` instead of `ExportAssistantSheet`
- Keep `ExportAssistantSheet` accessible as secondary option (overflow menu or after pricing completes)

**Modify** `androidApp/src/main/java/com/scanium/app/items/edit/ItemEditState.kt`
- Add `showPricingAssistantSheet: Boolean = false` (near existing fields at line 61-63)

#### Step 6: Feature flag
**Modify** `androidApp/src/main/java/com/scanium/app/config/FeatureFlags.kt`
- Add `allowPricingAssistant` flag (near existing pricing flags, lines 48-57)
- DEV flavor only initially

#### Step 7: Hilt wiring
**Modify** `androidApp/src/main/java/com/scanium/app/di/PricingModule.kt` (after line 70)
- Provide `VariantSchemaApi`

#### Step 8: String resources
**Modify** `androidApp/src/main/res/values/strings.xml` (near existing pricing strings, lines 1133-1171)

Add wizard step titles, explanation text, button labels.

#### Step 9: Tests
- **Create** `androidApp/src/test/java/com/scanium/app/pricing/PricingAssistantViewModelTest.kt` — step navigation, schema loading, submission
- **Create** `androidApp/src/androidTest/java/com/scanium/app/items/edit/PricingAssistantSheetTest.kt` — UI rendering per step

---

## 4. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Variant schemas become stale/incomplete | Start with ~10 categories; generic fallback with no variant fields always works |
| Marktplaats RSS returns irrelevant results with longer queries | Keep variant text short; fall back to base query if zero results |
| AI button behavior change confuses existing users | Feature-flag gated (`FeatureFlags.allowPricingAssistant`); dev flavor first |
| Backend deployment divergence | Follow NAS deployment protocol: Mac push → NAS `git fetch && git pull --ff-only` → verify SHA → `docker compose up -d` |

---

## 5. Deployment

### Backend (PR 1)
1. Merge to main on Mac
2. `ssh nas` → `cd /path/to/scanium && git fetch --all --prune && git pull --ff-only`
3. Verify commit SHA matches: `git rev-parse HEAD` on both Mac and NAS
4. `ssh nas "docker compose -p scanium-monitoring build backend && docker compose -p scanium-monitoring up -d backend"`
5. Verify: `curl https://scanium.gtemp1.com/v1/pricing/variant-schema?productType=electronics_laptop`

### Android (PR 2)
1. `./gradlew :androidApp:assembleDevDebug` — test on dev flavor
2. Manual QA: tap AI button → verify wizard flow → verify price result
3. Enable `allowPricingAssistant` in beta flavor → beta test
4. Enable in prod flavor → release

### Rollout order
1. Backend PR first (backward-compatible, new fields optional)
2. Android PR second (gated behind feature flag, dev → beta → prod)
