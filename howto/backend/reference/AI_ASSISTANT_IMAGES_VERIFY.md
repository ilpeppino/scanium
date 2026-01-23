# AI Assistant Images Verification Guide

This document describes how to verify that image attachments are correctly sent to the assistant
endpoint.

---

## Prerequisites

1. Backend is running and accessible
2. Android app is built with a valid `SCANIUM_API_BASE_URL`
3. Valid API key is configured

---

## Enabling Image Attachments

### Step 1: Enable the Toggle

1. Open the Scanium app
2. Go to **Settings** (gear icon)
3. Scroll to **Privacy** section
4. Enable **"Allow AI Assistant to analyze photos"**

This toggle controls whether images are sent to the backend for visual analysis.

### Step 2: Prepare Test Item

1. Scan an item with the camera
2. Ensure the item has at least one photo captured
3. Navigate to the Assistant screen for that item

---

## Manual Verification Steps

### Android Logs (Logcat)

Filter for these tags to see image attachment activity:

```bash
adb logcat -s ImageAttachmentBuilder ScaniumAssist Assistant
```

#### Expected Logs When Toggle is ON:

```
D/ImageAttachmentBuilder: Processing item=abc123 photos=2 (using first 3)
D/ImageAttachmentBuilder: Processing bytes image: itemId=abc123 index=0 size=1024 mimeType=image/jpeg
I/ImageAttachmentBuilder: Built 2 attachments, totalBytes=2048, itemCounts={abc123=2}, skipped=0, recompressed=0
I/Assistant: Assist request correlationId=assist_xxx items=1 messageLength=20 imagesEnabled=true attachments=2 totalBytes=2048 itemImageCounts={abc123=2}
I/ScaniumAssist: Multipart request: correlationId=assist_xxx imageCount=2 totalBytes=2048 itemImageCounts={abc123=2}
```

#### Expected Logs When Toggle is OFF:

```
D/ImageAttachmentBuilder: Images disabled by toggle, returning empty attachments
I/Assistant: Assist request correlationId=assist_xxx items=1 messageLength=20 imagesEnabled=false attachments=0 totalBytes=0 itemImageCounts={}
```

### Backend Logs

When images are received, the backend should log:

```
INFO  assistant: Multipart request received with 2 images
INFO  vision: Processing 2 images for item abc123
INFO  vision: Extracted visual facts: colors=[Black, Silver], ocrSnippets=[...], labelHints=[...]
```

If no images are sent, the request will be `application/json` (not multipart):

```
INFO  assistant: JSON request received (no images)
```

---

## Curl Test (Backend Only)

You can test the backend's multipart handling directly:

```bash
# Create a test image
echo "test image data" > /tmp/test.jpg

# Send multipart request
curl -X POST "http://localhost:3000/v1/assist/chat" \
  -H "X-API-Key: your-api-key" \
  -H "X-Scanium-Correlation-Id: test-123" \
  -F 'payload={"items":[{"itemId":"item-1","title":"Test"}],"message":"What color is this?"}' \
  -F 'itemImages[item-1]=@/tmp/test.jpg;type=image/jpeg'
```

Expected response includes visual evidence if vision extraction ran:

```json
{
  "reply": "...",
  "confidenceTier": "MED",
  "evidence": [
    { "type": "color", "text": "The dominant color appears to be black." }
  ]
}
```

---

## Unit Test Verification

Run the image attachment tests:

```bash
cd androidApp
./gradlew testDevDebugUnitTest --tests "*ImageAttachmentBuilderTest*"
./gradlew testDevDebugUnitTest --tests "*AssistantViewModelTest*sendMessage_withImages*"
./gradlew testDevDebugUnitTest --tests "*AssistantRepositoryMultipartTest*"
```

Expected output:

- All tests pass
- No image content logged (only metadata: counts, sizes)

---

## Verification Checklist

| Step                           | Expected Result                               |
|--------------------------------|-----------------------------------------------|
| Toggle OFF, item with photos   | No attachments sent, JSON request             |
| Toggle ON, item with photos    | Attachments sent, multipart request           |
| Toggle ON, item without photos | No attachments sent, JSON request             |
| Toggle ON, item with 5+ photos | Only 3 attachments sent (MAX_IMAGES_PER_ITEM) |
| Image > 2MB                    | Recompressed or skipped with warning          |
| Backend receives multipart     | Vision extraction runs, evidence returned     |

---

## Troubleshooting

### No Images Sent Despite Toggle ON

1. Check draft has photos: `ScaniumLog.d` should show "photos=N"
2. Verify photos have `ImageRef.Bytes` (not `ImageRef.CacheKey`)
3. Check for skipped images in logs

### Multipart Request Fails

1. Verify backend accepts multipart: check CORS and content-type handling
2. Check image sizes don't exceed 2MB limit
3. Verify field naming matches pattern: `itemImages[<itemId>]`

### Vision Extraction Not Running

1. Confirm backend vision is enabled: `VISION_ENABLED=true`
2. Check Google Cloud Vision API credentials
3. Review backend logs for vision errors

---

## Key Files

| File                                                 | Purpose                              |
|------------------------------------------------------|--------------------------------------|
| `androidApp/.../assistant/ImageAttachmentBuilder.kt` | Builds attachments from draft photos |
| `androidApp/.../assistant/AssistantViewModel.kt`     | Reads toggle, passes attachments     |
| `androidApp/.../assistant/AssistantRepository.kt`    | Builds multipart request             |
| `androidApp/.../data/SettingsRepository.kt`          | `allowAssistantImagesFlow` toggle    |
| `backend/src/modules/assistant/routes.ts`            | Parses multipart, extracts images    |
| `backend/src/modules/vision/`                        | Vision extraction pipeline           |

---

*Last updated: 2025-01-02*
