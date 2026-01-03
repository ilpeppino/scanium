***REMOVED*** Item Editing Fields

This document describes the user-editable fields added to the Edit Items feature.

***REMOVED******REMOVED*** Overview

The EditItemsScreen now supports two additional user-editable fields per item:
- **Price** - User-specified price in EUR
- **Condition** - Physical condition of the item

***REMOVED******REMOVED*** Fields

***REMOVED******REMOVED******REMOVED*** Price (`userPriceCents`)
- **Storage**: Integer cents (e.g., 1250 = €12.50)
- **Validation**:
  - Empty allowed (null)
  - Negative values not allowed
  - Maximum value: €1,000,000
- **Display**: Currency formatted with € prefix (e.g., "€12.50")
- **Input**: Decimal numeric keyboard with comma/period support

***REMOVED******REMOVED******REMOVED*** Condition (`condition`)
- **Type**: Enum (`ItemCondition`)
- **Values**:
  | Enum Value | Display Name |
  |------------|--------------|
  | `NEW` | "New" |
  | `AS_GOOD_AS_NEW` | "As good as new" |
  | `USED` | "Used" |
  | `REFURBISHED` | "Refurbished" |
- **Storage**: String (enum name) in database
- **Default**: null (not set)

***REMOVED******REMOVED*** Data Model

***REMOVED******REMOVED******REMOVED*** ScannedItem (shared/core-models)
```kotlin
data class ScannedItem<FullImageUri>(
    // ... existing fields ...
    val userPriceCents: Long? = null,
    val condition: ItemCondition? = null,
)
```

***REMOVED******REMOVED******REMOVED*** ItemCondition Enum (shared/core-models)
```kotlin
enum class ItemCondition(
    val displayName: String,
    val description: String,
) {
    NEW("New", "Brand new, never used"),
    AS_GOOD_AS_NEW("As good as new", "Like new condition"),
    USED("Used", "Previously used with visible wear"),
    REFURBISHED("Refurbished", "Restored to working condition"),
}
```

***REMOVED******REMOVED*** Persistence

***REMOVED******REMOVED******REMOVED*** Database Columns (ScannedItemEntity)
- `userPriceCents` - INTEGER NULL
- `condition` - TEXT NULL (stores enum name)

***REMOVED******REMOVED******REMOVED*** Migration
- Version 3 → 4 adds both columns to `scanned_items` and `scanned_item_history` tables
- Uses ALTER TABLE ADD COLUMN (safe for existing data)

***REMOVED******REMOVED*** UI Components

***REMOVED******REMOVED******REMOVED*** EditItemsScreen
- Price input: OutlinedTextField with numeric keyboard and € prefix
- Condition dropdown: ExposedDropdownMenuBox with 4 options + "Not set"
- Compact layout: Fields arranged side-by-side in a Row

***REMOVED******REMOVED******REMOVED*** ItemsListScreen
- Price display: Shows user price if set, otherwise estimated range
- User price uses tertiary color to differentiate from estimates
- Condition badge: Colored chip next to price

***REMOVED******REMOVED*** Update Flow

1. User selects item(s) in ItemsListScreen
2. User taps Edit button → navigates to EditItemsScreen
3. User modifies price/condition (stored in draft state per item)
4. User taps OK → `ItemFieldUpdate` created with new values
5. `ItemsViewModel.updateItemsFields()` persists changes
6. ItemsListScreen recomposes with updated values

***REMOVED******REMOVED*** ItemFieldUpdate Structure

```kotlin
data class ItemFieldUpdate(
    val labelText: String? = null,
    val recognizedText: String? = null,
    val barcodeValue: String? = null,
    val userPriceCents: Long? = null,
    val clearUserPriceCents: Boolean = false,
    val condition: ItemCondition? = null,
    val clearCondition: Boolean = false,
)
```

The `clear*` flags allow explicitly setting fields to null (distinguishing from "keep existing value").

***REMOVED******REMOVED*** Files Changed

- `shared/core-models/.../items/ScannedItem.kt` - Added fields and `formattedUserPrice`
- `shared/core-models/.../items/ItemCondition.kt` - New enum
- `core-models/.../items/ScannedItem.kt` - Added typealias for ItemCondition
- `androidApp/.../items/persistence/ScannedItemEntity.kt` - Added columns and mappings
- `androidApp/.../items/persistence/ScannedItemHistoryEntity.kt` - Added columns
- `androidApp/.../items/persistence/ScannedItemDatabase.kt` - Version bump and migration
- `androidApp/.../items/persistence/ScannedItemRepository.kt` - Updated snapshot hash
- `androidApp/.../items/state/ItemsStateManager.kt` - Extended ItemFieldUpdate
- `androidApp/.../items/EditItemsScreen.kt` - Added Price/Condition UI
- `androidApp/.../items/ItemsListScreen.kt` - Added ConditionBadge and price display
