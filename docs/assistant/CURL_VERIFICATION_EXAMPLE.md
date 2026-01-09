***REMOVED*** Assistant API Curl Verification Example

This document provides curl examples for verifying the `/v1/assist/chat` endpoint with multipart requests.

***REMOVED******REMOVED*** Backend Contract

The backend expects:
- **Required headers:**
  - `X-API-Key`: API key for authentication
  - `X-Scanium-Device-Id`: Device identifier (raw, not hashed)
- **Multipart fields:**
  - `payload`: JSON string containing the request body
  - `itemImages[<itemId>]`: Image files attached to specific items
- **Payload JSON structure:**
  - `message`: Non-empty string (required)
  - `items`: Array of objects, each with `itemId` (required)
  - `history`: Optional array with `role`, `content`, `timestamp`
  - `exportProfile`: Optional object with `id` and `displayName`
  - `assistantPrefs`: Optional preferences object

***REMOVED******REMOVED*** Example 1: Multipart Request with Images

```bash
***REMOVED***!/bin/bash

***REMOVED*** Configuration
API_KEY="your-api-key-here"
DEVICE_ID="test-device-123"
BASE_URL="https://your-nas-url/api"

***REMOVED*** Create a temporary JSON payload file
cat > /tmp/payload.json <<'EOF'
{
  "message": "Generate a marketplace-ready listing for this item",
  "items": [
    {
      "itemId": "item-abc123",
      "title": "Nike Air Max Sneakers",
      "description": null,
      "category": "Shoes",
      "confidence": 0.85,
      "attributes": [
        {
          "key": "brand",
          "value": "Nike",
          "confidence": 0.9,
          "source": "USER"
        },
        {
          "key": "color",
          "value": "Black",
          "confidence": 0.85,
          "source": "DETECTED"
        },
        {
          "key": "itemType",
          "value": "Sneakers",
          "confidence": 0.9,
          "source": "USER"
        },
        {
          "key": "size",
          "value": "US 10",
          "confidence": 1.0,
          "source": "USER"
        },
        {
          "key": "condition",
          "value": "Used - Good",
          "confidence": 1.0,
          "source": "USER"
        }
      ],
      "priceEstimate": 75.0,
      "photosCount": 2,
      "exportProfileId": "generic"
    }
  ],
  "history": [],
  "exportProfile": {
    "id": "generic",
    "displayName": "Generic"
  },
  "assistantPrefs": null
}
EOF

***REMOVED*** Create a dummy image file (1x1 black pixel PNG)
echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" | base64 -d > /tmp/test-image.jpg

***REMOVED*** Send multipart request
curl -X POST "${BASE_URL}/v1/assist/chat" \
  -H "X-API-Key: ${API_KEY}" \
  -H "X-Scanium-Device-Id: ${DEVICE_ID}" \
  -H "X-Scanium-Correlation-Id: test-curl-$(date +%s)" \
  -H "X-Client: Scanium-Test" \
  -H "X-App-Version: 1.0.0-test" \
  -F "payload=</tmp/payload.json" \
  -F "itemImages[item-abc123]=@/tmp/test-image.jpg;type=image/jpeg" \
  -v

***REMOVED*** Cleanup
rm -f /tmp/payload.json /tmp/test-image.jpg
```

***REMOVED******REMOVED*** Example 2: Multiple Images for Multiple Items

```bash
***REMOVED***!/bin/bash

API_KEY="your-api-key-here"
DEVICE_ID="test-device-456"
BASE_URL="https://your-nas-url/api"

***REMOVED*** Create payload with multiple items
cat > /tmp/payload-multi.json <<'EOF'
{
  "message": "Compare these items and provide listing advice",
  "items": [
    {
      "itemId": "item-001",
      "title": "Red T-Shirt",
      "category": "Clothing",
      "attributes": [
        {
          "key": "brand",
          "value": "Adidas",
          "source": "USER"
        },
        {
          "key": "color",
          "value": "Red",
          "source": "USER"
        }
      ],
      "photosCount": 2
    },
    {
      "itemId": "item-002",
      "title": "Blue Jeans",
      "category": "Clothing",
      "attributes": [
        {
          "key": "brand",
          "value": "Levi's",
          "source": "USER"
        },
        {
          "key": "color",
          "value": "Blue",
          "source": "USER"
        }
      ],
      "photosCount": 1
    }
  ],
  "history": [],
  "exportProfile": {
    "id": "generic",
    "displayName": "Generic"
  }
}
EOF

***REMOVED*** Create dummy images
echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" | base64 -d > /tmp/image1.jpg
echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" | base64 -d > /tmp/image2.jpg
echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" | base64 -d > /tmp/image3.jpg

***REMOVED*** Send request with multiple images
curl -X POST "${BASE_URL}/v1/assist/chat" \
  -H "X-API-Key: ${API_KEY}" \
  -H "X-Scanium-Device-Id: ${DEVICE_ID}" \
  -H "X-Scanium-Correlation-Id: test-multi-$(date +%s)" \
  -H "X-Client: Scanium-Test" \
  -F "payload=<./payload-multi.json" \
  -F "itemImages[item-001]=@/tmp/image1.jpg;type=image/jpeg" \
  -F "itemImages[item-001]=@/tmp/image2.jpg;type=image/jpeg" \
  -F "itemImages[item-002]=@/tmp/image3.jpg;type=image/jpeg" \
  -v

***REMOVED*** Cleanup
rm -f /tmp/payload-multi.json /tmp/image1.jpg /tmp/image2.jpg /tmp/image3.jpg
```

***REMOVED******REMOVED*** Example 3: With Conversation History

```bash
***REMOVED***!/bin/bash

API_KEY="your-api-key-here"
DEVICE_ID="test-device-789"
BASE_URL="https://your-nas-url/api"

***REMOVED*** Create payload with conversation history
cat > /tmp/payload-history.json <<'EOF'
{
  "message": "Can you make the title more catchy?",
  "items": [
    {
      "itemId": "item-xyz",
      "title": "Vintage Leather Jacket",
      "category": "Clothing",
      "attributes": [
        {
          "key": "brand",
          "value": "Schott",
          "confidence": 0.95,
          "source": "USER"
        },
        {
          "key": "material",
          "value": "Leather",
          "confidence": 1.0,
          "source": "USER"
        },
        {
          "key": "condition",
          "value": "Used - Excellent",
          "confidence": 1.0,
          "source": "USER"
        }
      ],
      "photosCount": 1
    }
  ],
  "history": [
    {
      "role": "USER",
      "content": "Generate a listing for this jacket",
      "timestamp": 1704067200000
    },
    {
      "role": "ASSISTANT",
      "content": "Here's a listing for your vintage leather jacket...",
      "timestamp": 1704067205000
    }
  ],
  "exportProfile": {
    "id": "generic",
    "displayName": "Generic"
  }
}
EOF

***REMOVED*** Send request
curl -X POST "${BASE_URL}/v1/assist/chat" \
  -H "X-API-Key: ${API_KEY}" \
  -H "X-Scanium-Device-Id: ${DEVICE_ID}" \
  -H "X-Scanium-Correlation-Id: test-history-$(date +%s)" \
  -H "X-Client: Scanium-Test" \
  -F "payload=<./payload-history.json" \
  -v

***REMOVED*** Cleanup
rm -f /tmp/payload-history.json
```

***REMOVED******REMOVED*** Expected Responses

***REMOVED******REMOVED******REMOVED*** Success (200 OK)

```json
{
  "reply": "Here's a compelling marketplace-ready listing for your Nike Air Max sneakers...",
  "actions": [
    {
      "type": "APPLY_DRAFT_UPDATE",
      "payload": {
        "field": "title",
        "value": "Nike Air Max Sneakers - Size US 10 - Black - Used Good Condition"
      },
      "requiresConfirmation": false
    }
  ],
  "citationsMetadata": {},
  "fromCache": false,
  "confidenceTier": "HIGH",
  "evidence": [
    {
      "type": "BRAND",
      "text": "Brand detected: Nike (confidence: 0.9)"
    }
  ],
  "suggestedDraftUpdates": [
    {
      "field": "title",
      "value": "Nike Air Max Sneakers - Size US 10 - Black - Used Good Condition",
      "confidence": "HIGH",
      "requiresConfirmation": false
    },
    {
      "field": "description",
      "value": "Classic Nike Air Max sneakers in black...",
      "confidence": "HIGH",
      "requiresConfirmation": false
    }
  ],
  "safety": {
    "blocked": false,
    "reasonCode": null,
    "requestId": "req-abc123"
  },
  "correlationId": "test-curl-1704067200"
}
```

***REMOVED******REMOVED******REMOVED*** Validation Error (400 Bad Request)

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Message could not be processed",
    "correlationId": "test-curl-1704067200"
  },
  "assistantError": {
    "type": "validation_error",
    "category": "policy",
    "retryable": false,
    "reasonCode": "VALIDATION_ERROR",
    "message": "Validation failed"
  },
  "safety": {
    "blocked": true,
    "reasonCode": "VALIDATION_ERROR",
    "requestId": "req-abc123"
  }
}
```

***REMOVED******REMOVED*** Common Validation Errors

1. **Missing itemId**:
   ```
   Error: items[0].itemId is required
   ```

2. **Empty message**:
   ```
   Error: message must be at least 1 character
   ```

3. **Invalid role in history**:
   ```
   Error: history[0].role must be one of USER, ASSISTANT, SYSTEM
   ```

4. **Missing payload field**:
   ```
   Error: Missing payload field in multipart request
   ```

***REMOVED******REMOVED*** NAS Verification

To verify the backend is working correctly:

```bash
***REMOVED*** SSH into NAS
ssh nas "docker exec scanium-backend-prod node dist/cli.js health"

***REMOVED*** Check backend logs
ssh nas "docker logs -f --tail 50 scanium-backend-prod | grep assist"

***REMOVED*** Test with actual image
curl -X POST "https://your-nas-url/api/v1/assist/chat" \
  -H "X-API-Key: your-key" \
  -H "X-Scanium-Device-Id: test-device" \
  -F "payload={\"message\":\"Test\",\"items\":[{\"itemId\":\"test-1\"}]}" \
  -F "itemImages[test-1]=@/path/to/real/image.jpg" \
  2>&1 | grep -E "(HTTP|error|reply)"
```

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Issue: 400 VALIDATION_ERROR

**Check:**
1. Ensure `itemId` is present in all items
2. Ensure `message` is non-empty
3. Ensure history roles are valid (USER, ASSISTANT, SYSTEM)
4. Ensure multipart has `payload` field

***REMOVED******REMOVED******REMOVED*** Issue: 401 UNAUTHORIZED

**Check:**
1. `X-API-Key` header is present and valid
2. API key matches backend configuration

***REMOVED******REMOVED******REMOVED*** Issue: 429 RATE_LIMITED

**Check:**
1. Device/IP rate limits not exceeded
2. Wait for `Retry-After` seconds before retrying

***REMOVED******REMOVED******REMOVED*** Issue: Image not processed

**Check:**
1. Field name is exactly `itemImages[<itemId>]`
2. MIME type is `image/jpeg` or `image/png`
3. File size is under 2MB
4. Max 3 images per item, 10 total
