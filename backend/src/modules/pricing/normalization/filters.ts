import { FetchedListing } from '../types-v4.js';

export const EXCLUDE_PARTS_PATTERNS = [
  /parts only/i,
  /for parts/i,
  /defect/i,
  /kapot/i,
  /voor onderdelen/i,
  /niet werkend/i,
  /broken/i,
];

export const BUNDLE_PATTERNS = [/\d+x\s/i, /set of/i];

export type ListingFilterOptions = {
  excludeParts?: boolean;
  excludeBundles?: boolean;
};

export function filterListings(
  listings: FetchedListing[],
  options: ListingFilterOptions = {}
): FetchedListing[] {
  const excludeParts = options.excludeParts ?? true;
  const excludeBundles = options.excludeBundles ?? true;
  return listings.filter((listing) => {
    if (listing.price <= 0) return false;
    if (listing.price > 10000) return false;

    const title = listing.title ?? '';
    if (excludeParts && EXCLUDE_PARTS_PATTERNS.some((pattern) => pattern.test(title))) {
      return false;
    }
    if (excludeBundles && BUNDLE_PATTERNS.some((pattern) => pattern.test(title))) {
      return false;
    }

    return true;
  });
}
