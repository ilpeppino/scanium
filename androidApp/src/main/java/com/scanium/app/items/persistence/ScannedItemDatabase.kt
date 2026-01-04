package com.scanium.app.items.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scanium.app.selling.persistence.ListingDraftDao
import com.scanium.app.selling.persistence.ListingDraftEntity

@Database(
    entities = [ScannedItemEntity::class, ScannedItemHistoryEntity::class, ListingDraftEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class ScannedItemDatabase : RoomDatabase() {
    abstract fun scannedItemDao(): ScannedItemDao

    abstract fun listingDraftDao(): ListingDraftDao

    companion object {
        @Volatile
        private var instance: ScannedItemDatabase? = null

        fun getInstance(context: Context): ScannedItemDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): ScannedItemDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ScannedItemDatabase::class.java,
                "scanned_items.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                // Allow destructive migration for future schema changes without a migration.
                .fallbackToDestructiveMigration()
                .build()
        }

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
    }
}
