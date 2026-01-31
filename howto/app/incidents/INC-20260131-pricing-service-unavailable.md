# INC-20260131-pricing-service-unavailable

## Summary
Users requesting a price estimate saw the Android error message "Pricing service unavailable" because the backend Pricing V4 endpoint was disabled at runtime. The backend returned HTTP 500 with `pricing.status=ERROR` and `fallbackReason="Pricing V4 disabled"`, which the Android client maps to the user-visible message for 5xx responses. A config fallback + explicit env update re-enabled Pricing V4.

## Impact
- User-visible: Pricing estimate flow fails with "Pricing service unavailable".
- Scope: All pricing V4 requests in production until fix.
- Start: Unknown (first observed during incident response on 2026-01-31).
- End: 2026-01-31 after backend redeploy.

## Timeline (CET)
- 2026-01-31 19:20:26 - Reproduced backend failure with curl; HTTP 500 and `Pricing V4 disabled` response.
- 2026-01-31 19:21 - Confirmed backend startup logs show Pricing V4 disabled.
- 2026-01-31 19:23 - Implemented config fallback and updated env example.
- 2026-01-31 19:33:45 - Redeployed backend on NAS (tag `2026.01.31-3b10cd06`).
- 2026-01-31 19:38:28 - Verified `/v1/pricing/v4` returns HTTP 200 with `status=FALLBACK`.

## Detection
- Manual verification during incident response. No automated alert fired.

## RCA Notes (Timestamped, CET)
- 2026-01-31 19:20 - Android string mapping shows 5xx maps to "Pricing service unavailable" (`androidApp/src/main/java/com/scanium/app/pricing/PricingV4Api.kt`).
- 2026-01-31 19:20 - NAS curl to `/v1/pricing/v4` returns HTTP 500 with `fallbackReason: "Pricing V4 disabled"` and correlationId `incident-20260131-3`.
- 2026-01-31 19:21 - NAS backend logs show `[Config] Pricing v4 enabled: false`.
- 2026-01-31 19:21 - `docker inspect` shows no `PRICING_V4_ENABLED` env in `scanium-backend` container.

## Root Cause
`PRICING_V4_ENABLED` was not set in the runtime backend environment, so Pricing V4 defaulted to disabled; the endpoint returned HTTP 500 with `status=ERROR`, which the Android client surfaced as "Pricing service unavailable".

## Contributing Factors
- Pricing V4 enablement depended on a separate env var from `PRICING_ENABLED`, with no fallback or startup warning.
- Backend logs are routed to OTEL; no Loki entries were available during the incident window (visibility gap).

## Evidence (Commands + Excerpts)
### 1) Backend response reproducing failure
```
ssh nas "cat <<'JSON' | curl -s -D - -X POST http://localhost:8080/v1/pricing/v4 \
  -H 'Content-Type: application/json' \
  -H 'x-api-key: Cr3UnvP9ubNBxSiKaJA7LWAaKEwl4WNdpVP-CzuxA6hAxyLlo3iPqqfHo3R4nxoz' \
  -H 'x-scanium-correlation-id: incident-20260131-3' \
  -H 'x-client: manual-curl' \
  --data-binary @- 
{
  "itemId": "incident-test-3",
  "brand": "Apple",
  "productType": "electronics_laptop",
  "model": "MacBook Pro",
  "condition": "GOOD",
  "countryCode": "NL"
}
JSON"
```
Response excerpt:
```
HTTP/1.1 500 Internal Server Error
{"success":false,"pricing":{"status":"ERROR","countryCode":"NL","sources":[],"totalListingsAnalyzed":0,"timeWindowDays":30,"confidence":"LOW","fallbackReason":"Pricing V4 disabled"},"cached":false,"processingTimeMs":1}
```

### 2) Backend startup log indicates Pricing V4 disabled
```
ssh nas "/usr/local/bin/docker logs scanium-backend --since 24h | grep -n 'Pricing v4 enabled'"
```
Output:
```
7:[Config] Pricing v4 enabled: false
28:[Config] Pricing v4 enabled: false
34:[Config] Pricing v4 enabled: false
```

### 3) Runtime env missing PRICING_V4_ENABLED
```
ssh nas "/usr/local/bin/docker inspect scanium-backend --format '{{json .Config.Env}}' | tr ',' '\n' | grep -n 'PRICING_V4_ENABLED'"
```
Output: (no matches)

## Fix
- Backend config fallback: when `PRICING_V4_ENABLED` is unset, fall back to `PRICING_ENABLED`.
- Documented `PRICING_V4_ENABLED` in `backend/.env.example`.
- Updated NAS runtime `.env` to explicitly set `PRICING_V4_ENABLED=true` and redeployed backend.

Files changed:
- `backend/src/config/index.ts`
- `backend/.env.example`

## Verification
### Commands
```
# NAS deploy
ssh nas "cd /volume1/docker/scanium/repo && bash howto/infra/scripts/deploy-backend-nas.sh"

# Confirm config loaded with v4 enabled
ssh nas "/usr/local/bin/docker logs scanium-backend --tail 80 | grep -n 'Pricing v4 enabled'"

# Pricing V4 request
ssh nas "cat <<'JSON' > /tmp/pricing-v4.json
{
  \"itemId\": \"incident-verify-10\",
  \"brand\": \"Apple\",
  \"productType\": \"electronics_laptop\",
  \"model\": \"MacBook Pro\",
  \"condition\": \"GOOD\",
  \"countryCode\": \"NL\"
}
JSON
curl -s -D - -X POST http://localhost:8080/v1/pricing/v4 \
  -H 'Content-Type: application/json' \
  -H 'x-api-key: Cr3UnvP9ubNBxSiKaJA7LWAaKEwl4WNdpVP-CzuxA6hAxyLlo3iPqqfHo3R4nxoz' \
  -H 'x-scanium-correlation-id: incident-20260131-verify-10' \
  -H 'x-client: manual-curl' \
  --data-binary @/tmp/pricing-v4.json"
```

Response excerpt:
```
HTTP/1.1 200 OK
{"success":true,"pricing":{"status":"FALLBACK","countryCode":"NL","sources":[{"id":"marktplaats","name":"Marktplaats","baseUrl":"https://www.marktplaats.nl","listingCount":0,"searchUrl":"https://www.marktplaats.nl/q/Apple%20MacBook%20Pro%20electronics_laptop/"},{"id":"ebay","name":"eBay","baseUrl":"https://www.ebay.nl","listingCount":0,"searchUrl":"https://www.ebay.nl/sch/i.html?_nkw=Apple%20MacBook%20Pro"}],"totalListingsAnalyzed":0,"timeWindowDays":30,"confidence":"LOW","fallbackReason":"Marketplace adapters failed"},"cached":false,"processingTimeMs":453}
```

### Expected behavior
- Backend log shows `Pricing v4 enabled: true` on startup.
- HTTP response is **200** with `success: true` and `pricing.status` in `{OK, NO_RESULTS, FALLBACK}`.
- Android UI shows a price estimate or a non-5xx fallback state (no "Pricing service unavailable").

## Preventative Actions
- Add a startup warning if `PRICING_ENABLED=true` but `PRICING_V4_ENABLED` is unset.
- Add a deployment checklist item verifying pricing v4 is enabled in runtime env.
- Add a Grafana/Loki alert for repeated `Pricing V4 disabled` fallbackReason (once logging pipeline is confirmed working).
