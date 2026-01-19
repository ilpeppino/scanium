package com.scanium.app.data

import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Helper to create a safe flow that catches IO exceptions and emits a default value.
 * This prevents crashes during startup if DataStore encounters transient IO issues.
 */
internal fun <T> Flow<Preferences>.safeMap(
    default: T,
    transform: (Preferences) -> T,
): Flow<T> =
    this
        .catch { exception ->
            if (exception is IOException) {
                Log.e(SETTINGS_DATASTORE_TAG, "DataStore IO error, using default", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            runCatching { transform(preferences) }.getOrElse { default }
        }
