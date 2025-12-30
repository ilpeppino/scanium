package com.scanium.app.testing

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.scanium.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Test configuration override for instrumented tests.
 *
 * Provides a way to override backend configuration (base URL, API key) and
 * force cloud mode enabled for regression tests.
 *
 * ***REMOVED******REMOVED*** Usage in Tests
 *
 * Pass instrumentation arguments when running tests:
 * ```
 * ./gradlew :androidApp:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.SCANIUM_BASE_URL=https://api.example.com \
 *     -Pandroid.testInstrumentationRunnerArguments.SCANIUM_API_KEY=test-api-key
 * ```
 *
 * Or via environment variables set before test run:
 * ```
 * export SCANIUM_BASE_URL=https://api.example.com
 * export SCANIUM_API_KEY=test-api-key
 * ```
 *
 * The override is applied on debug builds only and does not affect release builds.
 */
object TestConfigOverride {
    private const val TAG = "TestConfigOverride"

    // Instrumentation argument keys
    const val ARG_BASE_URL = "SCANIUM_BASE_URL"
    const val ARG_API_KEY = "SCANIUM_API_KEY"
    const val ARG_FORCE_CLOUD_MODE = "SCANIUM_FORCE_CLOUD_MODE"

    private val _isTestMode = MutableStateFlow(false)
    val isTestMode: StateFlow<Boolean> = _isTestMode.asStateFlow()

    private var _baseUrl: String? = null
    private var _apiKey: String? = null
    private var _forceCloudMode: Boolean = false
    private var _initialized = false

    /**
     * Override base URL for backend API (null = use BuildConfig default).
     */
    val baseUrl: String?
        get() = if (_initialized && BuildConfig.DEBUG) _baseUrl else null

    /**
     * Override API key for backend authentication (null = use secure store default).
     */
    val apiKey: String?
        get() = if (_initialized && BuildConfig.DEBUG) _apiKey else null

    /**
     * Whether cloud mode should be forced enabled for tests.
     */
    val forceCloudMode: Boolean
        get() = if (_initialized && BuildConfig.DEBUG) _forceCloudMode else false

    /**
     * Whether test configuration has been applied.
     */
    val isActive: Boolean
        get() = _initialized && BuildConfig.DEBUG && (_baseUrl != null || _apiKey != null || _forceCloudMode)

    /**
     * Initialize from instrumentation arguments (called from HiltTestRunner or Application).
     *
     * @param arguments Bundle from InstrumentationRegistry.getArguments() or similar
     */
    fun initFromArguments(arguments: Bundle?) {
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "Test config override ignored in non-debug build")
            return
        }

        arguments?.let { args ->
            _baseUrl = args.getString(ARG_BASE_URL)?.takeIf { it.isNotBlank() }
            _apiKey = args.getString(ARG_API_KEY)?.takeIf { it.isNotBlank() }
            _forceCloudMode = args.getString(ARG_FORCE_CLOUD_MODE)?.toBoolean() ?: false

            _initialized = true
            _isTestMode.value = _baseUrl != null || _apiKey != null

            Log.i(TAG, buildString {
                append("Test config initialized: ")
                append("baseUrl=${_baseUrl?.take(30)}..., ")
                append("apiKey=${if (_apiKey != null) "[set]" else "[not set]"}, ")
                append("forceCloudMode=$_forceCloudMode")
            })
        }
    }

    /**
     * Check if we have valid cloud configuration for testing.
     */
    fun hasCloudConfig(): Boolean {
        val effectiveBaseUrl = baseUrl ?: BuildConfig.SCANIUM_API_BASE_URL
        return effectiveBaseUrl.isNotBlank()
    }

    /**
     * Get effective base URL (test override or BuildConfig default).
     */
    fun getEffectiveBaseUrl(): String {
        return baseUrl ?: BuildConfig.SCANIUM_API_BASE_URL
    }

    /**
     * Reset test configuration (useful for test cleanup).
     */
    fun reset() {
        _baseUrl = null
        _apiKey = null
        _forceCloudMode = false
        _initialized = false
        _isTestMode.value = false
    }
}
