package com.scanium.diagnostics

import com.scanium.telemetry.TelemetryEvent

/**
 * Default implementation of [DiagnosticsPort] using [DiagnosticsBuffer] and [DiagnosticsBundleBuilder].
 *
 * This implementation:
 * - Stores breadcrumbs in a ring buffer with configurable limits
 * - Builds JSON diagnostics bundles with application context
 * - Provides thread-safe access to breadcrumbs
 *
 * ***REMOVED******REMOVED*** Usage
 * ```kotlin
 * val diagnosticsPort = DefaultDiagnosticsPort(
 *     contextProvider = { mapOf(
 *         "platform" to "android",
 *         "app_version" to "1.0.0",
 *         ...
 *     )},
 *     maxEvents = 200,
 *     maxBytes = 256 * 1024
 * )
 *
 * // Use in Telemetry facade
 * val telemetry = Telemetry(
 *     diagnosticsPort = diagnosticsPort,
 *     ...
 * )
 * ```
 *
 * @param contextProvider Function that provides current application context
 * @param maxEvents Maximum number of events to store (default: 200)
 * @param maxBytes Maximum byte size of stored events (default: 256KB)
 */
class DefaultDiagnosticsPort(
    private val contextProvider: () -> Map<String, String>,
    maxEvents: Int = DiagnosticsBuffer.DEFAULT_MAX_EVENTS,
    maxBytes: Int = DiagnosticsBuffer.DEFAULT_MAX_BYTES
) : DiagnosticsPort {

    private val buffer = DiagnosticsBuffer(maxEvents, maxBytes)
    private val bundleBuilder = DiagnosticsBundleBuilder()

    override fun appendBreadcrumb(event: TelemetryEvent) {
        buffer.append(event)
    }

    override fun buildDiagnosticsBundle(): ByteArray {
        val context = contextProvider()
        val events = buffer.snapshot()
        return bundleBuilder.buildJsonBytes(context, events)
    }

    override fun clearBreadcrumbs() {
        buffer.clear()
    }

    override fun breadcrumbCount(): Int {
        return buffer.size()
    }
}

/**
 * No-op implementation of [DiagnosticsPort] that discards all breadcrumbs.
 *
 * Useful for:
 * - Testing without diagnostics collection
 * - Disabling diagnostics in certain builds
 * - Default implementation when no diagnostics backend is configured
 */
object NoOpDiagnosticsPort : DiagnosticsPort {
    override fun appendBreadcrumb(event: TelemetryEvent) {
        // No-op: discard breadcrumb
    }

    override fun buildDiagnosticsBundle(): ByteArray {
        // Return empty JSON object
        return "{}".encodeToByteArray()
    }

    override fun clearBreadcrumbs() {
        // No-op: nothing to clear
    }

    override fun breadcrumbCount(): Int {
        return 0
    }
}
