package com.scanium.telemetry.ports

import com.scanium.telemetry.TelemetryEvent

/**
 * Port interface for emitting log/event telemetry.
 *
 * Implementations of this port are responsible for:
 * - Forwarding sanitized telemetry events to backend systems (OpenTelemetry, Sentry, custom analytics)
 * - Handling batching, retries, and error handling as appropriate for the backend
 *
 * The Telemetry facade guarantees that:
 * - Events are already sanitized (PII removed)
 * - Required attributes are present
 * - Event names follow naming conventions
 */
interface LogPort {
    /**
     * Emits a telemetry event.
     *
     * @param event The sanitized telemetry event to emit
     */
    fun emit(event: TelemetryEvent)
}
