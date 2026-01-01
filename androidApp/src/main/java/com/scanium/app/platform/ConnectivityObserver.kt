package com.scanium.app.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class ConnectivityStatus {
    ONLINE,
    OFFLINE,
}

interface ConnectivityStatusProvider {
    val statusFlow: Flow<ConnectivityStatus>
}

class ConnectivityObserver(context: Context) : ConnectivityStatusProvider {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val statusFlow: Flow<ConnectivityStatus> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(ConnectivityStatus.ONLINE)
                    }

                    override fun onLost(network: Network) {
                        trySend(ConnectivityStatus.OFFLINE)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        trySend(if (hasInternet) ConnectivityStatus.ONLINE else ConnectivityStatus.OFFLINE)
                    }
                }

            val request =
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

            val current =
                connectivityManager.activeNetwork?.let { network ->
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    if (hasInternet) ConnectivityStatus.ONLINE else ConnectivityStatus.OFFLINE
                } ?: ConnectivityStatus.OFFLINE
            trySend(current)

            connectivityManager.registerNetworkCallback(request, callback)

            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.distinctUntilChanged()
}
