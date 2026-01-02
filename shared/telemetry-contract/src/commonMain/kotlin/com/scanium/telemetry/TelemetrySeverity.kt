package com.scanium.telemetry

import kotlinx.serialization.Serializable

/**
 * Severity level for telemetry events.
 * Maps to standard observability severity levels.
 */
@Serializable
enum class TelemetrySeverity {
    /** Detailed information for debugging purposes */
    DEBUG,

    /** General informational messages */
    INFO,

    /** Warning conditions that don't prevent operation */
    WARN,

    /** Error conditions that affect functionality */
    ERROR,

    /** Critical failures requiring immediate attention */
    FATAL,
}
