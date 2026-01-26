# AI-Assisted Price Normalization (Option 4)

**Status:** Planned
**Owner:** Engineering
**Last updated:** 2026-01-26

---

## 1. Objectives

- Prices are **VERIFIABLE**: backed by real marketplace listings
- AI **normalizes**, never invents prices
- Output includes verifiable sources: marketplace name + URL + listing counts
- Optimize cost: use caching, TTL, only call AI when necessary (high noise scenarios)

## 2. Non-Goals

- AI hallucinating/inventing prices from training data
- Scraping without marketplace ToS compliance
- Real-time pricing (cached results are acceptable)
- Expanding beyond NL in initial implementation

---

## 3. Current State (Gap Analysis)

### What EXISTS

| Component | File Path | Status |
|-----------|-----------|--------|
| Pricing V3 Endpoint | `backend/src/modules/pricing/service-v3.ts` | Active - BUT asks OpenAI to **guess prices** from training data |
| Pricing V3 Prompts | `backend/src/modules/pricing/prompts-v3.ts` | Active - Token-optimized prompts, no real marketplace data |
| EbayCompsTool | `backend/src/modules/assistant/ebay-comps-tool.ts` | **MOCK ONLY** - `generateMockComps()` returns fake data |
| eBay OAuth Flow | `backend/src/modules/auth/ebay/oauth-flow.ts` | Complete - Can authenticate with eBay |
| Marketplace Config | `backend/config/marketplaces/marketplaces.eu.json` | Complete - 38+ countries, NL has Marktplaats, Bol.com, Amazon.nl |
| Android PriceEstimateCard | `androidApp/.../items/edit/PriceEstimateCard.kt` | Complete UI - All states implemented |
| PricingUiState | `androidApp/.../pricing/PricingUiState.kt` | Complete - Sealed interface with all states |
| Caching | `backend/src/infra/cache/unified-cache.ts` | Complete - TTL, LRU, dedup |

### Critical Gap: Current V3 Flow

```
Current Flow (PROBLEMATIC):
User request → Backend → OpenAI "estimate price for {brand} {model}" → AI guesses price → Return

Problem: AI invents prices from training data. No real marketplace lookups.
         "marketplacesUsed" in response is just config data, not actual sources.
```

### What's MISSING for Option 4

| Missing Component | Impact |
|-------------------|--------|
| Marktplaats Listing Adapter | Cannot fetch real NL classifieds listings |
| eBay Browse API Implementation | OAuth exists but API calls not implemented |
| Listing Normalization Pipeline | No AI clustering of fetched listings |
| Verifiable Source URLs | Response has no listing URLs or counts |
| Sample Listing Examples | Cannot show "example listings" to user |
| Rule-Based Fallback | No fallback when marketplace data unavailable |

---

## 4. Data Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         POST /v1/pricing/v4                                 │
│                                                                             │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐         │
│  │ 1. Cache Check  │ -> │ 2. Fetch Listings│ -> │ 3. Deterministic│         │
│  │   (SHA256 key)  │    │   (Marktplaats   │    │    Filters      │         │
│  │   TTL: 24h      │    │    + eBay)       │    │   (remove junk) │         │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘         │
│           │                     │                      │                    │
│           │  HIT               ▼                      ▼                    │
│           │           ┌─────────────────┐    ┌─────────────────┐           │
│           └──────────>│ Return Cached   │    │ 4. AI Normalize │           │
│                       │ Result          │    │  (cluster noisy │           │
│                       └─────────────────┘    │   listings)     │           │
│                                              └─────────────────┘           │
│                                                      │                     │
│                                                      ▼                     │
│                                              ┌─────────────────┐           │
│                                              │ 5. Aggregate    │           │
│                                              │ (median, p25/75,│           │
│                                              │  outlier removal)│          │
│                                              └─────────────────┘           │
│                                                      │                     │
│                                                      ▼                     │
│                                              ┌─────────────────┐           │
│                                              │ Response with   │           │
│                                              │ - price range   │           │
│                                              │ - source URLs   │           │
│                                              │ - listing count │           │
│                                              │ - sample titles │           │
│                                              └─────────────────┘           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Backend Architecture

### 5.1 Endpoint: POST /v1/pricing/v4

New endpoint separate from v3 to allow parallel rollout and A/B testing.

### 5.2 Request Schema

```typescript
interface PricingV4Request {
  itemId: string;
  brand: string;
  productType: string;
  model: string;
  condition: ItemCondition;
  countryCode: string; // NL for now
  preferredMarketplaces?: string[]; // ["marktplaats", "ebay"]
}
```

### 5.3 Response Schema (Verifiable)

```typescript
interface PricingV4Insights {
  status: 'OK' | 'NO_RESULTS' | 'FALLBACK' | 'ERROR' | 'TIMEOUT';
  countryCode: string;

  // Verifiable sources
  sources: MarketplaceSource[];
  totalListingsAnalyzed: number;
  timeWindowDays: number; // e.g., 30

  // Price range
  range?: {
    low: number;      // p25
    median: number;
    high: number;     // p75
    currency: string;
  };

  // Sample listings for verification
  sampleListings?: SampleListing[]; // max 3

  confidence: 'HIGH' | 'MED' | 'LOW';

  // Fallback indicator
  fallbackReason?: string; // "Insufficient marketplace data"
}

interface MarketplaceSource {
  id: string;           // "marktplaats"
  name: string;         // "Marktplaats"
  baseUrl: string;      // "https://www.marktplaats.nl"
  listingCount: number; // 12
  searchUrl?: string;   // URL user can visit to verify
}

interface SampleListing {
  title: string;        // "Philips 3200 koffiemachine"
  price: number;
  currency: string;
  condition?: string;
  url?: string;         // Direct link to listing (if available)
  marketplace: string;
}
```

### 5.4 Marketplace Adapters

#### Adapter Interface

```typescript
interface MarketplaceAdapter {
  readonly id: string;
  readonly name: string;

  fetchListings(query: ListingQuery): Promise<FetchedListing[]>;
  buildSearchUrl(query: ListingQuery): string;
  isHealthy(): Promise<boolean>;
}

interface FetchedListing {
  title: string;
  price: number;
  currency: string;
  condition?: string;
  url: string;
  postedDate?: Date;
  marketplace: string;
  raw?: unknown; // For debugging
}

interface ListingQuery {
  brand: string;
  model: string;
  productType: string;
  condition?: string;
  countryCode: string;
  maxResults: number;
}
```

#### NL Adapters (Initial Scope)

| Marketplace | Type | Data Source |
|-------------|------|-------------|
| Marktplaats | Classifieds | RSS feeds → Partner API (if available) |
| eBay.nl | Global | Browse API (OAuth already implemented) |

### 5.5 Normalization Pipeline (5 Stages)

#### Stage 1: Fetch (Adapter Layer)
- Parallel fetch from all configured adapters
- Timeout per adapter: 5 seconds
- Graceful degradation if one adapter fails

#### Stage 2: Deterministic Filters

```typescript
function filterListings(listings: FetchedListing[]): FetchedListing[] {
  return listings.filter(listing => {
    // Remove clearly wrong listings
    if (listing.price <= 0) return false;
    if (listing.price > 10000) return false; // Outlier cap

    // Remove "parts only", "defect", "voor onderdelen"
    const excludePatterns = [
      /parts only/i, /for parts/i, /defect/i, /kapot/i,
      /voor onderdelen/i, /niet werkend/i, /broken/i
    ];
    if (excludePatterns.some(p => p.test(listing.title))) return false;

    // Remove bundles (multiple items)
    if (/\d+x\s/i.test(listing.title)) return false;
    if (/set of/i.test(listing.title)) return false;

    return true;
  });
}
```

#### Stage 3: AI Normalization (Conditional)

AI is called ONLY when:
- Listing titles are noisy (brand/model variations)
- Need to cluster similar items
- High noise ratio detected (>30%)

```typescript
interface NormalizationInput {
  listings: FetchedListing[];
  targetBrand: string;
  targetModel: string;
}

interface NormalizationOutput {
  relevantListings: NormalizedListing[];
  excludedListings: { listing: FetchedListing; reason: string }[];
  clusterSummary: string;
}
```

**AI Prompt for Normalization** (NOT price generation):

```
You analyze marketplace listings to identify which match a target product.

Target: {brand} {model} ({productType})

Listings to analyze:
{listing_json_array}

For each listing, output JSON:
{
  "listingId": number,
  "isMatch": boolean,
  "matchConfidence": "HIGH" | "MED" | "LOW",
  "reason": "<20 chars>",
  "normalizedCondition": "NEW" | "LIKE_NEW" | "GOOD" | "FAIR" | "POOR" | null
}

Rules:
- HIGH: exact brand+model match
- MED: same product line, minor variation
- LOW: similar category, uncertain match
- Do NOT estimate prices, only classify relevance
```

#### Stage 4: Aggregation

```typescript
function aggregatePrices(listings: NormalizedListing[]): PriceAggregation {
  const prices = listings.map(l => l.price).sort((a, b) => a - b);

  // Remove outliers (IQR method)
  const q1 = percentile(prices, 25);
  const q3 = percentile(prices, 75);
  const iqr = q3 - q1;
  const lowerBound = q1 - 1.5 * iqr;
  const upperBound = q3 + 1.5 * iqr;

  const filtered = prices.filter(p => p >= lowerBound && p <= upperBound);

  return {
    low: percentile(filtered, 25),
    median: percentile(filtered, 50),
    high: percentile(filtered, 75),
    sampleSize: filtered.length,
    outliersRemoved: prices.length - filtered.length,
  };
}
```

#### Stage 5: Response Building
- Combine aggregation with source metadata
- Select sample listings (max 3, diverse conditions)
- Calculate confidence based on sample size and noise

### 5.6 Caching Strategy

| Aspect | Value |
|--------|-------|
| Cache Key | `pricing:v4:${sha256(brand+model+condition+country)}` |
| TTL | 24 hours (configurable) |
| Skip AI | If cache hit |
| Adapter Health | Separate cache, 5 min TTL |

### 5.7 Observability

**Metrics**:
- `pricing.v4.requests` (counter)
- `pricing.v4.latency_ms` (histogram)
- `pricing.v4.cache_hit_rate` (gauge)
- `pricing.v4.adapter_success_rate` (gauge per adapter)
- `pricing.v4.ai_normalization_invoked` (counter)
- `pricing.v4.listings_fetched` (histogram)

**Correlation IDs**:
- All logs include `correlationId` for request tracing
- Adapter calls tagged with parent correlation

**Error Codes**:
- `ADAPTER_TIMEOUT`: Marketplace adapter timed out
- `ADAPTER_ERROR`: Marketplace returned error
- `NO_LISTINGS`: No listings found across all adapters
- `AI_ERROR`: Normalization AI call failed
- `VALIDATION_ERROR`: Invalid request

---

## 6. Android Changes

### 6.1 New Repository

```kotlin
// PricingV4Repository.kt
@Singleton
class PricingV4Repository @Inject constructor(
    private val api: PricingV4Api,
    private val errorMapper: PricingErrorMapper,
) {
    suspend fun estimatePrice(request: PricingV4Request): Result<PricingV4Insights>
}
```

### 6.2 Enhanced PriceEstimateCard

Add verifiable sources section:

```kotlin
@Composable
private fun VerifiableSourcesSection(
    sources: List<MarketplaceSource>,
    sampleListings: List<SampleListing>?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.pricing_based_on_sources),
            style = MaterialTheme.typography.labelMedium,
        )

        sources.forEach { source ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = source.name)
                Text(
                    text = pluralStringResource(
                        R.plurals.pricing_listing_count,
                        source.listingCount,
                        source.listingCount
                    ),
                )
            }
        }

        // "View on Marktplaats" link
        source.searchUrl?.let { url ->
            TextButton(onClick = { openUrl(url) }) {
                Text(stringResource(R.string.pricing_view_listings))
            }
        }
    }
}
```

### 6.3 UI States

Existing states in `PricingUiState` remain valid. Add handling for:
- `FALLBACK` status: Show warning that data is limited
- Sources display in `Success` state
- Sample listings (optional, collapsible)

### 6.4 Localization

New strings required (no hardcoded English):

```xml
<string name="pricing_based_on_sources">Based on real listings from:</string>
<plurals name="pricing_listing_count">
    <item quantity="one">%d listing</item>
    <item quantity="other">%d listings</item>
</plurals>
<string name="pricing_view_listings">View listings</string>
<string name="pricing_time_window">Last %d days</string>
<string name="pricing_fallback_warning">Limited data available. Range is estimated.</string>
```

---

## 7. Security and Compliance

### API Keys
- eBay OAuth tokens stored encrypted in backend
- No marketplace credentials exposed to Android client
- All pricing calls go through authenticated backend

### Data Handling
- Do not store raw listing content beyond cache TTL
- No PII from listings stored (titles/prices only)
- Listing URLs may be stored for verification

### Marketplace ToS
- eBay: Use official Browse API only
- Marktplaats: Prefer RSS/API over scraping
- Rate limit all adapter calls
- Include proper User-Agent headers

---

## 8. Code References (Files to Create/Modify)

### Backend (Create)

| File | Purpose |
|------|---------|
| `backend/src/modules/pricing/types-v4.ts` | New type definitions |
| `backend/src/modules/pricing/routes-v4.ts` | New endpoint |
| `backend/src/modules/pricing/service-v4.ts` | Orchestration service |
| `backend/src/modules/pricing/prompts-v4.ts` | Normalization prompts |
| `backend/src/modules/marketplaces/adapters/ebay-adapter.ts` | eBay Browse API |
| `backend/src/modules/marketplaces/adapters/marktplaats-adapter.ts` | Marktplaats data |
| `backend/src/modules/pricing/normalization/filters.ts` | Deterministic filters |
| `backend/src/modules/pricing/normalization/ai-clusterer.ts` | AI clustering |
| `backend/src/modules/pricing/normalization/aggregator.ts` | Price aggregation |

### Backend (Modify)

| File | Change |
|------|--------|
| `backend/src/app.ts` | Register v4 route |
| `backend/src/config/index.ts` | Add v4 config flags |

### Android (Create)

| File | Purpose |
|------|---------|
| `androidApp/.../pricing/PricingV4Repository.kt` | New repository |
| `androidApp/.../pricing/PricingV4Api.kt` | Retrofit interface |
| `shared/core-models/.../pricing/PricingV4Models.kt` | New models |

### Android (Modify)

| File | Change |
|------|--------|
| `androidApp/.../items/edit/PriceEstimateCard.kt` | Add sources UI |
| `androidApp/src/main/res/values/strings.xml` | New strings |
| `androidApp/.../config/FeatureFlags.kt` | Add v4 flag |

---

## 9. Implementation Plan (PR Split)

| PR | Title | Scope | Est. Files |
|----|-------|-------|------------|
| PR-0 | Types + Design Doc | Backend types, this doc | 3 |
| PR-1A | eBay Browse API Adapter | eBay adapter + tests | 4 |
| PR-1B | Marktplaats Adapter | Marktplaats adapter + tests | 4 |
| PR-2 | Normalization Pipeline | Filters, AI clusterer, aggregator | 5 |
| PR-3 | V4 Endpoint | Routes, service, integration | 4 |
| PR-4 | Android UI + Models | Repository, UI changes, strings | 5 |
| PR-5 | Feature Flag + Monitoring | Config, dashboard | 4 |

**Total**: ~29 files, 7 PRs

---

## 10. Test Plan

### Unit Tests

| Module | Test Cases |
|--------|------------|
| eBay Adapter | Auth flow, API response parsing, error handling, rate limits |
| Marktplaats Adapter | Response parsing, category mapping, URL building |
| Filters | Remove defects, remove bundles, price bounds |
| AI Normalizer | Clustering accuracy, confidence assignment |
| Aggregator | Percentile calculation, outlier removal |

### Integration Tests

| Test | Description |
|------|-------------|
| Happy path | Full pipeline with mocked adapters |
| Cache hit | Verify cached response returned |
| Adapter failure | Graceful degradation to fallback |
| AI skipped | Low-noise scenario skips AI |
| Timeout handling | 504 after configured timeout |

### Golden Tests

| Test | Purpose |
|------|---------|
| Prompt stability | Normalization prompt doesn't change |
| Response schema | Output matches `PricingV4Insights` |
| Sample data | Known inputs produce expected ranges |

### Manual QA Checklist

- [ ] NL user sees Marktplaats + eBay as sources
- [ ] Price range shows p25/p75 bounds
- [ ] "View listings" link opens correct search
- [ ] LOW confidence shows warning
- [ ] Fallback message shown when no data
- [ ] Stale badge appears on attribute change

---

## 11. Rollout Strategy

| Phase | Audience | Duration | Success Criteria |
|-------|----------|----------|------------------|
| 1. Internal | Dev team | 3 days | error_rate < 5% |
| 2. Beta NL | Beta users in NL | 5 days | cache_hit > 30%, adapters healthy |
| 3. Prod NL 10% | 10% NL prod | 2 days | user satisfaction, cost < $5/day |
| 4. Prod NL 100% | All NL prod | - | - |
| 5. Expand EU | Other countries | TBD | Per-country adapter work |

### Feature Flags

```typescript
// Backend
PRICING_V4_ENABLED=false           // Master toggle
PRICING_V4_TIMEOUT_MS=20000
PRICING_V4_CACHE_TTL_SECONDS=86400
PRICING_V4_AI_NORM_ENABLED=true
PRICING_V4_FALLBACK_V3=true        // Use v3 if adapters fail

// Android (Remote Config)
enablePricingV4=false
```

---

## 12. Acceptance Criteria

When user clicks "Get Price Estimate" on an item:

### Must Have
- [ ] Returns verifiable price range (p25/median/p75)
- [ ] Shows sample size (number of listings analyzed)
- [ ] Shows time window (e.g., "last 30 days")
- [ ] Shows sources: marketplace names + listing counts
- [ ] AI does NOT invent prices (only normalizes real listings)
- [ ] Clear fallback message when data unavailable

### Should Have
- [ ] Search URL to verify on marketplace
- [ ] Sample listing titles
- [ ] Confidence tier (HIGH/MED/LOW)

### Could Have
- [ ] Direct links to individual listings (if ToS allows)
- [ ] Price trend over time

---

## 13. Deployment Notes

### Backend Deployment (Docker on NAS)

```bash
# 1. On Mac: Ensure clean working tree and push
git status  # Should be clean
git push origin feature/pricing-v4

# 2. On NAS: Pull changes
ssh nas
cd /path/to/scanium
git fetch --all --prune
git status  # Should be clean
git pull --ff-only

# 3. Verify commit SHA matches
git rev-parse HEAD  # Compare with Mac

# 4. Rebuild and deploy
docker compose -p scanium-backend build
docker compose -p scanium-backend up -d

# 5. Verify health
curl http://localhost:3000/health
```

### Environment Variables

```env
# Pricing V4
PRICING_V4_ENABLED=true
PRICING_V4_TIMEOUT_MS=20000
PRICING_V4_CACHE_TTL_SECONDS=86400
PRICING_V4_AI_NORM_ENABLED=true

# eBay API (existing, ensure populated)
EBAY_CLIENT_ID=xxx
EBAY_CLIENT_SECRET=xxx

# Marktplaats (if API available)
MARKTPLAATS_API_KEY=xxx  # Or leave empty for RSS fallback
```

---

## 14. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Marktplaats blocks scraping | Medium | High | Start with RSS feeds; partner API if available |
| eBay rate limits hit | Medium | Medium | Aggressive caching (24h); request coalescing |
| AI normalization hallucination | Low | High | AI only classifies relevance, never generates prices |
| Inconsistent listings data | High | Medium | Deterministic filters first; AI cleans noise |
| Cold cache on launch | Medium | Low | Pre-warm cache with common products |
| Adapter failures | Medium | Medium | Fallback to v3 behavior with clear messaging |
| Higher latency than v3 | High | Medium | Parallel adapter calls; cache aggressively |

---

## 15. Open Questions

1. **Marktplaats API Access**: Does Marktplaats offer a partner/affiliate API? If not, RSS is the fallback.

2. **eBay Completed Listings**: Does the Browse API expose sold/completed listings, or only active? Completed listings give better price accuracy.

3. **Sample Listing URLs**: Are direct listing URLs allowed in UI, or should we only show search URLs? (ToS consideration)

---

## 16. NL-First Marketplace Scope

### Initial Implementation (NL)

| Marketplace | Type | Data Source |
|-------------|------|-------------|
| Marktplaats | Classifieds | RSS feeds → API (if available) |
| eBay.nl | Global | Browse API |

### Future Expansion (Not in This Plan)

| Country | Marketplaces |
|---------|--------------|
| BE | 2dehands, Bol.com |
| DE | Kleinanzeigen, eBay.de |
| FR | Leboncoin, Amazon.fr |
| Fashion (any) | Vinted |
| Electronics (NL) | Tweakers Vraag & Aanbod |
