package com.scanium.app.telemetry

import android.content.Context
import android.util.Log
import com.scanium.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mobile telemetry client for Scanium (Option C implementation)
 *
 * Flow: Mobile App → HTTPS Backend (/v1/telemetry/mobile) → Structured JSON logs → Loki
 *
 * Features:
 * - Background queue (does not block UI thread)
 * - Batching (sends every 30s or 10 events, whichever first)
 * - Retry logic with exponential backoff
 * - Feature flag support (ENABLE_TELEMETRY)
 * - Low-cardinality labels only
 * - PII protection (no user IDs, GPS, photos, etc.)
 *
 * See: docs/telemetry/MOBILE_TELEMETRY_SCHEMA.md
 */
class MobileTelemetryClient private constructor(
    private val context: Context,
    private val baseUrl: String,
    private val enabled: Boolean
) {
    companion object {
        private const val TAG = "MobileTelemetry"
        private const val BATCH_SIZE = 10
        private const val BATCH_INTERVAL_MS = 30_000L // 30 seconds
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L

        @Volatile
        private var instance: MobileTelemetryClient? = null

        /**
         * Initialize the telemetry client
         *
         * Call this once during app startup (e.g., in Application.onCreate)
         */
        fun initialize(
            context: Context,
            baseUrl: String,
            enabled: Boolean = true // Can be controlled by feature flag
        ) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = MobileTelemetryClient(
                            context.applicationContext,
                            baseUrl,
                            enabled
                        )
                        Log.i(TAG, "Telemetry initialized (enabled=$enabled, baseUrl=$baseUrl)")
                    }
                }
            }
        }

        /**
         * Get the singleton instance
         */
        fun getInstance(): MobileTelemetryClient {
            return instance ?: throw IllegalStateException(
                "MobileTelemetryClient not initialized. Call initialize() first."
            )
        }
    }

    // Session ID (random UUID per app launch)
    private val sessionId: String = UUID.randomUUID().toString()

    // Event queue (thread-safe)
    private val eventQueue = ConcurrentLinkedQueue<TelemetryEvent>()

    // Background scope for sending events
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Flag to track if background worker is running
    private val isWorkerRunning = AtomicBoolean(false)

    // HTTP client with short timeouts (telemetry should not block app)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    // JSON serializer
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        if (enabled) {
            startBackgroundWorker()
        }
    }

    /**
     * Send a telemetry event
     *
     * Events are queued and sent in batches. This method never blocks.
     *
     * @param eventName Event identifier (e.g., "app_launch", "scan_started")
     * @param attributes Event-specific metadata (limited keys, no PII)
     */
    fun send(eventName: String, attributes: Map<String, Any> = emptyMap()) {
        if (!enabled) {
            Log.d(TAG, "Telemetry disabled, ignoring event: $eventName")
            return
        }

        val event = TelemetryEvent(
            event_name = eventName,
            platform = "android",
            app_version = BuildConfig.VERSION_NAME,
            build_type = getBuildType(),
            timestamp_ms = System.currentTimeMillis(),
            session_id = sessionId,
            attributes = sanitizeAttributes(attributes)
        )

        eventQueue.offer(event)
        Log.d(TAG, "Event queued: $eventName (queue size: ${eventQueue.size})")

        // Trigger immediate send if queue is full
        if (eventQueue.size >= BATCH_SIZE) {
            triggerSend()
        }
    }

    /**
     * Flush all queued events immediately
     *
     * Useful for app shutdown or important lifecycle events
     */
    fun flush() {
        if (!enabled) return
        triggerSend()
    }

    /**
     * Sanitize attributes to remove PII and high-cardinality data
     */
    private fun sanitizeAttributes(attributes: Map<String, Any>): Map<String, Any> {
        val disallowedKeys = setOf(
            "user_id", "email", "phone", "device_id", "imei", "android_id",
            "gps", "latitude", "longitude", "location", "ip_address", "city",
            "item_name", "barcode", "receipt_text", "prompt", "photo",
            "token", "password", "api_key", "secret"
        )

        return attributes.filterKeys { key ->
            !disallowedKeys.any { blocked -> key.lowercase().contains(blocked) }
        }.filterValues { value ->
            // Only allow primitives (String, Number, Boolean)
            value is String || value is Number || value is Boolean
        }
    }

    /**
     * Get build type based on BuildConfig
     */
    private fun getBuildType(): String {
        return when {
            BuildConfig.DEBUG -> "dev"
            BuildConfig.BUILD_TYPE == "beta" -> "beta"
            BuildConfig.BUILD_TYPE == "release" -> "prod"
            else -> "dev"
        }
    }

    /**
     * Start background worker that periodically sends batches
     */
    private fun startBackgroundWorker() {
        if (isWorkerRunning.compareAndSet(false, true)) {
            scope.launch {
                while (isWorkerRunning.get()) {
                    try {
                        delay(BATCH_INTERVAL_MS)
                        sendBatch()
                    } catch (e: Exception) {
                        Log.e(TAG, "Background worker error", e)
                    }
                }
            }
            Log.d(TAG, "Background worker started")
        }
    }

    /**
     * Trigger immediate send (called when queue is full)
     */
    private fun triggerSend() {
        scope.launch {
            sendBatch()
        }
    }

    /**
     * Send a batch of events to the backend
     */
    private suspend fun sendBatch() {
        if (eventQueue.isEmpty()) return

        val batch = mutableListOf<TelemetryEvent>()
        repeat(BATCH_SIZE) {
            eventQueue.poll()?.let { batch.add(it) }
        }

        if (batch.isEmpty()) return

        Log.d(TAG, "Sending batch of ${batch.size} events")

        for (event in batch) {
            sendEventWithRetry(event)
        }
    }

    /**
     * Send a single event with retry logic
     */
    private suspend fun sendEventWithRetry(event: TelemetryEvent) {
        var attempt = 0
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                val success = sendEvent(event)
                if (success) {
                    Log.d(TAG, "Event sent: ${event.event_name}")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send event (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS): ${e.message}")
            }

            attempt++
            if (attempt < MAX_RETRY_ATTEMPTS) {
                delay(RETRY_DELAY_MS * attempt) // Exponential backoff
            }
        }

        Log.e(TAG, "Event dropped after $MAX_RETRY_ATTEMPTS attempts: ${event.event_name}")
    }

    /**
     * Send a single event via HTTP
     *
     * @return true if successful (2xx response), false otherwise
     */
    private fun sendEvent(event: TelemetryEvent): Boolean {
        val jsonBody = json.encodeToString(event)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/v1/telemetry/mobile")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }

    /**
     * Stop the background worker (call on app shutdown)
     */
    fun shutdown() {
        isWorkerRunning.set(false)
        flush()
        Log.i(TAG, "Telemetry client shut down")
    }
}

/**
 * Telemetry event data class
 *
 * Matches the schema defined in docs/telemetry/MOBILE_TELEMETRY_SCHEMA.md
 */
@Serializable
private data class TelemetryEvent(
    val event_name: String,
    val platform: String,
    val app_version: String,
    val build_type: String,
    val timestamp_ms: Long,
    val session_id: String,
    val attributes: Map<String, @Serializable(with = PrimitiveValueSerializer::class) Any> = emptyMap()
)

/**
 * Custom serializer for primitive values (String, Number, Boolean)
 */
private object PrimitiveValueSerializer : kotlinx.serialization.KSerializer<Any> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("PrimitiveValue")

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Any) {
        when (value) {
            is String -> encoder.encodeString(value)
            is Int -> encoder.encodeInt(value)
            is Long -> encoder.encodeLong(value)
            is Float -> encoder.encodeFloat(value)
            is Double -> encoder.encodeDouble(value)
            is Boolean -> encoder.encodeBoolean(value)
            else -> encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Any {
        throw UnsupportedOperationException("Deserialization not supported")
    }
}

/**
 * Convenience extension functions for common telemetry events
 */
object TelemetryEvents {
    fun appLaunch(launchType: String = "cold_start") {
        MobileTelemetryClient.getInstance().send(
            "app_launch",
            mapOf("launch_type" to launchType)
        )
    }

    fun scanStarted(scanSource: String = "camera") {
        MobileTelemetryClient.getInstance().send(
            "scan_started",
            mapOf("scan_source" to scanSource)
        )
    }

    fun scanCompleted(durationMs: Long, itemCount: Int, hasNutritionData: Boolean) {
        MobileTelemetryClient.getInstance().send(
            "scan_completed",
            mapOf(
                "duration_ms" to durationMs,
                "item_count" to itemCount,
                "has_nutrition_data" to hasNutritionData
            )
        )
    }

    fun assistClicked(context: String = "scan_result") {
        MobileTelemetryClient.getInstance().send(
            "assist_clicked",
            mapOf("context" to context)
        )
    }

    fun shareStarted(shareType: String = "receipt") {
        MobileTelemetryClient.getInstance().send(
            "share_started",
            mapOf("share_type" to shareType)
        )
    }

    fun errorShown(errorCode: String, errorCategory: String, isRecoverable: Boolean) {
        MobileTelemetryClient.getInstance().send(
            "error_shown",
            mapOf(
                "error_code" to errorCode,
                "error_category" to errorCategory,
                "is_recoverable" to isRecoverable
            )
        )
    }

    fun crashMarker(crashType: String = "uncaught_exception") {
        MobileTelemetryClient.getInstance().send(
            "crash_marker",
            mapOf("crash_type" to crashType)
        )
    }
}
