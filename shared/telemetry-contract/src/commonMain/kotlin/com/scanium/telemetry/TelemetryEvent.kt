package com.scanium.telemetry

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Core telemetry event contract for Scanium KMP.
 *
 * This model defines the stable schema for all telemetry events across Android and iOS.
 * All events must follow the naming conventions defined in [TelemetryEventNaming].
 * All attributes are sanitized by [AttributeSanitizer] to ensure PII safety.
 *
 * ***REMOVED******REMOVED*** Required Attributes
 * Events must include these required attributes:
 * - `platform`: "android" or "ios"
 * - `app_version`: Semantic version (e.g., "1.2.3")
 * - `build`: Build number (e.g., "42")
 * - `env`: Environment ("dev", "staging", "prod")
 * - `session_id`: Unique session identifier
 * - `data_region`: Data residency region ("EU", "US")
 *
 * ***REMOVED******REMOVED*** Optional Attributes
 * - `trace_id`: Distributed tracing identifier (for correlating events)
 *
 * ***REMOVED******REMOVED*** Example
 * ```kotlin
 * val event = TelemetryEvent(
 *     name = "scan.completed",
 *     severity = TelemetrySeverity.INFO,
 *     timestamp = Clock.System.now(),
 *     attributes = mapOf(
 *         "platform" to "android",
 *         "app_version" to "1.0.0",
 *         "build" to "1",
 *         "env" to "prod",
 *         "session_id" to "abc-123",
 *         "scan_duration_ms" to "1234",
 *         "items_detected" to "5"
 *     )
 * )
 * ```
 */
@Serializable
data class TelemetryEvent(
    /**
     * Event name following the naming convention (e.g., "scan.started", "ml.classification_failed").
     * See [TelemetryEventNaming] for prefix groups.
     */
    val name: String,
    /**
     * Severity level of the event.
     */
    val severity: TelemetrySeverity,
    /**
     * UTC timestamp when the event occurred.
     */
    val timestamp: Instant,
    /**
     * Event attributes as key-value pairs.
     * All attributes are sanitized to remove PII before export.
     * Maximum string value length: 1024 characters (enforced by sanitizer).
     */
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "Event name must not be blank" }
        require(attributes.all { it.key.isNotBlank() }) { "Attribute keys must not be blank" }
    }

    companion object {
        /** Required attribute: platform identifier */
        const val ATTR_PLATFORM = "platform"

        /** Required attribute: application version */
        const val ATTR_APP_VERSION = "app_version"

        /** Required attribute: build number */
        const val ATTR_BUILD = "build"

        /** Required attribute: environment (dev, staging, prod) */
        const val ATTR_ENV = "env"

        /** Required attribute: session identifier */
        const val ATTR_SESSION_ID = "session_id"

        /** Required attribute: data residency region */
        const val ATTR_DATA_REGION = "data_region"

        /** Optional attribute: trace identifier for distributed tracing */
        const val ATTR_TRACE_ID = "trace_id"

        /** Platform value: Android */
        const val PLATFORM_ANDROID = "android"

        /** Platform value: iOS */
        const val PLATFORM_IOS = "ios"

        /** Environment value: development */
        const val ENV_DEV = "dev"

        /** Environment value: staging */
        const val ENV_STAGING = "staging"

        /** Environment value: production */
        const val ENV_PROD = "prod"
    }
}
