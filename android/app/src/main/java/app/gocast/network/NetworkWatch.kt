package app.gocast.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Follows the phone's default network so a cast moves the moment the phone
 * switches (Wi-Fi ↔ mobile data), instead of waiting for the old socket to
 * time out.
 *
 * @param onSwitched the default network changed or was lost: the old link is dead.
 * @param onInternetBack the new network just proved it reaches the internet. A
 *   fresh Wi-Fi often has no working DNS for a few seconds (seen 30 Oct), so
 *   attempts made in that window fail and back off; this restarts them on time.
 */
internal class NetworkWatch(
    context: Context,
    private val onSwitched: () -> Unit,
    private val onInternetBack: () -> Unit,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    // Not cleared on loss: Wi-Fi off reports "lost Wi-Fi" then "mobile data
    // available", and that pair is exactly the switch we want to catch.
    @Volatile
    private var lastNetwork: Network? = null

    @Volatile
    private var lostSinceLast = false

    @Volatile
    private var awaitingInternet = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = lastNetwork
            lastNetwork = network
            val lost = lostSinceLast
            lostSinceLast = false
            // The first call is the network we already had at registration. After a
            // loss, even the same network coming back needs a fresh connection.
            if (lost || (previous != null && previous != network)) {
                awaitingInternet = true
                onSwitched()
            }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (network != lastNetwork || !awaitingInternet) return
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
            awaitingInternet = false
            onInternetBack()
        }

        override fun onLost(network: Network) {
            if (network != lastNetwork) return
            // No internet at all: show Reconnecting now rather than after the ping timeout.
            lostSinceLast = true
            onSwitched()
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
