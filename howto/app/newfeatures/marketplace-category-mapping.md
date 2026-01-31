# Marketplace Category Mapping (Subtype → Category IDs)

## Purpose
Adds a data-driven, versioned mapping from Scanium subtypes (e.g., `electronics_phone`) to
marketplace category identifiers. The first implementation targets eBay’s `_sacat` parameter
for search URLs.

## Files
- Mapping file: `backend/config/marketplace_category_map_v1.json`
- Resolver: `backend/src/modules/catalog/marketplace-category-resolver.ts`
- eBay search URL builder: `backend/src/modules/marketplaces/adapters/ebay-adapter.ts`

## Mapping format (v1)
```json
{
  "version": "1.0.0",
  "ebay": {
    "electronics_phone": { "sacat": "9355" }
  }
}
```

Notes:
- Subtype keys must be lowercase and trimmed.
- Add new marketplaces as new top-level keys (e.g., `marktplaats`) with their own fields.
- Bump the file name (e.g., `marketplace_category_map_v2.json`) only when the schema changes.

## How to add a new subtype mapping
1) Open `backend/config/marketplace_category_map_v1.json`.
2) Add or update the subtype entry under the marketplace block.
3) Keep subtype keys lowercase (`electronics_laptop`, `furniture_chair`, etc.).
4) Commit and deploy.

## NAS deployment (Docker)
Use the NAS backend compose file, rebuild, and restart the API container.

Example (from NAS shell):
```bash
cd /volume1/docker/scanium/repo
/usr/local/bin/docker-compose -f deploy/nas/compose/docker-compose.nas.backend.yml build backend
/usr/local/bin/docker-compose -f deploy/nas/compose/docker-compose.nas.backend.yml up -d backend
```

## Quick validation (curl)
Call the pricing v4 endpoint with a mapped subtype and verify the response includes an eBay
source. You can also check the backend logs for the generated search URL.

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

If `_sacat` is mapped, the eBay search URL will include it (logged during pricing V4 processing).
