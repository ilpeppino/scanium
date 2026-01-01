package com.scanium.app.telemetry

import android.util.Log
import com.scanium.app.telemetry.otlp.*
import com.scanium.telemetry.TelemetryConfig
import com.scanium.telemetry.TelemetryEvent
import com.scanium.telemetry.TelemetrySeverity
import com.scanium.telemetry.ports.LogPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Android implementation of [LogPort] that exports logs via OTLP.
 *
 * ## Batching Strategy
 * - Accumulates log events in bounded queue
 * - Exports when batch size reached OR timeout elapsed
 * - Async/non-blocking (fire-and-forget)
 *
 * ## Bounded Queue Behavior
 * - Maximum queue size enforced (prevents memory exhaustion)
 * - Drop policy: DROP_OLDEST (default) or DROP_NEWEST
 * - When queue is full, oldest/newest events are dropped based on policy
 *
 * ## Thread Safety
 * Uses ReentrantLock to protect bounded queue operations.
 *
 * ## Example Usage
 * ```kotlin
 * val telemetryConfig = TelemetryConfig.development()
 * val otlpConfig = OtlpConfiguration.localDev(serviceVersion = "1.0.0")
 * val logPort = AndroidLogPortOtlp(telemetryConfig, otlpConfig)
 * ```
 */
class AndroidLogPortOtlp(
    private val telemetryConfig: TelemetryConfig,
    private val otlpConfig: OtlpConfiguration,
) : LogPort {
    private val tag = "AndroidLogPortOtlp"
    private val exporter = OtlpHttpExporter(otlpConfig, telemetryConfig)
    private val buffer = ArrayDeque<TelemetryEvent>()
    private val lock = ReentrantLock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        otlpConfig.validate()
        telemetryConfig // Validate via data class init

        // Start periodic batch export
        scope.launch {
            while (true) {
                delay(telemetryConfig.flushIntervalMs)
                flushIfNotEmpty()
            }
        }
    }

    override fun emit(event: TelemetryEvent) {
        if (!otlpConfig.enabled) return

        lock.withLock {
            // Check if queue is full
            if (buffer.size >= telemetryConfig.maxQueueSize) {
                when (telemetryConfig.dropPolicy) {
                    TelemetryConfig.DropPolicy.DROP_OLDEST -> {
                        buffer.removeFirstOrNull()
                        Log.w(tag, "Queue full (${telemetryConfig.maxQueueSize}), dropped oldest event")
                    }
                    TelemetryConfig.DropPolicy.DROP_NEWEST -> {
                        Log.w(tag, "Queue full (${telemetryConfig.maxQueueSize}), dropping newest event: ${event.name}")
                        return // Don't add the new event
                    }
                }
            }

            buffer.addLast(event)

            // Flush if batch size reached
            if (buffer.size >= telemetryConfig.maxBatchSize) {
                flush()
            }
        }
    }

    /**
     * Flushes all buffered events to OTLP endpoint.
     * Must be called within lock or from synchronized context.
     */
    private fun flush() {
        val events =
            lock.withLock {
                val batch = mutableListOf<TelemetryEvent>()
                while (batch.size < telemetryConfig.maxBatchSize && buffer.isNotEmpty()) {
                    batch.add(buffer.removeFirst())
                }
                batch
            }

        if (events.isEmpty()) return

        // Convert to OTLP log records
        val logRecords =
            events.map { event ->
                LogRecord(
                    timeUnixNano = (event.timestamp.toEpochMilliseconds() * 1_000_000).toString(),
                    severityNumber = mapSeverity(event.severity),
                    severityText = event.severity.name,
                    body = AnyValue.string(event.name),
                    attributes =
                        event.attributes.map { (key, value) ->
                            KeyValue(key, AnyValue.string(value))
                        },
                )
            }

        // Build OTLP request
        val request =
            ExportLogsServiceRequest(
                resourceLogs =
                    listOf(
                        ResourceLogs(
                            resource = exporter.buildResource(),
                            scopeLogs =
                                listOf(
                                    ScopeLogs(
                                        scope =
                                            InstrumentationScope(
                                                name = "com.scanium.telemetry",
                                                version = "1.0.0",
                                            ),
                                        logRecords = logRecords,
                                    ),
                                ),
                        ),
                    ),
            )

        // Export (async, with retry and backoff)
        exporter.exportLogs(request)
    }

    private fun flushIfNotEmpty() {
        val hasEvents = lock.withLock { buffer.isNotEmpty() }
        if (hasEvents) {
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
