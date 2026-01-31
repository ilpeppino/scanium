import { ItemCondition } from '../types-v3.js';

export type SubtypeClass = 'device' | 'accessory' | 'apparel' | 'furniture' | 'media' | 'other';

export interface CatalogQueryContext {
  subtype: string;
  subtypeClass: SubtypeClass;
  brand?: string;
  model?: string;
  freeText?: string;
  condition?: ItemCondition;
  identifier?: string;
}

export type CategoryResolution = {
  categoryId?: string;
  confidence: 'high' | 'medium' | 'low';
  source: 'override' | 'cache' | 'suggestion' | 'none';
};

export type QueryFilter =
  | { type: 'condition'; value: ItemCondition }
  | { type: 'excludeParts'; value: boolean }
  | { type: 'excludeBundles'; value: boolean };

export type PostFilterRuleId = 'exclude_accessory_like' | 'exclude_parts_only' | 'exclude_bundle';

export type CategoryRequirement = 'required' | 'optional' | 'none';

export interface QueryPolicy {
  id: SubtypeClass;
  categoryRequirement: CategoryRequirement;
  includeSubtypeInQuery: boolean;
  includeBrandInQuery: boolean;
  includeModelInQuery: boolean;
  includeFreeTextInQuery: boolean;
  excludePartsUnlessCondition: boolean;
  excludeBundles: boolean;
  accessoryPostFilter: boolean;
}

export interface QueryPlanTelemetry {
  subtype: string;
  subtypeClass: SubtypeClass;
  tokens: string[];
  categoryResolution: CategoryResolution;
  policyId: string;
  warnings?: string[];
}

export interface QueryPlan {
  q: string;
  categoryId?: string;
  filters: QueryFilter[];
  postFilterRules: PostFilterRuleId[];
  telemetry: QueryPlanTelemetry;
}
