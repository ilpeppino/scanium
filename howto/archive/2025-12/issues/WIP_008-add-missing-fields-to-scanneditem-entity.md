# Add Missing Fields to ScannedItemEntity (Schema Drift)

**Labels:** `bug`, `priority:p1`, `area:data`, `schema-drift`
**Type:** Data Model Bug
**Severity:** High (if database is ever activated)

## Problem

`ScannedItemEntity` (database model) is **missing 4 fields** that exist in `ScannedItem` (domain
model). If the database layer is ever activated (currently unused per Issue #002), eBay integration
data will be lost.

## Schema Comparison

### ScannedItem (Domain Model) - 13 properties

Located: `/app/src/main/java/com/scanium/app/items/ScannedItem.kt`

```kotlin
data class ScannedItem(
    val id: String,
    val thumbnail: Bitmap?,
    val category: ItemCategory,
    val priceRange: Pair<Double, Double>,
    val confidence: Float,
    val timestamp: Long,
    val domainCategoryId: String?,
    val fullImageUri: Uri?,              // ❌ MISSING IN ENTITY
    val listingStatus: ItemListingStatus, // ❌ MISSING IN ENTITY
    val listingId: String?,               // ❌ MISSING IN ENTITY
    val listingUrl: String?,              // ❌ MISSING IN ENTITY
    val barcodeValue: String?,
    val documentText: String?
)
```

### ScannedItemEntity (Database Model) - 9 properties

Located: `/app/src/main/java/com/scanium/app/data/ScannedItemEntity.kt`

```kotlin
@Entity(tableName = "scanned_items")
data class ScannedItemEntity(
    @PrimaryKey val id: String,
    val thumbnailBytes: ByteArray?,
    val category: String,
    val priceMin: Double,
    val priceMax: Double,
    val confidence: Float,
    val timestamp: Long,
    val domainCategoryId: String?,
    val barcodeValue: String?
    // ❌ MISSING: fullImageUri, listingStatus, listingId, listingUrl, documentText
)
```

## Impact

**If database is activated (see Issue #002):**

- eBay listing status lost on app restart
- Listing IDs and URLs not persisted
- High-quality image URIs lost (only thumbnails saved)
- Document text not persisted
- Users lose critical marketplace integration data

**Current state:**

- No immediate impact since database is unused
- But schema drift will cause bugs if database is later activated

## Decision Required

This issue depends on Issue #002 resolution:

### If Database is Deleted (Issue #002 Option A):

- This issue becomes moot - close as "won't fix"

### If Database is Activated (Issue #002 Option B):

- MUST fix this schema drift before activation
- Follow acceptance criteria below

## Acceptance Criteria (if database is activated)

- [ ] Add `fullImageUriString: String?` to ScannedItemEntity
- [ ] Add `listingStatus: String?` to ScannedItemEntity (store enum name)
- [ ] Add `listingId: String?` to ScannedItemEntity
- [ ] Add `listingUrl: String?` to ScannedItemEntity
- [ ] Add `documentText: String?` to ScannedItemEntity
- [ ] Create type converters for Uri ↔ String
- [ ] Create type converters for ItemListingStatus ↔ String
- [ ] Update toScannedItem() to handle new fields
- [ ] Update fromScannedItem() to serialize new fields
- [ ] Create database migration from version 1 to 2
- [ ] Update tests to verify new fields
- [ ] Document schema versioning strategy

## Suggested Approach (if database activated)

### 1. Update Entity

```kotlin
@Entity(tableName = "scanned_items")
data class ScannedItemEntity(
    @PrimaryKey val id: String,
    val thumbnailBytes: ByteArray?,
    val category: String,
    val priceMin: Double,
    val priceMax: Double,
    val confidence: Float,
    val timestamp: Long,
    val domainCategoryId: String?,
    val barcodeValue: String?,
    val documentText: String?,           // NEW
    val fullImageUriString: String?,     // NEW
    val listingStatus: String?,          // NEW (enum name)
    val listingId: String?,              // NEW
    val listingUrl: String?              // NEW
)
```

### 2. Add Type Converters

```kotlin
object UriConverter {
    @TypeConverter
    fun fromUri(uri: Uri?): String? = uri?.toString()

    @TypeConverter
    fun toUri(uriString: String?): Uri? = uriString?.let { Uri.parse(it) }
}

object ListingStatusConverter {
    @TypeConverter
    fun fromStatus(status: ItemListingStatus?): String? = status?.name

    @TypeConverter
    fun toStatus(name: String?): ItemListingStatus? =
        name?.let { ItemListingStatus.valueOf(it) }
}
```

### 3. Create Migration

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE scanned_items ADD COLUMN documentText TEXT")
        database.execSQL("ALTER TABLE scanned_items ADD COLUMN fullImageUriString TEXT")
        database.execSQL("ALTER TABLE scanned_items ADD COLUMN listingStatus TEXT")
        database.execSQL("ALTER TABLE scanned_items ADD COLUMN listingId TEXT")
        database.execSQL("ALTER TABLE scanned_items ADD COLUMN listingUrl TEXT")
    }
}
```

### 4. Update Database Version

```kotlin
@Database(
    entities = [ScannedItemEntity::class],
    version = 2,  // INCREMENT
    exportSchema = true
)
```

## Related Issues

- Issue #002 (Remove or activate Room database layer) - **BLOCKING**
- This issue should only be addressed if Issue #002 chooses Option B (activate database)
