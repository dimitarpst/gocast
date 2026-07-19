package app.gocast.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

object LocalIpResolver {
    /**
     * Picks a LAN address for QR / mDNS.
     * Prefers IPv4 when available; falls back to a non-link-local IPv6.
     */
    fun resolve(): String {
        val v4 = mutableListOf<Inet4Address>()
        val v6 = mutableListOf<Inet6Address>()

        for (networkInterface in NetworkInterface.getNetworkInterfaces().toList()) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            for (address in networkInterface.inetAddresses.toList()) {
                when (address) {
                    is Inet4Address -> if (!address.isLoopbackAddress) v4 += address
                    is Inet6Address -> {
                        if (!address.isLoopbackAddress && !address.isLinkLocalAddress) {
                            v6 += address
                        }
                    }
                    else -> Unit
                }
            }
        }

        v4.firstOrNull()?.hostAddress?.let { return it }

        val ipv6 = v6.firstOrNull()?.hostAddress?.substringBefore('%')
        if (ipv6 != null) return ipv6

        return "127.0.0.1"
    }
}
