import { describe, it, expect } from 'vitest';
import {
  mergeAttributes,
  computeSuggestedAdditions,
  formatSummaryText,
  parseSummaryText,
  applyEnrichment,
  applyUserSummaryEdit,
  acceptSuggestion,
  dismissSuggestion,
  createInitialEnrichmentState,
} from './attribute-merge.js';
import {
  StructuredAttribute,
  ItemEnrichmentState,
  EMPTY_ENRICHMENT_STATE,
} from './attribute-types.js';

// Helper to create a test attribute
function createAttr(
  key: string,
  value: string,
  source: 'USER' | 'DETECTED' | 'DEFAULT' | 'UNKNOWN',
  confidence: 'HIGH' | 'MED' | 'LOW' = 'MED',
  updatedAt: number = Date.now()
): StructuredAttribute {
  return { key, value, source, confidence, updatedAt };
}

describe('mergeAttributes', () => {
  describe('USER source priority', () => {
    it('should NOT overwrite USER values with DETECTED', () => {
      const existing: StructuredAttribute[] = [
        createAttr('brand', 'Nike', 'USER', 'HIGH'),
      ];
      const incoming: StructuredAttribute[] = [
        createAttr('brand', 'Adidas', 'DETECTED', 'HIGH'),
      ];

      const result = mergeAttributes(existing, incoming);

      expect(result.merged).toHaveLength(1);
      expect(result.merged[0].value).toBe('Nike');
      expect(result.merged[0].source).toBe('USER');
      expect(result.hasChanges).toBe(false);
    });

    it('should allow USER to overwrite DETECTED', () => {
      const existing: StructuredAttribute[] = [
        createAttr('brand', 'Nike', 'DETECTED', 'HIGH'),
      ];
      const incoming: StructuredAttribute[] = [
        createAttr('brand', 'Adidas', 'USER', 'HIGH'),
      ];

      const result = mergeAttributes(existing, incoming);

      expect(result.merged).toHaveLength(1);
      expect(result.merged[0].value).toBe('Adidas');
      expect(result.merged[0].source).toBe('USER');
      expect(result.hasChanges).toBe(true);
    });
  });

  describe('DETECTED fills missing keys', () => {
    it('should add new keys from DETECTED', () => {
      const existing: StructuredAttribute[] = [
        createAttr('brand', 'Nike', 'USER', 'HIGH'),
      ];
      const incoming: StructuredAttribute[] = [
        createAttr('color', 'Blue', 'DETECTED', 'MED'),
      ];

      const result = mergeAttributes(existing, incoming);

      expect(result.merged).toHaveLength(2);
      expect(result.merged.find((a) => a.key === 'brand')?.value).toBe('Nike');
      expect(result.merged.find((a) => a.key === 'color')?.value).toBe('Blue');
      expect(result.hasChanges).toBe(true);
    });

    it('should add multiple new keys', () => {
      const existing: StructuredAttribute[] = [];
      const incoming: StructuredAttribute[] = [
        createAttr('brand', 'Sony', 'DETECTED', 'HIGH'),
        createAttr('color', 'Black', 'DETECTED', 'MED'),
        createAttr('model', 'WH-1000XM4', 'DETECTED', 'LOW'),
      ];

      const result = mergeAttributes(existing, incoming);

      expect(result.merged).toHaveLength(3);
      expect(result.hasChanges).toBe(true);
    });
  });

  describe('DETECTED vs DETECTED - confidence comparison', () => {
    it('should replace DETECTED with higher confidence DETECTED', () => {
      const existing: StructuredAttribute[] = [
        createAttr('brand', 'Sonny', 'DETECTED', 'LOW'), // typo from low confidence
      ];
      const incoming: StructuredAttribute[] = [
        createAttr('brand', 'Sony', 'DETECTED', 'HIGH'), // correct with high confidence
      ];

      const result = mergeAttributes(existing, incoming);

      expect(result.merged).toHaveLength(1);
      expect(result.merged[0].value).toBe('Sony');
      expect(result.merged[0].confidence).toBe('HIGH');
      expect(result.hasChanges).toBe(true);
    });

    it('should NOT replace DETECTED with lower confidence DETECTED', () => {
      const existing: StructuredAttribute[] = [
        createAttr('brand', 'Sony', 'DETECTED', 'HIGH'),
      ];
      const incoming: StructuredAttribute[] = [
        createAttr('brand', 'Sonny', 'DETECTED', 'LOW'), // wrong with low confidence
      ];

      const result = mergeAttributes(existing, incoming);

      expect(result.merged).toHaveLength(1);
      expect(result.merged[0].value).toBe('Sony');
      expect(result.merged[0].confidence).toBe('HIGH');
      expect(result.hasChanges).toBe(false);
    });

    it('should prefer richer evidence when confidence is equal', () => {
      const existing: StructuredAttribute[] = [
        createAttr('brand', 'Sony', 'DETECTED', 'MED'),
      ];
      const withEvidence = createAttr('brand', 'Sony Corporation', 'DETECTED', 'MED');
      withEvidence.evidence = [
        { type: 'LOGO', rawValue: 'Sony', score: 0.9 },
        { type: 'OCR', rawValue: 'SONY', score: 0.8 },
      ];
      const incoming: StructuredAttribute[] = [withEvidence];

      const result = mergeAttributes(existing, incoming);

      expect(result.merged[0].value).toBe('Sony Corporation');
      expect(result.merged[0].evidence).toHaveLength(2);
    });
  });

  describe('edge cases', () => {
    it('should handle empty existing attributes', () => {
      const existing: StructuredAttribute[] = [];
      const incoming: StructuredAttribute[] = [
        createAttr('brand', 'Nike', 'DETECTED', 'MED'),
      ];

      const result = mergeAttributes(existing, incoming);

      expect(result.merged).toHaveLength(1);
      expect(result.hasChanges).toBe(true);
    });

    it('should handle empty incoming attributes', () => {
      const existing: StructuredAttribute[] = [
        createAttr('brand', 'Nike', 'USER', 'HIGH'),
      ];
      const incoming: StructuredAttribute[] = [];

      const result = mergeAttributes(existing, incoming);

      expect(result.merged).toHaveLength(1);
      expect(result.hasChanges).toBe(false);
    });

    it('should handle both empty', () => {
      const result = mergeAttributes([], []);
      expect(result.merged).toHaveLength(0);
      expect(result.hasChanges).toBe(false);
    });
  });
});

describe('computeSuggestedAdditions', () => {
  it('should suggest new keys as additions', () => {
    const existing: StructuredAttribute[] = [
      createAttr('brand', 'Nike', 'USER', 'HIGH'),
    ];
    const incoming: StructuredAttribute[] = [
      createAttr('color', 'Blue', 'DETECTED', 'MED'),
    ];

    const suggestions = computeSuggestedAdditions(existing, incoming);

    expect(suggestions).toHaveLength(1);
    expect(suggestions[0].action).toBe('add');
    expect(suggestions[0].attribute.key).toBe('color');
    expect(suggestions[0].attribute.value).toBe('Blue');
  });

  it('should NOT suggest replacing USER values', () => {
    const existing: StructuredAttribute[] = [
      createAttr('brand', 'Nike', 'USER', 'HIGH'),
    ];
    const incoming: StructuredAttribute[] = [
      createAttr('brand', 'Adidas', 'DETECTED', 'HIGH'),
    ];

    const suggestions = computeSuggestedAdditions(existing, incoming);

    expect(suggestions).toHaveLength(0);
  });

  it('should suggest replacing DETECTED with higher confidence', () => {
    const existing: StructuredAttribute[] = [
      createAttr('brand', 'Sonny', 'DETECTED', 'LOW'),
    ];
    const incoming: StructuredAttribute[] = [
      createAttr('brand', 'Sony', 'DETECTED', 'HIGH'),
    ];

    const suggestions = computeSuggestedAdditions(existing, incoming);

    expect(suggestions).toHaveLength(1);
    expect(suggestions[0].action).toBe('replace');
    expect(suggestions[0].existingValue).toBe('Sonny');
    expect(suggestions[0].attribute.value).toBe('Sony');
  });

  it('should generate suggestion when summaryTextUserEdited=true and new DETECTED arrives', () => {
    const state: ItemEnrichmentState = {
      attributesStructured: [createAttr('brand', 'Nike', 'USER', 'HIGH')],
      summaryText: 'Brand: Nike',
      summaryTextUserEdited: true,
      suggestedAdditions: [],
    };

    const incoming: StructuredAttribute[] = [
      createAttr('color', 'Red', 'DETECTED', 'MED'),
      createAttr('size', 'L', 'DETECTED', 'LOW'),
    ];

    const newState = applyEnrichment(state, incoming);

    expect(newState.suggestedAdditions).toHaveLength(2);
    expect(newState.attributesStructured).toHaveLength(1); // Not auto-merged
    expect(newState.summaryTextUserEdited).toBe(true);
  });
});

describe('formatSummaryText', () => {
  it('should format attributes in stable order', () => {
    const attrs: StructuredAttribute[] = [
      createAttr('color', 'Blue', 'DETECTED', 'MED'),
      createAttr('brand', 'Nike', 'USER', 'HIGH'),
      createAttr('model', 'Air Max', 'DETECTED', 'LOW'),
    ];

    const text = formatSummaryText(attrs);

    // Should be ordered: brand, model, color (based on ATTRIBUTE_KEY_ORDER)
    const lines = text.split('\n');
    expect(lines[0]).toBe('Brand: Nike');
    expect(lines[1]).toBe('Model: Air Max');
    expect(lines[2]).toBe('Color: Blue');
  });

  it('should produce deterministic output for same input', () => {
    const attrs: StructuredAttribute[] = [
      createAttr('brand', 'Sony', 'DETECTED', 'HIGH'),
      createAttr('color', 'Black', 'DETECTED', 'MED'),
      createAttr('product_type', 'Headphones', 'DETECTED', 'MED'),
    ];

    const text1 = formatSummaryText(attrs);
    const text2 = formatSummaryText(attrs);

    expect(text1).toBe(text2);
  });

  it('should handle empty attributes', () => {
    expect(formatSummaryText([])).toBe('');
  });

  it('should format unknown keys alphabetically after known keys', () => {
    const attrs: StructuredAttribute[] = [
      createAttr('zzzz_custom', 'Value', 'USER', 'HIGH'),
      createAttr('brand', 'Nike', 'USER', 'HIGH'),
      createAttr('aaaa_custom', 'Other', 'USER', 'HIGH'),
    ];

    const text = formatSummaryText(attrs);
    const lines = text.split('\n');

    expect(lines[0]).toBe('Brand: Nike');
    // Custom keys should be sorted alphabetically after known keys
    expect(lines).toContain('Aaaa Custom: Other');
    expect(lines).toContain('Zzzz Custom: Value');
  });

  it('should format key labels with proper capitalization', () => {
    const attrs: StructuredAttribute[] = [
      createAttr('product_type', 'T-Shirt', 'DETECTED', 'MED'),
      createAttr('secondary_color', 'White', 'DETECTED', 'LOW'),
    ];

    const text = formatSummaryText(attrs);

    expect(text).toContain('Product Type: T-Shirt');
    expect(text).toContain('Secondary Color: White');
  });
});

describe('parseSummaryText', () => {
  it('should parse formatted summary back to key-value pairs', () => {
    const text = 'Brand: Nike\nColor: Blue\nModel: Air Max';

    const parsed = parseSummaryText(text);

    expect(parsed).toHaveLength(3);
    expect(parsed.find((p) => p.key === 'brand')?.value).toBe('Nike');
    expect(parsed.find((p) => p.key === 'color')?.value).toBe('Blue');
    expect(parsed.find((p) => p.key === 'model')?.value).toBe('Air Max');
  });

  it('should handle multi-word keys', () => {
    const text = 'Product Type: Headphones\nSecondary Color: Red';

    const parsed = parseSummaryText(text);

    expect(parsed.find((p) => p.key === 'product_type')?.value).toBe('Headphones');
    expect(parsed.find((p) => p.key === 'secondary_color')?.value).toBe('Red');
  });

  it('should handle empty lines', () => {
    const text = 'Brand: Nike\n\nColor: Blue\n';

    const parsed = parseSummaryText(text);

    expect(parsed).toHaveLength(2);
  });

  it('should handle values with colons', () => {
    const text = 'Description: Model: XYZ-123';

    const parsed = parseSummaryText(text);

    expect(parsed).toHaveLength(1);
    expect(parsed[0].key).toBe('description');
    expect(parsed[0].value).toBe('Model: XYZ-123');
  });
});

describe('applyEnrichment', () => {
  it('should auto-merge when summaryTextUserEdited=false', () => {
    const state: ItemEnrichmentState = {
      attributesStructured: [],
      summaryText: '',
      summaryTextUserEdited: false,
      suggestedAdditions: [],
    };

    const incoming: StructuredAttribute[] = [
      createAttr('brand', 'Nike', 'DETECTED', 'HIGH'),
      createAttr('color', 'Blue', 'DETECTED', 'MED'),
    ];

    const newState = applyEnrichment(state, incoming);

    expect(newState.attributesStructured).toHaveLength(2);
    expect(newState.summaryText).toContain('Brand: Nike');
    expect(newState.summaryText).toContain('Color: Blue');
    expect(newState.suggestedAdditions).toHaveLength(0);
  });

  it('should compute suggestions when summaryTextUserEdited=true', () => {
    const state: ItemEnrichmentState = {
      attributesStructured: [createAttr('brand', 'Nike', 'USER', 'HIGH')],
      summaryText: 'Brand: Nike',
      summaryTextUserEdited: true,
      suggestedAdditions: [],
    };

    const incoming: StructuredAttribute[] = [
      createAttr('color', 'Blue', 'DETECTED', 'MED'),
    ];

    const newState = applyEnrichment(state, incoming);

    expect(newState.attributesStructured).toHaveLength(1); // Not merged
    expect(newState.suggestedAdditions).toHaveLength(1);
    expect(newState.suggestedAdditions[0].attribute.key).toBe('color');
  });
});

describe('applyUserSummaryEdit', () => {
  it('should mark values as USER source', () => {
    const state: ItemEnrichmentState = {
      attributesStructured: [createAttr('brand', 'Nike', 'DETECTED', 'MED')],
      summaryText: 'Brand: Nike',
      summaryTextUserEdited: false,
      suggestedAdditions: [],
    };

    const newState = applyUserSummaryEdit(state, 'Brand: Adidas\nColor: Red');

    expect(newState.summaryTextUserEdited).toBe(true);
    const brandAttr = newState.attributesStructured.find((a) => a.key === 'brand');
    expect(brandAttr?.value).toBe('Adidas');
    expect(brandAttr?.source).toBe('USER');

    const colorAttr = newState.attributesStructured.find((a) => a.key === 'color');
    expect(colorAttr?.value).toBe('Red');
    expect(colorAttr?.source).toBe('USER');
  });

  it('should clear suggestions after user edit', () => {
    const state: ItemEnrichmentState = {
      attributesStructured: [],
      summaryText: '',
      summaryTextUserEdited: true,
      suggestedAdditions: [
        {
          attribute: createAttr('color', 'Blue', 'DETECTED', 'MED'),
          reason: 'Detected color',
          action: 'add',
        },
      ],
    };

    const newState = applyUserSummaryEdit(state, 'Brand: Nike');

    expect(newState.suggestedAdditions).toHaveLength(0);
  });
});

describe('acceptSuggestion', () => {
  it('should merge accepted suggestion into attributes', () => {
    const colorAttr = createAttr('color', 'Blue', 'DETECTED', 'MED');
    const state: ItemEnrichmentState = {
      attributesStructured: [createAttr('brand', 'Nike', 'USER', 'HIGH')],
      summaryText: 'Brand: Nike',
      summaryTextUserEdited: true,
      suggestedAdditions: [
        { attribute: colorAttr, reason: 'Detected color', action: 'add' },
      ],
    };

    const newState = acceptSuggestion(state, 0);

    expect(newState.attributesStructured).toHaveLength(2);
    expect(newState.attributesStructured.find((a) => a.key === 'color')?.value).toBe('Blue');
    expect(newState.suggestedAdditions).toHaveLength(0);
    expect(newState.summaryText).toContain('Color: Blue');
  });

  it('should handle invalid index gracefully', () => {
    const state: ItemEnrichmentState = {
      attributesStructured: [],
      summaryText: '',
      summaryTextUserEdited: false,
      suggestedAdditions: [],
    };

    const newState = acceptSuggestion(state, 5);

    expect(newState).toEqual(state);
  });
});

describe('dismissSuggestion', () => {
  it('should remove dismissed suggestion', () => {
    const state: ItemEnrichmentState = {
      attributesStructured: [],
      summaryText: '',
      summaryTextUserEdited: true,
      suggestedAdditions: [
        { attribute: createAttr('color', 'Blue', 'DETECTED', 'MED'), reason: 'Detected', action: 'add' },
        { attribute: createAttr('size', 'L', 'DETECTED', 'LOW'), reason: 'Detected', action: 'add' },
      ],
    };

    const newState = dismissSuggestion(state, 0);

    expect(newState.suggestedAdditions).toHaveLength(1);
    expect(newState.suggestedAdditions[0].attribute.key).toBe('size');
  });
});

describe('createInitialEnrichmentState', () => {
  it('should create state with formatted summary', () => {
    const attrs: StructuredAttribute[] = [
      createAttr('brand', 'Sony', 'DETECTED', 'HIGH'),
      createAttr('color', 'Black', 'DETECTED', 'MED'),
    ];

    const state = createInitialEnrichmentState(attrs);

    expect(state.attributesStructured).toHaveLength(2);
    expect(state.summaryText).toContain('Brand: Sony');
    expect(state.summaryText).toContain('Color: Black');
    expect(state.summaryTextUserEdited).toBe(false);
    expect(state.suggestedAdditions).toHaveLength(0);
  });
});
