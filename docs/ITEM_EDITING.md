***REMOVED*** Item Editing Feature

This document describes the architecture and implementation of the item editing feature in Scanium.

***REMOVED******REMOVED*** Overview

The Edit feature allows users to modify properties of scanned items directly from the Items List screen. Users can select one or more items and edit them using a swipe-based interface.

***REMOVED******REMOVED*** Architecture

***REMOVED******REMOVED******REMOVED*** Data Flow

```
ItemsListScreen (selection)
        ↓
   Edit Button tap
        ↓
EditItemsScreen (draft state)
        ↓
   OK / Cancel
        ↓
ItemsViewModel.updateItems()
        ↓
ItemsStateManager._items
        ↓
ScannedItemStore.upsertAll()
        ↓
Room Database (scanned_items.db)
```

***REMOVED******REMOVED******REMOVED*** Storage Layer

Items are persisted using a multi-layer architecture:

1. **In-Memory State**: `ItemsStateManager._items: StateFlow<List<ScannedItem>>`
2. **Aggregation Layer**: `ItemAggregator` manages similarity-based deduplication
3. **Persistence**: `ScannedItemStore` interface with Room implementation
4. **Database**: Room database (`scanned_items.db`) with `ScannedItemEntity`

***REMOVED******REMOVED******REMOVED*** Update Path

Edits follow the established pattern used by `updateListingStatus()`:

1. Map over `_items.value` to create updated list
2. Update `_items` StateFlow
3. Persist via `itemsStore.upsertAll()`

***REMOVED******REMOVED*** Editable Fields

The following fields can be edited by users:

| Field | Type | Description |
|-------|------|-------------|
| `labelText` | String? | User-facing display name/label |
| `recognizedText` | String? | Extracted text (documents) |
| `barcodeValue` | String? | Barcode/QR code value |

Note: Fields like `category`, `priceRange`, `confidence` are ML-derived and not user-editable.

***REMOVED******REMOVED*** UI Components

***REMOVED******REMOVED******REMOVED*** ItemsListScreen Changes

- New "Edit" FAB button positioned at `Alignment.BottomCenter`
- Shown only when `selectionMode && selectedIds.isNotEmpty()`
- Uses `Icons.Default.Edit` icon

***REMOVED******REMOVED******REMOVED*** EditItemsScreen

- **Route**: `items/edit?ids={itemIds}`
- **Navigation**: Uses comma-separated item IDs in URL parameter
- **Layout**:
  - Top: Title "Edit Items" with count indicator (e.g., "1/3")
  - Body: HorizontalPager for swipe navigation between items
  - Each page: Item thumbnail + editable TextFields
  - Bottom: Cancel and OK buttons (sticky)

***REMOVED******REMOVED******REMOVED*** Draft State Management

- Local `Map<String, EditableDraft>` holds unsaved changes
- Drafts are created on screen entry, initialized from current item values
- Swipe navigation preserves draft state across pages
- Cancel discards all drafts
- OK applies all drafts atomically

***REMOVED******REMOVED*** Navigation

***REMOVED******REMOVED******REMOVED*** Route Definition

```kotlin
const val EDIT_ITEMS = "items/edit"

// Usage
"${Routes.EDIT_ITEMS}?ids={ids}"
```

***REMOVED******REMOVED******REMOVED*** Arguments

| Argument | Type | Description |
|----------|------|-------------|
| `ids` | String | Comma-separated list of item IDs |

***REMOVED******REMOVED*** Implementation Notes

***REMOVED******REMOVED******REMOVED*** Thread Safety

Updates go through `ItemsStateManager` which handles threading:
- State modifications on `workerDispatcher`
- UI updates on `mainDispatcher`

***REMOVED******REMOVED******REMOVED*** Stable IDs

Items already have stable UUIDs generated via `generateRandomId()` at creation time. No migration needed.

***REMOVED******REMOVED******REMOVED*** Error Handling

- Empty field validation: allowed (fields are nullable)
- Network errors: N/A (local persistence only)
- Concurrent modification: Last write wins (acceptable for single-user app)

***REMOVED******REMOVED*** Testing

***REMOVED******REMOVED******REMOVED*** Unit Tests

- `ItemsViewModelTest`: verify `updateItems()` updates state and persists
- `EditItemsViewModelTest`: verify draft management and apply/discard logic

***REMOVED******REMOVED******REMOVED*** Manual Test Cases

1. Select 1 item → Edit → change label → OK → verify list updates
2. Select 3 items → Edit → swipe through all → edit each → OK → verify all update
3. Edit → make changes → Cancel → verify no changes
4. Edit → rotate device → verify drafts preserved
5. No selection → verify Edit button hidden
