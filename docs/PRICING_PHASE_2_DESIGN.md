# Pricing Phase 2: Market-Aware Estimation

## Executive Summary

Phase 2 adds **market awareness** to the baseline pricing system without marketplace dependency. The goal is to answer:

> "What price will likely work in this market, right now, for this type of item?"

**Key Constraints:**
- No live marketplace scraping
- No marketplace APIs (eBay, Vinted, etc.)
- No user sales history
- No marketplace-specific heuristics
- All logic lives on backend

**Allowed Inputs:**
- Country/region (coarse)
- Urban vs non-urban signal
- Currency zone
- Month/seasonality
- Static or periodically refreshed historical aggregates
- Vision confidence signals (photo quality, count, wear indicators)

---

## Architecture Overview

### Layered Pricing Model

```
┌─────────────────────────────────────────────────────────────────┐
│                    LAYER 3: CONFIDENCE WEIGHTING                 │
│  Photo quality → Range narrowing                                 │
│  Attribute coverage → Confidence adjustment                      │
│  Vision signals → Wear indicators                               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    LAYER 2: MARKET ADJUSTMENT                    │
│  Regional index → Price scaling                                  │
│  Urban premium → Location-based adjustment                       │
│  Seasonality → Time-based demand                                │
│  Currency → Localized output                                    │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    LAYER 1: PHASE 1 BASELINE                     │
│  Category → Base price range                                     │
│  Brand tier → Value multiplier                                   │
│  Condition → Depreciation                                        │
│  Completeness → Bonus                                            │
└─────────────────────────────────────────────────────────────────┘
```

### Data Flow

```
PriceEstimateInputV2
    │
    ├── itemId, category, brand, condition, completeness (Phase 1)
    │
    ├── marketContext: { region, isUrban, currency }
    │
    └── visionQuality: { photoCount, avgResolution, blurScore, wearIndicators }
    │
    ▼
┌─────────────────────────────────────────────────────┐
│              estimatePriceV2()                       │
│                                                      │
│  1. Call Phase 1: estimatePrice(input)              │
│     → baseMin, baseMax, baseConfidence               │
│                                                      │
│  2. Apply Market Adjustments:                        │
│     → regionalMultiplier(region, category)           │
│     → urbanPremium(isUrban, category)                │
│     → seasonalityFactor(month, category)             │
│                                                      │
│  3. Apply Confidence Weighting:                      │
│     → photoQualityScore → range narrowing            │
│     → wearIndicators → condition refinement          │
│     → attributeCoverage → confidence boost           │
│                                                      │
│  4. Currency Conversion:                             │
│     → Apply fixed exchange rate (monthly refresh)    │
│                                                      │
│  5. Generate Explanation:                            │
│     → Phase 1 steps + market context lines           │
└─────────────────────────────────────────────────────┘
    │
    ▼
PriceEstimateResultV2
    │
    ├── priceEstimateMin, priceEstimateMax
    ├── confidence: HIGH | MEDIUM | LOW
    ├── currency: "EUR" | "USD" | "GBP" | ...
    ├── marketContext: "NL / urban / fashion"
    └── explanation: ["Base T-shirt €10-25", "Nike +20%", "Urban NL +10%", ...]
```

---

## Layer 2: Market Adjustment

### 2.1 Regional Price Index

Each region has a price index relative to a baseline (US = 1.0). Indices are category-specific.

**Data Model:**

```typescript
type RegionalPriceIndex = {
  region: RegionCode;
  categoryIndices: Record<CategorySegment, number>;
  defaultIndex: number;
  validFrom: string;      // ISO date
  validUntil: string;     // ISO date
  source: string;         // "eurostat_2024" | "manual_estimate"
};

type RegionCode =
  | 'NL' | 'DE' | 'BE' | 'FR' | 'UK'
  | 'US' | 'ES' | 'IT' | 'PL' | 'SE';

type CategorySegment =
  | 'electronics' | 'fashion' | 'furniture'
  | 'home_goods' | 'sports' | 'toys';
```

**Example Data:**

```json
{
  "region": "NL",
  "categoryIndices": {
    "electronics": 1.05,
    "fashion": 1.15,
    "furniture": 1.20,
    "home_goods": 1.10,
    "sports": 1.08,
    "toys": 1.00
  },
  "defaultIndex": 1.10,
  "validFrom": "2024-01-01",
  "validUntil": "2024-12-31",
  "source": "eurostat_ppp_2024"
}
```

**Justification:**
- PPP (Purchasing Power Parity) data is publicly available from Eurostat, OECD
- Updated annually (low maintenance)
- Category-specific because electronics resale differs from furniture resale

### 2.2 Urban Premium

Urban areas typically have higher resale prices due to:
- Higher disposable income
- Faster turnover
- More competition (can command higher prices)

**Data Model:**

```typescript
type UrbanPremiumConfig = {
  categoryPremiums: Record<CategorySegment, number>;
  defaultPremium: number;
};

const URBAN_PREMIUM: UrbanPremiumConfig = {
  categoryPremiums: {
    electronics: 1.05,      // +5% in urban
    fashion: 1.12,          // +12% in urban (higher for designer)
    furniture: 1.15,        // +15% in urban (delivery matters)
    home_goods: 1.05,       // +5% in urban
    sports: 1.08,           // +8% in urban
    toys: 1.03,             // +3% in urban
  },
  defaultPremium: 1.08,
};
```

**How "Urban" is Determined:**
- Client sends `isUrban: boolean` based on:
  - Device location (if permitted) → lookup against city boundaries
  - User-provided postal code → lookup against urban classification
  - Fallback: assume suburban (neutral 1.0 multiplier)

### 2.3 Seasonality Adjustments

Certain categories have predictable seasonal demand patterns.

**Data Model:**

```typescript
type SeasonalityFactor = {
  category: CategorySegment;
  monthlyFactors: Record<number, number>; // 1-12 → multiplier
};

const SEASONALITY: Record<CategorySegment, Record<number, number>> = {
  fashion: {
    1: 0.85,   // January: post-holiday slump
    2: 0.90,
    3: 1.00,   // Spring refresh
    4: 1.05,
    5: 1.00,
    6: 0.95,   // Summer slow
    7: 0.90,
    8: 1.10,   // Back to school
    9: 1.15,   // Fall fashion
    10: 1.10,
    11: 1.20,  // Holiday shopping
    12: 1.25,  // Peak holiday
  },
  electronics: {
    1: 0.80,   // Post-holiday crash
    2: 0.85,
    3: 0.90,
    4: 0.95,
    5: 0.95,
    6: 0.90,   // Summer slow
    7: 0.85,
    8: 1.00,   // Back to school
    9: 1.05,
    10: 1.05,
    11: 1.25,  // Black Friday
    12: 1.30,  // Holiday peak
  },
  // ... other categories with flatter curves
};
```

**Algorithm:**

```typescript
function getSeasonalityFactor(category: CategorySegment, month: number): number {
  const factors = SEASONALITY[category];
  if (!factors) return 1.0; // No seasonality data → neutral
  return factors[month] ?? 1.0;
}
```

### 2.4 Currency Zones

Prices displayed in local currency using fixed monthly exchange rates.

**Data Model:**

```typescript
type CurrencyConfig = {
  code: string;           // ISO 4217
  symbol: string;
  rate: number;           // Relative to USD
  decimals: number;
  validFrom: string;
  validUntil: string;
};

const CURRENCIES: Record<RegionCode, CurrencyConfig> = {
  NL: { code: 'EUR', symbol: '€', rate: 0.92, decimals: 2, ... },
  DE: { code: 'EUR', symbol: '€', rate: 0.92, decimals: 2, ... },
  UK: { code: 'GBP', symbol: '£', rate: 0.79, decimals: 2, ... },
  US: { code: 'USD', symbol: '$', rate: 1.00, decimals: 2, ... },
  // ...
};
```

**Note:** Exchange rates are refreshed monthly from public sources (ECB, Federal Reserve). NOT real-time to avoid external dependencies.

---

## Layer 3: Confidence Weighting

### 3.1 Vision Quality Signals

Better photos → narrower price range → higher confidence.

**Input Signals:**

```typescript
type VisionQualityInput = {
  /** Number of photos provided */
  photoCount: number;

  /** Average resolution (megapixels) */
  avgResolution: number;

  /** Blur score 0-1 (0 = sharp, 1 = blurry) */
  blurScore: number;

  /** Detected lighting quality */
  lightingQuality: 'GOOD' | 'FAIR' | 'POOR';

  /** Wear indicators detected in vision */
  wearIndicators: {
    scratchesDetected: boolean;
    stainDetected: boolean;
    tearDetected: boolean;
    fadeDetected: boolean;
  };
};
```

**Quality Score Calculation:**

```typescript
function calculatePhotoQualityScore(input: VisionQualityInput): number {
  let score = 0;

  // Photo count (1-5+ photos)
  score += Math.min(input.photoCount, 5) * 10;  // max 50 points

  // Resolution (0.5MP - 12MP+)
  const resScore = Math.min(input.avgResolution / 12, 1) * 20;  // max 20 points
  score += resScore;

  // Sharpness (inverse of blur)
  score += (1 - input.blurScore) * 15;  // max 15 points

  // Lighting
  const lightingScores = { GOOD: 15, FAIR: 8, POOR: 0 };
  score += lightingScores[input.lightingQuality] || 0;  // max 15 points

  return score;  // 0-100 scale
}
```

**Range Narrowing:**

```typescript
function narrowRangeByQuality(
  minCents: number,
  maxCents: number,
  qualityScore: number
): [number, number] {
  // Higher quality → narrower range
  // Quality 100 → 70% narrowing (30% of original spread)
  // Quality 0 → 0% narrowing (full spread)

  const narrowingFactor = qualityScore / 100 * 0.7;
  const spread = maxCents - minCents;
  const midpoint = (minCents + maxCents) / 2;

  const newSpread = spread * (1 - narrowingFactor);
  const newMin = Math.round(midpoint - newSpread / 2);
  const newMax = Math.round(midpoint + newSpread / 2);

  return [newMin, newMax];
}
```

### 3.2 Wear Indicator Condition Refinement

If vision detects wear indicators, adjust condition downward.

```typescript
function refineConditionByWear(
  declaredCondition: ItemCondition,
  wearIndicators: WearIndicators
): ItemCondition {
  const wearCount = Object.values(wearIndicators).filter(Boolean).length;

  if (wearCount === 0) return declaredCondition;

  const conditionOrder: ItemCondition[] = [
    'NEW_SEALED', 'NEW_WITH_TAGS', 'NEW_WITHOUT_TAGS',
    'LIKE_NEW', 'GOOD', 'FAIR', 'POOR'
  ];

  const currentIdx = conditionOrder.indexOf(declaredCondition);

  // Each wear indicator drops condition by 1 level (max 2 drops)
  const drops = Math.min(wearCount, 2);
  const newIdx = Math.min(currentIdx + drops, conditionOrder.length - 1);

  return conditionOrder[newIdx];
}
```

### 3.3 Confidence Calculation V2

Extended confidence that includes market signal quality.

```typescript
function calculateConfidenceV2(
  phase1Confidence: PriceConfidence,
  marketContext: MarketContext,
  visionQuality: VisionQualityInput
): PriceConfidence {
  let score = 0;

  // Phase 1 base score
  const phase1Scores = { HIGH: 6, MEDIUM: 4, LOW: 2 };
  score += phase1Scores[phase1Confidence];

  // Region known: +1
  if (marketContext.region) score += 1;

  // Urban/suburban known: +1
  if (marketContext.isUrban !== undefined) score += 1;

  // Photo quality
  const qualityScore = calculatePhotoQualityScore(visionQuality);
  if (qualityScore >= 70) score += 2;
  else if (qualityScore >= 40) score += 1;

  // Multiple photos: +1
  if (visionQuality.photoCount >= 3) score += 1;

  // Thresholds
  if (score >= 10) return 'HIGH';
  if (score >= 6) return 'MEDIUM';
  return 'LOW';
}
```

---

## Dataset Sourcing Strategy

### Data Sources

| Dataset | Source | Refresh | Cost | License |
|---------|--------|---------|------|---------|
| Regional PPP | Eurostat, OECD | Annual | Free | Open |
| Exchange Rates | ECB, Federal Reserve | Monthly | Free | Open |
| Urban Boundaries | OpenStreetMap, GeoNames | Annual | Free | ODbL |
| Seasonality Patterns | Internal analysis | Annual | Free | Internal |

### Storage & Versioning

**Location:** `backend/src/modules/pricing/data/`

```
pricing/
├── data/
│   ├── regional-indices/
│   │   ├── 2024-v1.json        # Active dataset
│   │   ├── 2024-v2.json        # Updated dataset (not yet active)
│   │   └── manifest.json       # Points to active version
│   ├── currencies/
│   │   ├── 2024-01.json
│   │   ├── 2024-02.json
│   │   └── manifest.json
│   ├── seasonality/
│   │   └── patterns-v1.json
│   └── urban-classification/
│       ├── EU-2024.json
│       └── US-2024.json
```

**Manifest Format:**

```json
{
  "active": "2024-v1.json",
  "versions": [
    { "file": "2024-v1.json", "validFrom": "2024-01-01", "validUntil": "2024-12-31" },
    { "file": "2024-v2.json", "validFrom": "2025-01-01", "validUntil": "2025-12-31" }
  ],
  "lastUpdated": "2024-01-15T00:00:00Z"
}
```

**Update Process:**

1. Data analyst downloads new PPP/exchange data
2. Transform to internal schema
3. Add as new version file
4. Update manifest to point to new version
5. Deploy backend (hot-reload not required; files are read at startup)

### Caching Strategy

```typescript
class MarketDataLoader {
  private regionalIndices: Map<RegionCode, RegionalPriceIndex>;
  private currencies: Map<RegionCode, CurrencyConfig>;
  private loadedAt: Date;

  constructor() {
    this.reload();
  }

  reload(): void {
    // Load from JSON files at startup
    this.regionalIndices = loadRegionalIndices();
    this.currencies = loadCurrencies();
    this.loadedAt = new Date();
  }

  // No runtime refresh - restart required for updates
  // This is intentional: pricing must be deterministic within a deployment
}
```

---

## API Contract

### Request

```typescript
// POST /v1/pricing/estimate (extended)
type PriceEstimateRequestV2 = {
  // Phase 1 fields (unchanged)
  itemId: string;
  category?: string;
  segment?: string;
  brand?: string;
  brandConfidence?: 'HIGH' | 'MED' | 'LOW';
  productType?: string;
  condition?: string;
  completeness?: Partial<CompletenessIndicators>;
  material?: string;
  visionHints?: VisionHints;

  // Phase 2 additions
  marketContext?: {
    region?: RegionCode;
    isUrban?: boolean;
    postalCode?: string;  // Alternative to isUrban
  };

  visionQuality?: {
    photoCount?: number;
    avgResolutionMp?: number;
    blurScore?: number;
    lightingQuality?: 'GOOD' | 'FAIR' | 'POOR';
    wearIndicators?: {
      scratchesDetected?: boolean;
      stainDetected?: boolean;
      tearDetected?: boolean;
      fadeDetected?: boolean;
    };
  };
};
```

### Response

```typescript
type PriceEstimateResponseV2 = {
  success: true;
  estimate: {
    // Core price (in local currency, cents)
    priceEstimateMinCents: number;
    priceEstimateMaxCents: number;

    // Formatted for display
    priceEstimateMin: number;
    priceEstimateMax: number;
    priceRangeFormatted: string;  // "€18 - €28"

    // Currency info
    currency: string;              // "EUR"
    currencySymbol: string;        // "€"

    // Confidence
    confidence: 'HIGH' | 'MEDIUM' | 'LOW';

    // Market context summary
    marketContext: string;         // "NL / urban / fashion"

    // Human-readable explanation
    explanation: string[];

    // Caveats/warnings
    caveats?: string[];

    // Input summary
    inputSummary: {
      category: string;
      categoryLabel: string;
      brand: string | null;
      brandTier: BrandTier;
      condition: ItemCondition;
      completenessScore: number;
      region: string;
      isUrban: boolean;
      photoQualityScore: number;
    };
  };

  // Debug info (with x-debug header)
  debug?: {
    calculationSteps: PricingStepV2[];
    phase1Result: PriceEstimateResult;
    marketAdjustments: MarketAdjustment[];
  };
};
```

---

## Pseudocode: Complete Algorithm

```typescript
function estimatePriceV2(input: PriceEstimateRequestV2): PriceEstimateResponseV2 {
  const steps: PricingStepV2[] = [];
  const caveats: string[] = [];

  // ═══════════════════════════════════════════════════════════════
  // LAYER 1: Phase 1 Baseline
  // ═══════════════════════════════════════════════════════════════

  const phase1Input: PriceEstimateInput = {
    itemId: input.itemId,
    category: input.category,
    segment: input.segment,
    brand: input.brand,
    brandConfidence: input.brandConfidence,
    productType: input.productType,
    condition: input.condition,
    completeness: input.completeness,
    material: input.material,
    visionHints: input.visionHints,
  };

  const phase1Result = estimatePrice(phase1Input);

  let minCents = phase1Result.priceEstimateMinCents;
  let maxCents = phase1Result.priceEstimateMaxCents;

  steps.push({
    step: 'phase1_baseline',
    description: 'Phase 1 baseline estimate',
    resultRange: [minCents, maxCents],
    details: phase1Result.calculationSteps,
  });

  // ═══════════════════════════════════════════════════════════════
  // LAYER 2: Market Adjustments
  // ═══════════════════════════════════════════════════════════════

  const marketContext = input.marketContext || {};
  const segment = mapCategoryToSegment(input.category);

  // --- 2a. Regional Price Index ---
  const region = marketContext.region || 'US';
  const regionalIndex = getRegionalIndex(region, segment);

  if (regionalIndex !== 1.0) {
    minCents = Math.round(minCents * regionalIndex);
    maxCents = Math.round(maxCents * regionalIndex);

    steps.push({
      step: 'regional_adjustment',
      description: `${region} market index`,
      factor: regionalIndex,
      resultRange: [minCents, maxCents],
    });
  }

  // --- 2b. Urban Premium ---
  const isUrban = resolveIsUrban(marketContext);

  if (isUrban) {
    const urbanPremium = getUrbanPremium(segment);
    maxCents = Math.round(maxCents * urbanPremium);
    // Only max affected - urban allows higher ceiling

    steps.push({
      step: 'urban_premium',
      description: 'Urban market premium',
      factor: urbanPremium,
      resultRange: [minCents, maxCents],
    });
  }

  // --- 2c. Seasonality ---
  const month = new Date().getMonth() + 1;
  const seasonalFactor = getSeasonalityFactor(segment, month);

  if (seasonalFactor !== 1.0) {
    minCents = Math.round(minCents * seasonalFactor);
    maxCents = Math.round(maxCents * seasonalFactor);

    const seasonLabel = seasonalFactor > 1 ? 'High demand season' : 'Low demand season';
    steps.push({
      step: 'seasonality',
      description: seasonLabel,
      factor: seasonalFactor,
      resultRange: [minCents, maxCents],
    });
  }

  // ═══════════════════════════════════════════════════════════════
  // LAYER 3: Confidence Weighting
  // ═══════════════════════════════════════════════════════════════

  const visionQuality = input.visionQuality || {};

  // --- 3a. Wear Indicator Condition Refinement ---
  if (visionQuality.wearIndicators) {
    const originalCondition = input.condition || 'GOOD';
    const refinedCondition = refineConditionByWear(
      parseCondition(originalCondition),
      visionQuality.wearIndicators
    );

    if (refinedCondition !== parseCondition(originalCondition)) {
      // Re-apply condition modifier
      const originalMod = getConditionModifier(parseCondition(originalCondition));
      const refinedMod = getConditionModifier(refinedCondition);
      const adjustment = refinedMod.multiplier / originalMod.multiplier;

      minCents = Math.round(minCents * adjustment);
      maxCents = Math.round(maxCents * adjustment);

      steps.push({
        step: 'wear_refinement',
        description: `Wear detected → ${refinedMod.label}`,
        factor: adjustment,
        resultRange: [minCents, maxCents],
      });

      caveats.push('Condition adjusted based on detected wear');
    }
  }

  // --- 3b. Range Narrowing by Photo Quality ---
  const photoQualityScore = calculatePhotoQualityScore(visionQuality);

  if (photoQualityScore >= 40) {
    [minCents, maxCents] = narrowRangeByQuality(minCents, maxCents, photoQualityScore);

    steps.push({
      step: 'range_narrowing',
      description: `Photo quality score: ${photoQualityScore}/100`,
      resultRange: [minCents, maxCents],
    });
  }

  // ═══════════════════════════════════════════════════════════════
  // CURRENCY CONVERSION
  // ═══════════════════════════════════════════════════════════════

  const currency = getCurrencyForRegion(region);

  if (currency.code !== 'USD') {
    minCents = Math.round(minCents * currency.rate);
    maxCents = Math.round(maxCents * currency.rate);

    steps.push({
      step: 'currency_conversion',
      description: `Converted to ${currency.code}`,
      factor: currency.rate,
      resultRange: [minCents, maxCents],
    });
  }

  // ═══════════════════════════════════════════════════════════════
  // FINAL CONFIDENCE
  // ═══════════════════════════════════════════════════════════════

  const confidence = calculateConfidenceV2(
    phase1Result.confidence,
    marketContext,
    visionQuality
  );

  // ═══════════════════════════════════════════════════════════════
  // GENERATE EXPLANATION
  // ═══════════════════════════════════════════════════════════════

  const explanation = generateExplanationV2({
    phase1Result,
    region,
    isUrban,
    seasonalFactor,
    photoQualityScore,
    currency,
    minCents,
    maxCents,
    confidence,
  });

  // ═══════════════════════════════════════════════════════════════
  // BUILD RESPONSE
  // ═══════════════════════════════════════════════════════════════

  return {
    success: true,
    estimate: {
      priceEstimateMinCents: minCents,
      priceEstimateMaxCents: maxCents,
      priceEstimateMin: minCents / 100,
      priceEstimateMax: maxCents / 100,
      priceRangeFormatted: formatPriceRange(minCents, maxCents, currency.code),
      currency: currency.code,
      currencySymbol: currency.symbol,
      confidence,
      marketContext: `${region} / ${isUrban ? 'urban' : 'suburban'} / ${segment}`,
      explanation,
      caveats: caveats.length > 0 ? caveats : undefined,
      inputSummary: {
        ...phase1Result.inputSummary,
        region,
        isUrban,
        photoQualityScore,
      },
    },
  };
}
```

---

## Cost & Performance Analysis

### Compute Cost

| Operation | Complexity | Latency Impact |
|-----------|------------|----------------|
| Phase 1 baseline | O(1) | ~1ms |
| Regional lookup | O(1) hash | ~0.01ms |
| Urban lookup | O(1) hash | ~0.01ms |
| Seasonality lookup | O(1) array | ~0.01ms |
| Photo quality calc | O(1) arithmetic | ~0.01ms |
| Range narrowing | O(1) arithmetic | ~0.01ms |
| Currency conversion | O(1) arithmetic | ~0.01ms |
| **Total Phase 2** | O(1) | **~1.1ms** |

### Memory Cost

| Data | Size | Load Frequency |
|------|------|----------------|
| Regional indices | ~5KB | Startup |
| Currency rates | ~2KB | Startup |
| Seasonality patterns | ~3KB | Startup |
| Urban classification | ~50KB (EU+US) | Startup |
| **Total** | **~60KB** | **Once** |

### Network Cost

| Operation | External Calls | Cost |
|-----------|----------------|------|
| Price estimation | 0 | Free |
| Dataset updates | 1/month (manual) | Free |

### Scaling

- Pricing is stateless → horizontally scalable
- No database queries during estimation
- All data loaded at startup → no cold-start penalty
- Can handle 10,000+ requests/second on single instance

---

## Acceptance Tests

### Unit Tests

```typescript
describe('Phase 2 Pricing', () => {
  // Layer 2: Market Adjustments
  describe('Regional Price Index', () => {
    it('applies NL index to electronics category', () => {
      const input = { category: 'consumer_electronics_portable', marketContext: { region: 'NL' } };
      const result = estimatePriceV2(input);
      // NL electronics index is 1.05
      expect(result.estimate.priceEstimateMaxCents).toBeGreaterThan(basePrice * 1.04);
    });

    it('uses default index for unknown category', () => {
      const input = { category: 'unknown', marketContext: { region: 'DE' } };
      const result = estimatePriceV2(input);
      expect(result.estimate.inputSummary.region).toBe('DE');
    });
  });

  describe('Urban Premium', () => {
    it('applies urban premium to fashion category', () => {
      const urban = estimatePriceV2({ category: 'textile', marketContext: { isUrban: true } });
      const suburban = estimatePriceV2({ category: 'textile', marketContext: { isUrban: false } });
      expect(urban.estimate.priceEstimateMaxCents).toBeGreaterThan(suburban.estimate.priceEstimateMaxCents);
    });
  });

  describe('Seasonality', () => {
    it('applies November boost to fashion', () => {
      jest.useFakeTimers().setSystemTime(new Date('2024-11-15'));
      const result = estimatePriceV2({ category: 'textile' });
      // November fashion factor is 1.20
      expect(result.estimate.explanation.some(e => e.includes('High demand'))).toBe(true);
    });

    it('applies January slump to electronics', () => {
      jest.useFakeTimers().setSystemTime(new Date('2024-01-15'));
      const result = estimatePriceV2({ category: 'consumer_electronics_portable' });
      expect(result.estimate.explanation.some(e => e.includes('Low demand'))).toBe(true);
    });
  });

  // Layer 3: Confidence Weighting
  describe('Photo Quality', () => {
    it('narrows range with high quality photos', () => {
      const lowQuality = estimatePriceV2({
        category: 'furniture',
        visionQuality: { photoCount: 1, blurScore: 0.8 }
      });
      const highQuality = estimatePriceV2({
        category: 'furniture',
        visionQuality: { photoCount: 5, blurScore: 0.1, avgResolutionMp: 12 }
      });

      const lowSpread = lowQuality.estimate.priceEstimateMaxCents - lowQuality.estimate.priceEstimateMinCents;
      const highSpread = highQuality.estimate.priceEstimateMaxCents - highQuality.estimate.priceEstimateMinCents;

      expect(highSpread).toBeLessThan(lowSpread);
    });
  });

  describe('Wear Detection', () => {
    it('downgrades condition when scratches detected', () => {
      const clean = estimatePriceV2({
        condition: 'LIKE_NEW',
        visionQuality: { wearIndicators: {} }
      });
      const scratched = estimatePriceV2({
        condition: 'LIKE_NEW',
        visionQuality: { wearIndicators: { scratchesDetected: true } }
      });

      expect(scratched.estimate.priceEstimateMaxCents).toBeLessThan(clean.estimate.priceEstimateMaxCents);
    });
  });

  // Currency
  describe('Currency Conversion', () => {
    it('converts USD to EUR for NL', () => {
      const result = estimatePriceV2({ marketContext: { region: 'NL' } });
      expect(result.estimate.currency).toBe('EUR');
      expect(result.estimate.currencySymbol).toBe('€');
    });

    it('uses GBP for UK', () => {
      const result = estimatePriceV2({ marketContext: { region: 'UK' } });
      expect(result.estimate.currency).toBe('GBP');
    });
  });

  // Confidence
  describe('Confidence V2', () => {
    it('boosts confidence with market context', () => {
      const noContext = estimatePriceV2({ category: 'furniture' });
      const withContext = estimatePriceV2({
        category: 'furniture',
        brand: 'IKEA',
        condition: 'GOOD',
        marketContext: { region: 'NL', isUrban: true },
        visionQuality: { photoCount: 4, blurScore: 0.1 }
      });

      // More context = higher confidence
      const confOrder = { LOW: 0, MEDIUM: 1, HIGH: 2 };
      expect(confOrder[withContext.estimate.confidence]).toBeGreaterThanOrEqual(
        confOrder[noContext.estimate.confidence]
      );
    });
  });
});
```

### Regression Tests

```typescript
describe('Phase 1 Compatibility', () => {
  it('returns same result as Phase 1 when no market context provided', () => {
    const input = {
      itemId: 'test',
      category: 'furniture',
      brand: 'IKEA',
      condition: 'GOOD'
    };

    const phase1 = estimatePrice(input);
    const phase2 = estimatePriceV2(input);

    // Without market context, Phase 2 should match Phase 1 (in USD)
    expect(phase2.estimate.priceEstimateMinCents).toBe(phase1.priceEstimateMinCents);
    expect(phase2.estimate.priceEstimateMaxCents).toBe(phase1.priceEstimateMaxCents);
  });

  it('preserves all Phase 1 explanation lines', () => {
    const result = estimatePriceV2({ category: 'textile', brand: 'NIKE' });

    expect(result.estimate.explanation.some(e => e.includes('Category'))).toBe(true);
    expect(result.estimate.explanation.some(e => e.includes('Brand'))).toBe(true);
  });
});
```

---

## Phase 3 Boundary (DO NOT IMPLEMENT)

Phase 3 will add marketplace awareness. It is explicitly **out of scope** for Phase 2:

| Feature | Phase 2 | Phase 3 |
|---------|---------|---------|
| Regional PPP indices | ✅ | ✅ |
| Urban/rural premium | ✅ | ✅ |
| Seasonality | ✅ | ✅ |
| Photo quality weighting | ✅ | ✅ |
| Live marketplace prices | ❌ | ✅ |
| User sales history | ❌ | ✅ |
| A/B pricing strategies | ❌ | ✅ |
| Marketplace-specific heuristics | ❌ | ✅ |
| Dynamic pricing optimization | ❌ | ✅ |
| Competitor monitoring | ❌ | ✅ |

**Phase 3 will require:**
- Marketplace API integrations (eBay, Vinted, etc.)
- Real-time price scraping infrastructure
- User sales tracking and analytics
- ML-based price optimization
- Legal review for price scraping compliance

**Phase 2 prepares for Phase 3 by:**
- Establishing the layered architecture
- Defining the market context contract
- Implementing the confidence framework
- Creating the explanation pipeline

---

## Implementation Checklist

### Files to Create

- [ ] `backend/src/modules/pricing/types-v2.ts` - Extended types
- [ ] `backend/src/modules/pricing/market-data.ts` - Market data loader
- [ ] `backend/src/modules/pricing/regional-indices.ts` - Regional adjustment logic
- [ ] `backend/src/modules/pricing/urban-premium.ts` - Urban/rural logic
- [ ] `backend/src/modules/pricing/seasonality.ts` - Seasonality factors
- [ ] `backend/src/modules/pricing/vision-quality.ts` - Photo quality scoring
- [ ] `backend/src/modules/pricing/wear-detection.ts` - Wear indicator processing
- [ ] `backend/src/modules/pricing/currency.ts` - Currency conversion
- [ ] `backend/src/modules/pricing/estimator-v2.ts` - Main Phase 2 estimator
- [ ] `backend/src/modules/pricing/data/regional-indices/2024-v1.json`
- [ ] `backend/src/modules/pricing/data/currencies/2024-01.json`
- [ ] `backend/src/modules/pricing/data/seasonality/patterns-v1.json`

### Files to Modify

- [ ] `backend/src/modules/pricing/routes.ts` - Add V2 endpoint
- [ ] `backend/src/modules/pricing/index.ts` - Export new functions
- [ ] `backend/src/app.ts` - No changes needed (routes auto-registered)

### Tests to Add

- [ ] `backend/src/modules/pricing/estimator-v2.test.ts` - Unit tests
- [ ] `backend/src/modules/pricing/market-data.test.ts` - Data loading tests
- [ ] `backend/src/modules/pricing/routes-v2.test.ts` - API tests

---

## Design Decisions (Resolved)

1. **Urban Classification Source:** ✅ Postal code database (lighter than OSM, sufficient accuracy)

2. **Seasonality Granularity:** ✅ Monthly factors (simpler, weekly adds complexity without proportional value)

3. **Wear Detection Integration:** ✅ Yes, add wear detection to vision pipeline first (Phase 2 will stub the interface)

4. **Currency Rate Source:** ✅ ECB for EUR, Federal Reserve for USD crosses

5. **Regional Coverage:** ✅ EU + US + UK only (expand later based on user base)
