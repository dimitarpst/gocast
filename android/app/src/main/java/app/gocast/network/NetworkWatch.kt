package app.gocast.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Follows the phone's default network so a cast moves the moment the phone
 * switches (Wi-Fi ↔ mobile data), instead of waiting for the old socket to
 * time out.
 */
internal class NetworkWatch(context: Context, private val onSwitched: () -> Unit) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    // Not cleared on loss: Wi-Fi off reports "lost Wi-Fi" then "mobile data
    // available", and that pair is exactly the switch we want to catch.
    @Volatile
    private var lastNetwork: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = lastNetwork
            lastNetwork = network
            // The first call is the network we already had at registration.
            if (previous != null && previous != network) onSwitched()
        }
    }

    init {
        runCatching { connectivity?.registerDefaultNetworkCallback(callback) }
    }

    /** True on Wi-Fi or Ethernet, where the PC's LAN address might answer. */
    fun isOnLocalNetwork(): Boolean {
        val caps = connectivity?.let { it.getNetworkCapabilities(it.activeNetwork) } ?: return true
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
