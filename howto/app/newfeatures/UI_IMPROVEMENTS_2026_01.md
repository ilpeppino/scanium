# UI Improvements - January 2026

This document describes three UI-only improvements implemented in January 2026.

## Features

### 1. Brand-First Item Title

**Location:** Items List screen

When displaying items in the list, brand is now shown as the primary title when available:
- First checks `item.attributes["brand"]` (user-editable)
- Falls back to `visionAttributes.primaryBrand` (ML-detected)
- Falls back to CustomerSafeCopyFormatter title
- Finally falls back to existing `displayLabel`

**Benefit:** Users can quickly identify items by brand, which is often the most recognizable identifier.

### 2. Item Info Chips Toggle

**Location:** Settings > General > Preferences

A new setting "Show item details" controls visibility of condition badges and attribute chips in the Items List.

- **ON (default):** Shows condition badge and attribute chips on item cards
- **OFF:** Hides chips for a cleaner, more compact view

Note: Diagnostic badges (dev flavor only) remain visible regardless of this setting.

### 3. Price Field Placement

**Location:** Edit Item screen

The Price field has been moved from near the bottom to the second position (after Brand), making it more accessible without scrolling.

**New field order:**
1. Brand
2. Price
3. Price Estimate Card
4. Product Type
5. Model
6. Color
7. Size
8. Material
9. Condition
10. Notes

## Files Modified

| File | Changes |
|------|---------|
| `ItemsListContent.kt` | Brand title logic, chips conditional rendering |
| `ItemEditSections.kt` | Reordered fields |
| `SettingsKeys.kt` | New preference key |
| `GeneralSettings.kt` | Flow + setter for chips setting |
| `SettingsRepository.kt` | Facade methods |
| `SettingsViewModel.kt` | StateFlow + setter |
| `SettingsGeneralScreen.kt` | Toggle UI |
| `ItemsListScreen.kt` | Pass setting to content |
| `strings.xml` | New localized strings |

## Testing Checklist

### Brand-First Title
- [ ] Scan item with recognizable brand logo
- [ ] Verify brand appears as row title
- [ ] Scan generic item without brand
- [ ] Verify fallback to category/type
- [ ] Edit item to add/change brand, verify title updates

### Chips Toggle
- [ ] Open Settings > General > Preferences
- [ ] Toggle "Show item details" OFF
- [ ] Navigate to Items List
- [ ] Verify condition badges hidden
- [ ] Verify attribute chips hidden
- [ ] Toggle ON and verify chips reappear
- [ ] (Dev only) Verify diagnostic badges still visible when OFF
- [ ] Kill and restart app, verify setting persists

### Price Placement
- [ ] Open any item for editing
- [ ] Verify Price field is second (after Brand)
- [ ] Verify Price Estimate card appears below Price
- [ ] Test keyboard navigation flows correctly
- [ ] Verify save/cancel still work
- [ ] Test on different screen sizes

### Build Verification
```bash
./gradlew :androidApp:assembleDevDebug
./gradlew :androidApp:assembleProdDebug
./gradlew :androidApp:assembleBetaDebug
./gradlew test
./gradlew lint
```
