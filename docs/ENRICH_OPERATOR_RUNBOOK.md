***REMOVED*** Enrichment Pipeline Operator Runbook

***REMOVED******REMOVED*** Overview

The enrichment pipeline provides automatic scan-to-enrichment for items:
- **POST /v1/items/enrich** - Submit item for enrichment (returns 202)
- **GET /v1/items/enrich/status/:requestId** - Poll for results

***REMOVED******REMOVED*** Pipeline Stages

1. **VISION_STARTED/VISION_DONE** - Google Vision extraction (OCR, logos, colors, labels)
2. **ATTRIBUTES_STARTED/ATTRIBUTES_DONE** - Normalize attributes with confidence + source
3. **DRAFT_STARTED/DRAFT_DONE** - Generate listing title/description via OpenAI

***REMOVED******REMOVED*** Deployment Commands

***REMOVED******REMOVED******REMOVED*** Build and Deploy Backend

```bash
***REMOVED*** SSH into NAS
ssh nas

***REMOVED*** Navigate to repo
cd /volume1/docker/scanium/repo

***REMOVED*** Pull latest changes
git pull origin main

***REMOVED*** Build and restart backend
cd deploy/nas/compose
docker-compose -f docker-compose.nas.backend.yml build backend
docker-compose -f docker-compose.nas.backend.yml up -d backend

***REMOVED*** Verify backend is running
docker-compose -f docker-compose.nas.backend.yml ps
```

***REMOVED******REMOVED******REMOVED*** Verify Deployment

```bash
***REMOVED*** Check if backend is healthy
curl -s https://api.scanium.family/healthz | jq .

***REMOVED*** Check root endpoint shows enrich routes
curl -s https://api.scanium.family/ | jq .endpoints.enrich
```

***REMOVED******REMOVED******REMOVED*** Test Enrichment Endpoint

```bash
***REMOVED*** Test with a sample image (requires API key)
API_KEY="your-api-key-here"

***REMOVED*** Submit enrichment request
curl -X POST https://api.scanium.family/v1/items/enrich \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: multipart/form-data" \
  -F "image=@/path/to/test-image.jpg" \
  -F "itemId=test-item-001" \
  -F "data={\"itemId\":\"test-item-001\"}"

***REMOVED*** Expected response: 202 Accepted
***REMOVED*** { "success": true, "requestId": "uuid", "correlationId": "..." }

***REMOVED*** Poll for status
curl -s https://api.scanium.family/v1/items/enrich/status/<requestId> \
  -H "X-API-Key: $API_KEY" | jq .
```

***REMOVED******REMOVED*** Monitoring

***REMOVED******REMOVED******REMOVED*** Tail Backend Logs

```bash
ssh nas "cd /volume1/docker/scanium/repo/deploy/nas/compose && \
  docker-compose -f docker-compose.nas.backend.yml logs -f --tail=100 backend"
```

***REMOVED******REMOVED******REMOVED*** Check Enrichment Metrics

```bash
curl -s https://api.scanium.family/v1/items/enrich/metrics | jq .
```

***REMOVED******REMOVED******REMOVED*** Key Log Patterns

```
***REMOVED*** Successful enrichment
msg: 'Enrichment request submitted'
msg: 'Enrichment stage update'
msg: 'Enrichment pipeline completed'

***REMOVED*** Vision cache hit
msg: 'Vision cache HIT'

***REMOVED*** Errors
msg: 'Vision extraction failed'
msg: 'LLM draft generation failed'
```

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Common Issues

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. 401 Unauthorized
- Check API key in `X-API-Key` header
- Verify API key is in `SCANIUM_API_KEYS` environment variable

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. Vision Extraction Fails
- Check `GOOGLE_APPLICATION_CREDENTIALS` is set
- Verify Google Vision API is enabled in GCP project
- Check `VISION_PROVIDER=google` in environment

***REMOVED******REMOVED******REMOVED******REMOVED*** 3. Draft Generation Fails (Falls Back to Template)
- Check `OPENAI_API_KEY` is set
- Verify OpenAI API quota
- Template fallback is expected if OpenAI unavailable

***REMOVED******REMOVED******REMOVED******REMOVED*** 4. Enrichment Stuck/Slow
- Check concurrent jobs: `curl .../items/enrich/metrics`
- Default max concurrent: 10
- Vision extraction timeout: 15s
- LLM timeout: 30s

***REMOVED******REMOVED******REMOVED*** Rollback

```bash
***REMOVED*** Rollback to previous version
ssh nas "cd /volume1/docker/scanium/repo && \
  git checkout HEAD~1 && \
  cd deploy/nas/compose && \
  docker-compose -f docker-compose.nas.backend.yml build backend && \
  docker-compose -f docker-compose.nas.backend.yml up -d backend"
```

***REMOVED******REMOVED*** Configuration

***REMOVED******REMOVED******REMOVED*** Required Environment Variables

```bash
***REMOVED*** Vision (required for Stage A)
VISION_PROVIDER=google
GOOGLE_APPLICATION_CREDENTIALS=/path/to/credentials.json

***REMOVED*** OpenAI (optional, enables Stage C draft generation)
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini  ***REMOVED*** default

***REMOVED*** API Keys (required for auth)
SCANIUM_API_KEYS=key1,key2,...
```

***REMOVED******REMOVED******REMOVED*** Tuning Options

```bash
***REMOVED*** Enrichment pipeline defaults (in code)
visionTimeoutMs: 15000      ***REMOVED*** 15s vision timeout
draftTimeoutMs: 30000       ***REMOVED*** 30s LLM timeout
maxConcurrent: 10           ***REMOVED*** Max parallel jobs
resultRetentionMs: 1800000  ***REMOVED*** 30min result retention
enableDraftGeneration: true ***REMOVED*** Can disable to only do vision+attributes
```

***REMOVED******REMOVED*** Health Checks

***REMOVED******REMOVED******REMOVED*** Backend Health
```bash
curl https://api.scanium.family/healthz
***REMOVED*** Expected: { "status": "ok" }
```

***REMOVED******REMOVED******REMOVED*** Enrichment Capacity
```bash
curl https://api.scanium.family/v1/items/enrich/metrics
***REMOVED*** Check: activeJobs < maxConcurrent
```

***REMOVED******REMOVED*** Security

- API key required for all enrichment endpoints
- Device ID header (`X-Scanium-Device-Id`) used for rate limiting
- Correlation ID (`X-Scanium-Correlation-Id`) for tracing
- No secrets logged (redacted in Fastify logger config)
