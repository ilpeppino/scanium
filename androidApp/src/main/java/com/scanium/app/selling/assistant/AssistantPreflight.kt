package com.scanium.app.selling.assistant

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.BuildConfig
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.network.DeviceIdProvider
import com.scanium.app.network.security.RequestSigner
import com.scanium.app.selling.assistant.AssistantPreflightManagerImpl.Companion.PREFLIGHT_CACHE_TTL_MS
import com.scanium.app.selling.assistant.network.AssistantHttpConfig
import com.scanium.app.selling.assistant.network.AssistantOkHttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Status of the assistant backend availability from preflight check.
 *
 * **Important**: Preflight failure should NOT block user typing.
 * Only NOT_CONFIGURED (missing API key/base URL) should prevent sending messages.
 * All other errors are informational - the actual chat request may succeed.
 */
enum class PreflightStatus {
    /** Assistant backend is reachable and ready */
    AVAILABLE,

    /** Backend is temporarily unavailable (network, timeout, 503) */
    TEMPORARILY_UNAVAILABLE,

    /** Backend is unreachable (offline, DNS failure) */
    OFFLINE,

    /** Rate limited by backend */
    RATE_LIMITED,

    /** Authorization issue (401/403) */
    UNAUTHORIZED,

    /** Backend not configured (no URL or no API key) */
    NOT_CONFIGURED,

    /** Endpoint not found (404) - likely wrong base URL or tunnel route */
    ENDPOINT_NOT_FOUND,

    /**
     * Client configuration error (400) - preflight request was malformed.
     * This does NOT mean the assistant is unavailable - real chat may work.
     * Input should remain enabled.
     */
    CLIENT_ERROR,

    /** Preflight check in progress */
    CHECKING,

    /** No preflight result yet */
    UNKNOWN,
}

/**
 * Result of a preflight health check.
 */
data class PreflightResult(
    val status: PreflightStatus,
    val latencyMs: Long,
    val checkedAt: Long = System.currentTimeMillis(),
    val correlationId: String? = null,
    val reasonCode: String? = null,
    val retryAfterSeconds: Int? = null,
) {
    val isAvailable: Boolean get() = status == PreflightStatus.AVAILABLE
    val canRetry: Boolean
        get() =
            status in
                listOf(
                    PreflightStatus.TEMPORARILY_UNAVAILABLE,
                    PreflightStatus.OFFLINE,
                    PreflightStatus.RATE_LIMITED,
                    PreflightStatus.CLIENT_ERROR, // Client error should allow chat attempt
                )
}

private val Context.preflightDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "assistant_preflight",
)

/**
 * Interface for assistant preflight health checks and warm-up.
 */
interface AssistantPreflightManager {
    val currentResult: StateFlow<PreflightResult>
    val lastStatusFlow: Flow<PreflightResult?>

    suspend fun preflight(forceRefresh: Boolean = false): PreflightResult

    suspend fun warmUp(): Boolean

    fun cancelWarmUp()

    suspend fun clearCache()
}

/**
 * Default implementation of AssistantPreflightManager.
 *
 * Features:
 * - Quick health check with tight timeouts (uses AssistantHttpConfig.PREFLIGHT)
 * - Result caching to avoid redundant checks
 * - Warm-up mechanism with rate limiting (uses AssistantHttpConfig.WARMUP)
 * - Non-blocking - does not affect camera/scanning performance
 *
 * @param context Application context
 * @param preflightConfig HTTP config for preflight checks (tight timeouts)
 * @param warmupConfig HTTP config for warmup requests (moderate timeouts)
 */
class AssistantPreflightManagerImpl(
    private val context: Context,
    private val preflightConfig: AssistantHttpConfig = AssistantHttpConfig.PREFLIGHT,
    private val warmupConfig: AssistantHttpConfig = AssistantHttpConfig.WARMUP,
) : AssistantPreflightManager {
    companion object {
        private const val TAG = "AssistantPreflight"

        // Cache TTL - reuse result if checked within this time
        private const val PREFLIGHT_CACHE_TTL_MS = 30_000L // 30 seconds

        // Warm-up rate limiting
        private const val WARMUP_MIN_INTERVAL_MS = 600_000L // 10 minutes

        // DataStore keys
        private val KEY_LAST_STATUS = stringPreferencesKey("last_preflight_status")
        private val KEY_LAST_CHECKED = longPreferencesKey("last_preflight_checked")
        private val KEY_LAST_LATENCY = longPreferencesKey("last_preflight_latency")
        private val KEY_LAST_REASON = stringPreferencesKey("last_preflight_reason")
        private val KEY_LAST_WARMUP = longPreferencesKey("last_warmup_timestamp")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Use factory-created clients with standardized configuration
    private val client: OkHttpClient =
        AssistantOkHttpClientFactory.create(
            config = preflightConfig,
            logStartupPolicy = false, // Don't log for preflight, let chat client log
        )

    // Lazy-init warmup client only when needed
    private val warmupClient: OkHttpClient by lazy {
        AssistantOkHttpClientFactory.create(
            config = warmupConfig,
            logStartupPolicy = false,
        )
    }

    init {
        // Log preflight configuration on init
        AssistantOkHttpClientFactory.logConfigurationUsage("preflight", preflightConfig)
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true // Required: ensures items=[] and history=[] are sent
        }
    private val preflightPolicy = PreflightPolicy(json)

    private val _currentResult =
        MutableStateFlow(
            PreflightResult(
                status = PreflightStatus.UNKNOWN,
                latencyMs = 0,
            ),
        )
    override val currentResult: StateFlow<PreflightResult> = _currentResult.asStateFlow()

    private var warmupJob: Job? = null

    private val baseUrl: String
        get() = BuildConfig.SCANIUM_API_BASE_URL.orEmpty().trimEnd('/')

    private val apiKey: String?
        get() = SecureApiKeyStore(context).getApiKey()

    init {
        // Load persisted state on init
        scope.launch {
            loadPersistedState()
        }
    }

    /**
     * Flow of the last persisted preflight status for developer diagnostics.
     */
    override val lastStatusFlow: Flow<PreflightResult?> =
        context.preflightDataStore.data.map { prefs ->
            val status =
                prefs[KEY_LAST_STATUS]?.let {
                    runCatching { PreflightStatus.valueOf(it) }.getOrNull()
                } ?: return@map null

            PreflightResult(
                status = status,
                latencyMs = prefs[KEY_LAST_LATENCY] ?: 0,
                checkedAt = prefs[KEY_LAST_CHECKED] ?: 0,
                reasonCode = prefs[KEY_LAST_REASON],
            )
        }

    /**
     * Perform a preflight health check.
     *
     * - Returns cached result if checked within [PREFLIGHT_CACHE_TTL_MS]
     * - Uses tight timeouts to avoid blocking UI
     * - Does not retry on failure (caller can retry if needed)
     *
     * @param forceRefresh If true, ignores cache and always makes a network request
     * @return PreflightResult with availability status
     */
    override suspend fun preflight(forceRefresh: Boolean): PreflightResult {
        // Check cache first
        val cached = _currentResult.value
        val now = System.currentTimeMillis()
        val cacheAge = now - cached.checkedAt

        if (!forceRefresh && cached.status != PreflightStatus.UNKNOWN && cacheAge < PREFLIGHT_CACHE_TTL_MS) {
            ScaniumLog.d(TAG, "Preflight: using cached result (age=${cacheAge}ms) status=${cached.status}")
            return cached
        }

        // Mark as checking
        _currentResult.value = cached.copy(status = PreflightStatus.CHECKING)

        return withContext(Dispatchers.IO) {
            performPreflightCheck()
        }
    }

    /**
     * Perform a warm-up request to reduce first-response latency.
     *
     * - Only runs if preflight says Available
     * - Rate-limited to once per 10 minutes
     * - Runs in background, cancellable
     *
     * @return true if warm-up was initiated, false if skipped (rate-limited or unavailable)
     */
    override suspend fun warmUp(): Boolean {
        val current = _currentResult.value
        if (!current.isAvailable) {
            ScaniumLog.d(TAG, "Warmup: skipped, assistant not available (status=${current.status})")
            return false
        }

        // Check rate limit
        val lastWarmup = context.preflightDataStore.data.first()[KEY_LAST_WARMUP] ?: 0
        val now = System.currentTimeMillis()
        val timeSinceWarmup = now - lastWarmup

        if (timeSinceWarmup < WARMUP_MIN_INTERVAL_MS) {
            ScaniumLog.d(TAG, "Warmup: skipped, rate limited (${timeSinceWarmup}ms since last)")
            return false
        }

        // Cancel any existing warmup
        warmupJob?.cancel()

        warmupJob =
            scope.launch {
                try {
                    performWarmUp()
                    // Update last warmup timestamp
                    context.preflightDataStore.edit { prefs ->
                        prefs[KEY_LAST_WARMUP] = System.currentTimeMillis()
                    }
                    ScaniumLog.i(TAG, "Warmup: completed successfully")
                } catch (e: CancellationException) {
                    ScaniumLog.d(TAG, "Warmup: cancelled")
                } catch (e: Exception) {
                    ScaniumLog.w(TAG, "Warmup: failed", e)
                }
            }

        return true
    }

    /**
     * Cancel any ongoing warm-up request.
     * Call this when user leaves assistant screen or app goes background.
     */
    override fun cancelWarmUp() {
        warmupJob?.cancel()
        warmupJob = null
        ScaniumLog.d(TAG, "Warmup: cancelled by caller")
    }

    /**
     * Clear cached preflight result, forcing next preflight() to make a fresh request.
     */
    override suspend fun clearCache() {
        _currentResult.value =
            PreflightResult(
                status = PreflightStatus.UNKNOWN,
                latencyMs = 0,
            )
        context.preflightDataStore.edit { prefs ->
            prefs.remove(KEY_LAST_STATUS)
            prefs.remove(KEY_LAST_CHECKED)
            prefs.remove(KEY_LAST_LATENCY)
            prefs.remove(KEY_LAST_REASON)
        }
        ScaniumLog.d(TAG, "Preflight cache cleared")
    }

    private suspend fun loadPersistedState() {
        val prefs = context.preflightDataStore.data.first()
        val status =
            prefs[KEY_LAST_STATUS]?.let {
                runCatching { PreflightStatus.valueOf(it) }.getOrNull()
            }

        if (status != null) {
            _currentResult.value =
                PreflightResult(
                    status = status,
                    latencyMs = prefs[KEY_LAST_LATENCY] ?: 0,
                    checkedAt = prefs[KEY_LAST_CHECKED] ?: 0,
                    reasonCode = prefs[KEY_LAST_REASON],
                )
        }
    }

    private suspend fun persistResult(result: PreflightResult) {
        context.preflightDataStore.edit { prefs ->
            prefs[KEY_LAST_STATUS] = result.status.name
            prefs[KEY_LAST_CHECKED] = result.checkedAt
            prefs[KEY_LAST_LATENCY] = result.latencyMs
            result.reasonCode?.let { prefs[KEY_LAST_REASON] = it } ?: prefs.remove(KEY_LAST_REASON)
        }
    }

    private suspend fun performPreflightCheck(): PreflightResult {
        val startTime = System.currentTimeMillis()

        // Check configuration first
        if (baseUrl.isBlank()) {
            val result =
                PreflightResult(
                    status = PreflightStatus.NOT_CONFIGURED,
                    latencyMs = 0,
                    reasonCode = "base_url_not_configured",
                )
            _currentResult.value = result
            persistResult(result)
            ScaniumLog.w(TAG, "Preflight: NOT_CONFIGURED (no base URL)")
            return result
        }

        val key = apiKey
        if (key.isNullOrBlank()) {
            // Missing API key - mark as UNKNOWN to allow chat attempt
            // The actual chat might work (different auth flow) or fail with clear error
            val result =
                PreflightResult(
                    status = PreflightStatus.UNKNOWN,
                    latencyMs = 0,
                    reasonCode = "api_key_missing",
                )
            _currentResult.value = result
            persistResult(result)
            ScaniumLog.w(TAG, "Preflight: UNKNOWN (no API key - will allow chat attempt)")
            return result
        }

        // Build preflight request using the actual chat endpoint
        // This ensures we test the exact same auth path as real chat requests
        val endpoint = "$baseUrl/v1/assist/chat"
        val parsedUrl = runCatching { java.net.URL(endpoint) }.getOrNull()
        val host = parsedUrl?.host ?: "unknown"
        val path = parsedUrl?.path ?: "/v1/assist/chat"

        return try {
            val request =
                preflightPolicy.buildRequest(
                    context = context,
                    endpoint = endpoint,
                    apiKey = key,
                )

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val body = response.body?.string()

                ScaniumLog.i(
                    TAG,
                    "Preflight: host=$host path=$path status=${response.code} latencyMs=$latency",
                )

                val result =
                    if (response.isSuccessful) {
                        preflightPolicy.parseSuccessResponse(body, latency)
                    } else {
                        preflightPolicy.mapHttpFailure(response.code, latency)
                    }

                val retryAfter = response.header("Retry-After")?.toIntOrNull()
                val resultWithRetry =
                    if (retryAfter != null && result.status == PreflightStatus.RATE_LIMITED) {
                        result.copy(retryAfterSeconds = retryAfter)
                    } else {
                        result
                    }

                _currentResult.value = resultWithRetry
                persistResult(resultWithRetry)
                ScaniumLog.i(
                    TAG,
                    "Preflight: ${resultWithRetry.status} latency=${resultWithRetry.latencyMs}ms reason=${resultWithRetry.reasonCode}",
                )
                resultWithRetry
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val result = preflightPolicy.mapException(e, latency)
            _currentResult.value = result
            persistResult(result)
            result
        }
    }

    private suspend fun performWarmUp() {
        // Use the warmup client with moderate timeouts
        val key = apiKey ?: return

        // Warm-up by making a lightweight chat request with minimal payload
        // This primes the connection and any backend caches
        val endpoint = "$baseUrl/v1/assist/warmup"

        val requestBuilder =
            Request
                .Builder()
                .url(endpoint)
                .post(ByteArray(0).toRequestBody(null))
                .header("X-API-Key", key)
                .header("X-Client", "Scanium-Android")
                .header("X-App-Version", BuildConfig.VERSION_NAME)

        // Add device ID header for rate limiting
        // Backend expects raw device ID (not hashed) in this header
        val deviceId = DeviceIdProvider.getRawDeviceId(context)
        if (deviceId.isNotBlank()) {
            requestBuilder.header("X-Scanium-Device-Id", deviceId)
        }

        // Add session token for authenticated requests
        val authToken = SecureApiKeyStore(context).getAuthToken()
        if (authToken != null) {
            ScaniumLog.d(TAG, "Warmup: Adding Authorization header (token len=${authToken.length})")
            requestBuilder.header("Authorization", "Bearer $authToken")
        } else {
            ScaniumLog.w(TAG, "Warmup: No auth token - user not signed in")
        }

        RequestSigner.addSignatureHeaders(
            builder = requestBuilder,
            apiKey = key,
            requestBody = "",
        )

        val request = requestBuilder.build()

        try {
            warmupClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ScaniumLog.i(TAG, "Warmup: SUCCESS (${response.code})")
                } else {
                    ScaniumLog.w(TAG, "Warmup: FAILED (${response.code}) - ${response.body?.string()?.take(200)}")
                }
            }
        } catch (e: Exception) {
            // Warm-up failures are not critical
            ScaniumLog.d(TAG, "Warmup request failed: ${e.message}")
        }
    }
}
