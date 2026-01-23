package com.scanium.app.items.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scanium.app.classification.persistence.ClassificationCorrectionDao
import com.scanium.app.classification.persistence.ClassificationCorrectionEntity
import com.scanium.app.selling.persistence.ListingDraftDao
import com.scanium.app.selling.persistence.ListingDraftEntity

@Database(
    entities = [
        ScannedItemEntity::class,
        ScannedItemHistoryEntity::class,
        ListingDraftEntity::class,
        ClassificationCorrectionEntity::class
    ],
    version = 11,
    exportSchema = false,
)
abstract class ScannedItemDatabase : RoomDatabase() {
    abstract fun scannedItemDao(): ScannedItemDao

    abstract fun listingDraftDao(): ListingDraftDao

    abstract fun classificationCorrectionDao(): ClassificationCorrectionDao

    companion object {
        @Volatile
        private var instance: ScannedItemDatabase? = null

        fun getInstance(context: Context): ScannedItemDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }

        private fun buildDatabase(context: Context): ScannedItemDatabase =
            Room
                .databaseBuilder(
                    context.applicationContext,
                    ScannedItemDatabase::class.java,
                    "scanned_items.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                )
                // Allow destructive migration for future schema changes without a migration.
                .fallbackToDestructiveMigration()
                .build()

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS scanned_item_history (
                            changeId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            itemId TEXT NOT NULL,
                            changedAt INTEGER NOT NULL,
                            snapshotHash TEXT NOT NULL,
                            category TEXT NOT NULL,
                            priceLow REAL NOT NULL,
                            priceHigh REAL NOT NULL,
                            confidence REAL NOT NULL,
                            timestamp INTEGER NOT NULL,
                            labelText TEXT,
                            recognizedText TEXT,
                            barcodeValue TEXT,
                            boundingBoxLeft REAL,
                            boundingBoxTop REAL,
                            boundingBoxRight REAL,
                            boundingBoxBottom REAL,
                            thumbnailBytes BLOB,
                            thumbnailMimeType TEXT,
                            thumbnailWidth INTEGER,
                            thumbnailHeight INTEGER,
                            thumbnailRefBytes BLOB,
                            thumbnailRefMimeType TEXT,
                            thumbnailRefWidth INTEGER,
                            thumbnailRefHeight INTEGER,
                            fullImageUri TEXT,
                            fullImagePath TEXT,
                            listingStatus TEXT NOT NULL,
                            listingId TEXT,
                            listingUrl TEXT,
                            classificationStatus TEXT NOT NULL,
                            domainCategoryId TEXT,
                            classificationErrorMessage TEXT,
                            classificationRequestId TEXT
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_scanned_item_history_itemId_changedAt ON scanned_item_history(itemId, changedAt)",
                    )
                }
            }

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS listing_drafts (
                            id TEXT NOT NULL PRIMARY KEY,
                            itemId TEXT NOT NULL,
                            profileId TEXT NOT NULL,
                            title TEXT NOT NULL,
                            titleConfidence REAL NOT NULL,
                            titleSource TEXT NOT NULL,
                            description TEXT NOT NULL,
                            descriptionConfidence REAL NOT NULL,
                            descriptionSource TEXT NOT NULL,
                            fieldsJson TEXT NOT NULL,
                            price REAL NOT NULL,
                            priceConfidence REAL NOT NULL,
                            priceSource TEXT NOT NULL,
                            status TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            photoBytes BLOB,
                            photoMimeType TEXT,
                            photoWidth INTEGER,
                            photoHeight INTEGER
                        )
                        """.trimIndent(),
                    )
                }
            }

        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Add userPriceCents and condition columns to scanned_items table
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN userPriceCents INTEGER")
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN condition TEXT")
                    // Add attributesJson column to scanned_items table
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN attributesJson TEXT")

                    // Add userPriceCents and condition columns to scanned_item_history table
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN userPriceCents INTEGER")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN condition TEXT")
                    // Add attributesJson column to history table as well
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN attributesJson TEXT")
                }
            }

        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Add detectedAttributesJson column to store original detected attributes
                    // This preserves the original backend detection even after user edits
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN detectedAttributesJson TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN detectedAttributesJson TEXT")
                }
            }

        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Add visionAttributesJson column to store OCR/color/logo/label data
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN visionAttributesJson TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN visionAttributesJson TEXT")
                }
            }

        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Add columns for multi-object scanning feature

                    // attributesSummaryText: User-editable attribute summary text
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN attributesSummaryText TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN attributesSummaryText TEXT")

                    // summaryTextUserEdited: Flag indicating user has manually edited the summary
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN summaryTextUserEdited INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN summaryTextUserEdited INTEGER NOT NULL DEFAULT 0")

                    // additionalPhotosJson: Additional photos attached to the item (close-ups)
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN additionalPhotosJson TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN additionalPhotosJson TEXT")

                    // sourcePhotoId: Links items captured from the same multi-object photo
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN sourcePhotoId TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN sourcePhotoId TEXT")

                    // enrichmentStatusJson: Status of each enrichment layer (A/B/C)
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN enrichmentStatusJson TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN enrichmentStatusJson TEXT")
                }
            }

        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Add columns for Export Assistant feature (Phase 4)

                    // exportTitle: AI-generated marketplace-ready title
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN exportTitle TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN exportTitle TEXT")

                    // exportDescription: AI-generated marketplace-ready description
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN exportDescription TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN exportDescription TEXT")

                    // exportBulletsJson: AI-generated bullet highlights for listing
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN exportBulletsJson TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN exportBulletsJson TEXT")

                    // exportGeneratedAt: Timestamp when export fields were generated
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN exportGeneratedAt INTEGER")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN exportGeneratedAt INTEGER")

                    // exportFromCache: Whether the export was served from cache (0/1)
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN exportFromCache INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN exportFromCache INTEGER NOT NULL DEFAULT 0")

                    // exportModel: LLM model used to generate the export
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN exportModel TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN exportModel TEXT")

                    // exportConfidenceTier: Confidence tier of AI-generated export (HIGH/MED/LOW)
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN exportConfidenceTier TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN exportConfidenceTier TEXT")
                }
            }

        private val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Add columns for Quality Loop feature (Phase 6)

                    // completenessScore: 0-100 score based on category-specific required attributes
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN completenessScore INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN completenessScore INTEGER NOT NULL DEFAULT 0")

                    // missingAttributesJson: List of missing attribute keys ordered by importance
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN missingAttributesJson TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN missingAttributesJson TEXT")

                    // lastEnrichedAt: Timestamp of the last enrichment operation
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN lastEnrichedAt INTEGER")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN lastEnrichedAt INTEGER")

                    // capturedShotTypesJson: Photo shot types that have been captured
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN capturedShotTypesJson TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN capturedShotTypesJson TEXT")

                    // isReadyForListing: Whether the item meets the completeness threshold
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN isReadyForListing INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN isReadyForListing INTEGER NOT NULL DEFAULT 0")
                }
            }

        private val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Add columns for multi-device sync (Phase E)

                    // serverId: Backend Item.id (UUID from server)
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN serverId TEXT")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN serverId TEXT")

                    // needsSync: 0 = synced, 1 = needs push
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 1")

                    // lastSyncedAt: Timestamp of last successful sync
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN lastSyncedAt INTEGER")

                    // syncVersion: Optimistic locking version (matches backend)
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")

                    // clientUpdatedAt: Client-side update timestamp for conflict resolution
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN clientUpdatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN clientUpdatedAt INTEGER NOT NULL DEFAULT 0")

                    // deletedAt: Soft delete timestamp (tombstone)
                    db.execSQL("ALTER TABLE scanned_items ADD COLUMN deletedAt INTEGER")
                    db.execSQL("ALTER TABLE scanned_item_history ADD COLUMN deletedAt INTEGER")

                    // Set clientUpdatedAt to timestamp for existing items
                    db.execSQL("UPDATE scanned_items SET clientUpdatedAt = timestamp WHERE clientUpdatedAt = 0")

                    // Mark all existing items as needing sync (they have no serverId yet)
                    db.execSQL("UPDATE scanned_items SET needsSync = 1 WHERE serverId IS NULL")

                    // Create indexes for sync queries
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_scanned_items_serverId ON scanned_items(serverId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_scanned_items_needsSync ON scanned_items(needsSync)")
                }
            }

        private val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Add classification_corrections table for local learning overlay
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS classification_corrections (
                            id TEXT NOT NULL PRIMARY KEY,
                            itemId TEXT NOT NULL,
                            imageHash TEXT NOT NULL,
                            originalCategoryId TEXT,
                            originalCategoryName TEXT,
                            originalConfidence REAL,
                            correctedCategoryId TEXT NOT NULL,
                            correctedCategoryName TEXT NOT NULL,
                            correctionMethod TEXT NOT NULL,
                            notes TEXT,
                            visualContext TEXT NOT NULL,
                            correctedAt INTEGER NOT NULL,
                            syncedToBackend INTEGER NOT NULL DEFAULT 0,
                            syncedAt INTEGER
                        )
                        """.trimIndent(),
                    )

                    // Create indexes for efficient queries
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_classification_corrections_itemId ON classification_corrections(itemId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_classification_corrections_correctedAt ON classification_corrections(correctedAt)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_classification_corrections_syncedToBackend ON classification_corrections(syncedToBackend)")
                }
            }
    }
}
