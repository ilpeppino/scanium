package com.scanium.app.ml.classification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton to track metrics for classification pipeline.
 * Exposed to UI (PerfOverlay) via StateFlows.
 */
object ClassificationMetrics {
    private val _lastLatencyMs = MutableStateFlow(0L)
    val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private val _callsStarted = MutableStateFlow(0)
    val callsStarted: StateFlow<Int> = _callsStarted.asStateFlow()

    private val _callsCompleted = MutableStateFlow(0)
    val callsCompleted: StateFlow<Int> = _callsCompleted.asStateFlow()

    private val _callsFailed = MutableStateFlow(0)
    val callsFailed: StateFlow<Int> = _callsFailed.asStateFlow()

    private val _queueDepth = MutableStateFlow(0)
    val queueDepth: StateFlow<Int> = _queueDepth.asStateFlow()

    fun recordStart() {
        _callsStarted.value++
        _queueDepth.value++
    }

    fun recordComplete(
        latencyMs: Long,
        success: Boolean,
    ) {
        _queueDepth.value = (_queueDepth.value - 1).coerceAtLeast(0)
        if (success) {
            _callsCompleted.value++
        } else {
            _callsFailed.value++
        }
        _lastLatencyMs.value = latencyMs
    }

    fun reset() {
        _callsStarted.value = 0
        _callsCompleted.value = 0
        _callsFailed.value = 0
        _queueDepth.value = 0
        _lastLatencyMs.value = 0L
    }
}
