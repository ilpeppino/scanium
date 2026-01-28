# Incident Plan: Catalog Autocomplete Wiring for Brand and Product/Type Fields

**Status**: Planning
**Date**: 2026-01-28
**Scope**: Wire Brand and Product/Type fields to JSON catalogs with editable dropdown autocomplete

---

## 1. Current State Audit

### UI Components

| Component | File | Lines | Current Implementation |
|-----------|------|-------|----------------------|
| Brand Field | `ItemEditSections.kt` | 253-269 | `LabeledTextField` (free-text) |
| Product Type Field | `ItemEditSections.kt` | 304-313 | `LabeledTextField` (free-text) |
| State Holder | `ItemEditState.kt` | 40-62 | `brandField: String`, `productTypeField: String` |

**Key Files:**
- `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt` - Main edit screen
- `androidApp/src/main/java/com/scanium/app/items/edit/ItemEditSections.kt` - Form fields
- `androidApp/src/main/java/com/scanium/app/items/edit/ItemEditState.kt` - UI state
- `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt` - Persistence

### Data Storage

Values stored in `ScannedItem.attributes: Map<String, ItemAttribute>`:
- `attributes["brand"]` → `ItemAttribute(value, confidence, source)`
- `attributes["itemType"]` → `ItemAttribute(value, confidence, source)`

Persisted via:
```kotlin
itemsViewModel.updateItemAttribute(itemId, "brand", ItemAttribute(value, confidence=1.0f, source="USER"))
```

### Existing Infrastructure

| Pattern | Location | Reusable For |
|---------|----------|--------------|
| `ExposedDropdownMenuBox` | `ItemEditSections.kt:454-528` | Dropdown UI pattern |
| `AttributeEditDialog` suggestion chips | `AttributeEditDialog.kt` | Suggestion display |
| `VariantSchemaRepository` | `pricing/VariantSchemaRepository.kt` | API-fetched options pattern |
| `ItemAttributeLocalizer` | `items/ItemAttributeLocalizer.kt` | Localization pattern |

**No existing catalog loader** - Domain Pack system handles product categories, not brand/type catalogs.

---

## 2. Target UX Behavior

### Editable Dropdown Autocomplete

1. **Typing triggers suggestions** - Dropdown appears as user types
2. **Contains-anywhere matching** - Not prefix-only
3. **Ranking priority**:
   - Exact match (case-insensitive)
   - Prefix match
   - Word-boundary match (query matches start of any word)
   - Contains match (anywhere in string)
   - Alias match
4. **Within same match type**: Sort by popularity (descending) → alphabetical
5. **Result limit**: Top 20
6. **Debounce**: 200ms
7. **Custom values**: If user types value not in catalog, allow it (store with `custom:` prefix ID)
8. **Selection**: Tapping suggestion fills field and stores catalog ID

---

## 3. Catalog Packaging Plan

### Asset Paths

```
androidApp/src/main/assets/catalog/
├── brands.json           # Brand catalog
├── product_types.json    # Product type catalog
└── manifest.json         # Version info for update detection
```

### JSON Schema

```json
// brands.json
{
  "version": 1,
  "lastUpdated": "2026-01-28T00:00:00Z",
  "entries": [
    {
      "id": "nike",
      "displayLabel": "Nike",
      "aliases": ["NIKE", "Nike Inc"],
      "category": "athletic",
      "popularity": 95
    }
  ]
}

// product_types.json
{
  "version": 1,
  "lastUpdated": "2026-01-28T00:00:00Z",
  "entries": [
    {
      "id": "t_shirt",
      "displayLabel": "T-Shirt",
      "aliases": ["tshirt", "tee", "t shirt"],
      "parentCategory": "clothing",
      "popularity": 90
    }
  ]
}
```

### Field Definitions

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | String | Yes | Stable key for storage (lowercase, snake_case) |
| `displayLabel` | String | Yes | Human-readable label |
| `aliases` | String[] | No | Alternative search terms |
| `popularity` | Int (0-100) | No | Ranking weight (default: 50) |
| `category` / `parentCategory` | String | No | Filtering metadata |

### Localization Strategy

Follow `ItemAttributeLocalizer` pattern:
- Store canonical English `id` in database
- Add string resources: `R.string.catalog_brand_<id>`, `R.string.catalog_product_type_<id>`
- Create `CatalogLocalizer` to resolve localized display labels
- Fallback to `displayLabel` from JSON if no resource exists

---

## 4. Architecture Scaffolding

### Package Structure

```
com.scanium.app.catalog/
├── CatalogEntry.kt         # Data class
├── CatalogType.kt          # Enum (BRANDS, PRODUCT_TYPES)
├── CatalogSource.kt        # Loading interface
├── CatalogSearch.kt        # Search interface
└── impl/
    ├── AssetCatalogSource.kt      # Load from assets
    ├── InMemoryCatalogSearch.kt   # Current implementation
    └── RoomCatalogSearch.kt       # Future FTS stub
```

### Interface: CatalogEntry

```kotlin
data class CatalogEntry(
    val id: String,
    val displayLabel: String,
    val aliases: List<String> = emptyList(),
    val popularity: Int = 50,
    val metadata: Map<String, String> = emptyMap(),
) {
    val searchTerms: List<String>
        get() = listOf(displayLabel) + aliases
}

data class CatalogSearchResult(
    val entry: CatalogEntry,
    val matchScore: Float,
    val matchType: MatchType,
) {
    enum class MatchType { EXACT, PREFIX, WORD_BOUNDARY, CONTAINS, ALIAS }
}

enum class CatalogType { BRANDS, PRODUCT_TYPES }
```

### Interface: CatalogSource

```kotlin
interface CatalogSource {
    suspend fun loadCatalog(type: CatalogType): List<CatalogEntry>
    suspend fun hasUpdate(type: CatalogType): Boolean
    suspend fun getVersion(type: CatalogType): Int
}
```

### Interface: CatalogSearch

```kotlin
interface CatalogSearch {
    suspend fun search(type: CatalogType, query: String, limit: Int = 20): List<CatalogSearchResult>
    fun searchFlow(type: CatalogType, queryFlow: Flow<String>, limit: Int = 20): Flow<List<CatalogSearchResult>>
    suspend fun getById(type: CatalogType, id: String): CatalogEntry?
    suspend fun exists(type: CatalogType, id: String): Boolean
}
```

### In-Memory Implementation

`InMemoryCatalogSearch`:
1. Load catalog via `CatalogSource` (cached after first load)
2. Normalize query (trim, lowercase)
3. Score each entry against query
4. Sort by matchType ordinal → popularity (desc) → alphabetical
5. Return top N results

**Scoring logic:**
```kotlin
private fun scoreEntry(entry: CatalogEntry, query: String): CatalogSearchResult? {
    val label = entry.displayLabel.lowercase()

    if (label == query) return result(EXACT, 1.0f)
    if (label.startsWith(query)) return result(PREFIX, query.length / label.length)
    if (label.split(Regex("[\\s\\-_]+")).any { it.startsWith(query) }) return result(WORD_BOUNDARY, 0.7f)
    if (label.contains(query)) return result(CONTAINS, 0.4f)
    if (entry.aliases.any { it.lowercase().contains(query) }) return result(ALIAS, 0.5f)

    return null
}
```

### Room FTS Stub (Future)

```kotlin
class RoomCatalogSearch @Inject constructor() : CatalogSearch {
    override suspend fun search(...) = TODO("Implement when catalog > 5000 entries")
    // Uses Room FTS5 MATCH queries
}
```

### Dependency Injection

```kotlin
// CatalogModule.kt
@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides @Singleton
    fun provideCatalogSource(@ApplicationContext context: Context): CatalogSource =
        AssetCatalogSource(context)

    @Provides @Singleton
    fun provideCatalogSearch(source: CatalogSource): CatalogSearch =
        InMemoryCatalogSearch(source)
}
```

**Future swap to Room FTS:**
```kotlin
fun provideCatalogSearch(inMemory: InMemoryCatalogSearch, room: RoomCatalogSearch, @CatalogSize size: Int) =
    if (size > 5000) room else inMemory
```

### Downloaded Override Support (Future)

To support downloaded catalog updates:
1. Add `DownloadedCatalogSource` that reads from app internal storage
2. Create `CompositeCatalogSource` that prefers downloaded over assets
3. Swap in DI without changing `CatalogSearch` interface

---

## 5. Data Model Plan

### Storage Strategy

Store both display value and catalog ID:

| Attribute Key | Value | Purpose |
|---------------|-------|---------|
| `brand` | "Nike" | Display value (backwards compatible) |
| `brandId` | "nike" or "custom:my_brand" | Stable catalog key |
| `itemType` | "T-Shirt" | Display value |
| `itemTypeId` | "t_shirt" or "custom:t_shirt" | Stable catalog key |

### Custom Value Format

When user enters value not in catalog:
```
brandId = "custom:" + value.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
```

Example: "My Custom Brand" → `custom:my_custom_brand`

### Persistence Changes

In `saveFieldsToAttributes()` (`EditItemScreenV3.kt:774-883`):

```kotlin
// Brand
if (brandField.isNotBlank()) {
    itemsViewModel.updateItemAttribute(itemId, "brand",
        ItemAttribute(value = brandField, confidence = 1.0f, source = "USER"))
    val id = brandId ?: "custom:${normalize(brandField)}"
    itemsViewModel.updateItemAttribute(itemId, "brandId",
        ItemAttribute(value = id, confidence = 1.0f, source = "USER"))
}

// Product Type
if (productTypeField.isNotBlank()) {
    itemsViewModel.updateItemAttribute(itemId, "itemType",
        ItemAttribute(value = productTypeField, confidence = 1.0f, source = "USER"))
    val id = productTypeId ?: "custom:${normalize(productTypeField)}"
    itemsViewModel.updateItemAttribute(itemId, "itemTypeId",
        ItemAttribute(value = id, confidence = 1.0f, source = "USER"))
}
```

### Backend Payload

Fully compatible - backend receives extended attributes:
```json
{
  "attributes": {
    "brand": { "value": "Nike", "confidence": 1.0, "source": "USER" },
    "brandId": { "value": "nike", "confidence": 1.0, "source": "USER" },
    "itemType": { "value": "T-Shirt", "confidence": 1.0, "source": "USER" },
    "itemTypeId": { "value": "t_shirt", "confidence": 1.0, "source": "USER" }
  }
}
```

---

## 6. UI and State Plan

### New Composable: CatalogAutocompleteField

Location: `androidApp/src/main/java/com/scanium/app/items/edit/components/CatalogAutocompleteField.kt`

```kotlin
@Composable
fun CatalogAutocompleteField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<CatalogSearchResult>,
    onQueryChange: (String) -> Unit,
    onSuggestionSelected: (CatalogSearchResult) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onNext: () -> Unit = {},
    isError: Boolean = false,
)
```

**Behavior:**
- Wraps `ExposedDropdownMenuBox` + `OutlinedTextField`
- Dropdown shows when focused AND suggestions exist AND value not empty
- Typing updates both `value` and triggers `onQueryChange`
- Selection calls `onSuggestionSelected` with full result (including ID)
- Clear button resets field and ID

### ItemEditState Changes

Add to `ItemEditState.kt`:

```kotlin
// Catalog IDs (persisted)
var brandId by mutableStateOf(item?.attributes?.get("brandId")?.value)
var productTypeId by mutableStateOf(item?.attributes?.get("itemTypeId")?.value)

// Query flows (trigger search)
val brandQueryFlow = MutableStateFlow("")
val productTypeQueryFlow = MutableStateFlow("")

// Suggestions (populated from search)
var brandSuggestions by mutableStateOf<List<CatalogSearchResult>>(emptyList())
var productTypeSuggestions by mutableStateOf<List<CatalogSearchResult>>(emptyList())
```

### EditItemScreenV3 Changes

Inject `CatalogSearch` and collect suggestion flows:

```kotlin
@Composable
fun EditItemScreenV3(
    catalogSearch: CatalogSearch, // via EntryPoint
    // ...
) {
    val brandSuggestions by catalogSearch
        .searchFlow(CatalogType.BRANDS, editState.brandQueryFlow)
        .collectAsState(emptyList())

    val productTypeSuggestions by catalogSearch
        .searchFlow(CatalogType.PRODUCT_TYPES, editState.productTypeQueryFlow)
        .collectAsState(emptyList())

    LaunchedEffect(brandSuggestions) { editState.brandSuggestions = brandSuggestions }
    LaunchedEffect(productTypeSuggestions) { editState.productTypeSuggestions = productTypeSuggestions }
}
```

### ItemEditSections Changes

Replace `LabeledTextField` with `CatalogAutocompleteField`:

**Brand (lines 253-269):**
```kotlin
CatalogAutocompleteField(
    label = stringResource(R.string.edit_item_field_brand),
    value = state.brandField,
    onValueChange = { state.brandField = it },
    suggestions = state.brandSuggestions,
    onQueryChange = { state.brandQueryFlow.value = it },
    onSuggestionSelected = { result ->
        state.brandField = result.entry.displayLabel
        state.brandId = result.entry.id
    },
    onClear = { state.brandField = ""; state.brandId = null },
    imeAction = ImeAction.Next,
    onNext = { focusManager.moveFocus(FocusDirection.Down) },
    isError = assistantMissingFields.contains(PricingMissingField.BRAND),
)
```

**Product Type (lines 304-313):** Same pattern with `productTypeField`, `productTypeSuggestions`, `productTypeQueryFlow`, `productTypeId`.

---

## 7. Acceptance Tests

### Unit Tests

**File:** `androidApp/src/test/java/com/scanium/app/catalog/InMemoryCatalogSearchTest.kt`

| Test | Description |
|------|-------------|
| `exact match ranks highest` | Query "Nike" matches entry "Nike" with EXACT type |
| `prefix match ranks above contains` | Query "Nik" matches "Nike" as PREFIX, not "Adidas Nike" as CONTAINS |
| `word boundary match works` | Query "shirt" matches "T-Shirt" via word boundary |
| `contains-anywhere finds substring` | Query "hir" matches "T-Shirt" as CONTAINS |
| `alias matches work` | Query "tee" matches "T-Shirt" via alias |
| `results limited to 20` | Query with 50 matches returns only 20 |
| `empty query returns empty` | Query "" returns empty list |
| `popularity affects ranking` | Same match type: higher popularity ranks first |
| `case insensitive matching` | Query "NIKE" matches "nike" |

**File:** `androidApp/src/test/java/com/scanium/app/catalog/AssetCatalogSourceTest.kt`

| Test | Description |
|------|-------------|
| `loads brands from assets` | Returns list of CatalogEntry from brands.json |
| `caches after first load` | Second call returns cached data |
| `handles missing asset` | Returns empty list, no crash |
| `handles malformed JSON` | Returns empty list, logs error |

### UI Tests

**File:** `androidApp/src/androidTest/java/com/scanium/app/items/edit/CatalogAutocompleteFieldTest.kt`

| Test | Description |
|------|-------------|
| `shows suggestions when typing` | Type "Nik" → dropdown shows "Nike" |
| `selecting suggestion fills field` | Tap "Nike" → field shows "Nike", brandId = "nike" |
| `allows custom values` | Type "MyBrand", no selection → saves with custom: prefix |
| `clear button works` | Tap X → field empty, brandId null |
| `dropdown closes on selection` | Tap suggestion → dropdown dismissed |

### Integration Tests

**File:** `androidApp/src/androidTest/java/com/scanium/app/items/edit/EditItemCatalogIntegrationTest.kt`

| Test | Description |
|------|-------------|
| `brand autocomplete persists selection` | Select "Nike" → save → reload → brand="Nike", brandId="nike" |
| `custom brand saves correctly` | Type "Custom" → save → reload → brandId="custom:custom" |
| `product type suggestions filtered` | Type "shirt" → only clothing types shown |

### Performance Budget

| Metric | Target |
|--------|--------|
| Catalog load time | < 100ms for 5000 entries |
| Search latency | < 50ms per query |
| UI responsiveness | No jank during typing (debounce handles this) |
| Memory overhead | < 5MB for 5000 entries in memory |

---

## 8. Execution Checklist

### New Files to Create

| File | Purpose |
|------|---------|
| `assets/catalog/brands.json` | Brand catalog data |
| `assets/catalog/product_types.json` | Product type catalog data |
| `assets/catalog/manifest.json` | Version metadata |
| `catalog/CatalogEntry.kt` | Data classes (CatalogEntry, CatalogSearchResult) |
| `catalog/CatalogType.kt` | Enum |
| `catalog/CatalogSource.kt` | Loading interface |
| `catalog/CatalogSearch.kt` | Search interface |
| `catalog/impl/AssetCatalogSource.kt` | Asset loader |
| `catalog/impl/InMemoryCatalogSearch.kt` | Search implementation |
| `catalog/impl/RoomCatalogSearch.kt` | Future FTS stub |
| `di/CatalogModule.kt` | Hilt module |
| `items/edit/components/CatalogAutocompleteField.kt` | UI composable |
| `test/.../InMemoryCatalogSearchTest.kt` | Unit tests |
| `test/.../AssetCatalogSourceTest.kt` | Unit tests |
| `androidTest/.../CatalogAutocompleteFieldTest.kt` | UI tests |
| `androidTest/.../EditItemCatalogIntegrationTest.kt` | Integration tests |

### Files to Modify

| File | Changes |
|------|---------|
| `items/edit/ItemEditState.kt` | Add brandId, productTypeId, query flows, suggestions |
| `items/edit/ItemEditSections.kt` | Replace LabeledTextField with CatalogAutocompleteField |
| `items/edit/EditItemScreenV3.kt` | Add CatalogSearch injection, collect flows, update save logic |
| `di/EntryPoints.kt` | Add CatalogSearch entry point |

### Documentation to Update

| File | Update |
|------|--------|
| `CLAUDE.md` | Add catalog system to architecture section |
| `howto/app/reference/` | Add catalog autocomplete documentation |

---

## 9. Verification Plan

### Manual Testing

1. **Fresh install**: Verify catalogs load from assets
2. **Brand field**: Type "Nik" → see "Nike" suggestion → select → verify persistence
3. **Custom value**: Type "MyBrand" → don't select → save → verify `custom:mybrand` stored
4. **Product type**: Type "shirt" → see "T-Shirt" → select → verify persistence
5. **Offline**: Verify autocomplete works without network
6. **Performance**: Type rapidly → no UI jank (debounce working)

### Automated Testing

```bash
# Unit tests
./gradlew :androidApp:testDevDebugUnitTest --tests "*CatalogSearch*"
./gradlew :androidApp:testDevDebugUnitTest --tests "*AssetCatalogSource*"

# UI tests (device required)
./gradlew :androidApp:connectedDevDebugAndroidTest --tests "*CatalogAutocomplete*"
./gradlew :androidApp:connectedDevDebugAndroidTest --tests "*EditItemCatalog*"
```

### Backend Verification

1. Create item with catalog brand → sync → verify `brandId` in backend payload
2. Create item with custom brand → sync → verify `custom:` prefix in backend
3. Verify pricing API still works with new attribute structure

---

## Appendix: Room FTS Migration Path

When catalog exceeds 5000 entries:

1. Create Room entities:
```kotlin
@Entity(tableName = "catalog_entries")
data class CatalogEntryEntity(
    @PrimaryKey val id: String,
    val type: String, // "BRANDS" or "PRODUCT_TYPES"
    val displayLabel: String,
    val aliases: String, // JSON array
    val popularity: Int,
)

@Fts4(contentEntity = CatalogEntryEntity::class)
@Entity(tableName = "catalog_entries_fts")
data class CatalogEntryFts(
    val displayLabel: String,
    val aliases: String,
)
```

2. Create DAO with FTS queries:
```kotlin
@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_entries WHERE id IN (SELECT id FROM catalog_entries_fts WHERE catalog_entries_fts MATCH :query) AND type = :type LIMIT :limit")
    suspend fun search(type: String, query: String, limit: Int): List<CatalogEntryEntity>
}
```

3. Implement `RoomCatalogSearch` using DAO

4. Update `CatalogModule` to conditionally provide Room implementation

5. Add migration to populate FTS table from assets on first launch
