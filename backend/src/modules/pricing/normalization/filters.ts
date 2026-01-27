import { FetchedListing } from '../types-v4.js';

const EXCLUDE_PATTERNS = [
  /parts only/i,
  /for parts/i,
  /defect/i,
  /kapot/i,
  /voor onderdelen/i,
  /niet werkend/i,
  /broken/i,
];

const BUNDLE_PATTERNS = [/\d+x\s/i, /set of/i];

export function filterListings(listings: FetchedListing[]): FetchedListing[] {
  return listings.filter((listing) => {
    if (listing.price <= 0) return false;
    if (listing.price > 10000) return false;

    const title = listing.title ?? '';
    if (EXCLUDE_PATTERNS.some((pattern) => pattern.test(title))) return false;
    if (BUNDLE_PATTERNS.some((pattern) => pattern.test(title))) return false;

    return true;
  });
}
