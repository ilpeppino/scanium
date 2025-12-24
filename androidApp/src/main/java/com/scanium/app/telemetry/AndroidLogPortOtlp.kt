package com.scanium.app.telemetry

import com.scanium.telemetry.TelemetryEvent
import com.scanium.telemetry.TelemetrySeverity
import com.scanium.telemetry.ports.LogPort
import com.scanium.app.telemetry.otlp.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Android implementation of [LogPort] that exports logs via OTLP.
 *
 * ## Batching Strategy
 * - Accumulates log events in memory buffer
 * - Exports when batch size reached OR timeout elapsed
 * - Async/non-blocking (fire-and-forget)
 *
 * ## Thread Safety
 * Uses ConcurrentLinkedQueue for thread-safe event accumulation.
 *
 * ## Example Usage
 * ```kotlin
 * val config = OtlpConfiguration.localDev(serviceVersion = "1.0.0")
 * val logPort = AndroidLogPortOtlp(config)
 *
 * val telemetry = Telemetry(
 *     defaultAttributesProvider = myProvider,
 *     logPort = logPort,
 *     ...
 * )
 * ```
 */
class AndroidLogPortOtlp(
    private val config: OtlpConfiguration
) : LogPort {

    private val exporter = OtlpHttpExporter(config)
    private val buffer = ConcurrentLinkedQueue<TelemetryEvent>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        config.validate()

        // Start periodic batch export
        scope.launch {
            while (true) {
                delay(config.batchTimeoutMs)
                flushIfNotEmpty()
            }
        }
    }

    override fun emit(event: TelemetryEvent) {
        if (!config.enabled) return

        buffer.offer(event)

        // Flush if batch size reached
        if (buffer.size >= config.maxBatchSize) {
            flush()
        }
    }

    /**
     * Flushes all buffered events to OTLP endpoint.
     */
    private fun flush() {
        val events = mutableListOf<TelemetryEvent>()

        // Drain buffer
        while (events.size < config.maxBatchSize) {
            val event = buffer.poll() ?: break
            events.add(event)
        }

        if (events.isEmpty()) return

        // Convert to OTLP log records
        val logRecords = events.map { event ->
            LogRecord(
                timeUnixNano = (event.timestamp.toEpochMilliseconds() * 1_000_000).toString(),
                severityNumber = mapSeverity(event.severity),
                severityText = event.severity.name,
                body = AnyValue.string(event.name),
                attributes = event.attributes.map { (key, value) ->
                    KeyValue(key, AnyValue.string(value))
                }
            )
        }

        // Build OTLP request
        val request = ExportLogsServiceRequest(
            resourceLogs = listOf(
                ResourceLogs(
                    resource = exporter.buildResource(),
                    scopeLogs = listOf(
                        ScopeLogs(
                            scope = InstrumentationScope(
                                name = "com.scanium.telemetry",
                                version = "1.0.0"
                            ),
                            logRecords = logRecords
                        )
                    )
                )
            )
        )

        // Export (async, fire-and-forget)
        exporter.exportLogs(request)
    }

    private fun flushIfNotEmpty() {
        if (buffer.isNotEmpty()) {
            flush()
        }
    }

    private fun mapSeverity(severity: TelemetrySeverity): Int {
        return when (severity) {
            TelemetrySeverity.DEBUG -> LogRecord.SEVERITY_DEBUG
            TelemetrySeverity.INFO -> LogRecord.SEVERITY_INFO
            TelemetrySeverity.WARN -> LogRecord.SEVERITY_WARN
            TelemetrySeverity.ERROR -> LogRecord.SEVERITY_ERROR
            TelemetrySeverity.FATAL -> LogRecord.SEVERITY_FATAL
        }
    }
}
