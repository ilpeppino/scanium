package com.scanium.app.items.state

import android.util.Log
import com.scanium.app.aggregation.AggregationStats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class ItemsTelemetry(
    private val scope: CoroutineScope,
    private var workerDispatcher: CoroutineDispatcher,
    private val statsProvider: () -> AggregationStats,
) {
    private val enabled = MutableStateFlow(false)
    private var telemetryJob: Job? = null

    fun setDispatcher(dispatcher: CoroutineDispatcher) {
        workerDispatcher = dispatcher
    }

    fun enable() {
        if (enabled.value) {
            Log.w(TAG, "Telemetry already enabled")
            return
        }

        enabled.value = true
        telemetryJob =
            scope.launch(workerDispatcher) {
                Log.i(TAG, "╔═══════════════════════════════════════════════════════════════")
                Log.i(TAG, "║ ASYNC TELEMETRY ENABLED")
                Log.i(TAG, "║ Collection interval: ${TELEMETRY_INTERVAL_MS}ms")
                Log.i(TAG, "╚═══════════════════════════════════════════════════════════════")

                while (isActive && enabled.value) {
                    delay(TELEMETRY_INTERVAL_MS)

                    val stats = statsProvider()
                    val runtime = Runtime.getRuntime()
                    val usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                    val maxMemoryMB = runtime.maxMemory() / 1024 / 1024

                    Log.i(TAG, "┌─────────────────────────────────────────────────────────────")
                    Log.i(TAG, "│ TELEMETRY SNAPSHOT")
                    Log.i(TAG, "├─────────────────────────────────────────────────────────────")
                    Log.i(TAG, "│ Aggregated items: ${stats.totalItems}")
                    Log.i(TAG, "│ Total merges: ${stats.totalMerges}")
                    Log.i(TAG, "│ Avg merges/item: ${"%.2f".format(stats.averageMergesPerItem)}")
                    Log.i(TAG, "│ Memory: ${usedMemoryMB}MB / ${maxMemoryMB}MB")
                    Log.i(TAG, "└─────────────────────────────────────────────────────────────")
                }

                Log.i(TAG, "Async telemetry stopped")
            }
    }

    fun disable() {
        enabled.value = false
        telemetryJob?.cancel()
        telemetryJob = null
        Log.i(TAG, "Async telemetry disabled")
    }

    fun isEnabled(): Boolean = enabled.value

    companion object {
        private const val TAG = "ItemsStateManager"
        private const val TELEMETRY_INTERVAL_MS = 5000L
    }
}
