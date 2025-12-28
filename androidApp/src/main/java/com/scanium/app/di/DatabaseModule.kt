package com.scanium.app.di

import android.content.Context
import com.scanium.app.items.persistence.NoopScannedItemSyncer
import com.scanium.app.items.persistence.ScannedItemDao
import com.scanium.app.items.persistence.ScannedItemDatabase
import com.scanium.app.items.persistence.ScannedItemRepository
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.app.items.persistence.ScannedItemSyncer
import com.scanium.app.selling.persistence.ListingDraftDao
import com.scanium.app.selling.persistence.ListingDraftRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing database dependencies.
 * Part of ARCH-001: DI Framework Migration.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideScannedItemDatabase(
        @ApplicationContext context: Context
    ): ScannedItemDatabase {
        return ScannedItemDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideScannedItemDao(
        database: ScannedItemDatabase
    ): ScannedItemDao {
        return database.scannedItemDao()
    }

    @Provides
    @Singleton
    fun provideListingDraftDao(
        database: ScannedItemDatabase
    ): ListingDraftDao {
        return database.listingDraftDao()
    }

    @Provides
    @Singleton
    fun provideScannedItemSyncer(): ScannedItemSyncer {
        return NoopScannedItemSyncer
    }

    @Provides
    @Singleton
    fun provideScannedItemRepository(
        dao: ScannedItemDao,
        syncer: ScannedItemSyncer
    ): ScannedItemRepository {
        return ScannedItemRepository(dao, syncer)
    }

    @Provides
    @Singleton
    fun provideScannedItemStore(
        repository: ScannedItemRepository
    ): ScannedItemStore {
        return repository
    }

    @Provides
    @Singleton
    fun provideListingDraftRepository(
        dao: ListingDraftDao
    ): ListingDraftRepository {
        return ListingDraftRepository(dao)
    }
}
