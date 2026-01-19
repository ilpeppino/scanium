package com.scanium.app.telemetry

import com.scanium.app.telemetry.otlp.*
import com.scanium.telemetry.TelemetryConfig
import com.scanium.telemetry.ports.MetricPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Android implementation of [MetricPort] that exports metrics via OTLP.
 *
 * ***REMOVED******REMOVED*** Metric Types
 * - Counters: Cumulative monotonic sum (e.g., total scans)
 * - Timers: Reported as gauge (last value)
 * - Gauges: Current value (e.g., items in memory)
 *
 * ***REMOVED******REMOVED*** Aggregation Strategy
 * - Counters: Sum delta values between exports
 * - Timers: Track last value only
 * - Gauges: Track last value only
 *
 * ***REMOVED******REMOVED*** Batching
 * - Exports all metrics periodically (batch timeout)
 * - No max batch size (metrics are lightweight)
 *
 * ***REMOVED******REMOVED*** Thread Safety
 * Uses ConcurrentHashMap for thread-safe metric updates.
 */
class AndroidMetricPortOtlp(
    private val telemetryConfig: TelemetryConfig,
    private val otlpConfig: OtlpConfiguration,
) : MetricPort {
    private val exporter = OtlpHttpExporter(otlpConfig, telemetryConfig)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Track counters (name+attrs -> cumulative value)
    private val counters = ConcurrentHashMap<String, CounterData>()

    // Track timers (name+attrs -> last value)
    private val timers = ConcurrentHashMap<String, GaugeData>()

    // Track gauges (name+attrs -> last value)
    private val gauges = ConcurrentHashMap<String, GaugeData>()

    // Track histograms (name+attrs -> bucket counts)
    private val histograms = ConcurrentHashMap<String, HistogramData>()

    // Default histogram bucket boundaries for latency metrics (in ms)
    // These boundaries create buckets: [0-10), [10-25), [25-50), ..., [500-1000), [1000-∞)
    private val defaultHistogramBounds = listOf(10.0, 25.0, 50.0, 75.0, 100.0, 150.0, 200.0, 300.0, 500.0, 1000.0)

    init {
        otlpConfig.validate()
        telemetryConfig // Validate via data class init

        // Start periodic metric export
        scope.launch {
            while (true) {
                delay(telemetryConfig.flushIntervalMs)
                flush()
            }
        }
    }

    override fun counter(
        name: String,
        delta: Long,
        attributes: Map<String, String>,
    ) {
        if (!otlpConfig.enabled) return

        val key = metricKey(name, attributes)
        counters.compute(key) { _, existing ->
            val newValue = (existing?.value ?: 0L) + delta
            CounterData(name, newValue, attributes, System.currentTimeMillis())
        }
    }

    override fun timer(
        name: String,
        millis: Long,
        attributes: Map<String, String>,
    ) {
        if (!otlpConfig.enabled) return

        val key = metricKey(name, attributes)
        timers[key] = GaugeData(name, millis.toDouble(), attributes, System.currentTimeMillis())
    }

    override fun gauge(
        name: String,
        value: Double,
        attributes: Map<String, String>,
    ) {
        if (!otlpConfig.enabled) return

        val key = metricKey(name, attributes)
        gauges[key] = GaugeData(name, value, attributes, System.currentTimeMillis())
    }

    override fun histogram(
        name: String,
        value: Double,
        attributes: Map<String, String>,
    ) {
        if (!otlpConfig.enabled) return

        val key = metricKey(name, attributes)
        histograms.compute(key) { _, existing ->
            val data =
                existing ?: HistogramData(
                    name = name,
                    attributes = attributes,
                    bounds = defaultHistogramBounds,
                    bucketCounts = LongArray(defaultHistogramBounds.size + 1),
                    count = 0L,
                    sum = 0.0,
                    timestampMs = System.currentTimeMillis(),
                )

            // Find the appropriate bucket for this value
            var bucketIndex = data.bounds.size // Default to overflow bucket
            for (i in data.bounds.indices) {
                if (value < data.bounds[i]) {
                    bucketIndex = i
                    break
                }
            }

            // Update bucket count, total count, and sum
            data.bucketCounts[bucketIndex]++
            data.copy(
                count = data.count + 1,
                sum = data.sum + value,
                timestampMs = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Exports all current metrics to OTLP endpoint.
     */
    private fun flush() {
        val metrics = mutableListOf<Metric>()

        // Export counters as monotonic sums
        counters.forEach { (_, data) ->
            metrics.add(
                Metric(
                    name = data.name,
                    unit = "1",
                    sum =
                        Sum(
                            dataPoints =
                                listOf(
                                    NumberDataPoint(
                                        timeUnixNano = (data.timestampMs * 1_000_000).toString(),
                                        asInt = data.value,
                                        attributes =
                                            data.attributes.map { (k, v) ->
                                                KeyValue(k, AnyValue.string(v))
                                            },
                                    ),
                                ),
                            aggregationTemporality = 2,
// CUMULATIVE
                            isMonotonic = true,
                        ),
                ),
            )
        }

        // Export timers as gauges (milliseconds)
        timers.forEach { (_, data) ->
            metrics.add(
                Metric(
                    name = data.name,
                    unit = "ms",
                    gauge =
                        Gauge(
                            dataPoints =
                                listOf(
                                    NumberDataPoint(
                                        timeUnixNano = (data.timestampMs * 1_000_000).toString(),
                                        asDouble = data.value,
                                        attributes =
                                            data.attributes.map { (k, v) ->
                                                KeyValue(k, AnyValue.string(v))
                                            },
                                    ),
                                ),
                        ),
                ),
            )
        }

        // Export gauges
        gauges.forEach { (_, data) ->
            metrics.add(
                Metric(
                    name = data.name,
                    gauge =
                        Gauge(
                            dataPoints =
                                listOf(
                                    NumberDataPoint(
                                        timeUnixNano = (data.timestampMs * 1_000_000).toString(),
                                        asDouble = data.value,
                                        attributes =
                                            data.attributes.map { (k, v) ->
                                                KeyValue(k, AnyValue.string(v))
                                            },
                                    ),
                                ),
                        ),
                ),
            )
        }

        // Export histograms
        histograms.forEach { (_, data) ->
            metrics.add(
                Metric(
                    name = data.name,
                    unit = "ms",
                    histogram =
                        Histogram(
                            dataPoints =
                                listOf(
                                    HistogramDataPoint(
                                        timeUnixNano = (data.timestampMs * 1_000_000).toString(),
                                        count = data.count,
                                        sum = data.sum,
                                        bucketCounts = data.bucketCounts.toList(),
                                        explicitBounds = data.bounds,
                                        attributes =
                                            data.attributes.map { (k, v) ->
                                                KeyValue(k, AnyValue.string(v))
                                            },
                                    ),
                                ),
                            aggregationTemporality = 2, // CUMULATIVE
                        ),
                ),
            )
        }

        if (metrics.isEmpty()) return

        // Build OTLP request
        val request =
            ExportMetricsServiceRequest(
                resourceMetrics =
                    listOf(
                        ResourceMetrics(
                            resource = exporter.buildResource(),
                            scopeMetrics =
                                listOf(
                                    ScopeMetrics(
                                        scope =
                                            InstrumentationScope(
                                                name = "com.scanium.telemetry",
                                                version = "1.0.0",
                                            ),
                                        metrics = metrics,
                                    ),
                                ),
                        ),
                    ),
            )

        // Export (async, fire-and-forget)
        exporter.exportMetrics(request)
    }

    private fun metricKey(
        name: String,
        attributes: Map<String, String>,
    ): String {
        val attrKey =
            attributes.entries
                .sortedBy { it.key }
                .joinToString(",") { "${it.key}=${it.value}" }
        return "$name{$attrKey}"
    }

    private data class CounterData(
        val name: String,
        val value: Long,
        val attributes: Map<String, String>,
        val timestampMs: Long,
    )

    private data class GaugeData(
        val name: String,
        val value: Double,
        val attributes: Map<String, String>,
        val timestampMs: Long,
    )

    private data class HistogramData(
        val name: String,
        val attributes: Map<String, String>,
        val bounds: List<Double>,
        val bucketCounts: LongArray,
        val count: Long,
        val sum: Double,
        val timestampMs: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HistogramData
            return name == other.name && attributes == other.attributes
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + attributes.hashCode()
            return result
        }
    }
}
