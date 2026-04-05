package tun.proxy.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

class NetworkMonitor(
    private val context: Context,
    private val onNetworkRestored: () -> Unit
) {
    private val TAG = "NetworkMonitor"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Volatile private var wasConnected = true
    @Volatile private var isRegistered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            if (!wasConnected) {
                wasConnected = true
                onNetworkRestored()
            }
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            wasConnected = false
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (hasInternet && !wasConnected) {
                wasConnected = true
                onNetworkRestored()
            }
        }
    }

    fun start() {
        if (isRegistered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        isRegistered = true
    }

    fun stop() {
        if (!isRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister: ${e.message}")
        }
        isRegistered = false
    }
}
