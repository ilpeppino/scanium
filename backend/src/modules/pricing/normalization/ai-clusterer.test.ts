import { describe, expect, it } from 'vitest';
import { AiClusterer } from './ai-clusterer.js';
import { NormalizationInput } from '../types-v4.js';

describe('AiClusterer', () => {
  it('passes through listings when disabled', async () => {
    const clusterer = new AiClusterer({
      enabled: false,
      openaiModel: 'gpt-4o-mini',
      timeoutMs: 10000,
    });

    const input: NormalizationInput = {
      targetBrand: 'Apple',
      targetModel: 'iPhone 13',
      targetProductType: 'smartphone',
      listings: [
        {
          title: 'Apple iPhone 13',
          price: 500,
          currency: 'EUR',
          url: 'u1',
          marketplace: 'ebay',
        },
        {
          title: 'Apple iPhone 12',
          price: 400,
          currency: 'EUR',
          url: 'u2',
          marketplace: 'ebay',
        },
      ],
    };

    const result = await clusterer.normalize(input);
    expect(result.relevantListings).toHaveLength(2);
    expect(result.excludedListings).toHaveLength(0);
    expect(result.clusterSummary).toContain('disabled');
  });
});
