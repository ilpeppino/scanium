# Pricing Query Policy

## Why this prevents wrong item type comps
Pricing contamination happens when marketplace searches mix devices, accessories, and unrelated item classes. The pricing query policy introduces a neutral query context + subtype class + policy rules so the search and post-filter steps are consistent across all categories. This keeps query text, category constraints, and post-filters aligned with the intended item class (for example, device vs accessory), reducing mismatched listings before normalization.

## How it works (high level)
- `CatalogQueryContext` is a marketplace-agnostic input model derived from the pricing request.
- `SubtypeClassifier` maps a subtype into a small set of classes (device, accessory, apparel, furniture, media, other).
- `CategoryResolver` resolves marketplace category IDs with explicit precedence and safe fallback.
- `QueryPolicy` builds a `QueryPlan` that drives marketplace search params and post-filters.

## Add or adjust subtype -> class mapping
Edit the classifier patterns in:
- `backend/src/modules/pricing/query-policy/subtype-class-map.ts`

Keep the map small and generic. Add only class-level patterns (not one-off product names).

## Add category overrides per marketplace
Overrides are simple subtype -> category ID maps, scoped per marketplace:
- `backend/src/modules/pricing/query-policy/category-overrides.ts`

Example:
```ts
export const CATEGORY_OVERRIDES = {
  ebay: {
    "electronics_smartphone": "9355",
  },
};
```

## Cache behavior and TTL tuning
Category resolutions are cached on disk to avoid repeated lookups.
- File: `tmp/pricing-category-cache.json`
- Default TTL: 7 days

To tune TTL, pass a custom `CategoryResolutionCache` into `EbayCategoryResolver` with a different `ttlMs` value. In production, prefer a service-level wiring change rather than ad-hoc overrides.

## Add or adjust class-level post-filter rules safely
Post-filter rules are class-level patterns (not phone-level). They are applied after the basic listing filter and before normalization.
- Rules: `backend/src/modules/pricing/query-policy/post-filter-rules.ts`
- Policy wiring: `backend/src/modules/pricing/query-policy/query-policy.ts`

To add a new rule:
1. Add the rule in `post-filter-rules.ts`.
2. Enable it for the appropriate class in `query-policy.ts`.
3. Add/adjust tests to ensure the rule only runs for the intended class.
