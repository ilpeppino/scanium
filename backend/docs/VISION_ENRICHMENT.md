# Vision Enrichment for /v1/classify

This document explains how Vision enrichment works in the classify endpoint and how to configure it for production.

## Overview

The `/v1/classify` endpoint has two independent Vision-related configurations:

1. **Classifier Provider** (`SCANIUM_CLASSIFIER_PROVIDER`)
   - Controls how category classification happens
   - Options: `mock` (deterministic), `google` (Google Vision labels)

2. **Vision Provider** (`VISION_PROVIDER`)
   - Controls attribute enrichment (OCR, colors, logos, labels)
   - Options: `mock` (test data), `google` (real Google Vision API)

**Key Insight:** These are INDEPENDENT. You can use `mock` classifier with `google` Vision enrichment. This is useful when you want rich attributes but don't need category mapping.

## Response Structure

When `enrichAttributes=true`:

```json
{
  "requestId": "uuid",
  "domainPackId": "home_resale",
  "domainCategoryId": "furniture",
  "confidence": 0.85,
  "label": "Storage & Organization > Shelves",
  "provider": "mock",
  "providerUnavailable": true,

  "visionStats": {
    "attempted": true,
    "visionProvider": "google-vision",
    "visionCacheHits": 0,
    "visionExtractions": 1,
    "visionErrors": 0
  },

  "visualFacts": {
    "itemId": "uuid",
    "dominantColors": [
      { "name": "blue", "rgbHex": "#1E40AF", "pct": 45 }
    ],
    "ocrSnippets": [
      { "text": "IKEA", "confidence": 0.95 }
    ],
    "labelHints": [
      { "label": "Furniture", "score": 0.92 }
    ],
    "logoHints": [
      { "brand": "IKEA", "score": 0.85 }
    ],
    "extractionMeta": {
      "provider": "google-vision",
      "timingsMs": { "total": 320, "ocr": 150, "labels": 100 },
      "imageCount": 1,
      "imageHashes": ["abc123..."]
    }
  },

  "visionAttributes": {
    "colors": [{ "name": "blue", "hex": "#1E40AF", "score": 0.45 }],
    "ocrText": "IKEA\nKALLAX\nMade in Sweden",
    "logos": [{ "name": "IKEA", "score": 0.85 }],
    "labels": [{ "name": "Furniture", "score": 0.92 }],
    "brandCandidates": ["IKEA"],
    "modelCandidates": ["KALLAX"]
  },

  "enrichedAttributes": {
    "brand": {
      "value": "IKEA",
      "confidence": "HIGH",
      "confidenceScore": 0.9,
      "evidenceRefs": [{ "type": "logo", "value": "IKEA", "score": 0.85 }]
    },
    "color": {
      "value": "blue",
      "confidence": "MED",
      "confidenceScore": 0.65,
      "evidenceRefs": [{ "type": "color", "value": "blue (45%)", "score": 0.45 }]
    }
  },

  "timingsMs": {
    "total": 450,
    "vision": 320,
    "mapping": 5,
    "enrichment": 320
  }
}
```

## Environment Configuration

### Development (Mock Everything)

```bash
SCANIUM_CLASSIFIER_PROVIDER=mock
VISION_PROVIDER=mock
CLASSIFIER_ENABLE_ATTRIBUTE_ENRICHMENT=true
```

### Production (Real Vision Enrichment)

```bash
# Classification (mock or google based on need)
SCANIUM_CLASSIFIER_PROVIDER=mock

# Vision enrichment (MUST be google for real attributes)
VISION_PROVIDER=google
VISION_ENABLED=true
VISION_ENABLE_OCR=true
VISION_ENABLE_LABELS=true
VISION_ENABLE_LOGOS=true
VISION_ENABLE_COLORS=true
VISION_OCR_MODE=TEXT_DETECTION

# Enable enrichment in classify endpoint
CLASSIFIER_ENABLE_ATTRIBUTE_ENRICHMENT=true

# Feature configuration for classification provider
VISION_FEATURE=LABEL_DETECTION,TEXT_DETECTION,IMAGE_PROPERTIES,LOGO_DETECTION

# Google credentials (REQUIRED for VISION_PROVIDER=google)
GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcp-vision.json
```

## NAS Deployment

### 1. Ensure GCP Credentials Are Mounted

In `docker-compose.yml`:
```yaml
volumes:
  - ./secrets:/secrets:ro
```

Place your service account JSON at `backend/secrets/gcp-vision.json`.

### 2. Update .env on NAS

```bash
# Add these to /volume1/docker/scanium/backend/.env

VISION_PROVIDER=google
VISION_ENABLED=true
VISION_ENABLE_OCR=true
VISION_ENABLE_LABELS=true
VISION_ENABLE_LOGOS=true
VISION_ENABLE_COLORS=true
CLASSIFIER_ENABLE_ATTRIBUTE_ENRICHMENT=true
GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcp-vision.json
```

### 3. Redeploy

```bash
cd /volume1/docker/scanium/backend
git pull origin main
docker compose down api
docker compose build --no-cache api
docker compose up -d api
```

### 4. Verify

```bash
# Test with enrichAttributes=true
curl -X POST "https://scanium.gtemp1.com/v1/classify?enrichAttributes=true" \
  -H "X-API-Key: YOUR_KEY" \
  -F "image=@test-image.jpg" \
  -F "domainPackId=home_resale"
```

Expected response includes:
- `visionStats.visionProvider: "google-vision"`
- `visualFacts` with real OCR text, colors, logos
- `enrichedAttributes` derived from Vision data

## Troubleshooting

### visionProvider shows "mock" instead of "google-vision"

**Cause:** `VISION_PROVIDER=mock` in environment.

**Fix:** Set `VISION_PROVIDER=google` and ensure credentials are mounted.

### providerUnavailable: true

**Cause:** This is expected when `SCANIUM_CLASSIFIER_PROVIDER=mock`.

**Explanation:** The `provider` field refers to the classification provider, not Vision enrichment. Vision enrichment status is in `visionStats.visionProvider`.

### No visualFacts in response

**Cause:** `enrichAttributes` not enabled or `CLASSIFIER_ENABLE_ATTRIBUTE_ENRICHMENT=false`.

**Fix:** Add `?enrichAttributes=true` to request URL and ensure env var is set.

### Vision extraction errors

Check logs:
```bash
docker logs scanium-api --tail 100 | grep -i vision
```

Common issues:
- Missing or invalid GCP credentials
- `GOOGLE_APPLICATION_CREDENTIALS` points to wrong path
- Vision API quota exceeded

## Caching

Vision extraction results are cached by image hash:

- **Vision Cache:** `VISION_CACHE_TTL_SECONDS` (default 6 hours)
- **Classifier Cache:** `CLASSIFIER_CACHE_TTL_SECONDS` (default 5 minutes)

On classifier cache hit, `visionStats.attempted: false` and no new Vision API call is made.

## Cost Considerations

Google Vision API pricing (per 1000 images):
- Label Detection: $1.50
- Text Detection (OCR): $1.50
- Logo Detection: $1.50
- Image Properties (colors): $1.50

With all features enabled: ~$6 per 1000 unique images.

Caching significantly reduces costs for repeated image uploads.
