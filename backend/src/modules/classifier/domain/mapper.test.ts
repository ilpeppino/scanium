import { describe, it, expect } from 'vitest';
import pack from './home-resale.json' assert { type: 'json' };
import { mapSignalsToDomainCategory } from './mapper.js';

describe('mapSignalsToDomainCategory', () => {
  it('selects the category with the strongest matching label', () => {
    const result = mapSignalsToDomainCategory(pack, {
      labels: [
        { description: 'kitchen table', score: 0.82 },
        { description: 'lamp', score: 0.4 },
      ],
    });

    expect(result.domainCategoryId).toBe('table');
    expect(result.confidence).toBeCloseTo(0.82, 2);
    expect(result.attributes.segment).toBe('surface');
  });

  it('returns null when below threshold', () => {
    const result = mapSignalsToDomainCategory(
      { ...pack, threshold: 0.9 },
      {
        labels: [{ description: 'chair', score: 0.5 }],
      }
    );

    expect(result.domainCategoryId).toBeNull();
    expect(result.confidence).toBeNull();
    expect(result.attributes).toEqual({});
  });
});
