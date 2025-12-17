# Cloud Classification for Scanium

## Overview

Scanium implements **cloud-first classification** that uploads cropped item images to a backend API for high-quality category recognition and attribute extraction. This system:

- ✅ Uploads only cropped item snapshots (not full camera frames)
- ✅ Strips EXIF metadata for privacy
- ✅ Retries transient network errors automatically
- ✅ Never blocks camera or scanning pipeline
- ✅ Integrates with Domain Pack for 23 fine-grained categories

## Architecture

```
Camera → ML Kit → ObjectTracker → ItemAggregator (stable items)
                                        ↓
                           ClassificationOrchestrator
                                  ↓
                              CloudClassifier
                                  ↓
                         Backend API (your server)
                                  ↓
                  Returns: domainCategoryId, confidence, attributes
                                  ↓
                          ItemsViewModel (UI update)
```

## Configuration

### 1. Set up local.properties

Copy the template and fill in your API details:

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
scanium.api.base.url=https://your-backend.com/api/v1
scanium.api.key=your-api-key-here
```

**IMPORTANT**: `local.properties` is gitignored. Never commit secrets!

### 2. Verify BuildConfig

The gradle build automatically reads from `local.properties` and exposes:

- `BuildConfig.SCANIUM_API_BASE_URL`
- `BuildConfig.SCANIUM_API_KEY`

For CI/production, use environment variables instead:

```bash
export SCANIUM_API_BASE_URL=https://api.scanium.example.com/v1
export SCANIUM_API_KEY=prod_key_from_secrets_manager
```

### 3. Build and Run

```bash
./build.sh assembleDebug
./gradlew installDebug
```

## Backend API Contract

### Endpoint

```
POST {SCANIUM_API_BASE_URL}/classify
```

### Request

**Content-Type**: `multipart/form-data`

**Fields**:
- `image`: JPEG file (cropped item thumbnail, EXIF stripped)
- `domainPackId`: string (default: "home_resale")

**Headers**:
- `X-API-Key`: Your API key (if configured)
- `X-Client`: "Scanium-Android"
- `X-App-Version`: App version (e.g., "1.0")

**Example using curl**:

```bash
curl -X POST https://your-backend.com/api/v1/classify \
  -H "X-API-Key: your-api-key" \
  -F "image=@/path/to/item.jpg" \
  -F "domainPackId=home_resale"
```

### Response

**Success (HTTP 200)**:

```json
{
  "domainCategoryId": "furniture_sofa",
  "confidence": 0.87,
  "label": "Sofa",
  "attributes": {
    "color": "brown",
    "material": "leather",
    "condition": "good"
  },
  "requestId": "req_abc123"
}
```

**Fields**:
- `domainCategoryId` (optional): Fine-grained category ID from domain pack (e.g., "furniture_sofa")
- `confidence` (optional): Classification confidence (0.0 - 1.0)
- `label` (optional): Human-readable label
- `attributes` (optional): Key-value attribute map
- `requestId` (optional): Backend request ID for debugging

**Error Codes**:
- `400 Bad Request`: Invalid image or domainPackId (non-retryable)
- `401 Unauthorized`: Invalid/missing API key (non-retryable)
- `403 Forbidden`: API key lacks permissions (non-retryable)
- `408 Request Timeout`: Request took too long (retryable)
- `429 Too Many Requests`: Rate limit exceeded (retryable)
- `500 Internal Server Error`: Backend error (retryable)

## Retry Logic

### Automatic Retry

The `ClassificationOrchestrator` automatically retries transient errors:

- **Max retries**: 3 (total 4 attempts)
- **Base delay**: 2 seconds
- **Max delay**: 16 seconds
- **Jitter**: ±25%

**Retry schedule** (approximate with jitter):
1. Immediate attempt
2. Retry after ~2 seconds
3. Retry after ~4 seconds
4. Retry after ~8 seconds

**Retryable errors**:
- HTTP 408 Request Timeout
- HTTP 429 Too Many Requests
- HTTP 5xx Server errors
- Network I/O errors (timeouts, connection failures)

**Non-retryable errors** (permanent failure):
- HTTP 400 Bad Request
- HTTP 401 Unauthorized
- HTTP 403 Forbidden
- HTTP 404 Not Found

### Manual Retry

If classification fails after automatic retries, the item remains visible with status "Unclassified (tap to retry)". Users can tap the retry button to reattempt classification.

## Privacy

### Data Uploaded

- ✅ **Cropped item thumbnail only** (not full camera frame)
- ✅ **EXIF metadata stripped** (re-compressed to JPEG)
- ❌ **No location data**
- ❌ **No device identifiers**
- ❌ **No user information**

### Headers Sent

- `X-Client: Scanium-Android` (generic client type)
- `X-App-Version: 1.0` (app version for backend analytics)
- `X-API-Key: <key>` (authentication)

### Privacy Notice

The app should display a privacy notice in Settings:

> **Cloud Mode Privacy Notice**
>
> When cloud classification is enabled, Scanium uploads cropped item snapshots to our backend for category recognition. We do not upload:
> - Full camera frames
> - Location data
> - Device identifiers
> - Personal information
>
> All images are processed server-side and deleted after classification.

## Concurrency Control

The `ClassificationOrchestrator` limits classification requests to **2 concurrent uploads** to:

- Prevent network congestion
- Avoid overwhelming the backend
- Preserve bandwidth for camera preview

Requests beyond the limit are queued and processed as slots become available.

## Performance

### Timeouts

- **Connect timeout**: 10 seconds
- **Read timeout**: 10 seconds
- **Total max time** (with retries): ~32 seconds (4 attempts × 8s avg)

### Image Compression

- **Format**: JPEG
- **Quality**: 85%
- **Typical size**: 50-200 KB per item

### Non-Blocking

- Classification runs on `Dispatchers.IO` (background thread)
- **Never blocks** camera preview or scanning
- UI updates asynchronously via StateFlow

## Switching Between Modes

### Default Mode

Cloud classification is **enabled by default** (as of this implementation).

### User Control

Users can switch between ON_DEVICE and CLOUD modes in Settings (future UI):

```kotlin
classificationModeViewModel.updateMode(ClassificationMode.CLOUD)
classificationModeViewModel.updateMode(ClassificationMode.ON_DEVICE)
```

### Persistence

Mode selection is persisted via DataStore:

- Survives app restarts
- User preference is remembered

## Testing

### Unit Tests

Test retry logic and error handling:

```bash
./gradlew test --tests "*ClassificationOrchestrator*"
```

### Mock Backend

For local development without a real backend, you can:

1. Leave `scanium.api.base.url` empty → classification skips silently
2. Use a mock server (e.g., `http://localhost:8080/api/v1`)
3. Return mock responses for testing

### Real Backend Testing

Set up staging environment:

```properties
scanium.api.base.url=https://staging-api.scanium.example.com/v1
scanium.api.key=staging_test_key_12345
```

## Domain Pack Integration

### Category Mapping

Cloud responses include `domainCategoryId` (e.g., "furniture_sofa") which maps to:

1. **Domain category** in `home_resale_domain_pack.json`
2. **Coarse ItemCategory** (e.g., HOME_GOOD)

Example mapping:

```
Backend response: { "domainCategoryId": "furniture_sofa", ... }
         ↓
Domain Pack lookup: DomainCategory(id="furniture_sofa", itemCategoryName="HOME_GOOD", ...)
         ↓
ItemCategory: HOME_GOOD (for pricing, icons, etc.)
```

### Unknown Categories

If backend returns an unknown `domainCategoryId`:

1. Log warning
2. Fall back to label-based category detection
3. Display label as-is
4. Do **not** crash

## Troubleshooting

### "Cloud endpoint not configured"

**Symptom**: Items not being classified, logs show "SCANIUM_API_BASE_URL is empty"

**Fix**: Add `scanium.api.base.url` to `local.properties` and rebuild

### "HTTP 401 Unauthorized"

**Symptom**: All classifications fail with 401 errors

**Fix**: Verify `scanium.api.key` in `local.properties` matches your backend API key

### "Request timeout" errors

**Symptom**: Frequent timeout errors, retries exhaust

**Fix**:
- Check network connection
- Verify backend is reachable
- Check backend response time (should be <10s)
- Consider increasing timeout in `CloudClassifier.kt`

### Items stuck as "Pending"

**Symptom**: Items show as pending but never complete

**Fix**:
- Check logcat for classification errors: `adb logcat | grep CloudClassifier`
- Verify backend is returning HTTP 200 responses
- Check response JSON format matches contract

### High memory usage

**Symptom**: App memory usage increases during classification

**Fix**: Thumbnails are recycled after classification. If memory is still high:
- Check for bitmap leaks in custom code
- Verify `AggregatedItem.cleanup()` is called on removal

## Future Enhancements

### On-Device CLIP

When implementing on-device classification:

1. Add TFLite model to `app/src/main/assets/`
2. Implement real `OnDeviceClassifier` using TFLite interpreter
3. Update `ClassificationOrchestrator` (already supports ON_DEVICE mode)
4. Add model download/update mechanism

### Batching

For better throughput, implement batch classification:

```
POST /classify/batch
{
  "items": [
    { "imageBase64": "...", "itemId": "agg_123" },
    { "imageBase64": "...", "itemId": "agg_456" }
  ],
  "domainPackId": "home_resale"
}
```

### Caching

Add persistent cache for classification results:

- Room database or DataStore
- Keyed by image hash
- Survives app restarts

## API Example Implementation

### Python FastAPI Backend (Example)

```python
from fastapi import FastAPI, File, Form, UploadFile
from pydantic import BaseModel

app = FastAPI()

class ClassificationResponse(BaseModel):
    domainCategoryId: str
    confidence: float
    label: str
    attributes: dict[str, str] | None = None
    requestId: str

@app.post("/classify")
async def classify(
    image: UploadFile = File(...),
    domainPackId: str = Form(...)
):
    # 1. Load image
    img_bytes = await image.read()

    # 2. Run your classifier model
    result = your_classifier_model.predict(img_bytes, domainPackId)

    # 3. Return response
    return ClassificationResponse(
        domainCategoryId=result.category_id,
        confidence=result.confidence,
        label=result.label,
        attributes=result.attributes,
        requestId=generate_request_id()
    )
```

## Related Documentation

- **Architecture**: `/md/architecture/ARCHITECTURE.md`
- **Domain Pack**: `/md/architecture/DOMAIN_PACK_ARCHITECTURE.md`
- **Testing**: `/md/testing/TEST_SUITE.md`
- **Setup**: `/SETUP.md`

## Support

For issues or questions:

1. Check logcat: `adb logcat | grep -E "CloudClassifier|ClassificationOrchestrator"`
2. Verify `local.properties` configuration
3. Test backend endpoint with curl
4. Review backend logs for errors
