package com.scanium.app.items.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.scanium.app.items.persistence.ScannedItemDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FirstSyncManager - Handles first sign-in flow with existing local items (Phase E)
 * Auto-syncs all existing items to the cloud when user signs in for the first time
 */
@Singleton
class FirstSyncManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val itemDao: ScannedItemDao,
        private val syncManager: ItemSyncManager,
    ) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences("first_sync_prefs", Context.MODE_PRIVATE)

        companion object {
            private const val TAG = "FirstSyncManager"
            private const val PREF_FIRST_SYNC_COMPLETED = "first_sync_completed"
        }

        /**
         * Check if first sync has been completed
         */
        fun isFirstSyncCompleted(): Boolean {
            return prefs.getBoolean(PREF_FIRST_SYNC_COMPLETED, false)
        }

        /**
         * Handle first sign-in flow
         * Auto-syncs all existing local items to the cloud
         * Returns: Pair<itemCount, syncSuccess>
         */
        suspend fun handleFirstSignIn(): Pair<Int, Boolean> =
            withContext(Dispatchers.IO) {
                if (isFirstSyncCompleted()) {
                    Log.d(TAG, "First sync already completed, skipping")
                    return@withContext 0 to true
                }

                // Count items (excluding deleted)
                val itemCount = itemDao.countItems()
                Log.i(TAG, "First sign-in: Found $itemCount items to sync")

                if (itemCount == 0) {
                    // No items to sync, mark as completed
                    markFirstSyncCompleted()
                    return@withContext 0 to true
                }

                // Mark all items for sync
                itemDao.markAllForSync()
                Log.d(TAG, "Marked all $itemCount items for sync")

                // Trigger sync
                when (val result = syncManager.syncAll()) {
                    is SyncResult.Success -> {
                        Log.i(
                            TAG,
                            "First sync completed successfully: pushed=${result.itemsPushed} items",
                        )
                        markFirstSyncCompleted()
                        itemCount to true
                    }
                    is SyncResult.NetworkError -> {
                        Log.w(TAG, "First sync failed: Network error", result.error)
                        // Don't mark as completed, will retry later
                        itemCount to false
                    }
                    is SyncResult.ServerError -> {
                        Log.e(TAG, "First sync failed: Server error", result.error)
                        // Don't mark as completed, will retry later
                        itemCount to false
                    }
                    is SyncResult.NotAuthenticated -> {
                        Log.w(TAG, "First sync failed: Not authenticated")
                        // This shouldn't happen, but don't mark as completed
                        itemCount to false
                    }
                }
            }

        /**
         * Mark first sync as completed
         */
        private fun markFirstSyncCompleted() {
            prefs.edit().putBoolean(PREF_FIRST_SYNC_COMPLETED, true).apply()
            Log.d(TAG, "First sync marked as completed")
        }

        /**
         * Reset first sync status (for testing)
         */
        fun resetFirstSync() {
            prefs.edit().putBoolean(PREF_FIRST_SYNC_COMPLETED, false).apply()
            Log.d(TAG, "First sync status reset")
        }
    }
