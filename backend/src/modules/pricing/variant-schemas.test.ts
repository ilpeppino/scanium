import { describe, expect, it } from 'vitest';
import { getVariantSchema } from './variant-schemas.js';

describe('Variant schemas', () => {
  it('returns laptop schema for laptop product types', () => {
    const schema = getVariantSchema('electronics_laptop');
    const keys = schema.fields.map((field) => field.key);
    expect(keys).toContain('storage');
    expect(keys).toContain('ram');
    expect(schema.completenessOptions.length).toBeGreaterThan(0);
  });

  it('returns fallback schema for unknown product types', () => {
    const schema = getVariantSchema('unknown_category');
    expect(schema.fields.length).toBe(0);
    expect(schema.completenessOptions.length).toBeGreaterThan(0);
  });
});
