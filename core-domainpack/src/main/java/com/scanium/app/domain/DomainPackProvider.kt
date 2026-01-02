package com.scanium.app.domain

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.scanium.app.domain.category.BasicCategoryEngine
import com.scanium.app.domain.category.CategoryEngine
import com.scanium.app.domain.repository.DomainPackRepository
import com.scanium.app.domain.repository.LocalDomainPackRepository

/**
 * Singleton provider for Domain Pack components.
 *
 * This class provides centralized access to:
 * - [DomainPackRepository]: Loads and caches Domain Pack configuration
 * - [CategoryEngine]: Selects domain categories for detected items
 *
 * **Usage:**
 * ```
 * // Initialize once in Application.onCreate() or MainActivity.onCreate()
 * DomainPackProvider.initialize(context)
 *
 * // Access anywhere in the app
 * val repository = DomainPackProvider.repository
 * val engine = DomainPackProvider.categoryEngine
 * ```
 *
 * **Design notes:**
 * - Singleton pattern for app-wide access (no DI framework needed)
 * - Lazy initialization to avoid blocking app startup
 * - Thread-safe initialization
 * - Provides convenient access without passing dependencies through layers
 *
 * **Future enhancement:**
 * If the app adopts Hilt/Koin, replace this with proper DI modules:
 * ```
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object DomainPackModule {
 *   @Provides @Singleton
 *   fun provideDomainPackRepository(context: Context): DomainPackRepository
 *   @Provides @Singleton
 *   fun provideCategoryEngine(repo: DomainPackRepository): CategoryEngine
 * }
 * ```
 */
object DomainPackProvider {
    private const val TAG = "DomainPackProvider"

    @Volatile
    private var _repository: DomainPackRepository? = null

    @Volatile
    private var _categoryEngine: CategoryEngine? = null

    private val initLock = Any()

    /**
     * Initialize the Domain Pack provider with an Android context.
     *
     * This method should be called once during app startup (e.g., in
     * Application.onCreate() or MainActivity.onCreate()).
     *
     * Subsequent calls are no-ops if already initialized.
     *
     * @param context Android context (application or activity context)
     */
    fun initialize(context: Context) {
        if (_repository != null && _categoryEngine != null) {
            Log.d(TAG, "Already initialized, skipping")
            return
        }

        synchronized(initLock) {
            // Double-check inside synchronized block
            if (_repository != null && _categoryEngine != null) {
                return
            }

            Log.i(TAG, "Initializing DomainPackProvider...")

            // Use application context to avoid leaks
            val appContext = context.applicationContext

            // Create repository
            val repo = LocalDomainPackRepository(appContext)
            _repository = repo

            // Create category engine
            val engine = BasicCategoryEngine(repo)
            _categoryEngine = engine

            Log.i(TAG, "DomainPackProvider initialized successfully")
        }
    }

    /**
     * Get the Domain Pack repository.
     *
     * @throws IllegalStateException if not initialized
     */
    val repository: DomainPackRepository
        get() =
            _repository ?: throw IllegalStateException(
                "DomainPackProvider not initialized. " +
                    "Call DomainPackProvider.initialize(context) first.",
            )

    /**
     * Get the category engine.
     *
     * @throws IllegalStateException if not initialized
     */
    val categoryEngine: CategoryEngine
        get() =
            _categoryEngine ?: throw IllegalStateException(
                "DomainPackProvider not initialized. " +
                    "Call DomainPackProvider.initialize(context) first.",
            )

    /**
     * Check if the provider is initialized.
     */
    val isInitialized: Boolean
        get() = _repository != null && _categoryEngine != null

    /**
     * Clear the provider state (useful for testing).
     *
     * WARNING: Only use this in tests. Calling in production code may cause
     * IllegalStateException in other parts of the app.
     */
    @VisibleForTesting
    fun reset() {
        synchronized(initLock) {
            _repository = null
            _categoryEngine = null
            Log.d(TAG, "Provider reset")
        }
    }
}
