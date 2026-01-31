import { describe, expect, it } from 'vitest';
import { SubtypeClassifier } from './subtype-classifier.js';

describe('SubtypeClassifier', () => {
  const classifier = new SubtypeClassifier();

  it('maps device-like subtypes to device class', () => {
    expect(classifier.classify('electronics_smartphone')).toBe('device');
  });

  it('maps apparel-like subtypes to apparel class', () => {
    expect(classifier.classify('textile_dress')).toBe('apparel');
  });

  it('maps furniture-like subtypes to furniture class', () => {
    expect(classifier.classify('furniture_table')).toBe('furniture');
  });
});
