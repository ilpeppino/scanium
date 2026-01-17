# Scanium Subtypes: Source of Truth

## Primary Authoritative Source

**File**: `/home/user/scanium/core-domainpack/src/main/res/raw/home_resale_domain_pack.json`

This is THE single source of truth for all domain-pack categories (subtypes) used by the Scanium app at runtime.

## Schema Structure

```json
{
  "id": "home_resale",
  "name": "Home Resale",
  "version": "1.0.0",
  "description": "Domain pack for second-hand home goods classification",
  "categories": [
    {
      "id": "furniture_sofa",           // ← SUBTYPE ID (snake_case, {parent}_{item})
      "displayName": "Sofa",            // ← Human-readable name
      "parentId": "furniture",          // ← Hierarchical grouping (optional)
      "itemCategoryName": "HOME_GOOD",  // ← Maps to ItemCategory enum
      "prompts": [...],                 // ← CLIP classification prompts
      "priority": 10,                   // ← Tie-breaking priority
      "enabled": true
    }
  ],
  "attributes": [
    {
      "name": "brand",
      "type": "STRING|NUMBER|ENUM|BOOLEAN",
      "extractionMethod": "OCR|BARCODE|CLIP|CLOUD|HEURISTIC|NONE",
      "appliesToCategoryIds": ["electronics_laptop", ...],
      "required": false
    }
  ]
}
```

## Runtime Access

| Component | File | Role |
|-----------|------|------|
| **Repository** | `core-domainpack/.../LocalDomainPackRepository.kt` | Loads JSON, parses, caches |
| **Provider** | `core-domainpack/.../DomainPackProvider.kt` | Singleton accessor |
| **Engine** | `core-domainpack/.../BasicCategoryEngine.kt` | Selects best category via substring + priority |

**Flow**: ML Kit detection → `BasicCategoryEngine` → `domainCategoryId` → `ClassificationResult` → `ScannedItem`

## Related Data Models

- `DomainPack.kt` - Root config object
- `DomainCategory.kt` - Single subtype definition
- `DomainAttribute.kt` - Attribute schema
- `ClassificationResult.kt` - Runtime classification result (includes `domainCategoryId`)
- `ScannedItem.kt` - Persisted item (includes `domainCategoryId`, `attributes`)

## Test References

- `GoldenDataset.kt` - Defines `ExpectedSubtype` with `subtypeId` for validation
- `GoldenDatasetRegressionTest.kt` - Validates cloud classifier returns matching `domainCategoryId`

## Key Constraints

1. **ID Format**: Snake_case, typically `{parent}_{item}` (e.g., `furniture_sofa`, `electronics_laptop`)
2. **Hierarchical**: Optional `parentId` for grouping (e.g., "furniture" parent)
3. **ItemCategory Mapping**: Each category maps to an ItemCategory enum value (HOME_GOOD, ELECTRONICS, FASHION, PLANT, UNKNOWN)
4. **Attributes**: Brand extraction is OCR-based; applicable to select categories via `appliesToCategoryIds`

## Validation

- `LocalDomainPackRepository` validates:
  - All category IDs are unique
  - All `itemCategoryName` values match ItemCategory enum
  - No duplicate IDs across categories
