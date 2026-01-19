package com.scanium.app.telemetry

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.scanium.telemetry.facade.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Collects and reports network type metrics periodically.
 *
 * This collector monitors the device's network connectivity and reports it as a gauge metric.
 * The metric helps correlate API performance with network conditions.
 *
 * ***REMOVED******REMOVED*** Metric
 * - **Name**: `mobile.network.type`
 * - **Type**: Gauge (current value)
 * - **Values**:
 *   - `0`: No connection
 *   - `1`: WiFi
 *   - `2`: Cellular
 *   - `3`: Ethernet (rare on mobile)
 *   - `4`: Other (VPN, etc.)
 *
 * ***REMOVED******REMOVED*** Usage
 * ```kotlin
 * val collector = NetworkTypeCollector(context, telemetry, scope)
 * collector.start()
 * // Later...
 * collector.stop()
 * ```
 *
 * ***REMOVED******REMOVED*** Privacy
 * This metric respects the telemetry opt-out setting. If telemetry is disabled,
 * no network metrics are collected.
 *
 * @param context Android context for accessing ConnectivityManager
 * @param telemetry Telemetry facade for reporting metrics
 * @param scope CoroutineScope for periodic collection
 * @param intervalMs Interval between metric reports (default: 30 seconds)
 */
class NetworkTypeCollector(
    private val context: Context,
    private val telemetry: Telemetry?,
    private val scope: CoroutineScope,
    private val intervalMs: Long = 30_000L,
) {
    companion object {
        private const val TAG = "NetworkTypeCollector"

        // Metric values
        const val NETWORK_TYPE_NONE = 0
        const val NETWORK_TYPE_WIFI = 1
        const val NETWORK_TYPE_CELLULAR = 2
        const val NETWORK_TYPE_ETHERNET = 3
        const val NETWORK_TYPE_OTHER = 4
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var collectionJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Cache current network type to avoid redundant metric reports
    @Volatile
    private var currentNetworkType: Int = NETWORK_TYPE_NONE

    /**
     * Starts periodic network type collection.
     *
     * This method:
     * 1. Registers a network callback for real-time network changes
     * 2. Starts a periodic job that reports the current network type
     *
     * If telemetry is null or disabled, this is a no-op.
     */
    fun start() {
        if (telemetry == null) {
            Log.d(TAG, "NetworkTypeCollector not started - telemetry is null")
            return
        }

        // Register network callback for real-time updates
        registerNetworkCallback()

        // Start periodic reporting
        collectionJob =
            scope.launch {
                Log.d(TAG, "NetworkTypeCollector started (interval=${intervalMs}ms)")
                while (isActive) {
                    reportNetworkType()
                    delay(intervalMs)
                }
            }
    }

    /**
     * Stops network type collection and unregisters callbacks.
     */
    fun stop() {
        collectionJob?.cancel()
        collectionJob = null

        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister network callback", e)
            }
        }
        networkCallback = null

        Log.d(TAG, "NetworkTypeCollector stopped")
    }

    /**
     * Registers a network callback to track network changes in real-time.
     *
     * This provides immediate updates when the network changes (e.g., WiFi → Cellular),
     * complementing the periodic reporting.
     */
    private fun registerNetworkCallback() {
        try {
            val networkRequest =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        updateCurrentNetworkType()
                        Log.d(TAG, "Network available: type=$currentNetworkType")
                    }

                    override fun onLost(network: Network) {
                        currentNetworkType = NETWORK_TYPE_NONE
                        Log.d(TAG, "Network lost")
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        updateCurrentNetworkType()
                        Log.d(TAG, "Network capabilities changed: type=$currentNetworkType")
                    }
                }

            connectivityManager.registerNetworkCallback(networkRequest, callback)
            networkCallback = callback

            // Get initial network type
            updateCurrentNetworkType()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    /**
     * Reports the current network type as a gauge metric.
     */
    private fun reportNetworkType() {
        updateCurrentNetworkType()

        telemetry?.gauge(
            "mobile.network.type",
            currentNetworkType.toDouble(),
            emptyMap(),
        )

        Log.d(TAG, "Reported network type: $currentNetworkType (${getNetworkTypeName(currentNetworkType)})")
    }

    /**
     * Updates the current network type by querying ConnectivityManager.
     */
    private fun updateCurrentNetworkType() {
        currentNetworkType = getCurrentNetworkType()
    }

    /**
     * Determines the current network type.
     *
     * @return Network type constant (NETWORK_TYPE_*)
     */
    private fun getCurrentNetworkType(): Int {
        try {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null) {
                return NETWORK_TYPE_NONE
            }

            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities == null) {
                return NETWORK_TYPE_NONE
            }

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NETWORK_TYPE_WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NETWORK_TYPE_CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NETWORK_TYPE_ETHERNET
                else -> NETWORK_TYPE_OTHER
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error determining network type", e)
            return NETWORK_TYPE_NONE
        }
    }

    /**
     * Gets a human-readable name for a network type constant.
     *
     * Useful for logging and debugging.
     */
    private fun getNetworkTypeName(type: Int): String =
        when (type) {
            NETWORK_TYPE_NONE -> "None"
            NETWORK_TYPE_WIFI -> "WiFi"
            NETWORK_TYPE_CELLULAR -> "Cellular"
            NETWORK_TYPE_ETHERNET -> "Ethernet"
            NETWORK_TYPE_OTHER -> "Other"
            else -> "Unknown($type)"
        }
}
