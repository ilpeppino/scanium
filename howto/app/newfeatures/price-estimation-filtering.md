# Price Estimation Filtering (Accessory Exclusion)

## Purpose
Improve price estimation precision by filtering accessory/irrelevant listings using generic signals:
- Marketplace category mapping (eBay `_sacat`)
- Config-driven negative keywords
- Heuristics (low price + accessory hint)

Filtering happens before aggregation and AI normalization.

## Files
- Config: `backend/config/pricing_filters_v1.json`
- Filter logic: `backend/src/modules/pricing/normalization/accessory-filter.ts`
- Pipeline integration: `backend/src/modules/pricing/service-v4.ts`
- eBay search URL builder: `backend/src/modules/marketplaces/adapters/ebay-adapter.ts`

## Config format (v1)
```json
{
  "version": "1.0.0",
  "defaults": {
    "fallback_min_results": 10,
    "strong_exclude_keywords": ["case", "cover", "screen protector"],
    "exclude_keywords": ["charger", "cable", "adapter"],
    "negative_keywords": ["case", "screen protector"],
    "heuristic_keywords": ["case", "cover", "charger"],
    "price_floor_ratio": 0.2,
    "price_floor_min_listings": 8
  },
  "groups": {
    "phones": {
      "subtypes": ["electronics_phone", "electronics_smartphone"],
      "exclude_keywords": ["wireless charger", "power bank"],
      "negative_keywords": ["case", "screen protector", "charger"]
    }
  },
  "subtypes": {
    "electronics_camera": {
      "exclude_keywords": ["lens", "tripod"],
      "negative_keywords": ["lens", "tripod"]
    }
  }
}
```

Notes:
- Subtype keys must be lowercase.
- `strong_exclude_keywords` are used first. If filtering is too aggressive, the system relaxes to
  strong-only filtering, then falls back to the original list.
- `negative_keywords` are appended to eBay queries as `-keyword` tokens.

## How filtering works
1) Resolve rule by subtype: explicit subtype override → group match → defaults.
2) Exclude listings that match strong or regular keywords.
3) Optional heuristic: if price < (median × ratio) and title has heuristic keyword, exclude.
4) Fallback: if remaining listings < `fallback_min_results`, relax to strong-only;
   if still too few, return the original list.

## Observability
Accessory filter diagnostics are logged at debug level from pricing V4:
- total input, kept, removed
- counts by keyword and reason
- fallback usage and reason

## How to add or tune filters
1) Edit `backend/config/pricing_filters_v1.json`.
2) Add a group with `subtypes` or a direct `subtypes` override.
3) Add localized keywords (EN/NL) under `exclude_keywords` and `negative_keywords`.
4) Deploy and watch debug logs during pricing requests.

## Quick validation (curl + logs)
```bash
curl -s -X POST "https://<YOUR_BACKEND_HOST>/v1/pricing/v4" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <YOUR_API_KEY>" \
  -d '{
    "itemId": "test-1",
    "brand": "Apple",
    "productType": "electronics_phone",
    "model": "iPhone 13",
    "condition": "GOOD",
    "countryCode": "NL"
  }'
```

Check backend logs for:
- `[PricingV4] Accessory filter diagnostics`
- eBay URL containing negative keywords (`-case`, `-screen protector`, etc.)

## NAS deployment (Docker)
```bash
cd /volume1/docker/scanium/repo
/usr/local/bin/docker-compose -f deploy/nas/compose/docker-compose.nas.backend.yml build backend
/usr/local/bin/docker-compose -f deploy/nas/compose/docker-compose.nas.backend.yml up -d backend
```
