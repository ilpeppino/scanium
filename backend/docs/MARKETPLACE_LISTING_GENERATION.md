***REMOVED*** Marketplace Listing Generation (PR4)

This document explains the marketplace-ready title and description generation feature in the `/v1/assist/chat` endpoint.

***REMOVED******REMOVED*** Overview

The assistant endpoint generates marketplace-ready listings using:
1. **User-provided attributes** (authoritative - used as-is)
2. **Detected attributes** from vision analysis (with confidence levels)
3. **Visual evidence** from images (OCR, logos, colors)

***REMOVED******REMOVED*** Key Concept: User Overrides

When a user manually edits an attribute (brand, model, color, condition), that value becomes **authoritative**. The system will:

- Mark it with `[USER]` tag in the prompt
- Treat it as `HIGH` confidence regardless of the original confidence
- Use it exactly as provided without questioning
- **NOT** override it with vision-detected alternatives

This ensures the final listing reflects the seller's knowledge of the item.

***REMOVED******REMOVED*** Request Payload

***REMOVED******REMOVED******REMOVED*** Item Context Snapshot

```json
{
  "items": [{
    "itemId": "item-123",
    "title": "Dell Laptop",
    "category": "Electronics",
    "attributes": [
      {
        "key": "brand",
        "value": "Dell",
        "confidence": 1.0,
        "source": "USER"
      },
      {
        "key": "model",
        "value": "XPS 15",
        "confidence": 1.0,
        "source": "USER"
      },
      {
        "key": "color",
        "value": "Silver",
        "confidence": 0.8,
        "source": "DETECTED"
      },
      {
        "key": "condition",
        "value": "Like New",
        "confidence": 1.0,
        "source": "USER"
      }
    ],
    "priceEstimate": 850,
    "photosCount": 3
  }],
  "message": "Generate a listing for this laptop",
  "assistantPrefs": {
    "language": "EN",
    "tone": "PROFESSIONAL",
    "region": "US"
  }
}
```

***REMOVED******REMOVED******REMOVED*** Attribute Source Values

| Source | Description | Handling |
|--------|-------------|----------|
| `USER` | User manually entered/edited | Authoritative, HIGH confidence, use as-is |
| `DETECTED` | ML/vision system detected | Use with confidence tier (HIGH/MED/LOW) |
| `DEFAULT` | System default value | Use with context |
| `UNKNOWN` | Source not specified | Treat as DETECTED for safety |

***REMOVED******REMOVED*** Response Format

The response includes structured `suggestedDraftUpdates` with title and description:

```json
{
  "reply": "**Suggested Title:**\nDell XPS 15 Laptop - Like New, Silver...",
  "actions": [
    {
      "type": "APPLY_DRAFT_UPDATE",
      "payload": {
        "itemId": "item-123",
        "title": "Dell XPS 15 Laptop - Like New, Silver, Professional Grade",
        "description": "Premium Dell XPS 15 laptop in like-new condition.\n\n• Brand: Dell\n• Model: XPS 15\n• Color: Silver\n• Condition: Like New\n\nPerfect for professionals and power users."
      },
      "label": "Apply suggested listing",
      "requiresConfirmation": false
    },
    {
      "type": "COPY_TEXT",
      "payload": {
        "label": "Title",
        "text": "Dell XPS 15 Laptop - Like New, Silver, Professional Grade"
      },
      "label": "Copy title"
    },
    {
      "type": "COPY_TEXT",
      "payload": {
        "label": "Description",
        "text": "Premium Dell XPS 15..."
      },
      "label": "Copy description"
    }
  ],
  "suggestedDraftUpdates": [
    {
      "field": "title",
      "value": "Dell XPS 15 Laptop - Like New, Silver, Professional Grade",
      "confidence": "HIGH",
      "requiresConfirmation": false
    },
    {
      "field": "description",
      "value": "Premium Dell XPS 15 laptop...",
      "confidence": "HIGH",
      "requiresConfirmation": false
    }
  ],
  "confidenceTier": "HIGH",
  "evidence": [
    { "type": "logo", "text": "Brand: Dell (detected from logo)" }
  ]
}
```

***REMOVED******REMOVED*** Title Generation Rules

Titles are generated following these rules:

- **Maximum 80 characters**
- **Format:** `[Brand] [Model/Type] - [Key Feature/Condition]`
- **Front-load keywords** for search visibility
- Include brand (if known) + model/type + key differentiator

**Examples:**
- `Dell XPS 15 Laptop - 16GB RAM, Excellent Condition` (55 chars)
- `IKEA KALLAX Bookshelf - White, 4x4 Cube Storage` (47 chars)
- `Sony WH-1000XM4 Headphones - Wireless, Noise Canceling` (54 chars)

***REMOVED******REMOVED*** Description Generation Rules

Descriptions follow a structured format:

1. **Overview:** 1-2 sentence intro
2. **Key Features:** Bullet points (•)
3. **Condition:** Detailed condition notes
4. **Additional Notes:** Shipping, pickup, etc.

**Example:**
```
Premium Dell XPS 15 laptop in like-new condition. Powerful performance for professionals.

• Brand: Dell
• Model: XPS 15
• Color: Silver
• Condition: Like New
• RAM: 16GB (if provided)

Includes original charger. Local pickup preferred, shipping available.
```

***REMOVED******REMOVED*** Confidence Levels

| Confidence | Meaning | Handling |
|------------|---------|----------|
| `HIGH` | User-provided OR strong visual evidence | Use with confidence |
| `MED` | Moderate evidence | Include "Please verify" warning |
| `LOW` | Insufficient evidence | Mark as "Possibly" or omit |

***REMOVED******REMOVED*** Prompt Template

The system prompt instructs the LLM to:

1. **Treat `[USER]` attributes as authoritative** - use exactly as given
2. **Apply confidence tiers** to `[DETECTED]` attributes
3. **Never hallucinate** specifications not provided
4. **Generate marketplace-ready** titles and descriptions

***REMOVED******REMOVED******REMOVED*** Example Prompt (User Section)

```
**User-provided attributes (use as-is):**
- brand: "Dell" [USER] [HIGH]
- model: "XPS 15" [USER] [HIGH]
- condition: "Like New" [USER] [HIGH]

Detected attributes:
- color: "Silver" [DETECTED] [HIGH] (from: color extraction)
```

***REMOVED******REMOVED*** Testing

***REMOVED******REMOVED******REMOVED*** cURL Example

```bash
curl -X POST "https://api.scanium.app/v1/assist/chat" \
  -H "X-API-Key: YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [{
      "itemId": "test-1",
      "title": "Laptop",
      "category": "Electronics",
      "attributes": [
        {"key": "brand", "value": "Dell", "source": "USER"},
        {"key": "condition", "value": "Like New", "source": "USER"}
      ]
    }],
    "message": "Generate a marketplace listing"
  }'
```

***REMOVED******REMOVED******REMOVED*** Unit Tests

Tests are located in `backend/src/modules/assistant/prompts/listing-generation.test.ts`:

```bash
cd backend && npm test -- --run src/modules/assistant/prompts/listing-generation.test.ts
```

Key test scenarios:
- User-provided attributes marked with `[USER]` tag
- User attributes treated as HIGH confidence
- User attributes shown before detected attributes
- Vision attributes don't override user-provided ones
- Condition included when user-edited

***REMOVED******REMOVED*** Android Integration

The Android app sends attributes with source via `ItemContextSnapshotBuilder`:

```kotlin
// When user edits a field
draft = draft.copy(
    fields = draft.fields + (DraftFieldKey.BRAND to DraftField(
        value = "Dell",
        confidence = 1f,
        source = DraftProvenance.USER_EDITED  // Maps to AttributeSource.USER
    ))
)
```

`ItemContextSnapshotBuilder.fromDraft()` converts `DraftProvenance` to `AttributeSource`:
- `USER_EDITED` → `USER` (authoritative)
- `DETECTED` → `DETECTED`
- `DEFAULT` → `DEFAULT`
- `UNKNOWN` → `UNKNOWN`

***REMOVED******REMOVED*** Configuration

No additional configuration is required. The feature is enabled by default when using the assistant endpoint.

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** User attributes not being treated as authoritative

**Check:** Ensure `source: "USER"` is set in the attribute
**Verify:** Look for `[USER]` tag in server logs

***REMOVED******REMOVED******REMOVED*** Low confidence despite user input

**Check:** The `source` field must be `"USER"`, not just high `confidence` value
**Fix:** Update client to set source when user edits a field

***REMOVED******REMOVED******REMOVED*** Vision attributes overriding user values

**Check:** This should not happen if `source: "USER"` is set correctly
**Debug:** Enable `ASSISTANT_LOG_CONTENT=true` to see the full prompt
