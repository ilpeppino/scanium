package com.scanium.app.telemetry

import com.scanium.telemetry.TelemetryConfig
import com.scanium.telemetry.ports.MetricPort
import com.scanium.app.telemetry.otlp.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Android implementation of [MetricPort] that exports metrics via OTLP.
 *
 * ## Metric Types
 * - Counters: Cumulative monotonic sum (e.g., total scans)
 * - Timers: Reported as gauge (last value)
 * - Gauges: Current value (e.g., items in memory)
 *
 * ## Aggregation Strategy
 * - Counters: Sum delta values between exports
 * - Timers: Track last value only
 * - Gauges: Track last value only
 *
 * ## Batching
 * - Exports all metrics periodically (batch timeout)
 * - No max batch size (metrics are lightweight)
 *
 * ## Thread Safety
 * Uses ConcurrentHashMap for thread-safe metric updates.
 */
class AndroidMetricPortOtlp(
    private val telemetryConfig: TelemetryConfig,
    private val otlpConfig: OtlpConfiguration
) : MetricPort {

    private val exporter = OtlpHttpExporter(otlpConfig, telemetryConfig)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Track counters (name+attrs -> cumulative value)
    private val counters = ConcurrentHashMap<String, CounterData>()

    // Track timers (name+attrs -> last value)
    private val timers = ConcurrentHashMap<String, GaugeData>()

    // Track gauges (name+attrs -> last value)
    private val gauges = ConcurrentHashMap<String, GaugeData>()

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

    override fun counter(name: String, delta: Long, attributes: Map<String, String>) {
        if (!otlpConfig.enabled) return

        val key = metricKey(name, attributes)
        counters.compute(key) { _, existing ->
            val newValue = (existing?.value ?: 0L) + delta
            CounterData(name, newValue, attributes, System.currentTimeMillis())
        }
    }

    override fun timer(name: String, millis: Long, attributes: Map<String, String>) {
        if (!otlpConfig.enabled) return

        val key = metricKey(name, attributes)
        timers[key] = GaugeData(name, millis.toDouble(), attributes, System.currentTimeMillis())
    }

    override fun gauge(name: String, value: Double, attributes: Map<String, String>) {
        if (!otlpConfig.enabled) return

        val key = metricKey(name, attributes)
        gauges[key] = GaugeData(name, value, attributes, System.currentTimeMillis())
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
                    sum = Sum(
                        dataPoints = listOf(
                            NumberDataPoint(
                                timeUnixNano = (data.timestampMs * 1_000_000).toString(),
                                asInt = data.value,
                                attributes = data.attributes.map { (k, v) ->
                                    KeyValue(k, AnyValue.string(v))
                                }
                            )
                        ),
                        aggregationTemporality = 2, // CUMULATIVE
                        isMonotonic = true
                    )
                )
            )
        }

        // Export timers as gauges (milliseconds)
        timers.forEach { (_, data) ->
            metrics.add(
                Metric(
                    name = data.name,
                    unit = "ms",
                    gauge = Gauge(
                        dataPoints = listOf(
                            NumberDataPoint(
                                timeUnixNano = (data.timestampMs * 1_000_000).toString(),
                                asDouble = data.value,
                                attributes = data.attributes.map { (k, v) ->
                                    KeyValue(k, AnyValue.string(v))
                                }
                            )
                        )
                    )
                )
            )
        }

        // Export gauges
        gauges.forEach { (_, data) ->
            metrics.add(
                Metric(
                    name = data.name,
                    gauge = Gauge(
                        dataPoints = listOf(
                            NumberDataPoint(
                                timeUnixNano = (data.timestampMs * 1_000_000).toString(),
                                asDouble = data.value,
                                attributes = data.attributes.map { (k, v) ->
                                    KeyValue(k, AnyValue.string(v))
                                }
                            )
                        )
                    )
                )
            )
        }

        if (metrics.isEmpty()) return

        // Build OTLP request
        val request = ExportMetricsServiceRequest(
            resourceMetrics = listOf(
                ResourceMetrics(
                    resource = exporter.buildResource(),
                    scopeMetrics = listOf(
                        ScopeMetrics(
                            scope = InstrumentationScope(
                                name = "com.scanium.telemetry",
                                version = "1.0.0"
                            ),
                            metrics = metrics
                        )
                    )
                )
            )
        )

        // Export (async, fire-and-forget)
        exporter.exportMetrics(request)
    }

    private fun metricKey(name: String, attributes: Map<String, String>): String {
        val attrKey = attributes.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        return "$name{$attrKey}"
    }

    private data class CounterData(
        val name: String,
        val value: Long,
        val attributes: Map<String, String>,
        val timestampMs: Long
    )

    private data class GaugeData(
        val name: String,
        val value: Double,
        val attributes: Map<String, String>,
        val timestampMs: Long
    )
}
