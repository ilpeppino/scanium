import { CatalogQueryContext, PostFilterRuleId, QueryFilter, QueryPlan } from './types.js';
import { CategoryResolver } from './category-resolver.js';
import { getQueryPolicy } from './query-policy.js';

function normalizeToken(value: string): string {
  return value.trim();
}

function buildTokens(context: CatalogQueryContext): string[] {
  const policy = getQueryPolicy(context.subtypeClass);
  const tokens: string[] = [];

  if (policy.includeBrandInQuery && context.brand) {
    tokens.push(normalizeToken(context.brand));
  }
  if (policy.includeModelInQuery && context.model) {
    tokens.push(normalizeToken(context.model));
  }
  if (policy.includeSubtypeInQuery && context.subtype) {
    tokens.push(normalizeToken(context.subtype));
  }
  if (policy.includeFreeTextInQuery && context.freeText) {
    tokens.push(normalizeToken(context.freeText));
  }

  return tokens.filter(Boolean);
}

function buildFilters(context: CatalogQueryContext): QueryFilter[] {
  const policy = getQueryPolicy(context.subtypeClass);
  const filters: QueryFilter[] = [];

  if (context.condition) {
    filters.push({ type: 'condition', value: context.condition });
  }

  if (policy.excludeBundles) {
    filters.push({ type: 'excludeBundles', value: true });
  }

  if (policy.excludePartsUnlessCondition) {
    const allowParts = context.condition === 'POOR';
    filters.push({ type: 'excludeParts', value: !allowParts });
  }

  return filters;
}

function buildPostFilters(context: CatalogQueryContext): PostFilterRuleId[] {
  const policy = getQueryPolicy(context.subtypeClass);
  const ruleIds: PostFilterRuleId[] = [];

  if (policy.accessoryPostFilter) {
    ruleIds.push('exclude_accessory_like');
  }

  return ruleIds;
}

export async function buildQueryPlan(
  context: CatalogQueryContext,
  marketplace: string,
  resolver: CategoryResolver
): Promise<QueryPlan> {
  const policy = getQueryPolicy(context.subtypeClass);
  const tokens = buildTokens(context);
  const q = tokens.join(' ').trim();
  const filters = buildFilters(context);
  const postFilterRules = buildPostFilters(context);
  const warnings: string[] = [];

  const categoryResolution = await resolver.resolve(context, marketplace);

  if (policy.categoryRequirement === 'required' && !categoryResolution.categoryId) {
    warnings.push('Category required by policy but not resolved');
  }

  return {
    q,
    categoryId: categoryResolution.categoryId,
    filters,
    postFilterRules,
    telemetry: {
      subtype: context.subtype,
      subtypeClass: context.subtypeClass,
      tokens,
      categoryResolution,
      policyId: policy.id,
      warnings: warnings.length ? warnings : undefined,
    },
  };
}
