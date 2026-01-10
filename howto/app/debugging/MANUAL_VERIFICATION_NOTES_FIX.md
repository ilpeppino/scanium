# Manual Verification Checklist: AI-Generated Notes Persistence Fix

## Bug Description
AI-generated Notes were not persisted when reopening an item. The Notes field was initialized from a computed property (`displayLabel`) and saved to the wrong database field (`labelText`).

## Root Cause
1. **EditItemScreenV3.kt:116** - `notesField` initialized from `item?.displayLabel` (a computed property, not stored)
2. **EditItemScreenV3.kt:648** - `notesField` saved to `labelText` (ML classification field, not user notes)

## Fix Applied
1. Changed initialization to read from `item?.attributesSummaryText` (proper persisted notes field)
2. Changed save to use `updateSummaryText()` which persists to `attributesSummaryText`

## Manual Testing Steps

### Test 1: AI Assistant Notes Persistence
1. Open Scanium app
2. Navigate to any item in the list
3. Tap Edit → AI Generate button
4. Wait for AI to generate text (title, description, bullets)
5. Verify Notes field is populated with AI-generated content
6. Tap Save button
7. Navigate back to item list
8. **VERIFY**: Reopen the same item
9. **EXPECTED**: AI-generated Notes should still be visible in the Notes field
10. **ACTUAL (before fix)**: Notes field was empty
11. **ACTUAL (after fix)**: Notes field contains AI-generated text

### Test 2: Manual Notes Persistence
1. Open any item → Edit
2. Manually type text in the Notes field (e.g., "Test notes for verification")
3. Tap Save
4. Navigate back
5. Reopen the item
6. **EXPECTED**: Manual notes should persist
7. **ACTUAL**: Notes field shows "Test notes for verification"

### Test 3: App Restart Persistence
1. Open item → Edit → Add notes
2. Save and close app completely (swipe away from recents)
3. Relaunch app
4. Navigate to the same item
5. **EXPECTED**: Notes persist through app restart
6. **ACTUAL**: Notes field contains the saved text

### Test 4: Empty Notes Handling
1. Open item → Edit → Clear any existing notes
2. Save with empty Notes field
3. Reopen item
4. **EXPECTED**: Notes remain empty (no phantom text appears)
5. **ACTUAL**: Notes field is empty

## Files Changed
- `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt`
  - Line 116: Changed `notesField` initialization from `displayLabel` to `attributesSummaryText`
  - Line 647-653: Changed save from `updateItemFields(labelText=...)` to `updateSummaryText(...)`

- `androidApp/src/test/java/com/scanium/app/items/state/ItemsStateManagerTest.kt`
  - Added `whenSummaryTextUpdated_thenItemIsPersistedWithNewSummaryText()` test

## Database Schema
Notes are now correctly persisted in:
- **Field**: `attributesSummaryText` (Room: `scanned_items` table)
- **Flag**: `summaryTextUserEdited` (tracks if user manually edited)
- **Migration**: Already exists (v7 schema), no migration needed

## Backend Impact
None. Backend assistant API unchanged. Fix is purely Android data flow.
