import { FetchedListing } from '../types-v4.js';
import { PostFilterRuleId } from './types.js';

const ACCESSORY_LIKE_PATTERNS = [
  /case/i,
  /cover/i,
  /charger/i,
  /cable/i,
  /adapter/i,
  /battery/i,
  /strap/i,
  /stand/i,
  /dock/i,
  /mount/i,
  /holder/i,
  /screen protector/i,
  /replacement/i,
];

const PARTS_ONLY_PATTERNS = [
  /parts only/i,
  /for parts/i,
  /defect/i,
  /kapot/i,
  /voor onderdelen/i,
  /niet werkend/i,
  /broken/i,
];

const BUNDLE_PATTERNS = [/\d+x\s/i, /set of/i];

type Rule = {
  id: PostFilterRuleId;
  matches: (title: string) => boolean;
};

const RULES: Record<PostFilterRuleId, Rule> = {
  exclude_accessory_like: {
    id: 'exclude_accessory_like',
    matches: (title) => ACCESSORY_LIKE_PATTERNS.some((pattern) => pattern.test(title)),
  },
  exclude_parts_only: {
    id: 'exclude_parts_only',
    matches: (title) => PARTS_ONLY_PATTERNS.some((pattern) => pattern.test(title)),
  },
  exclude_bundle: {
    id: 'exclude_bundle',
    matches: (title) => BUNDLE_PATTERNS.some((pattern) => pattern.test(title)),
  },
};

export function applyPostFilterRules(
  listings: FetchedListing[],
  ruleIds: PostFilterRuleId[]
): { kept: FetchedListing[]; excluded: Array<{ listing: FetchedListing; ruleId: PostFilterRuleId }> } {
  if (!ruleIds.length) {
    return { kept: listings, excluded: [] };
  }

  const excluded: Array<{ listing: FetchedListing; ruleId: PostFilterRuleId }> = [];
  const kept = listings.filter((listing) => {
    const title = listing.title ?? '';
    for (const ruleId of ruleIds) {
      const rule = RULES[ruleId];
      if (rule && rule.matches(title)) {
        excluded.push({ listing, ruleId });
        return false;
      }
    }
    return true;
  });

  return { kept, excluded };
}
