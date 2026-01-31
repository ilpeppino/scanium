export const CATEGORY_OVERRIDES: Record<string, Record<string, string>> = {
  ebay: {
    // Electronics (broad category to avoid over-filtering mixed device types)
    consumer_electronics_portable: '293',
    consumer_electronics_stationary: '293',
    home_electronics: '293',
    electronics_small: '293',
    // Household textiles (home category to keep bedding/bath/linens in-scope)
    textile: '11700',
  },
};
