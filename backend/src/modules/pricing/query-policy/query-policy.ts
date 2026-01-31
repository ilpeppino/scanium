import { QueryPolicy, SubtypeClass } from './types.js';

const DEFAULT_POLICY: QueryPolicy = {
  id: 'other',
  categoryRequirement: 'optional',
  includeSubtypeInQuery: true,
  includeBrandInQuery: true,
  includeModelInQuery: true,
  includeFreeTextInQuery: true,
  excludePartsUnlessCondition: false,
  excludeBundles: true,
  accessoryPostFilter: false,
};

const POLICIES: Record<SubtypeClass, QueryPolicy> = {
  device: {
    id: 'device',
    categoryRequirement: 'optional',
    includeSubtypeInQuery: false,
    includeBrandInQuery: true,
    includeModelInQuery: true,
    includeFreeTextInQuery: true,
    excludePartsUnlessCondition: true,
    excludeBundles: true,
    accessoryPostFilter: true,
  },
  accessory: {
    id: 'accessory',
    categoryRequirement: 'optional',
    includeSubtypeInQuery: true,
    includeBrandInQuery: true,
    includeModelInQuery: true,
    includeFreeTextInQuery: true,
    excludePartsUnlessCondition: false,
    excludeBundles: true,
    accessoryPostFilter: false,
  },
  apparel: {
    id: 'apparel',
    categoryRequirement: 'optional',
    includeSubtypeInQuery: true,
    includeBrandInQuery: true,
    includeModelInQuery: true,
    includeFreeTextInQuery: true,
    excludePartsUnlessCondition: false,
    excludeBundles: true,
    accessoryPostFilter: false,
  },
  furniture: {
    id: 'furniture',
    categoryRequirement: 'optional',
    includeSubtypeInQuery: true,
    includeBrandInQuery: true,
    includeModelInQuery: true,
    includeFreeTextInQuery: true,
    excludePartsUnlessCondition: false,
    excludeBundles: true,
    accessoryPostFilter: false,
  },
  media: {
    id: 'media',
    categoryRequirement: 'optional',
    includeSubtypeInQuery: true,
    includeBrandInQuery: true,
    includeModelInQuery: true,
    includeFreeTextInQuery: true,
    excludePartsUnlessCondition: false,
    excludeBundles: true,
    accessoryPostFilter: false,
  },
  other: DEFAULT_POLICY,
};

export function getQueryPolicy(subtypeClass: SubtypeClass): QueryPolicy {
  return POLICIES[subtypeClass] ?? DEFAULT_POLICY;
}
