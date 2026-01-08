package com.scanium.app.telemetry

import android.util.Log
import com.scanium.app.telemetry.otlp.*
import com.scanium.telemetry.TelemetryConfig
import com.scanium.telemetry.ports.SpanContext
import com.scanium.telemetry.ports.TracePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

/**
 * Android implementation of [TracePort] that exports traces via OTLP.
 *
 * ## Sampling
 * - Configurable sampling rate (0.0 to 1.0)
 * - Sampling decision made at span creation
 * - Non-sampled spans are no-ops (minimal overhead)
 *
 * ## ID Generation
 * - Trace ID: 16-byte hex string (32 chars)
 * - Span ID: 8-byte hex string (16 chars)
 * - Random generation (no collision handling for simplicity)
 *
 * ## Bounded Queue Behavior
 * - Maximum queue size enforced (prevents memory exhaustion)
 * - Drop policy: DROP_OLDEST (default) or DROP_NEWEST
 * - When queue is full, oldest/newest spans are dropped based on policy
 *
 * ## Batching
 * - Accumulates completed spans in bounded buffer
 * - Exports when batch size reached OR timeout elapsed
 *
 * ## Thread Safety
 * Uses ReentrantLock to protect bounded queue operations.
 */
class AndroidTracePortOtlp(
    private val telemetryConfig: TelemetryConfig,
    private val otlpConfig: OtlpConfiguration,
) : TracePort {
    private val tag = "AndroidTracePortOtlp"
    private val exporter = OtlpHttpExporter(otlpConfig, telemetryConfig)
    private val buffer = ArrayDeque<Span>()
    private val lock = ReentrantLock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val spanIdGenerator = AtomicLong(Random.nextLong())

    init {
        otlpConfig.validate()
        telemetryConfig // Validate via data class init

        // Start periodic span export
        scope.launch {
            while (true) {
                delay(telemetryConfig.flushIntervalMs)
                flushIfNotEmpty()
            }
        }
    }

    override fun beginSpan(
        name: String,
        attributes: Map<String, String>,
    ): SpanContext {
        if (!otlpConfig.enabled) {
            return NoOpSpanContext
        }

        // Sampling decision
        val shouldSample = Random.nextDouble() < telemetryConfig.traceSampleRate
        if (!shouldSample) {
            return NoOpSpanContext
        }

        val traceId = generateTraceId()
        val spanId = generateSpanId()
        val startTimeNano = System.currentTimeMillis() * 1_000_000

        return OtlpSpanContext(
            traceId = traceId,
            spanId = spanId,
            parentSpanId = null,
            name = name,
            startTimeNano = startTimeNano,
            attributes = attributes.toMutableMap(),
            onEnd = { span -> onSpanEnd(span) },
            sampled = true,
        )
    }

    override fun beginChildSpan(
        name: String,
        parent: SpanContext,
        attributes: Map<String, String>,
    ): SpanContext {
        if (!otlpConfig.enabled) {
            return NoOpSpanContext
        }

        // Inherit traceId from parent, generate new spanId
        val traceId = parent.getTraceId()
        if (traceId.isEmpty()) {
            // Parent is NoOp (not sampled), return NoOp child
            return NoOpSpanContext
        }

        val spanId = generateSpanId()
        val parentSpanId = parent.getSpanId()
        val startTimeNano = System.currentTimeMillis() * 1_000_000

        return OtlpSpanContext(
            traceId = traceId,
            spanId = spanId,
            parentSpanId = parentSpanId,
            name = name,
            startTimeNano = startTimeNano,
            attributes = attributes.toMutableMap(),
            onEnd = { span -> onSpanEnd(span) },
            sampled = true,
        )
    }

    private fun onSpanEnd(span: Span) {
        lock.withLock {
            // Check if queue is full
            if (buffer.size >= telemetryConfig.maxQueueSize) {
                when (telemetryConfig.dropPolicy) {
                    TelemetryConfig.DropPolicy.DROP_OLDEST -> {
                        buffer.removeFirstOrNull()
                        Log.w(tag, "Span queue full (${telemetryConfig.maxQueueSize}), dropped oldest span")
                    }
                    TelemetryConfig.DropPolicy.DROP_NEWEST -> {
                        Log.w(tag, "Span queue full (${telemetryConfig.maxQueueSize}), dropping newest span: ${span.name}")
                        return // Don't add the new span
                    }
                }
            }

            buffer.addLast(span)

            // Flush if batch size reached
            if (buffer.size >= telemetryConfig.maxBatchSize) {
                flush()
            }
        }
    }

    /**
     * Flushes all buffered spans to OTLP endpoint.
     * Must be called within lock or from synchronized context.
     */
    private fun flush() {
        val spans =
            lock.withLock {
                val batch = mutableListOf<Span>()
                while (batch.size < telemetryConfig.maxBatchSize && buffer.isNotEmpty()) {
                    batch.add(buffer.removeFirst())
                }
                batch
            }

        if (spans.isEmpty()) return

        // Build OTLP request
        val request =
            ExportTraceServiceRequest(
                resourceSpans =
                    listOf(
                        ResourceSpans(
                            resource = exporter.buildResource(),
                            scopeSpans =
                                listOf(
                                    ScopeSpans(
                                        scope =
                                            InstrumentationScope(
                                                name = "com.scanium.telemetry",
                                                version = "1.0.0",
                                            ),
                                        spans = spans,
                                    ),
                                ),
                        ),
                    ),
            )

        // Export (async, with retry and backoff)
        exporter.exportTraces(request)
    }

    private fun flushIfNotEmpty() {
        val hasSpans = lock.withLock { buffer.isNotEmpty() }
        if (hasSpans) {
            flush()
        }
    }

    private fun generateTraceId(): String {
        // 16-byte trace ID (32 hex chars)
        return Random.nextBytes(16).joinToString("") { "%02x".format(it) }
    }

    private fun generateSpanId(): String {
        // 8-byte span ID (16 hex chars)
        val id = spanIdGenerator.incrementAndGet()
        return "%016x".format(id)
    }

    /**
     * Implementation of SpanContext that builds an OTLP span.
     */
    private class OtlpSpanContext(
        private val traceId: String,
        private val spanId: String,
        private val parentSpanId: String? = null,
        private val name: String,
        private val startTimeNano: Long,
        private val attributes: MutableMap<String, String>,
        private val onEnd: (Span) -> Unit,
        private val sampled: Boolean,
    ) : SpanContext {
        @Volatile
        private var ended = false
        private var errorMessage: String? = null

        override fun end(additionalAttributes: Map<String, String>) {
            if (ended) return
            ended = true

            attributes.putAll(additionalAttributes)

            val endTimeNano = System.currentTimeMillis() * 1_000_000

            val status =
                if (errorMessage != null) {
                    SpanStatus(
                        code = SpanStatus.STATUS_CODE_ERROR,
                        message = errorMessage!!,
                    )
                } else {
                    SpanStatus(
                        code = SpanStatus.STATUS_CODE_OK,
                    )
                }

            val span =
                Span(
                    traceId = traceId,
                    spanId = spanId,
                    parentSpanId = parentSpanId,
                    name = name,
                    kind = Span.SPAN_KIND_INTERNAL,
                    startTimeUnixNano = startTimeNano.toString(),
                    endTimeUnixNano = endTimeNano.toString(),
                    attributes =
                        attributes.map { (k, v) ->
                            KeyValue(k, AnyValue.string(v))
                        },
                    status = status,
                )

            onEnd(span)
        }

        override fun setAttribute(
            key: String,
            value: String,
        ) {
            if (!ended) {
                attributes[key] = value
            }
        }

        override fun recordError(
            error: String,
            attributes: Map<String, String>,
        ) {
            if (!ended) {
                this.errorMessage = error
                this.attributes.putAll(attributes)
            }
        }

        override fun getTraceId(): String = traceId

        override fun getSpanId(): String = spanId

        override fun getTraceFlags(): String = if (sampled) "01" else "00"
    }

    private object NoOpSpanContext : SpanContext {
        override fun end(additionalAttributes: Map<String, String>) {}

        override fun setAttribute(
            key: String,
            value: String,
        ) {}

        override fun recordError(
            error: String,
            attributes: Map<String, String>,
        ) {}

        override fun getTraceId(): String = ""

        override fun getSpanId(): String = ""

        override fun getTraceFlags(): String = "00"
    }
}
