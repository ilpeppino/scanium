package com.scanium.app.di

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.scanium.app.items.sync.ItemSyncWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * WorkManagerModule - Provides WorkManager and sets up periodic sync (Phase E)
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager {
        return WorkManager.getInstance(context)
    }

    /**
     * Initialize periodic sync worker
     * This should be called once at app startup
     */
    @Provides
    @Singleton
    fun providePeriodicSyncInitializer(
        workManager: WorkManager,
    ): PeriodicSyncInitializer {
        return PeriodicSyncInitializer(workManager)
    }
}

/**
 * Initializes periodic sync on app startup
 */
class PeriodicSyncInitializer(private val workManager: WorkManager) {
    /**
     * Set up periodic sync worker
     * Call this from Application.onCreate()
     */
    fun initialize() {
        val syncWorkRequest =
            PeriodicWorkRequestBuilder<ItemSyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                ).build()

        workManager.enqueueUniquePeriodicWork(
            ItemSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest,
        )
    }

    /**
     * Cancel periodic sync
     * Useful for testing or if user disables sync
     */
    fun cancel() {
        workManager.cancelUniqueWork(ItemSyncWorker.WORK_NAME)
    }
}
