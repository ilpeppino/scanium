***REMOVED*** Vision Enrichment for /v1/classify

This document explains how Vision enrichment works in the classify endpoint and how to configure it for production.

***REMOVED******REMOVED*** Overview

The `/v1/classify` endpoint has two independent Vision-related configurations:

1. **Classifier Provider** (`SCANIUM_CLASSIFIER_PROVIDER`)
   - Controls how category classification happens
   - Options: `mock` (deterministic), `google` (Google Vision labels)

2. **Vision Provider** (`VISION_PROVIDER`)
   - Controls attribute enrichment (OCR, colors, logos, labels)
   - Options: `mock` (test data), `google` (real Google Vision API)

**Key Insight:** These are INDEPENDENT. You can use `mock` classifier with `google` Vision enrichment. This is useful when you want rich attributes but don't need category mapping.

***REMOVED******REMOVED*** Response Structure

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
      { "name": "blue", "rgbHex": "***REMOVED***1E40AF", "pct": 45 }
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
    "colors": [{ "name": "blue", "hex": "***REMOVED***1E40AF", "score": 0.45 }],
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

***REMOVED******REMOVED*** Environment Configuration

***REMOVED******REMOVED******REMOVED*** Development (Mock Everything)

```bash
SCANIUM_CLASSIFIER_PROVIDER=mock
VISION_PROVIDER=mock
CLASSIFIER_ENABLE_ATTRIBUTE_ENRICHMENT=true
```

***REMOVED******REMOVED******REMOVED*** Production (Real Vision Enrichment)

```bash
***REMOVED*** Classification (mock or google based on need)
SCANIUM_CLASSIFIER_PROVIDER=mock

***REMOVED*** Vision enrichment (MUST be google for real attributes)
VISION_PROVIDER=google
VISION_ENABLED=true
VISION_ENABLE_OCR=true
VISION_ENABLE_LABELS=true
VISION_ENABLE_LOGOS=true
VISION_ENABLE_COLORS=true
VISION_OCR_MODE=TEXT_DETECTION

***REMOVED*** Enable enrichment in classify endpoint
CLASSIFIER_ENABLE_ATTRIBUTE_ENRICHMENT=true

***REMOVED*** Feature configuration for classification provider
VISION_FEATURE=LABEL_DETECTION,TEXT_DETECTION,IMAGE_PROPERTIES,LOGO_DETECTION

***REMOVED*** Google credentials (REQUIRED for VISION_PROVIDER=google)
GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcp-vision.json
```

***REMOVED******REMOVED*** NAS Deployment

***REMOVED******REMOVED******REMOVED*** 1. Ensure GCP Credentials Are Mounted

In `docker-compose.yml`:
```yaml
volumes:
  - ./secrets:/secrets:ro
```

Place your service account JSON at `backend/secrets/gcp-vision.json`.

***REMOVED******REMOVED******REMOVED*** 2. Update .env on NAS

```bash
***REMOVED*** Add these to /volume1/docker/scanium/backend/.env

VISION_PROVIDER=google
VISION_ENABLED=true
VISION_ENABLE_OCR=true
VISION_ENABLE_LABELS=true
VISION_ENABLE_LOGOS=true
VISION_ENABLE_COLORS=true
CLASSIFIER_ENABLE_ATTRIBUTE_ENRICHMENT=true
GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcp-vision.json
```

***REMOVED******REMOVED******REMOVED*** 3. Redeploy

```bash
cd /volume1/docker/scanium/backend
git pull origin main
docker compose down api
docker compose build --no-cache api
docker compose up -d api
```

***REMOVED******REMOVED******REMOVED*** 4. Verify

```bash
***REMOVED*** Test with enrichAttributes=true
curl -X POST "https://scanium.gtemp1.com/v1/classify?enrichAttributes=true" \
  -H "X-API-Key: YOUR_KEY" \
  -F "image=@test-image.jpg" \
  -F "domainPackId=home_resale"
```

Expected response includes:
- `visionStats.visionProvider: "google-vision"`
- `visualFacts` with real OCR text, colors, logos
- `enrichedAttributes` derived from Vision data

***REMOVED******REMOVED*** Error Responses

***REMOVED******REMOVED******REMOVED*** 400 INVALID_IMAGE

Returned when the uploaded image is corrupted or cannot be processed:

```json
{
  "error": {
    "code": "INVALID_IMAGE",
    "message": "Invalid or corrupted image data. Please upload a valid JPEG, PNG, or WebP image.",
    "correlationId": "uuid"
  }
}
```

This replaces the previous 500 error for corrupted images.

***REMOVED******REMOVED******REMOVED*** 400 VALIDATION_ERROR

Returned for validation failures:
- Missing image file
- Unsupported content type (not JPEG, PNG, or WebP)
- Invalid hints JSON
- Unknown domainPackId

***REMOVED******REMOVED*** Testing with curl

***REMOVED******REMOVED******REMOVED*** Basic Classification with Enrichment

```bash
***REMOVED*** Replace YOUR_API_KEY with your actual API key
curl -X POST "https://scanium.gtemp1.com/v1/classify?enrichAttributes=true" \
  -H "X-API-Key: YOUR_API_KEY" \
  -F "image=@/path/to/image.jpg;type=image/jpeg"
```

***REMOVED******REMOVED******REMOVED*** Full Request with All Options

```bash
curl -X POST "https://scanium.gtemp1.com/v1/classify" \
  -H "X-API-Key: YOUR_API_KEY" \
  -F "image=@photo.jpg;type=image/jpeg" \
  -F "domainPackId=home_resale" \
  -F "enrichAttributes=true" \
  -F 'hints={"category":"furniture","condition":"used"}'
```

***REMOVED******REMOVED******REMOVED*** Download and Test a Sample Image

```bash
***REMOVED*** Download a random test image
curl -L -o /tmp/test.jpg "https://picsum.photos/640/480.jpg"

***REMOVED*** Classify with enrichment
curl -X POST "https://scanium.gtemp1.com/v1/classify?enrichAttributes=true" \
  -H "X-API-Key: YOUR_API_KEY" \
  -F "image=@/tmp/test.jpg;type=image/jpeg" | jq .
```

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** visionProvider shows "mock" instead of "google-vision"

**Cause:** `VISION_PROVIDER=mock` in environment.

**Fix:** Set `VISION_PROVIDER=google` and ensure credentials are mounted.

***REMOVED******REMOVED******REMOVED*** providerUnavailable: true

**Cause:** This is expected when `SCANIUM_CLASSIFIER_PROVIDER=mock`.

**Explanation:** The `provider` field refers to the classification provider, not Vision enrichment. Vision enrichment status is in `visionStats.visionProvider`.

***REMOVED******REMOVED******REMOVED*** No visualFacts in response

**Cause:** `enrichAttributes` not enabled or `CLASSIFIER_ENABLE_ATTRIBUTE_ENRICHMENT=false`.

**Fix:** Add `?enrichAttributes=true` to request URL and ensure env var is set.

***REMOVED******REMOVED******REMOVED*** Vision extraction errors

Check logs:
```bash
docker logs scanium-api --tail 100 | grep -i vision
```

Common issues:
- Missing or invalid GCP credentials
- `GOOGLE_APPLICATION_CREDENTIALS` points to wrong path
- Vision API quota exceeded

***REMOVED******REMOVED*** Caching

Vision extraction results are cached by image hash:

- **Vision Cache:** `VISION_CACHE_TTL_SECONDS` (default 6 hours)
- **Classifier Cache:** `CLASSIFIER_CACHE_TTL_SECONDS` (default 5 minutes)

On classifier cache hit, `visionStats.attempted: false` and no new Vision API call is made.

***REMOVED******REMOVED*** Multi-Feature Configuration

***REMOVED******REMOVED******REMOVED*** VISION_FEATURE (CSV Format)

The `VISION_FEATURE` environment variable controls which Google Vision API features are requested for classification. It supports CSV format:

```bash
***REMOVED*** Single feature (backward compatible)
VISION_FEATURE=LABEL_DETECTION

***REMOVED*** Multiple features (comma-separated)
VISION_FEATURE=LABEL_DETECTION,TEXT_DETECTION,IMAGE_PROPERTIES,LOGO_DETECTION

***REMOVED*** All available features
VISION_FEATURE=LABEL_DETECTION,OBJECT_LOCALIZATION,TEXT_DETECTION,DOCUMENT_TEXT_DETECTION,IMAGE_PROPERTIES,LOGO_DETECTION
```

**Available Features:**
| Feature | Output | Use Case |
|---------|--------|----------|
| `LABEL_DETECTION` | Category/material hints | Classification, material detection |
| `TEXT_DETECTION` | OCR text snippets | Brand/model from labels |
| `DOCUMENT_TEXT_DETECTION` | Dense OCR (structured) | Detailed text extraction |
| `IMAGE_PROPERTIES` | Dominant colors | Color attribute extraction |
| `LOGO_DETECTION` | Brand logos with confidence | Brand identification |
| `OBJECT_LOCALIZATION` | Object bounding boxes | Classification signals |

***REMOVED******REMOVED******REMOVED*** Recommended Production Config

```bash
***REMOVED*** Multi-feature Vision extraction (NAS production)
VISION_FEATURE=LABEL_DETECTION,TEXT_DETECTION,IMAGE_PROPERTIES,LOGO_DETECTION
```

This enables:
- **ocrText**: Brand/model from label text
- **colors**: Dominant color extraction `[{name, hex, score}]`
- **logos**: Brand detection `[{name, score}]`
- **labels**: Category/material hints `[{name, score}]`
- **brandCandidates**: From logos + OCR token heuristics
- **modelCandidates**: Regex patterns from OCR tokens

***REMOVED******REMOVED*** Timeout and Retry Configuration

```bash
***REMOVED*** Vision API timeout (ms) - default 10000
VISION_TIMEOUT_MS=10000

***REMOVED*** Max retries for transient failures - default 2
VISION_MAX_RETRIES=2
```

Retries use exponential backoff with jitter: `200ms * 2^attempt * (1 + random(0.3))`

***REMOVED******REMOVED*** Logging and Observability

***REMOVED******REMOVED******REMOVED*** Vision Counters in Response

Every `/v1/classify` response includes `visionStats`:

```json
{
  "visionStats": {
    "attempted": true,
    "visionProvider": "google-vision",
    "visionExtractions": 1,
    "visionCacheHits": 0,
    "visionErrors": 0
  }
}
```

| Counter | Description |
|---------|-------------|
| `visionExtractions` | Number of Vision API calls made |
| `visionCacheHits` | Number of cache hits (no API call) |
| `visionErrors` | Number of extraction failures |

***REMOVED******REMOVED******REMOVED*** Server Logs

Classifier response logs include these counters:

```json
{
  "msg": "Classifier response",
  "requestId": "uuid",
  "visionExtractions": 1,
  "visionCacheHits": 0,
  "visionErrors": 0
}
```

***REMOVED******REMOVED******REMOVED*** Metrics

Metrics are recorded for:
- `scanium_classifier_request_latency_ms` - Request duration
- `scanium_attribute_extractions_total` - Attribute extraction count by type
- `scanium_attribute_confidence` - Confidence distribution

***REMOVED******REMOVED*** Cost Considerations

Google Vision API pricing (per 1000 images):
- Label Detection: $1.50
- Text Detection (OCR): $1.50
- Logo Detection: $1.50
- Image Properties (colors): $1.50

With all features enabled: ~$6 per 1000 unique images.

Caching significantly reduces costs for repeated image uploads.

***REMOVED******REMOVED*** Complete .env Example (NAS Production)

```bash
***REMOVED*** =============================================================================
***REMOVED*** Vision Enrichment Configuration (NAS Production)
***REMOVED*** =============================================================================

***REMOVED*** Classifier provider (mock = no real classification, google = Vision labels)
SCANIUM_CLASSIFIER_PROVIDER=mock

***REMOVED*** Vision features for classification (CSV format)
VISION_FEATURE=LABEL_DETECTION,TEXT_DETECTION,IMAGE_PROPERTIES,LOGO_DETECTION

***REMOVED*** Vision enrichment provider (must be 'google' for real attributes)
VISION_PROVIDER=google
VISION_ENABLED=true

***REMOVED*** Enable individual Vision features for enrichment
VISION_ENABLE_OCR=true
VISION_ENABLE_LABELS=true
VISION_ENABLE_LOGOS=true
VISION_ENABLE_COLORS=true

***REMOVED*** OCR mode: TEXT_DETECTION (general) or DOCUMENT_TEXT_DETECTION (dense text)
VISION_OCR_MODE=TEXT_DETECTION

***REMOVED*** Enable attribute enrichment in classify endpoint
CLASSIFIER_ENABLE_ATTRIBUTE_ENRICHMENT=true

***REMOVED*** Timeout and retry settings
VISION_TIMEOUT_MS=10000
VISION_MAX_RETRIES=2

***REMOVED*** Google Cloud credentials (required for VISION_PROVIDER=google)
GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcp-vision.json

***REMOVED*** Caching settings
VISION_CACHE_TTL_SECONDS=21600
CLASSIFIER_CACHE_TTL_SECONDS=300

***REMOVED*** Confidence thresholds (0.0 - 1.0)
VISION_MIN_OCR_CONFIDENCE=0.5
VISION_MIN_LABEL_CONFIDENCE=0.5
VISION_MIN_LOGO_CONFIDENCE=0.5

***REMOVED*** Feature limits
VISION_MAX_OCR_SNIPPETS=10
VISION_MAX_LABEL_HINTS=10
VISION_MAX_LOGO_HINTS=5
VISION_MAX_COLORS=5
```

***REMOVED******REMOVED*** Sample JSON Response

Complete response with all features enabled:

```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "correlationId": "660e8400-e29b-41d4-a716-446655440001",
  "domainPackId": "home_resale",
  "domainCategoryId": "furniture",
  "confidence": 0.92,
  "label": "Storage & Organization > Shelves",
  "attributes": {
    "segment": "furniture"
  },
  "provider": "mock",
  "providerUnavailable": true,
  "cacheHit": false,

  "visionStats": {
    "attempted": true,
    "visionProvider": "google-vision",
    "visionExtractions": 1,
    "visionCacheHits": 0,
    "visionErrors": 0
  },

  "visualFacts": {
    "itemId": "550e8400-e29b-41d4-a716-446655440000",
    "dominantColors": [
      { "name": "white", "rgbHex": "***REMOVED***FFFFFF", "pct": 35 },
      { "name": "brown", "rgbHex": "***REMOVED***8B5A2B", "pct": 28 },
      { "name": "gray", "rgbHex": "***REMOVED***2D2D2D", "pct": 15 }
    ],
    "ocrSnippets": [
      { "text": "IKEA", "confidence": 0.95 },
      { "text": "KALLAX", "confidence": 0.92 },
      { "text": "77x77 cm", "confidence": 0.88 },
      { "text": "Art. 802.758.87", "confidence": 0.90 }
    ],
    "labelHints": [
      { "label": "Furniture", "score": 0.95 },
      { "label": "Shelf", "score": 0.92 },
      { "label": "Wood", "score": 0.82 },
      { "label": "Storage", "score": 0.75 }
    ],
    "logoHints": [
      { "brand": "IKEA", "score": 0.91 }
    ],
    "extractionMeta": {
      "provider": "google-vision",
      "timingsMs": {
        "total": 450,
        "ocr": 180,
        "labels": 120,
        "logos": 100,
        "colors": 50
      },
      "imageCount": 1,
      "imageHashes": ["a1b2c3d4e5f6g7h8"]
    }
  },

  "visionAttributes": {
    "colors": [
      { "name": "white", "hex": "***REMOVED***FFFFFF", "score": 0.35 },
      { "name": "brown", "hex": "***REMOVED***8B5A2B", "score": 0.28 }
    ],
    "ocrText": "IKEA\nKALLAX\n77x77 cm\nArt. 802.758.87",
    "logos": [
      { "name": "IKEA", "score": 0.91 }
    ],
    "labels": [
      { "name": "Furniture", "score": 0.95 },
      { "name": "Shelf", "score": 0.92 }
    ],
    "brandCandidates": ["IKEA"],
    "modelCandidates": ["802.758.87", "KALLAX"]
  },

  "enrichedAttributes": {
    "brand": {
      "value": "IKEA",
      "confidence": "HIGH",
      "confidenceScore": 0.91,
      "evidenceRefs": [
        { "type": "logo", "value": "IKEA", "score": 0.91 }
      ]
    },
    "model": {
      "value": "KALLAX",
      "confidence": "MED",
      "confidenceScore": 0.65,
      "evidenceRefs": [
        { "type": "ocr", "value": "KALLAX", "score": 0.92 }
      ]
    },
    "color": {
      "value": "white",
      "confidence": "MED",
      "confidenceScore": 0.65,
      "evidenceRefs": [
        { "type": "color", "value": "white (35%)", "score": 0.35 }
      ]
    },
    "material": {
      "value": "wood",
      "confidence": "MED",
      "confidenceScore": 0.65,
      "evidenceRefs": [
        { "type": "label", "value": "Wood", "score": 0.82 }
      ]
    }
  },

  "timingsMs": {
    "total": 520,
    "vision": 15,
    "mapping": 5,
    "enrichment": 450
  }
}
```
