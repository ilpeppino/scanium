/**
 * Pricing Estimator V2 Tests
 *
 * Acceptance tests for the market-aware price estimation algorithm.
 * Tests regional adjustments, urban premium, seasonality, and vision quality.
 */

import { describe, it, expect, beforeAll } from 'vitest';
import { estimatePriceV2, estimatePriceV2Batch } from './estimator-v2.js';
import { initializeMarketData } from './market-data.js';
import { PriceEstimateInputV2 } from './types-v2.js';

// Initialize market data before tests
beforeAll(() => {
  initializeMarketData();
});

describe('Pricing Estimator V2', () => {
  describe('Basic Functionality', () => {
    it('produces a price range in local currency', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'test-1',
        category: 'consumer_electronics_portable',
        marketContext: {
          region: 'DE',
        },
      };

      const result = estimatePriceV2(input);

      expect(result.priceEstimateMinCents).toBeLessThanOrEqual(result.priceEstimateMaxCents);
      expect(result.priceEstimateMinCents).toBeGreaterThan(0);
      expect(result.currency).toBe('EUR');
      expect(result.currencySymbol).toBe('€');
    });

    it('includes market context in result', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'test-2',
        category: 'furniture',
        marketContext: {
          region: 'UK',
          isUrban: true,
        },
      };

      const result = estimatePriceV2(input);

      expect(result.marketContext).toContain('UK');
      expect(result.marketContext).toContain('urban');
      expect(result.inputSummary.region).toBe('UK');
      expect(result.inputSummary.isUrban).toBe(true);
    });

    it('formats price range with local currency symbol', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'test-3',
        category: 'textile',
        marketContext: {
          region: 'SE',
        },
      };

      const result = estimatePriceV2(input);

      expect(result.priceRangeFormatted).toMatch(/kr/);
      expect(result.currency).toBe('SEK');
    });

    it('includes photo quality score in summary', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'test-4',
        category: 'decor',
        visionQuality: {
          photoCount: 5,
          avgResolutionMp: 12,
          blurScore: 0.1,
          lightingQuality: 'GOOD',
        },
      };

      const result = estimatePriceV2(input);

      expect(result.inputSummary.photoQualityScore).toBeGreaterThan(0);
    });
  });

  describe('Regional Price Adjustments', () => {
    it('adjusts prices based on region', () => {
      const baseInput: PriceEstimateInputV2 = {
        itemId: 'regional-1',
        category: 'consumer_electronics_portable',
        brand: 'APPLE',
      };

      const usResult = estimatePriceV2({
        ...baseInput,
        marketContext: { region: 'US' },
      });

      const deResult = estimatePriceV2({
        ...baseInput,
        marketContext: { region: 'DE' },
      });

      // Germany typically has different price index than US
      // The results should differ when converted to cents
      expect(usResult.priceEstimateMinCents).not.toBe(deResult.priceEstimateMinCents);
    });

    it('defaults to US when no region specified', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'regional-2',
        category: 'furniture',
      };

      const result = estimatePriceV2(input);

      expect(result.inputSummary.region).toBe('US');
      expect(result.currency).toBe('USD');
    });

    it('applies correct currency for each region', () => {
      const regions = [
        { region: 'US', currency: 'USD', symbol: '$' },
        { region: 'UK', currency: 'GBP', symbol: '£' },
        { region: 'DE', currency: 'EUR', symbol: '€' },
        { region: 'FR', currency: 'EUR', symbol: '€' },
        { region: 'SE', currency: 'SEK', symbol: 'kr' },
      ] as const;

      for (const { region, currency, symbol } of regions) {
        const input: PriceEstimateInputV2 = {
          itemId: `currency-${region}`,
          category: 'kitchen_appliance',
          marketContext: { region },
        };

        const result = estimatePriceV2(input);

        expect(result.currency).toBe(currency);
        expect(result.currencySymbol).toBe(symbol);
      }
    });
  });

  describe('Urban Premium', () => {
    it('applies urban premium to max price', () => {
      const baseInput: PriceEstimateInputV2 = {
        itemId: 'urban-1',
        category: 'furniture', // Has 15% urban premium
        marketContext: { region: 'US' },
      };

      const suburbanResult = estimatePriceV2({
        ...baseInput,
        marketContext: { region: 'US', isUrban: false },
      });

      const urbanResult = estimatePriceV2({
        ...baseInput,
        marketContext: { region: 'US', isUrban: true },
      });

      // Urban max should be higher than suburban
      expect(urbanResult.priceEstimateMaxCents).toBeGreaterThan(
        suburbanResult.priceEstimateMaxCents
      );

      // Min should stay the same (urban premium only affects max)
      expect(urbanResult.priceEstimateMinCents).toBe(suburbanResult.priceEstimateMinCents);
    });

    it('resolves urban status from postal code', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'urban-2',
        category: 'textile',
        marketContext: {
          region: 'US',
          postalCode: '10001', // NYC prefix
        },
      };

      const result = estimatePriceV2(input);

      expect(result.inputSummary.isUrban).toBe(true);
    });

    it('explicit isUrban overrides postal code lookup', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'urban-3',
        category: 'textile',
        marketContext: {
          region: 'US',
          postalCode: '10001', // NYC prefix (urban)
          isUrban: false, // Explicit override
        },
      };

      const result = estimatePriceV2(input);

      expect(result.inputSummary.isUrban).toBe(false);
    });

    it('fashion has higher urban premium than electronics', () => {
      const fashionUrban: PriceEstimateInputV2 = {
        itemId: 'premium-1',
        category: 'textile', // fashion segment
        marketContext: { region: 'US', isUrban: true },
      };

      const fashionSuburban: PriceEstimateInputV2 = {
        ...fashionUrban,
        marketContext: { region: 'US', isUrban: false },
      };

      const electronicsUrban: PriceEstimateInputV2 = {
        itemId: 'premium-2',
        category: 'consumer_electronics_portable',
        marketContext: { region: 'US', isUrban: true },
      };

      const electronicsSuburban: PriceEstimateInputV2 = {
        ...electronicsUrban,
        marketContext: { region: 'US', isUrban: false },
      };

      const fashionUrbanResult = estimatePriceV2(fashionUrban);
      const fashionSuburbanResult = estimatePriceV2(fashionSuburban);
      const electronicsUrbanResult = estimatePriceV2(electronicsUrban);
      const electronicsSuburbanResult = estimatePriceV2(electronicsSuburban);

      const fashionPremiumPct =
        (fashionUrbanResult.priceEstimateMaxCents - fashionSuburbanResult.priceEstimateMaxCents) /
        fashionSuburbanResult.priceEstimateMaxCents;

      const electronicsPremiumPct =
        (electronicsUrbanResult.priceEstimateMaxCents -
          electronicsSuburbanResult.priceEstimateMaxCents) /
        electronicsSuburbanResult.priceEstimateMaxCents;

      // Fashion (12%) should have higher premium than electronics (5%)
      expect(fashionPremiumPct).toBeGreaterThan(electronicsPremiumPct);
    });
  });

  describe('Vision Quality', () => {
    it('higher photo quality narrows price range', () => {
      const lowQuality: PriceEstimateInputV2 = {
        itemId: 'quality-1',
        category: 'consumer_electronics_portable',
        visionQuality: {
          photoCount: 1,
          avgResolutionMp: 2,
          blurScore: 0.8,
          lightingQuality: 'POOR',
        },
      };

      const highQuality: PriceEstimateInputV2 = {
        itemId: 'quality-2',
        category: 'consumer_electronics_portable',
        visionQuality: {
          photoCount: 5,
          avgResolutionMp: 12,
          blurScore: 0.1,
          lightingQuality: 'GOOD',
        },
      };

      const lowQualityResult = estimatePriceV2(lowQuality);
      const highQualityResult = estimatePriceV2(highQuality);

      const lowSpread =
        lowQualityResult.priceEstimateMaxCents - lowQualityResult.priceEstimateMinCents;
      const highSpread =
        highQualityResult.priceEstimateMaxCents - highQualityResult.priceEstimateMinCents;

      // High quality should have narrower spread
      expect(highSpread).toBeLessThan(lowSpread);
    });

    it('calculates photo quality score correctly', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'score-1',
        category: 'decor',
        visionQuality: {
          photoCount: 5, // 50 points
          avgResolutionMp: 12, // 20 points
          blurScore: 0, // 15 points
          lightingQuality: 'GOOD', // 15 points
        },
      };

      const result = estimatePriceV2(input);

      // Should be max score (100)
      expect(result.inputSummary.photoQualityScore).toBe(100);
    });

    it('describes photo quality in explanation', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'describe-1',
        category: 'furniture',
        visionQuality: {
          photoCount: 3,
          avgResolutionMp: 8,
          blurScore: 0.2,
          lightingQuality: 'FAIR',
        },
      };

      const result = estimatePriceV2(input);

      expect(result.explanation.some((line) => line.includes('photo quality'))).toBe(true);
    });
  });

  describe('Wear Detection', () => {
    it('wear indicators refine condition downward', () => {
      const noWear: PriceEstimateInputV2 = {
        itemId: 'wear-1',
        category: 'textile',
        condition: 'LIKE_NEW',
      };

      const withWear: PriceEstimateInputV2 = {
        itemId: 'wear-2',
        category: 'textile',
        condition: 'LIKE_NEW',
        visionQuality: {
          wearIndicators: {
            scratchesDetected: true,
            stainDetected: true,
          },
        },
      };

      const noWearResult = estimatePriceV2(noWear);
      const withWearResult = estimatePriceV2(withWear);

      // With wear should have lower prices
      expect(withWearResult.priceEstimateMaxCents).toBeLessThan(noWearResult.priceEstimateMaxCents);
    });

    it('describes detected wear in explanation', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'wear-3',
        category: 'furniture',
        visionQuality: {
          wearIndicators: {
            scratchesDetected: true,
            fadeDetected: true,
          },
        },
      };

      const result = estimatePriceV2(input);

      expect(result.explanation.some((line) => line.includes('Scratches detected'))).toBe(true);
      expect(result.explanation.some((line) => line.includes('Color fading detected'))).toBe(true);
    });

    it('adds caveat when condition adjusted by wear', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'wear-4',
        category: 'consumer_electronics_portable',
        condition: 'GOOD',
        visionQuality: {
          wearIndicators: {
            tearDetected: true,
          },
        },
      };

      const result = estimatePriceV2(input);

      expect(result.caveats?.some((c) => c.includes('wear'))).toBe(true);
    });
  });

  describe('Confidence Calculation', () => {
    it('HIGH confidence with full market context and good photos', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'conf-1',
        category: 'consumer_electronics_portable',
        brand: 'APPLE',
        brandConfidence: 'HIGH',
        condition: 'GOOD',
        productType: 'smartphone',
        marketContext: {
          region: 'US',
          isUrban: true,
        },
        visionQuality: {
          photoCount: 5,
          avgResolutionMp: 12,
          blurScore: 0.1,
          lightingQuality: 'GOOD',
        },
      };

      const result = estimatePriceV2(input);

      expect(result.confidence).toBe('HIGH');
    });

    it('market context improves confidence', () => {
      const baseInput: PriceEstimateInputV2 = {
        itemId: 'conf-2',
        category: 'furniture',
      };

      const noContextResult = estimatePriceV2(baseInput);

      const withContextResult = estimatePriceV2({
        ...baseInput,
        marketContext: {
          region: 'DE',
          isUrban: true,
        },
      });

      // With market context should have at least same or better confidence
      const confidenceOrder = { LOW: 0, MEDIUM: 1, HIGH: 2 };
      expect(confidenceOrder[withContextResult.confidence]).toBeGreaterThanOrEqual(
        confidenceOrder[noContextResult.confidence]
      );
    });

    it('photo quality affects confidence', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'conf-3',
        category: 'textile',
        brand: 'NIKE',
        visionQuality: {
          photoCount: 5,
          avgResolutionMp: 12,
          blurScore: 0.1,
          lightingQuality: 'GOOD',
        },
      };

      const result = estimatePriceV2(input);

      // High quality photos should contribute to at least MEDIUM confidence
      expect(['MEDIUM', 'HIGH']).toContain(result.confidence);
    });
  });

  describe('Calculation Steps', () => {
    it('includes phase1 baseline step', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'steps-1',
        category: 'drinkware',
      };

      const result = estimatePriceV2(input);

      expect(result.calculationSteps.some((s) => s.step === 'phase1_baseline')).toBe(true);
    });

    it('includes regional adjustment step when applicable', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'steps-2',
        category: 'kitchen_appliance',
        marketContext: {
          region: 'DE', // Has different index than US
        },
      };

      const result = estimatePriceV2(input);

      expect(result.calculationSteps.some((s) => s.step === 'regional_adjustment')).toBe(true);
    });

    it('includes urban premium step when urban', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'steps-3',
        category: 'furniture',
        marketContext: {
          region: 'US',
          isUrban: true,
        },
      };

      const result = estimatePriceV2(input);

      expect(result.calculationSteps.some((s) => s.step === 'urban_premium')).toBe(true);
    });

    it('includes currency conversion step for non-USD regions', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'steps-4',
        category: 'textile',
        marketContext: {
          region: 'UK',
        },
      };

      const result = estimatePriceV2(input);

      expect(result.calculationSteps.some((s) => s.step === 'currency_conversion')).toBe(true);
    });
  });

  describe('Batch Processing', () => {
    it('processes multiple items in batch', () => {
      const inputs: PriceEstimateInputV2[] = [
        {
          itemId: 'batch-1',
          category: 'drinkware',
          marketContext: { region: 'US' },
        },
        {
          itemId: 'batch-2',
          category: 'furniture',
          marketContext: { region: 'UK' },
        },
        {
          itemId: 'batch-3',
          category: 'textile',
          marketContext: { region: 'DE' },
        },
      ];

      const results = estimatePriceV2Batch(inputs);

      expect(results).toHaveLength(3);
      expect(results[0].currency).toBe('USD');
      expect(results[1].currency).toBe('GBP');
      expect(results[2].currency).toBe('EUR');
    });
  });

  describe('Edge Cases', () => {
    it('handles empty market context', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'edge-1',
        category: 'consumer_electronics_portable',
        marketContext: {},
      };

      const result = estimatePriceV2(input);

      expect(result.priceEstimateMinCents).toBeGreaterThan(0);
      expect(result.inputSummary.region).toBe('US');
    });

    it('handles empty vision quality', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'edge-2',
        category: 'furniture',
        visionQuality: {},
      };

      const result = estimatePriceV2(input);

      expect(result.priceEstimateMinCents).toBeGreaterThan(0);
      // Empty object gets default blur (0.5) and lighting (FAIR) scores
      expect(result.inputSummary.photoQualityScore).toBeGreaterThanOrEqual(0);
    });

    it('handles unknown region gracefully', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'edge-3',
        category: 'textile',
        marketContext: {
          region: 'XX' as never, // Invalid region
        },
      };

      const result = estimatePriceV2(input);

      // Should fall back to USD
      expect(result.currency).toBe('USD');
      expect(result.priceEstimateMinCents).toBeGreaterThan(0);
    });
  });

  describe('Determinism', () => {
    it('produces same result for same input', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'determinism-1',
        category: 'consumer_electronics_portable',
        brand: 'APPLE',
        condition: 'LIKE_NEW',
        marketContext: {
          region: 'DE',
          isUrban: true,
        },
        visionQuality: {
          photoCount: 3,
          avgResolutionMp: 8,
          blurScore: 0.2,
          lightingQuality: 'GOOD',
        },
      };

      const result1 = estimatePriceV2(input);
      const result2 = estimatePriceV2(input);

      expect(result1.priceEstimateMinCents).toBe(result2.priceEstimateMinCents);
      expect(result1.priceEstimateMaxCents).toBe(result2.priceEstimateMaxCents);
      expect(result1.currency).toBe(result2.currency);
      expect(result1.confidence).toBe(result2.confidence);
    });
  });

  describe('Phase 1 Inheritance', () => {
    it('inherits caveats from Phase 1', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'inherit-1',
        category: 'unknown_category_xyz', // Will generate caveat
        brand: 'UnknownBrandABC', // Will generate caveat
      };

      const result = estimatePriceV2(input);

      // Should have caveats from Phase 1
      expect(result.caveats).toBeDefined();
      expect(result.caveats!.length).toBeGreaterThan(0);
    });

    it('preserves brand tier from Phase 1', () => {
      const input: PriceEstimateInputV2 = {
        itemId: 'inherit-2',
        category: 'textile',
        brand: 'LOUIS VUITTON',
      };

      const result = estimatePriceV2(input);

      expect(result.inputSummary.brandTier).toBe('LUXURY');
    });
  });
});
