***REMOVED*** Pricing Insights Feature

***REMOVED******REMOVED*** Overview

The Pricing Insights feature uses OpenAI's chat completion API to search the web for comparable
marketplace listings and compute price ranges for items.

***REMOVED******REMOVED*** How It Works

1. **User Trigger**: Only runs when user taps the AI assistant button on Edit Item screen
2. **Request Flag**: Client must set `includePricing: true` and provide `pricingPrefs`
3. **Web Search**: Uses OpenAI to search marketplace domains for comparable listings
4. **Response**: Returns up to 5 results with prices, URLs, and computed price range

***REMOVED******REMOVED*** Request Format

```json
POST /v1/assist/chat
{
  "items": [...],
  "history": [...],
  "message": "Write a listing",
  "includePricing": true,
  "pricingPrefs": {
    "countryCode": "NL",
    "maxResults": 5
  }
}
```

***REMOVED******REMOVED*** Response Format

When `includePricing: true`, the response includes optional `pricingInsights`:

```json
{
  "reply": "...",
  "actions": [...],
  "pricingInsights": {
    "status": "OK",
    "countryCode": "NL",
    "marketplacesUsed": [
      {
        "id": "marktplaats",
        "name": "Marktplaats",
        "baseUrl": "marktplaats.nl"
      }
    ],
    "querySummary": "Nike Air Max 90 in NL",
    "results": [
      {
        "title": "Nike Air Max 90 - Size 42",
        "price": {
          "amount": 35.0,
          "currency": "EUR"
        },
        "url": "https://www.marktplaats.nl/...",
        "sourceMarketplaceId": "marktplaats"
      }
    ],
    "range": {
      "low": 25.0,
      "high": 45.0,
      "currency": "EUR"
    },
    "confidence": "HIGH"
  }
}
```

***REMOVED******REMOVED*** Status Codes

- `OK` - Successfully retrieved pricing data
- `NOT_SUPPORTED` - Web search not available
- `DISABLED` - Feature disabled via config
- `ERROR` - Error occurred during lookup
- `TIMEOUT` - Request timed out
- `NO_RESULTS` - No comparable listings found

***REMOVED******REMOVED*** Error Codes

When status is not `OK`, `errorCode` provides details:

- `NO_TOOLING` - OpenAI client not initialized
- `NO_RESULTS` - No listings found
- `PROVIDER_ERROR` - OpenAI API error
- `VALIDATION` - Request validation failed
- `TIMEOUT` - Request exceeded timeout
- `RATE_LIMITED` - OpenAI rate limited

***REMOVED******REMOVED*** Caching

- **Key**: Hash of item attributes + pricing preferences
- **TTL**: 24 hours (configurable via `PRICING_CACHE_TTL_SECONDS`)
- **Eviction**: Automatic cleanup every 5 minutes
- **Cache hits**: Return cached results immediately

***REMOVED******REMOVED*** Configuration

Environment variables:

```bash
***REMOVED*** Enable pricing feature
PRICING_ENABLED=true

***REMOVED*** Timeout for pricing lookup (ms)
PRICING_TIMEOUT_MS=6000

***REMOVED*** Cache TTL (seconds, default 24h)
PRICING_CACHE_TTL_SECONDS=86400

***REMOVED*** Path to marketplaces catalog
PRICING_CATALOG_PATH=config/marketplaces/marketplaces.eu.json
```

***REMOVED******REMOVED*** Failure Modes

***REMOVED******REMOVED******REMOVED*** Graceful Degradation

- If pricing fails, assistant text generation still succeeds
- Timeout budget separate from assistant timeout
- Never blocks main response with 200 status

***REMOVED******REMOVED******REMOVED*** Error Handling

1. **Provider Unavailable**: Returns `status: 'ERROR'`, `errorCode: 'NO_TOOLING'`
2. **Timeout**: Returns `status: 'TIMEOUT'`, `errorCode: 'TIMEOUT'`
3. **No Results**: Returns `status: 'NO_RESULTS'`, `errorCode: 'NO_RESULTS'`
4. **Rate Limited**: Returns `status: 'ERROR'`, `errorCode: 'RATE_LIMITED'`

***REMOVED******REMOVED******REMOVED*** Logging

- **No Secrets**: Query summaries exclude personal data
- **Metrics**: `recordPricingRequest(status, country, latency, errorCode)`
- **Cache Stats**: Available via `pricingService.getCacheStats()`

***REMOVED******REMOVED*** Cost Control

- **On-Demand Only**: No automatic triggers during scan/typing
- **Domain Limiting**: Max 5 marketplace domains per request
- **Result Limiting**: Max 5 results per response
- **Caching**: 24-hour cache reduces repeated lookups
- **Timeouts**: Hard 6-second limit (configurable)

***REMOVED******REMOVED*** OpenAI Model

- Uses same OpenAI credentials as assistant (`OPENAI_API_KEY`)
- Model: Same as assistant model (e.g., `gpt-4o-mini`)
- Temperature: 0.2 (lowered for factual extraction)
- Max Tokens: 2000
- Response Format: JSON object
- **Note**: As of Phase 3, OpenAI's standard API doesn't have built-in web browsing. The service is
  designed to support web_search when OpenAI makes it available in the API (currently only in
  ChatGPT). The code includes placeholders for `tools: [{ type: 'web_search' }]` for future
  integration.

***REMOVED******REMOVED*** Security

- **Input Sanitization**: Item attributes filtered for PII
- **URL Validation**: Only https:// URLs accepted
- **Price Validation**: Positive numbers only
- **Domain Whitelisting**: Only catalog domains searched
- **No User Data**: Logs exclude item descriptions

***REMOVED******REMOVED*** Testing

***REMOVED******REMOVED******REMOVED*** Unit Tests

```bash
npm test src/modules/pricing/service.test.ts
```

***REMOVED******REMOVED******REMOVED*** Integration Tests

```bash
npm test src/modules/assistant/routes.e2e.test.ts
```

***REMOVED******REMOVED******REMOVED*** Manual Testing

```bash
***REMOVED*** Without pricing
curl -X POST https://scanium.gtemp1.com/v1/assist/chat \
  -H "X-API-Key: $API_KEY" \
  -H "X-Scanium-Device-Id: test" \
  -H "Content-Type: application/json" \
  -d '{...}'

***REMOVED*** With pricing
curl -X POST https://scanium.gtemp1.com/v1/assist/chat \
  -H "X-API-Key: $API_KEY" \
  -H "X-Scanium-Device-Id: test" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [...],
    "message": "Write listing",
    "includePricing": true,
    "pricingPrefs": {"countryCode": "NL"}
  }'
```

***REMOVED******REMOVED*** Metrics

- `pricing_requests_total` - Total pricing requests
- `pricing_latency_ms` - Latency histogram
- `pricing_cache_size` - Current cache size
- `pricing_status_total` - Requests by status

***REMOVED******REMOVED*** Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ POST /v1/assist/chat
       │ includePricing: true
       v
┌─────────────────────────┐
│  AssistantRoutes        │
│  - Validate request     │
│  - Call assistant       │
│  - Call pricing (async) │
└──────────┬──────────────┘
           │
           v
    ┌──────────────────┐
    │ PricingService   │
    │ - Check cache    │
    │ - Build query    │
    │ - Call OpenAI    │
    │ - Parse results  │
    │ - Compute range  │
    └────────┬─────────┘
             │
             v
      ┌─────────────┐
      │ OpenAI API  │
      │ - Web search│
      │ - Parse     │
      └─────────────┘
```

***REMOVED******REMOVED*** Supported Countries

See `config/marketplaces/marketplaces.eu.json` for full list. Includes:

- NL (Netherlands) - Marktplaats, Bol.com, Amazon.nl
- DE (Germany) - Kleinanzeigen, Amazon.de, eBay.de, Otto.de
- FR (France) - Leboncoin, Amazon.fr, Cdiscount
- UK (United Kingdom) - Gumtree, Amazon.co.uk, eBay.co.uk
- And 30+ more European countries

***REMOVED******REMOVED*** Future Enhancements

- [ ] Historical price tracking
- [ ] Price trend analysis
- [ ] Condition-adjusted pricing
- [ ] Seasonal price patterns
- [ ] Multi-currency normalization
