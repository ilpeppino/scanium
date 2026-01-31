import { describe, expect, it } from 'vitest';
import { buildCatalogQueryContext } from './catalog-query-context.js';
import { SubtypeClassifier } from './subtype-classifier.js';
import { buildQueryPlan } from './query-plan.js';
import { CategoryResolver } from './category-resolver.js';

const resolver: CategoryResolver = {
  resolve: async () => ({ confidence: 'low', source: 'none' }),
};

describe('buildQueryPlan', () => {
  const classifier = new SubtypeClassifier();

  it('builds device query without subtype token and with accessory post-filter', async () => {
    const context = buildCatalogQueryContext(
      {
        subtype: 'electronics_smartphone',
        brand: 'Apple',
        model: 'iPhone 13',
        condition: 'GOOD',
      },
      classifier
    );

    const plan = await buildQueryPlan(context, 'ebay', resolver);
    expect(plan.q).toBe('Apple iPhone 13');
    expect(plan.postFilterRules).toContain('exclude_accessory_like');
    expect(plan.filters.some((filter) => filter.type === 'excludeParts')).toBe(true);
  });

  it('builds apparel query with subtype token', async () => {
    const context = buildCatalogQueryContext(
      {
        subtype: 'textile_dress',
        brand: 'Zara',
        model: 'Floral',
        condition: 'GOOD',
      },
      classifier
    );

    const plan = await buildQueryPlan(context, 'marktplaats', resolver);
    expect(plan.q).toContain('Zara');
    expect(plan.q).toContain('Floral');
    expect(plan.q).toContain('textile_dress');
    expect(plan.postFilterRules).not.toContain('exclude_accessory_like');
  });

  it('builds furniture query with subtype token', async () => {
    const context = buildCatalogQueryContext(
      {
        subtype: 'furniture_table',
        brand: 'Ikea',
        model: 'Lack',
        condition: 'GOOD',
      },
      classifier
    );

    const plan = await buildQueryPlan(context, 'marktplaats', resolver);
    expect(plan.q).toContain('Ikea');
    expect(plan.q).toContain('Lack');
    expect(plan.q).toContain('furniture_table');
  });
});
