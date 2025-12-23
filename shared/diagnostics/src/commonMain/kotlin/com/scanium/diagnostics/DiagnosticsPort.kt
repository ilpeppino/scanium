package com.scanium.diagnostics

import com.scanium.telemetry.TelemetryEvent

/**
 * Port interface for diagnostics breadcrumb collection and bundle generation.
 *
 * Implementations of this port are responsible for:
 * - Storing recent telemetry events in memory (ring buffer)
 * - Building diagnostic bundles for crash reports or "send report" functionality
 *
 * This port is intended to be used by:
 * - The Telemetry facade (to automatically record breadcrumbs)
 * - Crash reporting systems (to attach diagnostics to crash reports)
 * - Manual "send report" features (user-initiated diagnostics export)
 *
 * ***REMOVED******REMOVED*** Usage in Telemetry Facade
 * ```kotlin
 * class Telemetry(
 *     private val diagnosticsPort: DiagnosticsPort,
 *     ...
 * ) {
 *     fun event(...) {
 *         val event = TelemetryEvent(...)
 *         logPort.emit(event)
 *         diagnosticsPort.appendBreadcrumb(event)  // Auto-capture for diagnostics
 *     }
 * }
 * ```
 *
 * ***REMOVED******REMOVED*** Usage in Crash Handler
 * ```kotlin
 * class CrashHandler(private val diagnosticsPort: DiagnosticsPort) {
 *     fun handleCrash(exception: Throwable) {
 *         val diagnostics = diagnosticsPort.buildDiagnosticsBundle()
 *         crashReporter.send(exception, diagnosticsAttachment = diagnostics)
 *     }
 * }
 * ```
 */
interface DiagnosticsPort {
    /**
     * Appends a telemetry event as a diagnostic breadcrumb.
     *
     * Events are stored in a bounded ring buffer (FIFO eviction).
     * Events should be already sanitized (PII removed).
     *
     * @param event The telemetry event to store as a breadcrumb
     */
    fun appendBreadcrumb(event: TelemetryEvent)

    /**
     * Builds a diagnostics bundle containing recent breadcrumbs and context.
     *
     * The bundle is a UTF-8 encoded JSON byte array that can be attached to crash reports
     * or sent via "send report" functionality.
     *
     * @return UTF-8 encoded JSON diagnostics bundle
     */
    fun buildDiagnosticsBundle(): ByteArray

    /**
     * Clears all stored breadcrumbs.
     *
     * This should be called when starting a new session or after successfully
     * sending a diagnostics bundle.
     */
    fun clearBreadcrumbs()

    /**
     * Returns the current number of breadcrumbs in the buffer.
     *
     * Useful for monitoring/debugging.
     *
     * @return Number of breadcrumbs currently stored
     */
    fun breadcrumbCount(): Int
}
