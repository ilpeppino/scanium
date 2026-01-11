***REMOVED*** Marketplaces Catalog API

> **Phase 1:** Read-only catalog exposure for EU marketplaces
> **Status:** ✅ Deployed
> **Version:** 1.0

***REMOVED******REMOVED*** Overview

The Marketplaces Catalog API provides a read-only, API-key protected endpoint to retrieve marketplace information for EU countries. This is Phase 1 of the marketplace pricing integration.

***REMOVED******REMOVED******REMOVED*** Features

- ✅ Safe, validated JSON catalog loading
- ✅ API key authentication required
- ✅ Support for 32 EU countries
- ✅ Marketplace classification (global, marketplace, classifieds)
- ✅ Currency information per country

***REMOVED******REMOVED******REMOVED*** Non-Goals (Phase 1)

- ❌ Price retrieval from marketplaces (Phase 2)
- ❌ Android app integration (Phase 2)
- ❌ Assistant integration (Phase 2)

---

***REMOVED******REMOVED*** API Endpoints

***REMOVED******REMOVED******REMOVED*** 1. List Countries

**GET** `/v1/marketplaces/countries`

Returns list of supported country codes.

**Headers:**
- `X-API-Key` (required): Valid API key

**Response (200 OK):**
```json
{
  "countries": ["AL", "AD", "AT", "BE", "BG", ...]
}
```

**Errors:**
- `401 UNAUTHORIZED`: Missing or invalid API key
- `503 SERVICE_UNAVAILABLE`: Catalog failed to load

---

***REMOVED******REMOVED******REMOVED*** 2. Get Marketplaces for Country

**GET** `/v1/marketplaces/:countryCode`

Returns marketplaces for a specific country.

**Parameters:**
- `countryCode` (path): ISO 3166-1 alpha-2 country code (e.g., "NL", "DE")

**Headers:**
- `X-API-Key` (required): Valid API key

**Response (200 OK):**
```json
{
  "countryCode": "NL",
  "defaultCurrency": "EUR",
  "marketplaces": [
    {
      "id": "bol",
      "name": "Bol.com",
      "domains": ["bol.com"],
      "type": "marketplace"
    },
    {
      "id": "marktplaats",
      "name": "Marktplaats",
      "domains": ["marktplaats.nl"],
      "type": "classifieds"
    },
    {
      "id": "amazon",
      "name": "Amazon",
      "domains": ["amazon.nl"],
      "type": "global"
    }
  ]
}
```

**Errors:**
- `400 INVALID_COUNTRY_CODE`: Country code must be 2 letters
- `401 UNAUTHORIZED`: Missing or invalid API key
- `404 NOT_FOUND`: Country not supported
- `503 SERVICE_UNAVAILABLE`: Catalog failed to load

---

***REMOVED******REMOVED*** Catalog Format

***REMOVED******REMOVED******REMOVED*** File Location

`backend/config/marketplaces/marketplaces.eu.json`

***REMOVED******REMOVED******REMOVED*** Schema

```json
{
  "version": 1,
  "countries": [
    {
      "code": "NL",
      "defaultCurrency": "EUR",
      "marketplaces": [
        {
          "id": "bol",
          "name": "Bol.com",
          "domains": ["bol.com"],
          "type": "marketplace"
        }
      ]
    }
  ]
}
```

***REMOVED******REMOVED******REMOVED*** Validation Rules

- `version`: Positive integer
- `countries`: Non-empty array
- `code`: Exactly 2 uppercase letters (ISO 3166-1 alpha-2)
- `defaultCurrency`: Exactly 3 uppercase letters (ISO 4217)
- `marketplaces`: Non-empty array per country
- `id`: Unique marketplace identifier (stable, lowercase, alphanumeric + underscore)
- `name`: Display name
- `domains`: Array of domain names (without protocol)
- `type`: One of `global`, `marketplace`, `classifieds`

***REMOVED******REMOVED******REMOVED*** Marketplace Types

- **global**: Large international platforms (Amazon, eBay, AliExpress)
- **marketplace**: Regional e-commerce platforms (Bol.com, Allegro, Alza)
- **classifieds**: Classified ad sites (Marktplaats, Leboncoin, Kleinanzeigen)

---

***REMOVED******REMOVED*** Updating the Catalog

***REMOVED******REMOVED******REMOVED*** Safe Update Procedure

1. **Edit JSON file:**
   ```bash
   vim backend/config/marketplaces/marketplaces.eu.json
   ```

2. **Validate locally:**
   ```bash
   cd backend
   npm test -- src/modules/marketplaces/schema.test.ts
   ```

3. **Commit changes:**
   ```bash
   git add backend/config/marketplaces/marketplaces.eu.json
   git commit -m "feat(marketplaces): add new marketplace XYZ for country AB"
   git push
   ```

4. **Deploy:**
   ```bash
   ssh nas
   cd /volume1/docker/scanium
   git pull
   docker-compose build backend
   docker-compose up -d backend
   docker-compose logs -f backend | grep -i marketplace
   ```

5. **Verify:**
   ```bash
   ***REMOVED*** Check logs show successful load
   ***REMOVED*** Should see: "Marketplaces catalog loaded successfully"
   curl -s https://scanium.gtemp1.com/v1/marketplaces/countries \
     -H "X-API-Key: YOUR_KEY" | jq '.countries | length'
   ```

***REMOVED******REMOVED******REMOVED*** Rollback Procedure

```bash
ssh nas
cd /volume1/docker/scanium
git log --oneline backend/config/marketplaces/marketplaces.eu.json
git checkout <previous-commit-hash> backend/config/marketplaces/marketplaces.eu.json
docker-compose restart backend
```

---

***REMOVED******REMOVED*** Acceptance Tests

***REMOVED******REMOVED******REMOVED*** Local Testing

```bash
cd backend
npm test -- src/modules/marketplaces
```

***REMOVED******REMOVED******REMOVED*** Production Verification

```bash
***REMOVED*** Replace YOUR_KEY with actual API key from .env
API_KEY="your-actual-api-key-here"

***REMOVED*** Test 1: Health check still works
curl -s https://scanium.gtemp1.com/health | jq '.'

***REMOVED*** Test 2: Countries endpoint returns 401 without key
curl -s -i https://scanium.gtemp1.com/v1/marketplaces/countries | head -20

***REMOVED*** Test 3: Countries endpoint works with valid key
curl -s https://scanium.gtemp1.com/v1/marketplaces/countries \
  -H "X-API-Key: $API_KEY" | jq '.'

***REMOVED*** Test 4: Get marketplaces for Netherlands
curl -s https://scanium.gtemp1.com/v1/marketplaces/NL \
  -H "X-API-Key: $API_KEY" | jq '.'

***REMOVED*** Test 5: Get marketplaces for Germany
curl -s https://scanium.gtemp1.com/v1/marketplaces/DE \
  -H "X-API-Key: $API_KEY" | jq '.'

***REMOVED*** Test 6: Verify 404 for unsupported country
curl -s https://scanium.gtemp1.com/v1/marketplaces/US \
  -H "X-API-Key: $API_KEY" | jq '.'

***REMOVED*** Test 7: Verify 400 for invalid country code format
curl -s https://scanium.gtemp1.com/v1/marketplaces/USA \
  -H "X-API-Key: $API_KEY" | jq '.'
```

---

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Catalog fails to load on startup

**Symptom:** Logs show "Marketplaces catalog failed to load"

**Check:**
```bash
ssh nas
docker-compose logs backend | grep -i marketplace
```

**Common causes:**
1. JSON syntax error
   - Fix: Validate JSON with `jq . < marketplaces.eu.json`
2. Schema validation failure
   - Fix: Run local tests to see validation errors
3. File not found
   - Fix: Verify file exists in container: `docker exec scanium-backend ls -la /app/backend/config/marketplaces/`

***REMOVED******REMOVED******REMOVED*** Endpoints return 503

**Symptom:** All requests return `SERVICE_UNAVAILABLE`

**Cause:** Catalog failed validation at startup

**Fix:**
1. Check logs for validation error
2. Fix the catalog JSON
3. Restart backend container

***REMOVED******REMOVED******REMOVED*** New country not appearing

**Checklist:**
- ✅ Country code is exactly 2 uppercase letters
- ✅ Currency code is exactly 3 uppercase letters
- ✅ At least one marketplace defined
- ✅ Each marketplace has required fields (id, name, domains, type)
- ✅ Code committed and deployed
- ✅ Backend restarted after deployment

---

***REMOVED******REMOVED*** Implementation Details

***REMOVED******REMOVED******REMOVED*** Architecture

```
Routes (routes.ts)
  ↓
Service (service.ts) - Business logic
  ↓
Loader (loader.ts) - File reading + caching
  ↓
Schema (schema.ts) - Zod validation
```

***REMOVED******REMOVED******REMOVED*** Security

- **Authentication:** X-API-Key header (same keys as classifier/assistant)
- **Rate limiting:** Covered by global API guard (30 req/10s default)
- **Input validation:** Zod schema + path param validation
- **No data exposure:** Read-only access, no PII

***REMOVED******REMOVED******REMOVED*** Caching

- **In-memory cache:** Catalog loaded once at startup
- **No expiry:** Cache persists for app lifetime
- **Reload:** Requires backend restart

***REMOVED******REMOVED******REMOVED*** Monitoring

Logs available via:
```bash
ssh nas
docker-compose logs -f backend | grep -E "(marketplace|Marketplace)"
```

Startup log on success:
```
Marketplaces catalog loaded successfully {
  catalogPath: 'backend/config/marketplaces/marketplaces.eu.json',
  version: 1,
  countriesCount: 32
}
```

---

***REMOVED******REMOVED*** Future Enhancements (Phase 2+)

- [ ] Price scraping from marketplaces
- [ ] Search URL generation
- [ ] Android app integration
- [ ] Assistant recommendations
- [ ] Marketplace availability tracking
- [ ] Dynamic catalog updates (no restart required)

---

***REMOVED******REMOVED*** Related Documentation

- [Backend Architecture](./README.md)
- [API Authentication](./authentication.md)
- [Deployment Guide](../infra/deployment.md)
