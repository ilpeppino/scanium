# Enrichment Pipeline Operator Runbook

## Overview

The enrichment pipeline provides automatic scan-to-enrichment for items:

- **POST /v1/items/enrich** - Submit item for enrichment (returns 202)
- **GET /v1/items/enrich/status/:requestId** - Poll for results

## Pipeline Stages

1. **VISION_STARTED/VISION_DONE** - Google Vision extraction (OCR, logos, colors, labels)
2. **ATTRIBUTES_STARTED/ATTRIBUTES_DONE** - Normalize attributes with confidence + source
3. **DRAFT_STARTED/DRAFT_DONE** - Generate listing title/description via OpenAI

## Deployment Commands

### Build and Deploy Backend

```bash
# SSH into NAS
ssh nas

# Navigate to repo
cd /volume1/docker/scanium/repo

# Pull latest changes
git pull origin main

# Build and restart backend
cd deploy/nas/compose
docker-compose -f docker-compose.nas.backend.yml build backend
docker-compose -f docker-compose.nas.backend.yml up -d backend

# Verify backend is running
docker-compose -f docker-compose.nas.backend.yml ps
```

### Verify Deployment

```bash
# Check if backend is healthy
curl -s https://api.scanium.family/healthz | jq .

# Check root endpoint shows enrich routes
curl -s https://api.scanium.family/ | jq .endpoints.enrich
```

### Test Enrichment Endpoint

```bash
# Test with a sample image (requires API key)
API_KEY="your-api-key-here"

# Submit enrichment request
curl -X POST https://api.scanium.family/v1/items/enrich \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: multipart/form-data" \
  -F "image=@/path/to/test-image.jpg" \
  -F "itemId=test-item-001" \
  -F "data={\"itemId\":\"test-item-001\"}"

# Expected response: 202 Accepted
# { "success": true, "requestId": "uuid", "correlationId": "..." }

# Poll for status
curl -s https://api.scanium.family/v1/items/enrich/status/<requestId> \
  -H "X-API-Key: $API_KEY" | jq .
```

## Monitoring

### Tail Backend Logs

```bash
ssh nas "cd /volume1/docker/scanium/repo/deploy/nas/compose && \
  docker-compose -f docker-compose.nas.backend.yml logs -f --tail=100 backend"
```

### Check Enrichment Metrics

```bash
curl -s https://api.scanium.family/v1/items/enrich/metrics | jq .
```

### Key Log Patterns

```
# Successful enrichment
msg: 'Enrichment request submitted'
msg: 'Enrichment stage update'
msg: 'Enrichment pipeline completed'

# Vision cache hit
msg: 'Vision cache HIT'

# Errors
msg: 'Vision extraction failed'
msg: 'LLM draft generation failed'
```

## Troubleshooting

### Common Issues

#### 1. 401 Unauthorized

- Check API key in `X-API-Key` header
- Verify API key is in `SCANIUM_API_KEYS` environment variable

#### 2. Vision Extraction Fails

- Check `GOOGLE_APPLICATION_CREDENTIALS` is set
- Verify Google Vision API is enabled in GCP project
- Check `VISION_PROVIDER=google` in environment

#### 3. Draft Generation Fails (Falls Back to Template)

- Check `OPENAI_API_KEY` is set
- Verify OpenAI API quota
- Template fallback is expected if OpenAI unavailable

#### 4. Enrichment Stuck/Slow

- Check concurrent jobs: `curl .../items/enrich/metrics`
- Default max concurrent: 10
- Vision extraction timeout: 15s
- LLM timeout: 30s

### Rollback

```bash
# Rollback to previous version
ssh nas "cd /volume1/docker/scanium/repo && \
  git checkout HEAD~1 && \
  cd deploy/nas/compose && \
  docker-compose -f docker-compose.nas.backend.yml build backend && \
  docker-compose -f docker-compose.nas.backend.yml up -d backend"
```

## Configuration

### Required Environment Variables

```bash
# Vision (required for Stage A)
VISION_PROVIDER=google
GOOGLE_APPLICATION_CREDENTIALS=/path/to/credentials.json

# OpenAI (optional, enables Stage C draft generation)
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini  # default

# API Keys (required for auth)
SCANIUM_API_KEYS=key1,key2,...
```

### Tuning Options

```bash
# Enrichment pipeline defaults (in code)
visionTimeoutMs: 15000      # 15s vision timeout
draftTimeoutMs: 30000       # 30s LLM timeout
maxConcurrent: 10           # Max parallel jobs
resultRetentionMs: 1800000  # 30min result retention
enableDraftGeneration: true # Can disable to only do vision+attributes
```

## Health Checks

### Backend Health

```bash
curl https://api.scanium.family/healthz
# Expected: { "status": "ok" }
```

### Enrichment Capacity

```bash
curl https://api.scanium.family/v1/items/enrich/metrics
# Check: activeJobs < maxConcurrent
```

## Security

- API key required for all enrichment endpoints
- Device ID header (`X-Scanium-Device-Id`) used for rate limiting
- Correlation ID (`X-Scanium-Correlation-Id`) for tracing
- No secrets logged (redacted in Fastify logger config)
