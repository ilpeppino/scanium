package com.scanium.app.items.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * ItemSyncWorker - Periodic background sync using WorkManager (Phase E)
 * Runs every 15 minutes to sync items with backend when:
 * - Device has network connectivity
 * - Battery is not low
 * - User is authenticated
 */
@HiltWorker
class ItemSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val syncManager: ItemSyncManager,
    ) : CoroutineWorker(context, params) {
        companion object {
            private const val TAG = "ItemSyncWorker"
            const val WORK_NAME = "item_sync_periodic"
        }

        override suspend fun doWork(): Result {
            Log.d(TAG, "ItemSyncWorker started (attempt ${runAttemptCount + 1})")

            return try {
                when (val result = syncManager.syncAll()) {
                    is SyncResult.Success -> {
                        Log.i(
                            TAG,
                            "Sync completed: pushed=${result.itemsPushed}, pulled=${result.itemsPulled}, conflicts=${result.conflicts}",
                        )
                        Result.success()
                    }
                    is SyncResult.NotAuthenticated -> {
                        Log.w(TAG, "Sync skipped: User not authenticated")
                        // Don't retry - user needs to sign in
                        Result.failure()
                    }
                    is SyncResult.NetworkError -> {
                        Log.w(TAG, "Sync failed: Network error", result.error)
                        // Retry with exponential backoff
                        Result.retry()
                    }
                    is SyncResult.ServerError -> {
                        Log.e(TAG, "Sync failed: Server error", result.error)
                        // Retry with exponential backoff
                        Result.retry()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ItemSyncWorker failed unexpectedly", e)
                // Retry on unexpected errors
                Result.retry()
            }
        }
    }
