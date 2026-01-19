***REMOVED*** eBay Selling Flow

***REMOVED******REMOVED*** Overview

The eBay selling flow allows users to list scanned items on eBay (currently mocked for development).

***REMOVED******REMOVED*** Listing Title Generation

Listing titles are generated from scanned items using a **single source of truth**:
`ListingTitleBuilder`.

***REMOVED******REMOVED******REMOVED*** Title Selection Priority

The title builder uses the following priority to select the best available name:

1. **Label Text** (`item.labelText`) - Most specific
    - From cloud classification (e.g., "Decor / Wall Art", "Chair", "Sofa / Couch")
    - From on-device ML Kit classification
    - This is the preferred source as it's most item-specific

2. **Category Display Name** (`item.category.displayName`) - Generic fallback
    - Used when labelText is null or blank
    - Examples: "Home Good", "Electronics", "Fashion"

3. **"Item"** - Ultimate fallback
    - Only used if all else fails (should never happen in practice)

***REMOVED******REMOVED******REMOVED*** Title Formatting

All titles are formatted with:

- **"Used" prefix** - indicates second-hand marketplace condition
- **Capitalized first character** - proper title casing
- **80 character max** - eBay title length limit
- **Whitespace trimmed** - clean formatting

***REMOVED******REMOVED******REMOVED*** Examples

| Input (labelText)  | Input (category) | Output Title            |
|--------------------|------------------|-------------------------|
| "Decor / Wall Art" | HOME_GOOD        | "Used Decor / Wall Art" |
| "Chair"            | HOME_GOOD        | "Used Chair"            |
| "Laptop"           | ELECTRONICS      | "Used Laptop"           |
| "mug"              | HOME_GOOD        | "Used Mug"              |
| null               | FASHION          | "Used Fashion"          |
| "" (blank)         | HOME_GOOD        | "Used Home Good"        |

***REMOVED******REMOVED*** Data Flow

```
ScannedItem (with labelText from classification)
    ↓
ListingDraftMapper.fromScannedItem()
    ↓
ListingTitleBuilder.buildTitle(item)
    ↓
ListingDraft (with computed title)
    ↓
MockEbayApi.createListing(draft)
    ↓
Listing (echoes draft.title)
```

***REMOVED******REMOVED*** Key Files

- `ListingTitleBuilder.kt` - Core title generation logic (pure, testable)
- `ListingDraftMapper.kt` - Maps ScannedItem → ListingDraft
- `ListingViewModel.kt` - Orchestrates the sell flow
- `MockEbayApi.kt` - Mocked eBay API (echoes title from draft)

***REMOVED******REMOVED*** Testing

Title generation is comprehensively tested:

- `ListingTitleBuilderTest.kt` - Unit tests for title builder
- `ListingDraftMapperTest.kt` - Integration tests for draft mapping

Run tests:

```bash
./gradlew test --tests "com.scanium.app.selling.util.*"
```

***REMOVED******REMOVED*** Debugging

When selling an item, check logs for title generation:

```
ListingDraftMapper: Creating listing draft for item <id>:
ListingDraftMapper:   - labelText: <label>
ListingDraftMapper:   - category: <category>
ListingDraftMapper:   - generated title: <title>
```

***REMOVED******REMOVED*** Common Issues

***REMOVED******REMOVED******REMOVED*** "Used table" appears for all items

**Root Cause**: labelText is null/blank, falling back to category display name.

**Solution**: Ensure cloud classification is working and `enhancedLabelText` is populated in
AggregatedItem.

**Debug Steps**:

1. Check if cloud classification is enabled (CLOUD mode)
2. Verify backend returns proper `label` field in response
3. Check ItemsViewModel applies `result.label` to aggregated item
4. Verify AggregatedItem.toScannedItem() includes `enhancedLabelText`

***REMOVED******REMOVED******REMOVED*** Title shows generic category instead of specific item

**Root Cause**: Same as above - labelText not populated.

**Solution**: The flow works correctly IF cloud classification returns a label. Check:

1. Backend domain pack has `label` field for each category
2. CloudClassifier parses response correctly
3. ItemsViewModel applies enhanced classification

***REMOVED******REMOVED*** Future Enhancements

- Add brand information to title (e.g., "Used Samsung Monitor")
- Support custom condition prefixes (e.g., "New", "Like New")
- Multilingual title support
- Category-specific title templates
