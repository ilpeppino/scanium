package com.scanium.app.telemetry

import com.scanium.telemetry.ports.SpanContext
import com.scanium.telemetry.ports.TracePort
import com.scanium.app.telemetry.otlp.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Android implementation of [TracePort] that exports traces via OTLP.
 *
 * ***REMOVED******REMOVED*** Sampling
 * - Configurable sampling rate (0.0 to 1.0)
 * - Sampling decision made at span creation
 * - Non-sampled spans are no-ops (minimal overhead)
 *
 * ***REMOVED******REMOVED*** ID Generation
 * - Trace ID: 16-byte hex string (32 chars)
 * - Span ID: 8-byte hex string (16 chars)
 * - Random generation (no collision handling for simplicity)
 *
 * ***REMOVED******REMOVED*** Batching
 * - Accumulates completed spans in buffer
 * - Exports when batch size reached OR timeout elapsed
 *
 * ***REMOVED******REMOVED*** Thread Safety
 * Uses ConcurrentLinkedQueue for thread-safe span accumulation.
 */
class AndroidTracePortOtlp(
    private val config: OtlpConfiguration
) : TracePort {

    private val exporter = OtlpHttpExporter(config)
    private val buffer = ConcurrentLinkedQueue<Span>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val spanIdGenerator = AtomicLong(Random.nextLong())

    init {
        config.validate()

        // Start periodic span export
        scope.launch {
            while (true) {
                delay(config.batchTimeoutMs)
                flushIfNotEmpty()
            }
        }
    }

    override fun beginSpan(name: String, attributes: Map<String, String>): SpanContext {
        if (!config.enabled) {
            return NoOpSpanContext
        }

        // Sampling decision
        val shouldSample = Random.nextDouble() < config.traceSamplingRate
        if (!shouldSample) {
            return NoOpSpanContext
        }

        val traceId = generateTraceId()
        val spanId = generateSpanId()
        val startTimeNano = System.currentTimeMillis() * 1_000_000

        return OtlpSpanContext(
            traceId = traceId,
            spanId = spanId,
            name = name,
            startTimeNano = startTimeNano,
            attributes = attributes.toMutableMap(),
            onEnd = { span -> onSpanEnd(span) }
        )
    }

    private fun onSpanEnd(span: Span) {
        buffer.offer(span)

        // Flush if batch size reached
        if (buffer.size >= config.maxBatchSize) {
            flush()
        }
    }

    /**
     * Flushes all buffered spans to OTLP endpoint.
     */
    private fun flush() {
        val spans = mutableListOf<Span>()

        // Drain buffer
        while (spans.size < config.maxBatchSize) {
            val span = buffer.poll() ?: break
            spans.add(span)
        }

        if (spans.isEmpty()) return

        // Build OTLP request
        val request = ExportTraceServiceRequest(
            resourceSpans = listOf(
                ResourceSpans(
                    resource = exporter.buildResource(),
                    scopeSpans = listOf(
                        ScopeSpans(
                            scope = InstrumentationScope(
                                name = "com.scanium.telemetry",
                                version = "1.0.0"
                            ),
                            spans = spans
                        )
                    )
                )
            )
        )

        // Export (async, fire-and-forget)
        exporter.exportTraces(request)
    }

    private fun flushIfNotEmpty() {
        if (buffer.isNotEmpty()) {
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
        private val name: String,
        private val startTimeNano: Long,
        private val attributes: MutableMap<String, String>,
        private val onEnd: (Span) -> Unit
    ) : SpanContext {

        @Volatile
        private var ended = false
        private var errorMessage: String? = null

        override fun end(additionalAttributes: Map<String, String>) {
            if (ended) return
            ended = true

            attributes.putAll(additionalAttributes)

            val endTimeNano = System.currentTimeMillis() * 1_000_000

            val status = if (errorMessage != null) {
                SpanStatus(
                    code = SpanStatus.STATUS_CODE_ERROR,
                    message = errorMessage!!
                )
            } else {
                SpanStatus(
                    code = SpanStatus.STATUS_CODE_OK
                )
            }

            val span = Span(
                traceId = traceId,
                spanId = spanId,
                name = name,
                kind = Span.SPAN_KIND_INTERNAL,
                startTimeUnixNano = startTimeNano.toString(),
                endTimeUnixNano = endTimeNano.toString(),
                attributes = attributes.map { (k, v) ->
                    KeyValue(k, AnyValue.string(v))
                },
                status = status
            )

            onEnd(span)
        }

        override fun setAttribute(key: String, value: String) {
            if (!ended) {
                attributes[key] = value
            }
        }

        override fun recordError(error: String, attributes: Map<String, String>) {
            if (!ended) {
                this.errorMessage = error
                this.attributes.putAll(attributes)
            }
        }
    }

    private object NoOpSpanContext : SpanContext {
        override fun end(additionalAttributes: Map<String, String>) {}
        override fun setAttribute(key: String, value: String) {}
        override fun recordError(error: String, attributes: Map<String, String>) {}
    }
}
