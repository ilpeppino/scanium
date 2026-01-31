import { ItemCondition } from '../types-v3.js';
import { SubtypeClassifier } from './subtype-classifier.js';
import { CatalogQueryContext } from './types.js';

export type CatalogQueryContextInput = {
  subtype: string;
  brand?: string;
  model?: string;
  freeText?: string;
  condition?: ItemCondition;
  identifier?: string;
};

export function buildCatalogQueryContext(
  input: CatalogQueryContextInput,
  classifier: SubtypeClassifier
): CatalogQueryContext {
  const subtype = input.subtype?.trim() ?? '';
  return {
    subtype,
    subtypeClass: classifier.classify(subtype),
    brand: input.brand?.trim() || undefined,
    model: input.model?.trim() || undefined,
    freeText: input.freeText?.trim() || undefined,
    condition: input.condition,
    identifier: input.identifier?.trim() || undefined,
  };
}
