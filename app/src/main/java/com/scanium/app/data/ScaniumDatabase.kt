package com.scanium.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for Scanium app.
 *
 * This is the main database class that provides access to the DAOs
 * and manages the database instance as a singleton.
 *
 * Database version: 1
 * Entities: ScannedItemEntity
 *
 * Future migration strategy:
 * - When schema changes are needed, increment the version number
 * - Provide migration paths using Room's Migration class
 * - For testing, use fallbackToDestructiveMigration() in test databases
 */
@Database(
    entities = [ScannedItemEntity::class],
    version = 1,
    exportSchema = false  // Set to true and provide schemaLocation in production
)
abstract class ScaniumDatabase : RoomDatabase() {
    /**
     * Provides access to the items DAO.
     */
    abstract fun itemsDao(): ItemsDao

    companion object {
        private const val DATABASE_NAME = "scanium_database"

        @Volatile
        private var INSTANCE: ScaniumDatabase? = null

        /**
         * Gets the singleton database instance.
         * Uses double-checked locking to ensure thread safety.
         *
         * @param context Application context (not Activity context to avoid leaks)
         * @return The database instance
         */
        fun getInstance(context: Context): ScaniumDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        /**
         * Builds the database instance.
         * Can be overridden for testing with different configurations.
         */
        private fun buildDatabase(context: Context): ScaniumDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ScaniumDatabase::class.java,
                DATABASE_NAME
            )
                // Add migrations here when schema changes
                // .addMigrations(MIGRATION_1_2, MIGRATION_2_3, etc.)
                .build()
        }

        /**
         * Creates an in-memory database for testing.
         * Data is lost when the process is killed.
         *
         * @param context Test context
         * @return An in-memory database instance
         */
        fun createInMemoryDatabase(context: Context): ScaniumDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                ScaniumDatabase::class.java
            )
                .allowMainThreadQueries() // Only for testing
                .build()
        }

        /**
         * Clears the singleton instance.
         * Useful for testing to ensure a fresh database for each test.
         */
        @androidx.annotation.VisibleForTesting
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

/*
 * Example migration for future schema changes:
 *
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(database: SupportSQLiteDatabase) {
 *         // Add new column, rename table, etc.
 *         database.execSQL("ALTER TABLE scanned_items ADD COLUMN new_field TEXT")
 *     }
 * }
 */
