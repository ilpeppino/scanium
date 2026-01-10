# AI Assistant Item Signals Map

> **Purpose**: Document the complete flow of item signals from Android app to backend assistant endpoint. This map enables implementation of OCR/color/brand/size features with minimal ambiguity.

---

## A. Current Assistant Endpoints (Android → Backend)

### Endpoint Summary

| Endpoint | Method | Purpose | Source File |
|----------|--------|---------|-------------|
| `/v1/assist/warmup` | POST | Lightweight readiness probe before enabling assistant UI | `backend/src/modules/assistant/routes.ts:298` |
| `/v1/assist/chat` | POST | Main AI assistant chat | `backend/src/modules/assistant/routes.ts:260` |
| `/v1/assist/chat/status/:requestId` | GET | Staged request status | `backend/src/modules/assistant/routes.ts:729` |

### Sequence Diagram

```
┌─────────────────┐     ┌─────────────────────┐     ┌───────────────────────┐
│  Android App    │     │   AssistantRepo     │     │   Backend /v1/assist  │
│  (ViewModel)    │     │   (OkHttpClient)    │     │   /chat               │
└────────┬────────┘     └──────────┬──────────┘     └───────────┬───────────┘
         │                         │                            │
         │ sendMessage(text)       │                            │
         │ ──────────────────────► │                            │
         │                         │                            │
         │                         │  Build ItemContextSnapshot │
         │                         │  from ListingDraft         │
         │                         │                            │
         │                         │ POST /v1/assist/chat       │
         │                         │ Headers:                   │
         │                         │   X-API-Key                │
         │                         │   X-Scanium-Correlation-Id │
         │                         │   X-Client                 │
         │                         │   X-App-Version            │
         │                         │   X-Scanium-Signature      │
         │                         │   X-Scanium-Timestamp      │
         │                         │ ─────────────────────────► │
         │                         │                            │
         │                         │                            │ Validate API key
         │                         │                            │ Parse request schema
         │                         │                            │ Rate limit check
         │                         │                            │ Extract visual facts
         │                         │                            │   (if images attached)
         │                         │                            │ Call provider.respond()
         │                         │                            │
         │                         │     200 AssistantResponse  │
         │                         │ ◄───────────────────────── │
         │                         │                            │
         │    AssistantResponse    │                            │
         │ ◄────────────────────── │                            │
         │                         │                            │
```

### Two AssistantRepository Implementations

**CRITICAL**: There are TWO AssistantRepository classes in the codebase:

| Class | Location | Used By | Status |
|-------|----------|---------|--------|
| `com.scanium.app.assistant.AssistantRepository` | `androidApp/src/main/java/com/scanium/app/assistant/AssistantRepository.kt:55-266` | Basic assistant flow | Active but simpler |
| `com.scanium.app.selling.assistant.AssistantRepository` | `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt:50-629` | Selling assistant flow | **Primary - supports multipart images** |

The selling assistant repository (`com.scanium.app.selling.assistant`) is the more feature-rich implementation that supports:
- Image attachments via multipart form data
- ItemContextSnapshot with attributes
- Export profile context
- Assistant preferences

---

## B. Item Payload Schema Currently Sent

### Request Body Structure (JSON)

```json
{
  "items": [
    {
      "itemId": "uuid-string",
      "title": "Used Chair",
      "description": "Detected furniture item from Scanium scan.",
      "category": "Furniture",
      "confidence": 0.85,
      "attributes": [
        { "key": "category", "value": "Furniture", "confidence": 0.85 },
        { "key": "condition", "value": "Used", "confidence": 1.0 },
        { "key": "brand", "value": "IKEA", "confidence": 0.75 }
      ],
      "priceEstimate": 25.0,
      "photosCount": 3,
      "exportProfileId": "GENERIC"
    }
  ],
  "history": [
    {
      "role": "USER",
      "content": "What color is this?",
      "timestamp": 1704067200000,
      "itemContextIds": ["uuid-string"]
    }
  ],
  "message": "Can you suggest a better title?",
  "exportProfile": {
    "id": "GENERIC",
    "displayName": "Generic Marketplace"
  },
  "assistantPrefs": {
    "language": "EN",
    "tone": "FRIENDLY",
    "region": "US",
    "units": "IMPERIAL",
    "verbosity": "NORMAL"
  }
}
```

### Code References

**Android ItemContextSnapshotDto** (`androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt:572-596`):
```kotlin
private data class ItemContextSnapshotDto(
    val itemId: String,
    val title: String? = null,
    val category: String? = null,
    val confidence: Float? = null,
    val attributes: List<ItemAttributeSnapshotDto> = emptyList(),
    val priceEstimate: Double? = null,
    val photosCount: Int = 0,
    val exportProfileId: String? = null,
)
```

**Backend Request Schema** (`backend/src/modules/assistant/routes.ts:62-72`):
```typescript
const itemContextSchema = z.object({
  itemId: z.string(),
  title: z.string().optional().nullable(),
  description: z.string().optional().nullable(),
  category: z.string().optional().nullable(),
  confidence: z.number().optional().nullable(),
  attributes: z.array(itemAttributeSchema).optional(),
  priceEstimate: z.number().optional().nullable(),
  photosCount: z.number().int().optional(),
  exportProfileId: z.string().optional(),
});
```

### Multipart Request (with images)

When images are attached, the request uses `multipart/form-data`:
- `payload`: JSON string of the request body
- `itemImages[<itemId>]`: Binary image data (max 3 per item, max 2MB each)

**Image Field Pattern** (`backend/src/modules/assistant/routes.ts:117`):
```typescript
const ITEM_IMAGE_FIELD_PATTERN = /^itemImages\[(.+)\]$/;
```

---

## C. Item Signals Matrix

| Signal | Exists in Android? | Source | Storage | Confidence? | Sent to Assistant? | Notes |
|--------|-------------------|--------|---------|-------------|-------------------|-------|
| **Title** | ✅ Yes | `ListingDraftBuilder.buildTitle()` | `ListingDraft.title` (Room DB via `ListingDraftEntity`) | ✅ Yes (`DraftField.confidence`) | ✅ Yes | Auto-generated from label + category |
| **Description** | ✅ Yes | `ListingDraftBuilder.buildDescription()` | `ListingDraft.description` | ✅ Yes | ✅ Yes | Template-based generation |
| **Category** | ✅ Yes | `OnDeviceClassifier` / `CloudClassifier` | `ScannedItem.category`, `ListingDraft.fields[CATEGORY]` | ✅ Yes | ✅ Yes | ML Kit + Cloud classification |
| **Category ID (domain)** | ✅ Yes | `CloudClassifier` response | `ScannedItem.domainCategoryId` | ❌ No | ❌ **NO** | Fine-grained domain category NOT sent |
| **Condition** | ✅ Yes | `ListingDraftBuilder` (default "Used") | `ListingDraft.fields[CONDITION]` | ✅ Yes (always 1.0) | ✅ Yes | Hardcoded default |
| **Brand** | ⚠️ Partial | `ListingDraftBuilder` uses `labelText` as brand | `ListingDraft.fields[BRAND]` | ✅ Yes | ✅ Yes | Uses ML Kit label as brand fallback |
| **Model** | ❌ No | Not extracted | - | - | ❌ No | No extraction pipeline |
| **Color** | ❌ No | Not extracted on Android | - | - | ❌ No | Backend can extract from images |
| **Size** | ❌ No | Not extracted | - | - | ❌ No | No extraction pipeline |
| **Price Estimate** | ✅ Yes | `ScannedItem.priceRange` midpoint | `ListingDraft.price` | ✅ Yes | ✅ Yes | From category price ranges |
| **Photos Count** | ✅ Yes | `ListingDraft.photos.size` | `ListingDraft.photos` | ❌ N/A | ✅ Yes | Just count, not image data |
| **Photo Thumbnails** | ✅ Yes | `ScannedItem.thumbnailRef` | `ImageRef` (in-memory) | ❌ N/A | ⚠️ **NOT CURRENTLY** | See line 302-310 in AssistantViewModel |
| **OCR Text** | ⚠️ Partial | `DocumentTextRecognitionClient` | `ScannedItem.recognizedText` | ❌ No | ❌ **NO** | Only for DOCUMENT category, NOT sent |
| **Barcode Value** | ✅ Yes | Barcode scanner | `ScannedItem.barcodeValue` | ❌ No | ❌ **NO** | Not included in ItemContextSnapshot |
| **Label Text (ML Kit)** | ✅ Yes | `OnDeviceClassifier` | `ScannedItem.labelText` | ⚠️ Implicit | ❌ **NO** | Used for brand, but raw label not sent |
| **Bounding Box** | ✅ Yes | Object detection | `ScannedItem.boundingBox` | ❌ N/A | ❌ No | UI only |
| **Classification Status** | ✅ Yes | Cloud classifier | `ScannedItem.classificationStatus` | ❌ N/A | ❌ No | Internal tracking only |
| **Export Profile ID** | ✅ Yes | User selection | `ListingDraft.profile` | ❌ N/A | ✅ Yes | Marketplace context |
| **Quality Score** | ⚠️ Partial | Not populated | `ScannedItem.qualityScore` | ❌ N/A | ❌ No | Field exists but unused |

### Signal Sources Detail

**On-Device Classification** (`androidApp/src/main/java/com/scanium/app/ml/classification/OnDeviceClassifier.kt`):
- ML Kit Object Detection provides `labelText`
- Custom TensorFlow Lite model for `category`
- Confidence scores from both

**Cloud Classification** (`androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt:166-188`):
- POST to `{BASE_URL}/v1/classify`
- Returns `domainCategoryId` and `labels`
- Uses same `X-API-Key` header

**OCR/Document Text** (`androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt:21-217`):
- ML Kit TextRecognition with Latin script
- Only runs for DOCUMENT category items
- Text stored in `ScannedItem.recognizedText` (line 116)
- **NOT sent to assistant**

---

## D. Gaps Preventing "Full AI Assistance"

### 1. OCR Text Not Sent to Assistant

**Evidence**:
- `ScannedItem.recognizedText` exists (`shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/items/ScannedItem.kt:76`)
- `DocumentTextRecognitionClient` populates it (`androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt:116`)
- `ItemContextSnapshot` model has NO `recognizedText` field (`shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/assistant/AssistantModels.kt:205-215`)
- `ItemContextSnapshotBuilder.fromDraft()` does NOT map `recognizedText` (`shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/assistant/AssistantModels.kt:265-292`)

**Impact**: Backend cannot use OCR-detected brand names, model numbers, or specifications.

### 2. Color Not Extracted on Android

**Evidence**:
- No color extraction in ML pipelines
- `DraftFieldKey.COLOR` exists (`shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/listing/ListingDraft.kt:76`)
- Never populated by any builder
- Backend DOES have color extraction (`backend/src/modules/vision/types.ts:11-18`) but only from uploaded images

**Impact**: Color questions rely entirely on uploaded images being processed by backend vision.

### 3. Brand Extraction is Unreliable

**Evidence**:
- `ListingDraftBuilder.buildFields()` uses `labelText` as brand (`shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/listing/ListingDraft.kt:243-250`)
- ML Kit labels are often generic ("Chair", "Furniture") not brand names
- Backend has logo detection (`backend/src/modules/vision/types.ts:43-48`) but requires image upload

**Impact**: Brand is often wrong or missing; relying on generic ML Kit labels.

### 4. Images Not Currently Sent

**Evidence**:
- `AssistantViewModel.sendMessage()` explicitly passes empty list (`androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt:302-313`):
  ```kotlin
  imageAttachments = emptyList(), // Explicit: no images sent
  ```
- `CloudAssistantRepository.buildMultipartRequest()` supports images (`androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt:250-281`)
- Settings toggle exists: `allowAssistantImagesFlow` (referenced at line 277-278)

**Impact**: Backend vision extraction (`VisualFacts`) never runs because no images are sent.

### 5. Model/Size Never Extracted

**Evidence**:
- `DraftFieldKey.MODEL` exists (`shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/listing/ListingDraft.kt:75`)
- No extraction pipeline populates it
- Backend template system expects model (`backend/src/modules/assistant/template-packs.ts`)

**Impact**: Model numbers from labels/tags never captured.

### 6. Domain Category ID Not Sent

**Evidence**:
- `CloudClassifier` returns `domainCategoryId` (`androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt`)
- Stored in `ScannedItem.domainCategoryId` (`shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/items/ScannedItem.kt:86`)
- `ItemContextSnapshot` has no `domainCategoryId` field
- Only string `category` is sent (display name, not ID)

**Impact**: Backend cannot use domain-specific templates based on fine-grained categories.

---

## E. Minimal Changes Needed (High-Level)

### E1. Send OCR Text to Assistant

1. **Add field to shared model** (`shared/core-models/.../assistant/AssistantModels.kt`):
   - Add `ocrText: String?` to `ItemContextSnapshot`

2. **Update snapshot builder**:
   - Pass `recognizedText` from draft or item context

3. **Update Android DTO**:
   - Add `ocrText` to `ItemContextSnapshotDto`

4. **Backend already handles text** via `attributes` parsing

### E2. Enable Image Sending

1. **Wire up the toggle** in `AssistantViewModel`:
   - Check `settingsRepository.allowAssistantImagesFlow`
   - Build `ItemImageAttachment` list from draft photos

2. **Test multipart upload** path already exists:
   - `CloudAssistantRepository.buildMultipartRequest()` at line 250

### E3. Extract Color on Android (Optional)

1. **Add color extraction** using Palette API or ML Kit
2. **Populate** `DraftFieldKey.COLOR` in `ListingDraftBuilder`
3. **Or** rely on backend vision when images are sent

### E4. Improve Brand Detection

1. **Use OCR text** to find brand names (regex/NLP)
2. **Cross-reference** with domainpack brand lists
3. **Fall back** to logo detection from images

### E5. Add Domain Category ID

1. **Add field** `domainCategoryId: String?` to `ItemContextSnapshot`
2. **Map from** `ScannedItem.domainCategoryId` in builder
3. **Backend** can use for template selection

---

## F. Risks & Pitfalls

### F1. Multiple AssistantRepository Versions

**Risk**: Code changes may target the wrong repository class.

**Evidence**:
- `com.scanium.app.assistant.AssistantRepository` (basic, line 55)
- `com.scanium.app.selling.assistant.AssistantRepository` (full-featured, line 50)

**Mitigation**: Primary development should target `selling.assistant.AssistantRepository`.

### F2. API Key Provider Default Null

**Risk**: Missing API key causes silent 401 failures.

**Evidence**:
- `apiKeyProvider: () -> String? = { null }` (`androidApp/src/main/java/com/scanium/app/assistant/AssistantRepository.kt:56`)
- Warning logs exist but no crash (`androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt:301`)

**Mitigation**: Ensure DI provides `SecureApiKeyStore.getApiKey()` properly.

### F3. Conditional Headers

**Risk**: Headers may be missing in some conditions.

**Evidence** (`androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt:289-302`):
```kotlin
if (apiKey != null) {
    builder.header("X-API-Key", apiKey!!)
    // HMAC signature also depends on apiKey
} else {
    Log.w("ScaniumAuth", "AssistantRepo: apiKey is NULL - X-API-Key header will NOT be added!")
}
```

**Mitigation**: Fail fast if API key is null before making request.

### F4. Schema Drift Between Android/Backend

**Risk**: Android DTO and backend Zod schema may diverge.

**Evidence**:
- Android DTO has no `description` in `ItemContextSnapshotDto` (line 572)
- Backend schema DOES accept `description` (line 65)

**Mitigation**: Add shared schema validation or TypeScript types generation.

### F5. Images Explicitly Disabled

**Risk**: Image-dependent features won't work until toggle is wired.

**Evidence** (`androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt:302-313`):
```kotlin
// Note: Currently images are not attached in this implementation.
// When image support is added, check settingsRepository.allowAssistantImagesFlow
imageAttachments = emptyList(), // Explicit: no images sent
```

**Mitigation**: Implement image attachment before testing vision features.

### F6. Build Config URL Variations

**Risk**: Different base URLs for debug vs release builds.

**Evidence** (`androidApp/build.gradle.kts:169-188`):
- Debug: `SCANIUM_API_BASE_URL_DEBUG` fallback to `SCANIUM_API_BASE_URL`
- Release: `SCANIUM_API_BASE_URL`

**Mitigation**: Document URL configuration requirements clearly.

---

## G. Next PR-Ready Tasks

The following tasks are implementation-ready based on this analysis:

### Task 1: Enable Image Attachments
- [ ] Wire `allowAssistantImagesFlow` toggle in `AssistantViewModel`
- [ ] Build `ItemImageAttachment` list from `ListingDraft.photos`
- [ ] Test multipart upload end-to-end
- [ ] Verify backend vision extraction works

### Task 2: Add OCR Text to Payload
- [ ] Add `ocrText: String?` to `ItemContextSnapshot` (shared)
- [ ] Update `ItemContextSnapshotBuilder.fromDraft()` to map `recognizedText`
- [ ] Add field to Android DTO
- [ ] Update backend schema (if needed)
- [ ] Add test for OCR text in request

### Task 3: Add Domain Category ID
- [ ] Add `domainCategoryId: String?` to `ItemContextSnapshot`
- [ ] Map from `ScannedItem.domainCategoryId`
- [ ] Backend can use for template pack selection

### Task 4: Improve Brand Extraction
- [ ] Parse OCR text for brand patterns
- [ ] Cross-reference domainpack brand lists
- [ ] Update `ListingDraftBuilder` brand logic

### Task 5: Fix Android DTO Description Field
- [ ] Add `description` to `ItemContextSnapshotDto`
- [ ] Map from `ItemContextSnapshot.description`
- [ ] Backend already accepts it

---

## Appendix: Key File Locations

| Component | Path | Key Lines |
|-----------|------|-----------|
| **Android AssistantRepository (selling)** | `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt` | 50-629 |
| **Android AssistantRepository (basic)** | `androidApp/src/main/java/com/scanium/app/assistant/AssistantRepository.kt` | 55-266 |
| **Android AssistantViewModel** | `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt` | 195-1005 |
| **Shared ItemContextSnapshot** | `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/assistant/AssistantModels.kt` | 205-215 |
| **Shared ListingDraft** | `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/listing/ListingDraft.kt` | 19-261 |
| **Shared ScannedItem** | `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/items/ScannedItem.kt` | 66-128 |
| **SecureApiKeyStore** | `androidApp/src/main/java/com/scanium/app/config/SecureApiKeyStore.kt` | 9-69 |
| **Backend Routes** | `backend/src/modules/assistant/routes.ts` | 260-780 |
| **Backend Types** | `backend/src/modules/assistant/types.ts` | 1-300 |
| **Backend Vision Types** | `backend/src/modules/vision/types.ts` | 1-146 |
| **Backend Provider** | `backend/src/modules/assistant/provider.ts` | 69-138 |
| **OCR Client** | `androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt` | 21-217 |
| **Build Config** | `androidApp/build.gradle.kts` | 82-189 |

---

*Generated: 2025-01-02*
*Author: AI Assistant Analysis*
