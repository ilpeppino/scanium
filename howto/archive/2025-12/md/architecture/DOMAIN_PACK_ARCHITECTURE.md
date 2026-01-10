# Domain Pack Architecture (Track A)

## Overview

The Domain Pack system provides a **config-driven, fine-grained category taxonomy** for Scanium that extends beyond the coarse-grained `ItemCategory` enum. This architecture enables multi-tenancy, domain-specific classification, and future integration with CLIP and cloud classifiers.

**Status:** ✅ Track A Complete (Foundation)
**Future Tracks:** Track B (On-Device CLIP), Track C (Cloud Classifier), Track D (Attribute Engine)

---

## Core Concepts

### What is a Domain Pack?

A **Domain Pack** is a JSON configuration file that defines:
- **Category taxonomy**: Fine-grained categories (e.g., "sofa", "laptop", "running shoes")
- **Attribute definitions**: Properties to extract (e.g., brand, color, condition)
- **Extraction methods**: How to extract attributes (OCR, CLIP, barcode, cloud, heuristic)
- **CLIP prompts**: Text prompts for on-device classification (future use)
- **Mapping to ItemCategory**: Bridge to existing coarse categories

Example Domain Pack: `home_resale_domain_pack.json` (for second-hand furniture, electronics, clothing)

### Why Domain Packs?

| Problem | Solution |
|---------|----------|
| Coarse ML Kit categories (5 types) | Fine-grained domain categories (20+ types) |
| Hardcoded category logic | Config-driven taxonomy (JSON) |
| Single business domain | Multi-tenancy support (e.g., "home_resale", "retail_inventory") |
| No attribute extraction | Extensible attribute definitions |
| Future CLIP/cloud integration | Prompts and extraction methods pre-configured |

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                   Domain Pack System                        │
│                    (Track A - Config)                        │
└─────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌────────────────────────────────────────────────────────────┐
│  res/raw/home_resale_domain_pack.json                      │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│  {                                                         │
│    "id": "home_resale",                                    │
│    "categories": [                                         │
│      { "id": "electronics_laptop",                        │
│        "displayName": "Laptop",                           │
│        "itemCategoryName": "ELECTRONICS",                 │
│        "prompts": ["a photo of a laptop computer"],       │
│        "priority": 15 }                                   │
│    ],                                                     │
│    "attributes": [                                        │
│      { "name": "brand", "extractionMethod": "OCR" }       │
│    ]                                                      │
│  }                                                        │
└────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌────────────────────────────────────────────────────────────┐
│  DomainPackRepository (interface)                          │
│  ├─ LocalDomainPackRepository (impl)                      │
│  │  - Loads JSON from res/raw                             │
│  │  - Parses with Kotlinx Serialization                   │
│  │  - Caches in memory                                    │
│  │  - Validates category IDs, itemCategoryName            │
│  │  - Provides fallback on error                          │
│  └─ Future: RemoteDomainPackRepository (API)              │
└────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌────────────────────────────────────────────────────────────┐
│  CategoryEngine (interface)                                │
│  ├─ BasicCategoryEngine (Track A impl)                    │
│  │  - Simple ML Kit label matching                        │
│  │  - Substring search on display names                   │
│  │  - Priority-based tie-breaking                         │
│  │  - Filters disabled categories                         │
│  │                                                         │
│  ├─ Future: ClipCategoryEngine (Track B)                  │
│  │  - On-device CLIP prompt matching                      │
│  │  - Semantic similarity scoring                         │
│  │                                                         │
│  └─ Future: HybridCategoryEngine (Track C)                │
│     - Combines ML Kit + CLIP + Cloud                      │
│     - Confidence-based selection                          │
└────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌────────────────────────────────────────────────────────────┐
│  CategoryMapper                                            │
│  - Maps DomainCategory → ItemCategory enum                 │
│  - Validates itemCategoryName values                       │
│  - Provides fallback to UNKNOWN                            │
└────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌────────────────────────────────────────────────────────────┐
│  Existing App Code                                         │
│  - ScannedItem (new field: domainCategoryId)              │
│  - ItemsViewModel (unchanged, uses ItemCategory)           │
│  - PricingEngine (unchanged, uses ItemCategory)            │
└────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
app/src/main/java/com/scanium/app/domain/
├── config/
│   ├── AttributeType.kt              # Enum: STRING, NUMBER, ENUM, BOOLEAN
│   ├── ExtractionMethod.kt           # Enum: OCR, CLIP, CLOUD, BARCODE, HEURISTIC, NONE
│   ├── DomainAttribute.kt            # Attribute definition data class
│   ├── DomainCategory.kt             # Category definition data class
│   └── DomainPack.kt                 # Top-level pack data class with helper methods
├── repository/
│   ├── DomainPackRepository.kt       # Interface for loading packs
│   └── LocalDomainPackRepository.kt  # Loads from res/raw, caches in memory
├── category/
│   ├── CategorySelectionInput.kt     # Input data for category selection
│   ├── CategoryEngine.kt             # Interface for category selection
│   ├── BasicCategoryEngine.kt        # Simple ML Kit label matching impl
│   └── CategoryMapper.kt             # Maps DomainCategory ↔ ItemCategory
└── DomainPackProvider.kt             # Singleton for app-wide access

app/src/main/res/raw/
└── home_resale_domain_pack.json      # Default Domain Pack configuration

app/src/test/java/com/scanium/app/domain/
├── config/
│   └── DomainPackTest.kt             # 10 tests for DomainPack data class
├── repository/
│   └── LocalDomainPackRepositoryTest.kt  # 14 tests for repository
├── category/
│   ├── CategoryMapperTest.kt         # 11 tests for mapper
│   └── BasicCategoryEngineTest.kt    # 16 tests for engine
└── DomainPackProviderTest.kt         # 10 tests for provider

**Total: 61 tests added** (all passing ✅)
```

---

## Data Models

### DomainPack

Top-level container for the entire domain configuration.

```kotlin
data class DomainPack(
    val id: String,                    // e.g., "home_resale"
    val name: String,                  // e.g., "Home Resale"
    val version: String,               // e.g., "1.0.0" (semantic versioning)
    val description: String?,          // Optional description
    val categories: List<DomainCategory>,
    val attributes: List<DomainAttribute>
) {
    fun getEnabledCategories(): List<DomainCategory>
    fun getCategoryById(categoryId: String): DomainCategory?
    fun getCategoriesForItemCategory(itemCategoryName: String): List<DomainCategory>
    fun getAttributesForCategory(categoryId: String): List<DomainAttribute>
    fun getCategoriesByPriority(): List<DomainCategory>
}
```

### DomainCategory

Defines a single category in the taxonomy.

```kotlin
data class DomainCategory(
    val id: String,                    // e.g., "electronics_laptop"
    val displayName: String,           // e.g., "Laptop"
    val parentId: String?,             // e.g., "electronics" (hierarchical)
    val itemCategoryName: String,      // e.g., "ELECTRONICS" (maps to ItemCategory)
    val prompts: List<String>,         // e.g., ["a photo of a laptop computer"]
    val priority: Int?,                // Tie-breaking (higher = preferred)
    val enabled: Boolean               // Enable/disable without code changes
)

// Optional pack-level knobs (used in latest backend build)
data class DomainPack(
    val contextPenalty: Double = 0.5,   // Down-rank stoplisted context tokens (table, surface, etc.)
    val contextStoplist: List<String> = emptyList() // Strings that should not dominate classification
)

// Mapper always emits the *matched token* as the display label so UIs can show "Mug" instead of "Drinkware".
```

### DomainAttribute

Defines an extractable attribute (e.g., brand, color, condition).

```kotlin
data class DomainAttribute(
    val name: String,                  // e.g., "brand"
    val type: AttributeType,           // STRING, NUMBER, ENUM, BOOLEAN
    val extractionMethod: ExtractionMethod,  // OCR, CLIP, CLOUD, etc.
    val appliesToCategoryIds: List<String>,  // Categories this applies to
    val required: Boolean              // Mandatory vs optional
)
```

---

## Usage Examples

### Initialize Domain Pack System

```kotlin
// In MainActivity.onCreate()
DomainPackProvider.initialize(context)
```

### Load Domain Pack

```kotlin
// Access via provider
val repository = DomainPackProvider.repository
val pack = repository.getActiveDomainPack()

println("Loaded ${pack.name} v${pack.version}")
println("Categories: ${pack.categories.size}")
println("Attributes: ${pack.attributes.size}")
```

### Select Category

```kotlin
// Create input with ML Kit label
val input = CategorySelectionInput(
    mlKitLabel = "Home good",
    mlKitConfidence = 0.85f
)

// Select category
val engine = DomainPackProvider.categoryEngine
val category = engine.selectCategory(input)

if (category != null) {
    println("Selected: ${category.displayName} (${category.id})")

    // Map to ItemCategory
    val itemCategory = category.toItemCategory()
    println("Maps to: $itemCategory")
}
```

### Get Multiple Candidates

```kotlin
val input = CategorySelectionInput(mlKitLabel = "electronics")
val candidates = engine.getCandidateCategories(input)

candidates.forEach { (category, score) ->
    println("${category.displayName}: ${score * 100}%")
}
// Output:
// Laptop: 100.0%
// Smartphone: 70.0%
// Television: 70.0%
```

### Store Domain Category in ScannedItem

```kotlin
val domainCategory = engine.selectCategory(input)
val scannedItem = ScannedItem(
    id = "item_123",
    category = domainCategory.toItemCategoryOrDefault(),  // Existing field
    domainCategoryId = domainCategory?.id,                // NEW field (Track A)
    priceRange = Pair(100.0, 200.0),
    confidence = 0.85f
    // ... other fields
)
```

---

## Home Resale Domain Pack

The default pack (`home_resale_domain_pack.json`) contains **23 categories** and **10 attributes**.

### Categories by Group

| Group | Categories | ItemCategory Mapping |
|-------|-----------|---------------------|
| **Furniture** | Sofa, Chair, Table, Bookshelf | HOME_GOOD |
| **Electronics** | Laptop, Monitor, TV, Phone, Tablet, Speaker | ELECTRONICS |
| **Clothing** | Shoes, Jacket, Bag | FASHION |
| **Kitchenware** | Pan, Pot, Blender | HOME_GOOD |
| **Appliances** | Microwave, Vacuum Cleaner | HOME_GOOD |
| **Toys** | Action Figure, Board Game | UNKNOWN |
| **Other** | Indoor Plant, Book, Bicycle | PLANT, UNKNOWN, UNKNOWN |

### Attributes

| Attribute | Type | Extraction Method | Applies To |
|-----------|------|------------------|-----------|
| `brand` | STRING | OCR | Electronics, Clothing, Appliances |
| `color` | STRING | CLIP | Furniture, Clothing |
| `material` | STRING | CLIP | Furniture, Kitchenware |
| `size` | STRING | HEURISTIC | Clothing, Electronics (TV/Monitor) |
| `condition` | ENUM | CLOUD | Electronics, Furniture |
| `model` | STRING | OCR | Electronics |
| `year` | NUMBER | OCR | Electronics, Bicycle |
| `sku` | STRING | BARCODE | Books, Board Games |
| `isbn` | STRING | BARCODE | Books |
| `plant_type` | STRING | CLIP | Indoor Plant |

---

## Integration Points

### Non-Breaking Integration

The Domain Pack system is **purely additive** and does not break existing functionality:

1. **ScannedItem**: Added optional `domainCategoryId: String?` field
   - Existing code continues to use `category: ItemCategory`
   - New code can optionally populate `domainCategoryId`

2. **ItemsViewModel**: No changes required
   - Continues to work with `ItemCategory`
   - Future: Could use `domainCategoryId` for richer UI

3. **PricingEngine**: No changes required
   - Continues to use `ItemCategory` for pricing
   - Future: Could use fine-grained categories for more accurate prices

4. **ObjectDetectorClient**: No changes in Track A
   - Future Tracks (B/C) will integrate CLIP/cloud here

### Initialization

```kotlin
// MainActivity.onCreate()
DomainPackProvider.initialize(this)
```

This loads the pack asynchronously on first access (lazy initialization).

---

## Testing Strategy

### Test Coverage

**61 tests added** across 5 test files:

1. **DomainPackTest.kt** (10 tests)
   - JSON serialization/deserialization
   - Helper methods (getEnabledCategories, getCategoryById, etc.)
   - Priority sorting
   - Filtering logic

2. **LocalDomainPackRepositoryTest.kt** (14 tests)
   - Loading from res/raw
   - Caching behavior
   - Validation (category IDs, itemCategoryName, prompts)
   - Attribute referential integrity

3. **CategoryMapperTest.kt** (11 tests)
   - Valid enum mapping
   - Invalid name handling
   - Case sensitivity
   - Default fallback

4. **BasicCategoryEngineTest.kt** (16 tests)
   - ML Kit label matching
   - Case insensitivity
   - Priority-based selection
   - Disabled category filtering
   - Candidate scoring

5. **DomainPackProviderTest.kt** (10 tests)
   - Singleton initialization
   - Idempotency
   - Error handling before init
   - Reset functionality

### Running Tests

```bash
# Run all domain tests
./gradlew test --tests "com.scanium.app.domain.*"

# Run specific test file
./gradlew test --tests "com.scanium.app.domain.category.BasicCategoryEngineTest"

# Run with console output
./gradlew test --tests "com.scanium.app.domain.*" --console=plain
```

---

## Future Enhancements (Track B, C, D)

### Track B: On-Device CLIP Integration

**Goal:** Use on-device CLIP model for fine-grained classification

```kotlin
// Future: ClipCategoryEngine
class ClipCategoryEngine(
    private val repository: DomainPackRepository,
    private val clipModel: ClipModel  // On-device CLIP
) : CategoryEngine {
    override suspend fun selectCategory(input: CategorySelectionInput): DomainCategory? {
        val pack = repository.getActiveDomainPack()

        // Compute embeddings for image and all category prompts
        val imageEmbedding = clipModel.encodeImage(input.image)
        val similarities = pack.categories.map { category ->
            val textEmbeddings = category.prompts.map { clipModel.encodeText(it) }
            val maxSimilarity = textEmbeddings.maxOf { it.cosineSimilarity(imageEmbedding) }
            category to maxSimilarity
        }

        // Return highest similarity
        return similarities.maxByOrNull { it.second }?.first
    }
}
```

### Track C: Cloud Classifier Integration

**Goal:** Use cloud API for highest accuracy (optional, premium feature)

```kotlin
// Future: HybridCategoryEngine
class HybridCategoryEngine(
    private val repository: DomainPackRepository,
    private val mlKitEngine: BasicCategoryEngine,
    private val clipEngine: ClipCategoryEngine,
    private val cloudClient: CloudClassifierClient
) : CategoryEngine {
    override suspend fun selectCategory(input: CategorySelectionInput): DomainCategory? {
        // Strategy: Cloud (if available) > CLIP > ML Kit

        if (input.cloudLabel != null) {
            // Use cloud classification (highest accuracy)
            return matchByDisplayName(input.cloudLabel)
        }

        if (input.clipCandidateLabel != null && input.clipSimilarity > CLIP_THRESHOLD) {
            // Use CLIP (good accuracy, fast)
            return clipEngine.selectCategory(input)
        }

        // Fallback to ML Kit (baseline)
        return mlKitEngine.selectCategory(input)
    }
}
```

### Track D: Attribute Extraction Engine

**Goal:** Extract attributes based on Domain Pack configuration

```kotlin
// Future: AttributeEngine
class AttributeEngine(
    private val repository: DomainPackRepository,
    private val ocrClient: TextRecognitionClient,
    private val barcodeClient: BarcodeScannerClient,
    private val clipModel: ClipModel
) {
    suspend fun extractAttributes(
        category: DomainCategory,
        image: Bitmap
    ): Map<String, Any?> {
        val pack = repository.getActiveDomainPack()
        val attributes = pack.getAttributesForCategory(category.id)

        return attributes.associate { attribute ->
            val value = when (attribute.extractionMethod) {
                ExtractionMethod.OCR -> extractWithOCR(image, attribute)
                ExtractionMethod.BARCODE -> extractWithBarcode(image, attribute)
                ExtractionMethod.CLIP -> extractWithCLIP(image, attribute)
                ExtractionMethod.CLOUD -> extractWithCloud(image, attribute)
                ExtractionMethod.HEURISTIC -> extractWithHeuristic(image, attribute)
                ExtractionMethod.NONE -> null
            }
            attribute.name to value
        }
    }
}
```

---

## Design Decisions

### Why JSON Configuration?

| Pros | Cons |
|------|------|
| ✅ No code changes for new categories | ❌ Validation needed at runtime |
| ✅ A/B testing via enable/disable flags | ❌ Larger APK size (negligible) |
| ✅ Multi-tenancy support | ❌ No compile-time type safety |
| ✅ Easy to version and audit | ❌ Requires careful schema design |

**Decision:** JSON wins for flexibility. Validation handled by comprehensive tests.

### Why Kotlinx Serialization?

- **Modern**: Kotlin-first, multiplatform-ready
- **Efficient**: Compile-time code generation
- **Flexible**: Lenient mode handles unknown fields
- **Alternative considered**: Gson (more mature but Java-centric)

### Why Singleton Provider?

- **No DI framework**: Project doesn't use Hilt/Koin yet
- **Simple access**: `DomainPackProvider.categoryEngine`
- **Future-proof**: Easy to migrate to DI modules later

```kotlin
// Future Hilt migration:
@Module
@InstallIn(SingletonComponent::class)
object DomainPackModule {
    @Provides @Singleton
    fun provideDomainPackRepository(context: Context): DomainPackRepository =
        LocalDomainPackRepository(context)

    @Provides @Singleton
    fun provideCategoryEngine(repo: DomainPackRepository): CategoryEngine =
        BasicCategoryEngine(repo)
}
```

### Why Priority Field?

Handles ambiguous cases where multiple categories match:

```
ML Kit label: "Home good"
Matches: Sofa (priority 10), Chair (priority 10), Table (priority 10)
Selected: Sofa (first alphabetically, same priority)

ML Kit label: "Electronics"
Matches: Laptop (priority 15), Phone (priority 12), TV (priority 12)
Selected: Laptop (highest priority)
```

### Why Hierarchical Categories?

`parentId` enables grouping and future hierarchical matching:

```
furniture (parent)
├─ furniture_sofa (leaf)
├─ furniture_chair (leaf)
└─ furniture_table (leaf)

electronics (parent)
├─ electronics_laptop (leaf)
└─ electronics_phone (leaf)
```

Future: If "furniture_sofa" doesn't match, try parent "furniture".

---

## Performance Considerations

### Memory

- **Domain Pack**: ~50KB JSON → ~200KB in memory (parsed objects)
- **Caching**: Loaded once, cached for app lifetime
- **Cleanup**: Not needed (small, persistent data)

### Loading Time

- **First access**: ~50ms (JSON parse + validation)
- **Subsequent access**: <1ms (cached)
- **Initialization**: Async on first use (doesn't block startup)

### Matching Performance

- **BasicCategoryEngine**: O(n) where n = number of categories (~20-30)
- **Single match**: <1ms
- **Candidate scoring**: <5ms

Future optimizations:
- Build trie for fast prefix matching
- Cache embedding computations (CLIP)

---

## Migration Path for Existing Code

The Domain Pack system is designed for gradual adoption:

### Phase 1: Foundation (Track A - COMPLETE ✅)
- Domain Pack loaded and cached
- CategoryEngine available but not required
- Existing code continues using ItemCategory

### Phase 2: Optional Usage (Track B/C)
- New detections populate `domainCategoryId`
- UI can show fine-grained categories: "Laptop" instead of "Electronics"
- Pricing can optionally use fine-grained categories
- Existing data (without domainCategoryId) still works

### Phase 3: Full Integration (Track D+)
- Attribute extraction based on Domain Pack
- Rich item details (brand, color, condition, etc.)
- Domain Pack becomes primary source of truth

---

## Validation & Error Handling

### Validation Checks

1. **Load-time validation** (LocalDomainPackRepository):
   - At least one enabled category
   - Unique category IDs
   - Valid `itemCategoryName` values (warns if invalid)
   - Attributes reference valid category IDs

2. **Fallback behavior**:
   - Invalid pack → use empty fallback pack (app doesn't crash)
   - Invalid category mapping → return UNKNOWN ItemCategory
   - No matching category → return null (caller handles)

### Error Scenarios

| Error | Behavior |
|-------|----------|
| JSON parse failure | Log error, use fallback pack |
| Invalid itemCategoryName | Log warning, allow load (graceful degradation) |
| Duplicate category IDs | Throw DomainPackLoadException |
| Missing category in attribute | Log warning at load time |
| CategoryEngine before init | Throw IllegalStateException |

---

## Summary

**Track A deliverables:**

✅ **Configuration-driven category taxonomy**
- JSON schema defined and documented
- 23 categories + 10 attributes in home_resale pack

✅ **Data models and repository**
- DomainPack, DomainCategory, DomainAttribute data classes
- LocalDomainPackRepository with caching and validation
- DomainPackProvider singleton for app-wide access

✅ **Category selection engine**
- CategoryEngine interface
- BasicCategoryEngine with ML Kit label matching
- CategoryMapper for DomainCategory ↔ ItemCategory

✅ **Light integration**
- MainActivity initialization
- ScannedItem.domainCategoryId field (optional)
- Existing code unmodified and working

✅ **Comprehensive testing**
- 61 tests across 5 test files
- 100% test pass rate
- Covers data models, repository, mapper, engine, provider

✅ **Documentation**
- This architecture document
- Inline KDoc comments
- Usage examples and migration path

**Next steps:**
- Track B: Integrate on-device CLIP model
- Track C: Add cloud classifier support
- Track D: Implement attribute extraction engine

**Key architectural win:**
The system is fully self-contained and CLIP/cloud-agnostic. Future tracks can add advanced classification without breaking Track A foundation.
