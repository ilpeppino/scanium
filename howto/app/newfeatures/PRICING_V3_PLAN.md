# Scanium Resale Pricing Feature - Implementation Plan

## 1. Executive Summary

- **Goal**: Enable users to get accurate resale price ranges for scanned items on second-hand marketplaces in their region, optimized for user trust and low LLM cost
- **Approach**: Dedicated `POST /v1/pricing/v3` endpoint with text-only OpenAI prompts optimized for minimal token usage (~500 tokens per request)
- **Gating Rule**: Price request requires Brand, Product Type, Model, and Condition to be manually filled by user before enabling the "Get Price" button. This **manual-only trigger** ensures users verify attributes are correct before pricing, improving trust and reducing wasted API calls
- **Region Integration**: Prices filtered by user's region from Settings (`primaryRegionCountryFlow`), using `marketplaces.json` for country-specific marketplace domains
- **Cost Control**: 24h cache TTL, ~500 token budget per request, rate limiting at 30 req/10s
- **UI Priority**: Price is the MOST important information - displayed prominently with confidence indicator, price range, and marketplace context
- **Existing Infrastructure**: Reuses `PricingInsights` model, backend OpenAI integration patterns, and marketplaces configuration
- **Deliverables**: 5 PR-sized milestones (PR 0: Domain pack & condition enum update as prerequisite)

---

## 2. Current State (Repo Reality Check)

### Existing Pricing Infrastructure

| Component | File Path | Status |
|-----------|-----------|--------|
| Backend Pricing Service | `backend/src/modules/pricing/service.ts` | Active - Uses OpenAI gpt-4o-mini with 6h cache |
| Backend Pricing Routes | `backend/src/modules/pricing/routes.ts` | Active - v1/v2 endpoints (deterministic algorithm) |
| PricingInsights Model | `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/assistant/AssistantModels.kt` | Complete - status, range, confidence, results |
| Android PriceModels | `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/pricing/PriceModels.kt` | Exists - `PriceRange`, `PriceEstimationStatus` sealed interface |
| PriceEstimationRepository | `androidApp/src/main/java/com/scanium/app/pricing/PriceEstimationRepository.kt` | Placeholder - Uses `MockPriceEstimatorProvider` |
| ScannedItem Model | `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/items/ScannedItem.kt` | Has pricing fields: `priceRange`, `estimatedPriceRange`, `priceEstimationStatus`, `condition`, `attributes` |
| ItemCondition Enum | `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/items/ItemCondition.kt` | Exists: NEW, AS_GOOD_AS_NEW, USED, REFURBISHED (will expand to 7 values) |
| Marketplaces Config | `androidApp/src/main/assets/config/marketplaces.json` | Complete - 35+ European countries with marketplace domains |
| MarketplaceRepository | `androidApp/src/main/java/com/scanium/app/data/MarketplaceRepository.kt` | Loads marketplaces.json |
| Domain Pack | `core-domainpack/src/main/res/raw/home_resale_domain_pack.json` | 68 categories, 10 attributes including brand, model, condition |
| Brands Catalog | `core-domainpack/src/main/res/raw/brands_catalog_bundle_v1.json` | 55+ category-specific brand lists |
| Edit Screen | `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt` | Has structured fields, condition dropdown |
| ItemEditState | `androidApp/src/main/java/com/scanium/app/items/edit/ItemEditState.kt` | Already has `pricingInsights` field |
| Settings Region | `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt` | `primaryRegionCountryFlow` available |

### Current Pricing Flow (via Export Assistant)

Pricing is currently bundled with the Export Assistant flow:
```
User taps "Generate Listing" -> ExportAssistantViewModel -> Backend /v1/assist/chat with includePricing=true
```

**Problem**: Users cannot get pricing without triggering full AI listing generation. A standalone pricing endpoint is needed.

---

## 3. Target User Experience (FTUE + Main Flow)

### User Flow: Scan -> List -> Edit -> Price

```
[SCAN SCREEN]
User scans item -> Detection -> Item appears in list

[ITEMS LIST]
User taps item -> Opens Edit Screen

[EDIT SCREEN]
User sees:
+-- Photo carousel at top
+-- Structured fields (Brand, Type, Model, Color, Size, Condition, Notes)
+-- Price section with disabled "Get Price Estimate" button
+-- Helper text: "Fill in Brand, Type, Model, and Condition to get a price estimate"

User fills required fields:
+-- Brand: "Philips"
+-- Product Type: "Coffee Machine"
+-- Model: "3200 Series"
+-- Condition: "Used" (dropdown)

[PRICE BUTTON BECOMES ACTIVE]
User taps "Get Price Estimate"
-> Loading: "Checking market prices..." (shimmer animation)
-> Success: Price card appears:

    +-------------------------------------------+
    | ESTIMATED RESALE PRICE                    |
    |                                           |
    |         EUR 75 - 120                      |
    |         Confidence: HIGH                  |
    |                                           |
    | Based on 4 similar listings on            |
    | Marktplaats, Amazon NL, Bol.com           |
    |                                           |
    | [Use EUR 97 as price]     [Refresh]       |
    +-------------------------------------------+

User taps "Use EUR 97 as price" -> Price field populated with median
```

### States

| State | Trigger | Display |
|-------|---------|---------|
| Insufficient Data | Missing required field | Helper text + disabled button |
| Ready | All 4 fields filled | Enabled button |
| Loading | Button tapped | Shimmer + "Checking market prices..." |
| Success (HIGH) | Response received | Price range, confidence bar, marketplace list |
| Success (LOW) | Response with LOW confidence | Price range + warning about wide range |
| No Results | No comparables found | "No comparable listings found. Try a more specific model name." |
| Error | Network/API error | Error message + Retry button |
| Stale | Required field changed after pricing | Stale badge + prompt to re-request |

### Acceptance Criteria

- [ ] Price button disabled until Brand, Type, Model, Condition are all filled
- [ ] Tapping disabled button shows tooltip explaining what's missing
- [ ] Loading state shows for minimum 1 second (UX smoothness)
- [ ] Price range shows currency from user's region (EUR, GBP, etc.)
- [ ] Confidence tier shown with visual indicator (HIGH=green, MED=yellow, LOW=red)
- [ ] "Use price" populates price field with median value
- [ ] Changing any required field after pricing shows "stale" badge
- [ ] Stale price can still be used but shows warning
- [ ] Works offline if result is cached

---

## 4. Functional Requirements

### FR-1: Required Attributes for Pricing Gate

| Attribute | Source | Validation |
|-----------|--------|------------|
| Brand | `attributes["brand"]` or user input | Non-empty string |
| Product Type | `domainCategoryId` (e.g., "electronics_laptop") | Non-empty string (mapped from domain category) |
| Model | `attributes["model"]` or user input | Non-empty string |
| Condition | `condition` field (ItemCondition enum) | One of 7 values: NEW_SEALED, NEW_WITH_TAGS, NEW_WITHOUT_TAGS, LIKE_NEW, GOOD, FAIR, POOR |

**Note**: ItemCondition enum will be expanded from 4 to 7 values to match backend granularity for better pricing accuracy.

### FR-2: Region-Based Marketplace Selection

1. Read user's region from `primaryRegionCountryFlow` (e.g., "NL")
2. Look up country in `marketplaces.json`
3. Get `defaultCurrency` for display (e.g., "EUR")
4. Get `marketplaces[]` array (e.g., ["marktplaats", "bol", "amazon"])
5. Pass marketplace IDs to pricing endpoint

### FR-3: Price Output Format

| Field | Type | Description |
|-------|------|-------------|
| minPrice | Double | Lower bound of price range |
| maxPrice | Double | Upper bound of price range |
| typicalPrice | Double | Median/typical price |
| currency | String | ISO currency code (EUR, GBP, etc.) |
| confidence | Enum | HIGH (exact matches), MED (similar items), LOW (estimates) |
| briefReason | String | Max 200 chars explanation |
| marketplacesConsidered | List<String> | Marketplace names used |
| timestamp | Long | When price was estimated |
| promptVersion | String | Version of prompt used (for A/B testing) |

### FR-4: Manual Action Only

- Price request triggered ONLY by explicit user tap on "Get Price Estimate" button
- Never auto-trigger on field change or screen load
- Button shows clear call-to-action: "Get Price Estimate"

### FR-5: Invalidation Rules

When any of (brand, productType, model, condition) changes AFTER a price has been fetched:
1. Mark price as "stale" in UI state
2. Show stale badge on price card
3. Price remains visible but with visual de-emphasis
4. User must tap "Refresh" to get new price

---

## 5. Non-Functional Requirements (Cost, Latency, Reliability, Abuse Prevention)

### NFR-1: Cost Budget

| Metric | Target |
|--------|--------|
| Tokens per request | ≤500 (input + output) |
| Monthly API cost | <$50 for 10,000 active users (10% pricing usage) |
| Cache hit rate | >40% |

### NFR-2: Latency

| Metric | Target |
|--------|--------|
| P50 | <3 seconds |
| P95 | <8 seconds |
| Timeout | 15 seconds |

### NFR-3: Reliability

| Metric | Target |
|--------|--------|
| Availability | 99.5% |
| Retry policy | 1 auto-retry on transient failure |
| Fallback | Show "Unable to estimate" without blocking edit |

### NFR-4: Abuse Prevention

| Control | Implementation |
|---------|----------------|
| Rate limit | 30 requests/10 seconds per API key |
| Per-item limit | 1 request per item per 10 minutes |
| Daily quota | 100 pricing requests per device per day |
| Idempotency | SHA256 cache key prevents duplicate billing |

### NFR-5: Privacy

- Do NOT log raw OpenAI prompts (may contain user data)
- Log only normalized metadata: brand category, country, confidence tier
- No PII in telemetry

---

## 6. Data & Domain Modeling

### Pricing Request Schema (Android -> Backend)

```kotlin
@Serializable
data class PricingV3Request(
    val itemId: String,
    val brand: String,
    val productType: String, // From domainCategoryId (e.g., "electronics_laptop")
    val model: String,
    val condition: String, // NEW_SEALED, NEW_WITH_TAGS, NEW_WITHOUT_TAGS, LIKE_NEW, GOOD, FAIR, POOR
    val countryCode: String, // ISO 2-letter code: NL, DE, FR, GB
    val year: Int? = null, // Optional: year of manufacture
    val hasAccessories: Boolean? = null, // Optional signal
)
```

### Pricing Response Schema (Backend -> Android)

```kotlin
// Reuse existing PricingInsights from AssistantModels.kt
@Serializable
data class PricingInsights(
    val status: String, // OK, NOT_SUPPORTED, DISABLED, ERROR, TIMEOUT, NO_RESULTS
    val countryCode: String,
    val marketplacesUsed: List<MarketplaceUsed> = emptyList(),
    val querySummary: String? = null,
    val results: List<PricingResult> = emptyList(),
    val range: PriceRange? = null, // {low, high, currency}
    val confidence: String? = null, // HIGH, MED, LOW
    val errorCode: String? = null,
)
```

### ScannedItem Pricing Fields (already exist)

| Field | Type | Usage |
|-------|------|-------|
| `priceRange` | `Pair<Double, Double>` | Legacy price range |
| `estimatedPriceRange` | `PriceRange?` | From pricing service |
| `priceEstimationStatus` | `PriceEstimationStatus` | Idle/Estimating/Ready/Failed |
| `userPriceCents` | `Long?` | User's manual price |
| `condition` | `ItemCondition?` | Item condition |

### New UI State Model

```kotlin
sealed interface PricingUiState {
    object Idle : PricingUiState
    object InsufficientData : PricingUiState
    object Ready : PricingUiState // Button enabled
    object Loading : PricingUiState
    data class Success(
        val insights: PricingInsights,
        val isStale: Boolean = false
    ) : PricingUiState
    data class Error(val message: String, val retryable: Boolean) : PricingUiState
}
```

---

## 7. Pricing System Design Options + Decision Matrix

### Option A: Dedicated v3 Endpoint (RECOMMENDED)

**Architecture**: New `POST /v1/pricing/v3` endpoint that calls OpenAI with text-only prompts

```
Android -> POST /v1/pricing/v3 -> Backend
                                    |-> Cache check (SHA256 key)
                                    |-> Load marketplaces for country
                                    |-> Build token-optimized prompt
                                    |-> OpenAI API (gpt-4o-mini, JSON mode)
                                    |-> Parse & validate response
                                    |-> Cache result (24h TTL)
                               <- PricingInsights
```

**Pros**: Clean separation, easier caching, independent optimization, secure (no API key on client)
**Cons**: New endpoint to maintain, can't leverage images (text-only)

### Option B: Extend Assistant Chat Endpoint

**Architecture**: Add `pricingOnly=true` flag to existing `/v1/assist/chat`

**Pros**: Reuses existing infrastructure
**Cons**: Heavier payload, couples pricing to assistant logic, harder to optimize independently

### Option C: Android Calls OpenAI Directly

**Architecture**: Android makes direct calls to OpenAI API

**Pros**: Lowest latency, simpler backend
**Cons**: API key exposed on client, no server-side caching, no cost control, security risk

### Decision Matrix

| Criterion | Weight | Option A | Option B | Option C |
|-----------|--------|----------|----------|----------|
| Security | 25% | 5 | 5 | 2 |
| Cost Control | 20% | 5 | 4 | 2 |
| Latency | 15% | 4 | 3 | 5 |
| Maintainability | 20% | 5 | 3 | 2 |
| Cacheability | 15% | 5 | 4 | 1 |
| Implementation Speed | 5% | 4 | 5 | 3 |
| **Weighted Score** | | **4.70** | **3.80** | **2.35** |

**DECISION: Option A - Dedicated v3 Endpoint**

---

## 8. Chosen Architecture + End-to-End Sequence Diagrams

### System Architecture

```
+-----------------------------------------------------------------------+
|                          Android App                                   |
+-----------------------------------------------------------------------+
|  EditItemScreenV3                                                     |
|    +-- ItemEditState                                                  |
|    |     +-- brandField, productTypeField, modelField                |
|    |     +-- conditionField: ItemCondition?                          |
|    |     +-- pricingUiState: PricingUiState                          |
|    |     +-- pricingInsights: PricingInsights?                       |
|    |                                                                  |
|    +-- PriceEstimateCard (Composable)                                |
|          +-- "Get Price Estimate" button                              |
|          +-- Loading shimmer                                          |
|          +-- Price range display                                      |
|          +-- Error/retry state                                        |
|                                                                       |
|  PricingV3Repository (new)                                           |
|    +-- estimatePrice(request): Flow<PricingInsights>                 |
|    +-- Retrofit -> POST /v1/pricing/v3                               |
+-----------------------------------------------------------------------+
                              |
                              | HTTPS (X-API-Key header)
                              v
+-----------------------------------------------------------------------+
|                          Backend                                       |
+-----------------------------------------------------------------------+
|  Fastify Routes                                                       |
|    +-- POST /v1/pricing/v3                                           |
|          +-- Zod validation                                           |
|          +-- API key auth                                             |
|          +-- Rate limiting                                            |
|          +-- PricingServiceV3.estimateResalePrice()                  |
|                                                                       |
|  PricingServiceV3 (new)                                              |
|    +-- buildCacheKey(brand, type, model, condition, country)         |
|    +-- checkCache() -> hit/miss                                       |
|    +-- loadMarketplacesForCountry(countryCode)                       |
|    +-- buildOpenAIPrompt()  // Token-optimized, text-only            |
|    +-- callOpenAI(gpt-4o-mini, JSON mode)                            |
|    +-- parseAndValidateResponse()                                     |
|    +-- cacheResult(24h TTL)                                           |
+-----------------------------------------------------------------------+
                              |
                              | HTTPS
                              v
+-----------------------------------------------------------------------+
|                       OpenAI API                                       |
|                     gpt-4o-mini                                        |
|                   JSON response format                                 |
+-----------------------------------------------------------------------+
```

### Sequence Diagram: Happy Path

```
User          EditScreen       PricingRepo      Backend        OpenAI
  |                |                |              |              |
  |-- Fill fields ->                |              |              |
  |                |                |              |              |
  |-- Tap "Get Price" ->           |              |              |
  |                |                |              |              |
  |                |-- estimatePrice()             |              |
  |                |     request    |              |              |
  |                |--------------->|              |              |
  |                |                |              |              |
  |                |                |-- POST /v1/pricing/v3 ----->|
  |                |                |              |              |
  |                |                |              |-- Cache check |
  |                |                |              |   (miss)     |
  |                |                |              |              |
  |                |                |              |-- Load marketplaces
  |                |                |              |   for NL     |
  |                |                |              |              |
  |                |                |              |------------->|
  |                |                |              |  gpt-4o-mini |
  |                |                |              |  text prompt |
  |                |                |              |              |
  |                |                |              |<-- JSON response
  |                |                |              |              |
  |                |                |              |-- Cache result
  |                |                |              |   (24h TTL)  |
  |                |                |              |              |
  |                |                |<-- PricingInsights ---------|
  |                |<---------------|              |              |
  |                |  PricingInsights              |              |
  |                |                |              |              |
  |<-- Display ----|                |              |              |
  |   Price Card   |                |              |              |
```

### Sequence Diagram: Stale Price (User Edits After Pricing)

```
User          EditScreen       State
  |                |              |
  |  [Price shown] |              |
  |                |              |
  |-- Edit "Model" field -------->|
  |                |              |
  |                |-- Compare with pricing input fields
  |                |              |
  |                |-- Model changed -> mark stale
  |                |              |
  |<-- Show stale badge --------->|
  |   "Price may be outdated"     |
  |   [Refresh] button            |
  |                |              |
  |-- Tap Refresh ->              |
  |                |-- Clear stale flag
  |                |-- Trigger new pricing request
  |                |              |
```

### Sequence Diagram: Error Handling

```
User          EditScreen       PricingRepo      Backend
  |                |                |              |
  |-- Tap "Get Price" ->           |              |
  |                |--------------->|              |
  |                |                |-- POST ----->|
  |                |                |              |
  |                |                |<-- 429 Rate Limited
  |                |                |              |
  |                |<-- Error ------|              |
  |                |                |              |
  |<-- Show error --|              |              |
  |   "Too many requests.          |              |
  |    Try again in 10s"           |              |
  |   [Retry] (disabled 10s)       |              |
```

---

## 9. OpenAI Prompting Strategy (Token-Minimized, Text-Only)

### Token Budget

| Component | Target Tokens |
|-----------|---------------|
| System prompt | 120 |
| User prompt | 80 |
| Marketplace context | 50 |
| **Total Input** | **250** |
| Output (JSON) | 150 |
| **Total Request** | **400** |

### System Prompt (v1.0)

```
You estimate secondhand prices. Given item details, estimate resale price range.

Output ONLY JSON:
{"low":number,"high":number,"cur":"EUR","conf":"HIGH|MED|LOW","why":"<50 chars"}

Rules:
- Prices in seller's currency
- HIGH: exact model matches
- MED: similar items
- LOW: category estimates
```

### User Prompt Template

```
{brand} {productType} {model}
Cond: {condition}
Cat: {category}
Region: {countryCode}
Sites: {marketplace_ids}
```

### Example Request (Complete)

**System**: (as above)

**User**:
```
Philips Coffee Machine 3200 Series
Cond: USED
Cat: appliance_coffee_machine
Region: NL
Sites: marktplaats,bol,amazon
```

### Example Response

```json
{"low":75,"high":120,"cur":"EUR","conf":"HIGH","why":"4 similar on Marktplaats"}
```

### Token Optimization Rules

1. **Abbreviate field names**: "conf" not "confidence", "cur" not "currency"
2. **Use short enums**: "HIGH" not "High Confidence"
3. **Marketplace IDs not names**: "marktplaats" not "Marktplaats.nl"
4. **Cap reason length**: max 50 chars in response
5. **No nested objects**: flat JSON structure
6. **Remove optional fields**: only include non-null values in request

### Prompt Versioning

- Include `promptVersion: "1.0.0"` in request
- Store version in cached response
- Allow A/B testing of prompt variants via config

### Prompt Evaluation Plan

**Test Set** (10 representative items):

| Category | Brand | Model | Expected Range |
|----------|-------|-------|----------------|
| Electronics | Apple | iPhone 13 Pro | EUR 400-600 |
| Electronics | Samsung | Galaxy S21 | EUR 250-400 |
| Appliances | Philips | 3200 Coffee | EUR 75-120 |
| Appliances | Dyson | V11 | EUR 200-350 |
| Furniture | IKEA | KALLAX | EUR 20-50 |
| Fashion | Nike | Air Max 90 | EUR 40-80 |
| Gaming | Nintendo | Switch | EUR 180-250 |
| Audio | Sony | WH-1000XM4 | EUR 120-180 |
| Tools | Bosch | GSR 18V | EUR 80-150 |
| Sports | Garmin | Fenix 6 | EUR 200-350 |

**Metrics**:
- Output parse rate: >99%
- Variance sanity: high-low ratio <3x
- Token count: P95 <500

---

## 10. UI/UX Plan (Screens, States, Copy, Edge Cases)

### Price Estimate Card Location

- **Primary location**: EditItemScreenV3, below the main fields, above Notes
- **Secondary location**: ItemsListScreen card (small badge showing price if available)

### Card States

#### State 1: Insufficient Data
```
+-------------------------------------------+
| PRICE ESTIMATE                            |
| ----------------------------------------- |
|                                           |
| Fill in Brand, Type, Model, and           |
| Condition to get a price estimate         |
|                                           |
| [ GET PRICE ESTIMATE ] (grayed out)       |
+-------------------------------------------+
```

#### State 2: Ready to Estimate
```
+-------------------------------------------+
| PRICE ESTIMATE                            |
| ----------------------------------------- |
|                                           |
| Get an estimated resale price based on    |
| similar items in your region (NL)         |
|                                           |
| [ GET PRICE ESTIMATE ]                    |
+-------------------------------------------+
```

#### State 3: Loading
```
+-------------------------------------------+
| PRICE ESTIMATE                            |
| ----------------------------------------- |
|                                           |
| ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░          |
| Checking market prices...                 |
| ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░          |
|                                           |
+-------------------------------------------+
```

#### State 4: Success (HIGH Confidence)
```
+-------------------------------------------+
| ESTIMATED RESALE PRICE                    |
| ----------------------------------------- |
|                                           |
|         EUR 75 - 120                      |
|         [################] HIGH           |
|                                           |
| Based on 4 similar listings on            |
| Marktplaats, Bol.com                      |
|                                           |
| [Use EUR 97]              [Refresh]       |
+-------------------------------------------+
```

#### State 5: Success (LOW Confidence)
```
+-------------------------------------------+
| ESTIMATED RESALE PRICE                    |
| ----------------------------------------- |
|                                           |
|         EUR 50 - 200                      |
|         [####............] LOW            |
|                                           |
| Wide range - limited data available       |
|                                           |
| [Use EUR 125]             [Refresh]       |
+-------------------------------------------+
```

#### State 6: Stale
```
+-------------------------------------------+
| ESTIMATED RESALE PRICE          [STALE]   |
| ----------------------------------------- |
|                                           |
|         EUR 75 - 120                      |
|                                           |
| Price may be outdated (model changed)     |
|                                           |
| [Use EUR 97]              [Refresh]       |
+-------------------------------------------+
```

#### State 7: Error
```
+-------------------------------------------+
| PRICE ESTIMATE                            |
| ----------------------------------------- |
|                                           |
| Unable to get price estimate              |
| Network error. Please try again.          |
|                                           |
| [ RETRY ]                                 |
+-------------------------------------------+
```

#### State 8: No Results
```
+-------------------------------------------+
| PRICE ESTIMATE                            |
| ----------------------------------------- |
|                                           |
| No comparable listings found              |
| Try a more specific model name            |
|                                           |
| [ TRY AGAIN ]                             |
+-------------------------------------------+
```

### Copy Strings

```xml
<!-- strings.xml additions -->
<string name="pricing_card_title">Price Estimate</string>
<string name="pricing_estimated_title">Estimated Resale Price</string>
<string name="pricing_button_get_estimate">Get Price Estimate</string>
<string name="pricing_button_use_price">Use %1$s</string>
<string name="pricing_button_refresh">Refresh</string>
<string name="pricing_button_retry">Retry</string>
<string name="pricing_button_try_again">Try Again</string>
<string name="pricing_loading">Checking market prices...</string>
<string name="pricing_based_on">Based on %1$d similar listings on %2$s</string>
<string name="pricing_insufficient_data">Fill in Brand, Type, Model, and Condition to get a price estimate</string>
<string name="pricing_ready_subtitle">Get an estimated resale price based on similar items in your region (%1$s)</string>
<string name="pricing_confidence_high">HIGH</string>
<string name="pricing_confidence_med">MEDIUM</string>
<string name="pricing_confidence_low">LOW</string>
<string name="pricing_low_confidence_warning">Wide range - limited data available</string>
<string name="pricing_stale_badge">STALE</string>
<string name="pricing_stale_warning">Price may be outdated (%1$s changed)</string>
<string name="pricing_error_network">Network error. Please try again.</string>
<string name="pricing_error_rate_limit">Too many requests. Try again in %1$d seconds.</string>
<string name="pricing_error_region">Price estimates not available for %1$s</string>
<string name="pricing_no_results_title">No comparable listings found</string>
<string name="pricing_no_results_subtitle">Try a more specific model name</string>
```

### Interaction Rules

1. **Manual only**: Never auto-trigger pricing
2. **Single request**: One request at a time per item
3. **Debounce**: 500ms debounce on button tap
4. **Minimum loading**: Show loading for minimum 1 second
5. **Retry cooldown**: Disable retry button for 3 seconds after error

---

## 11. Backend/API Plan (Routes, Auth, Rate Limits, Caching)

### New Endpoint

**Route**: `POST /v1/pricing/v3`

**File**: `backend/src/modules/pricing/routes-v3.ts`

### Request Schema (Zod)

```typescript
const PricingV3BodySchema = z.object({
  itemId: z.string().min(1).max(100),
  brand: z.string().min(1).max(100),
  productType: z.string().min(1).max(100), // domainCategoryId
  model: z.string().min(1).max(200),
  condition: z.enum(['NEW_SEALED', 'NEW_WITH_TAGS', 'NEW_WITHOUT_TAGS', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR']),
  countryCode: z.string().length(2),
  year: z.number().int().min(1990).max(2030).optional(),
  hasAccessories: z.boolean().optional(),
});
```

### Response Schema

```typescript
interface PricingV3Response {
  success: boolean;
  pricing: {
    status: 'OK' | 'NO_RESULTS' | 'ERROR' | 'TIMEOUT' | 'RATE_LIMITED';
    countryCode: string;
    marketplacesUsed: Array<{ id: string; name: string }>;
    range?: { low: number; high: number; currency: string };
    confidence?: 'HIGH' | 'MED' | 'LOW';
    reason?: string;
    resultCount?: number;
  };
  cached: boolean;
  processingTimeMs: number;
  promptVersion: string;
}
```

### Authentication

- Header: `X-API-Key`
- Reuse existing `ApiKeyManager` from classifier module
- Same keys as other protected endpoints

### Rate Limits

| Limit | Value | Scope |
|-------|-------|-------|
| Global | 30 req/10s | Per API key |
| Per-item | 1 req/10min | Per itemId |
| Daily | 1000 req/day | Per API key |

### Caching Strategy

```typescript
// Cache key construction
const normalizedInput = {
  brand: brand.toLowerCase().trim(),
  productType: productType.toLowerCase().trim(),
  model: model.toLowerCase().trim(),
  condition,
  countryCode,
};
const cacheKey = `pricing:v3:${sha256(JSON.stringify(normalizedInput))}`;

// TTL: 24 hours
const CACHE_TTL_SECONDS = 86400;
```

### Error Contracts

| HTTP Status | Code | Description |
|-------------|------|-------------|
| 400 | INVALID_REQUEST | Zod validation failed |
| 401 | UNAUTHORIZED | Missing or invalid API key |
| 429 | RATE_LIMITED | Rate limit exceeded |
| 500 | INTERNAL_ERROR | Server error |
| 504 | TIMEOUT | OpenAI timeout |

### Configuration

```typescript
// config/index.ts additions
pricing: {
  v3Enabled: envBool('PRICING_V3_ENABLED', false),
  v3TimeoutMs: envNumber('PRICING_V3_TIMEOUT_MS', 15000),
  v3CacheTtlSeconds: envNumber('PRICING_V3_CACHE_TTL_SECONDS', 86400),
  v3DailyQuota: envNumber('PRICING_V3_DAILY_QUOTA', 1000),
  v3PromptVersion: envString('PRICING_V3_PROMPT_VERSION', '1.0.0'),
}
```

---

## 12. Telemetry, Monitoring, and Cost Controls

### Metrics to Track

| Metric | Type | Alert Threshold |
|--------|------|-----------------|
| `pricing.v3.requests` | Counter | - |
| `pricing.v3.latency_ms` | Histogram | P95 > 10s |
| `pricing.v3.cache_hit_rate` | Gauge | < 30% |
| `pricing.v3.error_rate` | Gauge | > 5% |
| `pricing.v3.confidence_distribution` | Counter by tier | LOW > 50% |
| `pricing.v3.token_usage` | Histogram | P95 > 600 |
| `pricing.v3.openai_cost_usd` | Counter | Daily > $10 |

### Logging (Structured)

```typescript
// Request log
logger.info({
  event: 'pricing_v3_request',
  correlationId,
  itemId,
  brand: brand.length, // Length only, not value
  productType,
  countryCode,
  hasCategory: !!category,
});

// Response log
logger.info({
  event: 'pricing_v3_response',
  correlationId,
  status,
  confidence,
  resultCount,
  latencyMs,
  cached,
  tokenCount,
});
```

### Alerts

| Alert | Condition | Channel |
|-------|-----------|---------|
| High Error Rate | error_rate > 5% for 5min | Slack #alerts |
| High Latency | P95 > 10s for 10min | Slack #alerts |
| Cost Spike | daily_cost > $10 | Slack #billing |
| Low Cache Hit | cache_hit_rate < 30% for 1h | Slack #perf |

### Cost Controls

1. **Hard daily limit**: Disable pricing after 1000 requests per API key per day
2. **Token guardrail**: Reject prompts > 600 tokens, responses > 300 tokens
3. **Circuit breaker**: Disable pricing if error_rate > 20% for 5 minutes
4. **Budget alert**: Notify when daily cost > $5

---

## 13. Testing Strategy (Unit, Integration, Golden, UI)

### Unit Tests

| Test File | Coverage |
|-----------|----------|
| `backend/src/modules/pricing/service-v3.test.ts` | Cache key generation, prompt building, response parsing |
| `backend/src/modules/pricing/routes-v3.test.ts` | Request validation, error handling |
| `androidApp/.../PricingV3RepositoryTest.kt` | Request/response mapping, error states |
| `androidApp/.../PricingUiStateTest.kt` | State transitions, stale detection |
| `androidApp/.../ItemEditStateTest.kt` | Required field validation |

### Integration Tests

| Test | Coverage |
|------|----------|
| `POST /v1/pricing/v3` happy path | End-to-end with mocked OpenAI |
| Cache hit/miss | TTL enforcement, key stability |
| Rate limiting | 429 response when exceeded |
| Timeout handling | 504 after 15s |
| Marketplace loading | Country lookup from JSON |

### Golden Tests

| Test | Purpose |
|------|---------|
| Prompt stability | Ensure prompt doesn't change unexpectedly |
| Response parsing | Validate parsing of known good responses |
| Token budget | Verify prompts stay under 500 tokens |

### UI Tests (Compose)

| Test | Coverage |
|------|----------|
| `PriceEstimateCardTest` | All 8 states render correctly |
| `EditItemScreenV3Test` | Button enabled/disabled based on fields |
| `PriceApplicationTest` | "Use price" updates priceField |
| `StaleDetectionTest` | Stale badge appears on field change |

### Manual QA Checklist

- [ ] Price estimate works for NL, DE, FR, GB regions
- [ ] Loading state shows for >1 second
- [ ] Error state shows retry button
- [ ] LOW confidence shows warning
- [ ] "Use price" populates field with median
- [ ] Price persists after screen rotation
- [ ] Stale badge appears when model changed
- [ ] Works offline with cached result
- [ ] Rate limit error shows countdown

---

## 14. Rollout Plan (Feature Flags, Beta, Metrics Gates, Rollback)

### Feature Flags

| Flag | Default | Description |
|------|---------|-------------|
| `feature_pricing_v3_enabled` | false | Master toggle |
| `pricing_v3_prompt_version` | "1.0.0" | Prompt variant |
| `pricing_v3_cache_ttl_hours` | 24 | Cache duration |

### Staged Rollout

| Phase | Duration | Audience | Success Criteria |
|-------|----------|----------|------------------|
| 1. Internal | 3 days | Dev team only | error_rate < 5%, latency P95 < 10s |
| 2. Beta | 5 days | Beta flavor users | error_rate < 3%, cache_hit > 30% |
| 3. Prod 10% | 2 days | 10% of prod | cost < $5/day, confidence_high > 40% |
| 4. Prod 50% | 2 days | 50% of prod | Same metrics |
| 5. Prod 100% | - | All users | - |

### Metrics Gates

Before proceeding to next phase:
- Error rate < 5%
- Latency P95 < 10s
- Cache hit rate > 30%
- Daily cost < $10
- User feedback: no major complaints

### Rollback Instructions

1. **Immediate**: Set `feature_pricing_v3_enabled = false` in Remote Config
2. **Effect**: UI hides price estimate card, button disabled
3. **Backend**: Route returns 503 with `"status": "DISABLED"`
4. **User impact**: Users see "Price estimates temporarily unavailable"

---

## 15. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Hallucinated prices | Medium | High | Show confidence tier; require HIGH for "Use price"; human review for LOW |
| Inaccurate ranges | Medium | Medium | Show "estimated" language; allow manual override |
| Token cost blowups | Medium | High | Hard token budget; daily cost cap; cache aggressively |
| Marketplace data stale | Low | Low | 24h cache refresh; use recent data in prompt |
| Region not supported | Low | Low | Clear error message; default to EU-wide |
| Model name variations | High | Medium | Normalize input; fuzzy matching in prompt |
| Abuse/scraping | Medium | Medium | Rate limits; per-item cooldown; daily quota |
| User confusion | Medium | Medium | Clear UX copy: "Estimated" not "Guaranteed" |
| OpenAI rate limiting | Medium | High | Exponential backoff; circuit breaker; cache |
| Stale prices after edits | Low | Low | Stale badge; manual refresh required |

---

## 16. Work Breakdown Structure (PR-sized milestones)

### PR 0: Domain Pack & Condition Enum Update (Prerequisite)

**Goal**: Review and update domain packs for complete home resale coverage; expand ItemCondition enum to 7 values

**Files to Modify**:
- `core-domainpack/src/main/res/raw/home_resale_domain_pack.json` - Review categories, add missing home resale items
- `core-domainpack/src/main/res/raw/brands_catalog_bundle_v1.json` - Add missing brands per category
- `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/items/ItemCondition.kt` - Expand to 7 values
- `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt` - Update condition dropdown
- `androidApp/src/main/res/values/strings.xml` - Add condition display names

**New ItemCondition Enum Values**:
```kotlin
enum class ItemCondition(val displayName: String, val description: String) {
    NEW_SEALED("New, Sealed", "Factory sealed, never opened"),
    NEW_WITH_TAGS("New with Tags", "New with original tags attached"),
    NEW_WITHOUT_TAGS("New without Tags", "New, no tags, never used"),
    LIKE_NEW("Like New", "Used briefly, no visible wear"),
    GOOD("Good", "Normal use, minor wear"),
    FAIR("Fair", "Noticeable wear, fully functional"),
    POOR("Poor", "Heavy wear, may have defects"),
}
```

**Domain Pack Review Checklist**:
- [ ] All 68 categories have appropriate brand lists
- [ ] Electronics categories have comprehensive brand coverage (Apple, Samsung, Sony, etc.)
- [ ] Furniture categories have EU brand coverage (IKEA, Hay, etc.)
- [ ] Condition attribute applies to high-value categories
- [ ] No duplicate category IDs

**Tests**:
- Unit tests for new condition enum values
- Domain pack validation tests

**Acceptance Criteria**:
- [ ] ItemCondition has 7 values matching backend
- [ ] Condition dropdown shows all 7 options with descriptions
- [ ] Domain pack JSON validates without errors
- [ ] All high-value categories have brand attribute enabled

---

### PR 1: Backend Pricing V3 Endpoint

**Goal**: Implement dedicated `/v1/pricing/v3` endpoint with OpenAI integration

**Files to Create**:
- `backend/src/modules/pricing/routes-v3.ts`
- `backend/src/modules/pricing/service-v3.ts`
- `backend/src/modules/pricing/types-v3.ts`
- `backend/src/modules/pricing/prompts-v3.ts`
- `backend/src/modules/pricing/service-v3.test.ts`
- `backend/src/modules/pricing/routes-v3.test.ts`

**Files to Modify**:
- `backend/src/app.ts` (register route)
- `backend/src/config/index.ts` (add pricing v3 config)

**Tests**:
- Cache key generation unit tests
- Prompt building unit tests
- Response parsing unit tests
- Route integration tests with mocked OpenAI

**Acceptance Criteria**:
- [ ] Endpoint returns valid PricingInsights for valid request
- [ ] Cache hit returns instantly
- [ ] Rate limiting returns 429
- [ ] Invalid request returns 400
- [ ] OpenAI timeout returns 504

---

### PR 2: Android PricingV3Repository & State

**Goal**: Implement Android repository and UI state management

**Files to Create**:
- `androidApp/src/main/java/com/scanium/app/pricing/PricingV3Repository.kt`
- `androidApp/src/main/java/com/scanium/app/pricing/PricingV3Api.kt`
- `androidApp/src/main/java/com/scanium/app/pricing/PricingUiState.kt`
- `androidApp/src/main/java/com/scanium/app/di/PricingModule.kt`
- `androidApp/src/test/java/com/scanium/app/pricing/PricingV3RepositoryTest.kt`
- `androidApp/src/test/java/com/scanium/app/pricing/PricingUiStateTest.kt`

**Files to Modify**:
- `androidApp/src/main/java/com/scanium/app/items/edit/ItemEditState.kt`

**Tests**:
- Repository request/response mapping
- UI state transitions
- Stale detection logic
- Error handling

**Acceptance Criteria**:
- [ ] Repository calls backend and returns PricingInsights
- [ ] UI state correctly transitions between states
- [ ] Stale flag set when required field changes after pricing

---

### PR 3: UI - PriceEstimateCard Component

**Goal**: Implement Compose UI for pricing feature

**Files to Create**:
- `androidApp/src/main/java/com/scanium/app/items/edit/PriceEstimateCard.kt`
- `androidApp/src/test/java/com/scanium/app/items/edit/PriceEstimateCardTest.kt`

**Files to Modify**:
- `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt`
- `androidApp/src/main/res/values/strings.xml`

**Tests**:
- All 8 states render correctly
- Button enabled/disabled based on fields
- "Use price" updates price field
- Stale badge appears on field change

**Acceptance Criteria**:
- [ ] Card shows correct state based on PricingUiState
- [ ] Button disabled when required fields missing
- [ ] Loading shimmer shows during request
- [ ] Price range displays with confidence indicator
- [ ] "Use price" populates price field

---

### PR 4: Feature Flag, Config & Rollout

**Goal**: Add feature flag and prepare for staged rollout

**Files to Create**:
- `howto/app/reference/PRICING_V3.md`

**Files to Modify**:
- `androidApp/src/main/java/com/scanium/app/config/FeatureFlags.kt`
- `backend/src/config/index.ts`
- Firebase Remote Config (manual)

**Tests**:
- Feature flag toggles pricing visibility
- Config values load correctly

**Acceptance Criteria**:
- [ ] Feature disabled by default
- [ ] Can enable via Remote Config
- [ ] Monitoring dashboards configured
- [ ] Documentation complete

---

## Plan Verification Checklist

Before implementation, verify:

- [ ] `backend/src/modules/pricing/service.ts` - Confirm OpenAI integration patterns
- [ ] `shared/core-models/.../AssistantModels.kt` - Confirm PricingInsights model
- [ ] `androidApp/src/main/assets/config/marketplaces.json` - Confirm country/marketplace structure
- [ ] `androidApp/.../ItemEditState.kt` - Confirm existing pricingInsights field
- [ ] `core-domainpack/.../home_resale_domain_pack.json` - Confirm brand attribute coverage
- [ ] `shared/core-models/.../ItemCondition.kt` - Verify current enum values before expansion

After PR 0 (Domain Pack & Condition Update):

- [ ] ItemCondition enum has 7 values
- [ ] Condition dropdown shows all 7 options
- [ ] Domain pack validates without errors
- [ ] Run `./gradlew test` - All tests pass

After PR 1-4 (Pricing Feature):

- [ ] Run `./gradlew test` - All Android tests pass
- [ ] Run `cd backend && npm test` - All backend tests pass
- [ ] Manual test: Price estimate on dev flavor
- [ ] Manual test: All 8 UI states work correctly
- [ ] Manual test: Stale detection works
- [ ] Manual test: productType derived from domainCategoryId
- [ ] Cost monitoring: Verify daily cost < $1 during testing
