package com.scanium.diagnostics

import com.scanium.telemetry.TelemetryEvent
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Diagnostic bundle data structure for crash reports.
 *
 * @property generatedAt ISO 8601 timestamp when the bundle was generated
 * @property context Application context (platform, version, build, etc.)
 * @property events Recent telemetry events (breadcrumbs)
 */
@Serializable
data class DiagnosticsBundle(
    val generatedAt: String,
    val context: Map<String, String>,
    val events: List<TelemetryEvent>
)

/**
 * Builds compact diagnostics bundles for crash reports and "send report" functionality.
 *
 * The bundle includes:
 * - Generation timestamp
 * - Application context (platform, version, build, env, session_id)
 * - Recent telemetry events (breadcrumbs)
 *
 * All events are assumed to be already sanitized (PII removed).
 *
 * ***REMOVED******REMOVED*** Usage
 * ```kotlin
 * val builder = DiagnosticsBundleBuilder()
 *
 * val context = mapOf(
 *     "platform" to "android",
 *     "app_version" to "1.0.0",
 *     "build" to "42",
 *     "env" to "prod",
 *     "session_id" to "abc-123"
 * )
 *
 * val events = buffer.snapshot()
 *
 * // JSON string output
 * val jsonString = builder.buildJsonString(context, events)
 *
 * // Byte array output (UTF-8)
 * val bytes = builder.buildJsonBytes(context, events)
 * ```
 *
 * ***REMOVED******REMOVED*** Output Format
 * ```json
 * {
 *   "generatedAt": "2025-12-24T10:30:00Z",
 *   "context": {
 *     "platform": "android",
 *     "app_version": "1.0.0",
 *     "build": "42",
 *     "env": "prod",
 *     "session_id": "abc-123"
 *   },
 *   "events": [
 *     {
 *       "name": "scan.started",
 *       "severity": "INFO",
 *       "timestamp": "2025-12-24T10:29:55Z",
 *       "attributes": {...}
 *     },
 *     ...
 *   ]
 * }
 * ```
 */
class DiagnosticsBundleBuilder {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * Builds a diagnostics bundle as a JSON string.
     *
     * @param context Application context attributes
     * @param events Recent telemetry events (already sanitized)
     * @return JSON string representation of the bundle
     */
    fun buildJsonString(
        context: Map<String, String>,
        events: List<TelemetryEvent>
    ): String {
        val bundle = DiagnosticsBundle(
            generatedAt = Clock.System.now().toString(),
            context = context,
            events = events
        )

        return json.encodeToString(bundle)
    }

    /**
     * Builds a diagnostics bundle as a UTF-8 byte array.
     *
     * @param context Application context attributes
     * @param events Recent telemetry events (already sanitized)
     * @return UTF-8 encoded JSON byte array
     */
    fun buildJsonBytes(
        context: Map<String, String>,
        events: List<TelemetryEvent>
    ): ByteArray {
        return buildJsonString(context, events).encodeToByteArray()
    }

    /**
     * Builds a diagnostics bundle with a capped number of events.
     *
     * If the event list exceeds maxEvents, only the most recent events are included.
     *
     * @param context Application context attributes
     * @param events Recent telemetry events (already sanitized)
     * @param maxEvents Maximum number of events to include (default: 100)
     * @return JSON string representation of the bundle
     */
    fun buildJsonStringCapped(
        context: Map<String, String>,
        events: List<TelemetryEvent>,
        maxEvents: Int = 100
    ): String {
        val cappedEvents = if (events.size > maxEvents) {
            events.takeLast(maxEvents)
        } else {
            events
        }

        return buildJsonString(context, cappedEvents)
    }
}
