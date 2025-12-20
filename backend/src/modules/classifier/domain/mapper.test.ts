import { describe, it, expect } from 'vitest';
import pack from './home-resale.json' assert { type: 'json' };
import { mapSignalsToDomainCategory } from './mapper.js';

const tieredPack = {
  id: 'tiered',
  name: 'Tiered Pack',
  threshold: 0.3,
  contextPenalty: 0.5,
  contextStoplist: ['table', 'surface', 'furniture'],
  categories: [
    {
      id: 'table',
      label: 'Table',
      tokens: ['table'],
      priority: 2,
      priorityTier: 3,
    },
    {
      id: 'mug',
      label: 'Mug',
      tokens: ['mug'],
      priority: 5,
      priorityTier: 1,
    },
    {
      id: 'decor',
      label: 'Decor',
      tokens: ['decor'],
      priority: 3,
      priorityTier: 2,
    },
  ],
};

describe('mapSignalsToDomainCategory', () => {
  it('prefers higher-priority categories when multiple matches exist', () => {
    const result = mapSignalsToDomainCategory(pack, {
      labels: [
        { description: 'kitchen table', score: 0.82 },
        { description: 'glass bottle', score: 0.4 },
      ],
    });

    // Drinkware (priority 10) should win over furniture (priority 2)
    expect(result.domainCategoryId).toBe('drinkware');
    expect(result.confidence).toBeCloseTo(0.4, 2);
    expect(result.attributes.segment).toBe('container');
    expect(result.debug.bestScore).toBeCloseTo(0.4, 2);
    expect(result.debug.reason).toContain('threshold');
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
    expect(result.debug.reason).toContain('No category met threshold');
  });

  it('prefers tier 1 items over higher-confidence context labels', () => {
    const result = mapSignalsToDomainCategory(tieredPack, {
      labels: [
        { description: 'wooden table', score: 0.9 },
        { description: 'coffee mug', score: 0.4 },
      ],
    });

    expect(result.domainCategoryId).toBe('mug');
    expect(result.confidence).toBeCloseTo(0.4, 2);
  });

  it('falls back to lower priorities when higher priorities lack confident matches', () => {
    const result = mapSignalsToDomainCategory(
      { ...tieredPack, threshold: 0.35 },
      {
        labels: [
          { description: 'tiny mug', score: 0.2 }, // below threshold
          { description: 'wall decor', score: 0.5 },
        ],
      }
    );

    expect(result.domainCategoryId).toBe('decor');
    expect(result.confidence).toBeCloseTo(0.5, 2);
  });

  it('applies context penalty for stoplisted signals', () => {
    const result = mapSignalsToDomainCategory(
      { ...tieredPack, threshold: 0.4 },
      {
        labels: [{ description: 'table', score: 0.9 }],
      }
    );

    expect(result.domainCategoryId).toBe('table');
    expect(result.confidence).toBeCloseTo(0.45, 2);
    expect(result.debug.contextPenaltyApplied).toBe(true);
  });

  it('falls back to context when only generic labels exist', () => {
    const result = mapSignalsToDomainCategory(pack, {
      labels: [
        { description: 'wood table', score: 0.75 },
        { description: 'hardwood surface', score: 0.6 },
      ],
    });

    expect(result.domainCategoryId).toBe('furniture');
    expect(result.debug.reason).toContain('tier 3');
  });

  it('prefers specific tier when both specific and generic labels exceed threshold', () => {
    const result = mapSignalsToDomainCategory(pack, {
      labels: [
        { description: 'table', score: 0.9 },
        { description: 'coffee mug', score: 0.32 },
      ],
    });

    expect(result.domainCategoryId).toBe('drinkware');
    expect(result.confidence).toBeCloseTo(0.32, 2);
  });
});
