# Vision Insights Feature

Displays vision-derived attributes (colors, brands, logos, OCR text, label hints) in the assistant UI and allows users to apply them as item attributes.

## Overview

When the backend analyzes item images, it returns `suggestedAttributes` in the assistant response. These are displayed as interactive chips that users can tap to apply to their items.

## Feature Flag

```kotlin
// RemoteConfig.kt
data class FeatureFlags(
    val enableVisionInsights: Boolean = true, // Default ON for dev builds
)
```

## Attribute Key Mapping

The following table shows how vision sources map to attribute keys and their alternatives (used when there's a conflict):

| Source | Attribute Key | Display Name | Alternative Key |
|--------|---------------|--------------|-----------------|
| color  | color         | Color        | secondaryColor  |
| logo   | brand         | Brand        | brand2          |
| brand  | brand         | Brand        | brand2          |
| ocr    | model         | Model        | model2          |
| label  | category      | Category     | subcategory     |
| *      | (as-is)       | (capitalized)| key + "2"       |

## Confidence Tier Mapping

| Tier | Float Value | Chip Color |
|------|-------------|------------|
| HIGH | 0.9         | primaryContainer (green) |
| MED  | 0.6         | tertiaryContainer (amber) |
| LOW  | 0.3         | surfaceVariant (gray) |

## Conflict Handling

When a user taps a chip for an attribute they've already set:

1. A dialog appears showing "Current" vs "Detected" values
2. User can choose:
   - **Replace**: Overwrites the existing value
   - **Add as Alternative**: Creates a secondary attribute (e.g., `secondaryColor` instead of `color`)
   - **Cancel**: Dismisses without changes

User must explicitly confirm - no auto-apply.

## Telemetry

When a vision attribute is applied, a log event is recorded:

```
Vision attribute applied itemId=$itemId key=$targetKey value=$value source=$source confidence=$tier wasAlternative=$bool
```

## API Format

### Curl Example (Multipart with Images)

```bash
curl -X POST "https://your-backend/v1/assist/chat" \
  -H "Content-Type: multipart/form-data" \
  -H "X-API-Key: your-api-key" \
  -H "X-Scanium-Correlation-Id: test-123" \
  -F 'payload={"items":[{"itemId":"item-1","title":"Test Item","photosCount":1}],"message":"What can you tell from the photos?","history":[]}' \
  -F 'itemImages[item-1]=@/path/to/image.jpg'
```

### Expected Response (with Vision Data)

```json
{
  "reply": "I can see this is a Nike shoe in blue color.",
  "confidenceTier": "HIGH",
  "evidence": [
    { "type": "logo", "text": "Nike swoosh logo detected" },
    { "type": "color", "text": "Dominant color is blue" }
  ],
  "suggestedAttributes": [
    {
      "key": "brand",
      "value": "Nike",
      "confidence": "HIGH",
      "source": "logo"
    },
    {
      "key": "color",
      "value": "blue",
      "confidence": "HIGH",
      "source": "color"
    }
  ]
}
```

## Testing Instructions

1. Open an item with photos in the Items list
2. Tap "Get AI Help" to open the Assistant screen
3. Send a message like "What can you tell from the photos?"
4. Observe the "Vision Insights" section below the assistant response:
   - Chips grouped by type: Colors, Brands, Labels, OCR Text
   - Each chip shows `Key: value` with a `+` icon
   - Confidence indicated by chip color
5. Tap a chip to apply the attribute:
   - If no conflict: Attribute is applied, snackbar confirms
   - If conflict: Dialog appears with Replace/Add as Alternative/Cancel options
6. Verify the attribute appears in item details

## Files

- `AssistantViewModel.kt` - `applyVisionAttribute()`, `getExistingAttribute()`, `getAlternativeKey()`
- `AssistantScreen.kt` - Vision conflict dialog state and MessageBubble integration
- `VisionInsightsSection.kt` - Main UI component with grouped chips
- `VisionConflictDialog.kt` - Conflict resolution dialog
- `VisionInsightsTest.kt` - Unit tests
