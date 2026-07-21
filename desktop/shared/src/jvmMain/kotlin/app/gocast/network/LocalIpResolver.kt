package app.gocast.network

import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

object LocalIpResolver {
    /**
     * Picks a LAN address for QR / mDNS.
     *
     * Laptops often have Hyper-V / VirtualBox / Docker / VPN adapters whose IPv4
     * appears *before* Wi-Fi. Taking the first address puts a host-only IP
     * (e.g. 192.168.56.1) in the QR — the phone can never reach it.
     *
     * Strategy:
     * 1. Prefer the IPv4 Windows would use to reach the internet (UDP connect trick)
     *    when that address is a real non-virtual LAN NIC.
     * 2. Otherwise score remaining interfaces (Wi-Fi > Ethernet, skip virtual).
     */
    fun resolve(): String {
        val routeIp = routeLocalIpv4()
        if (routeIp != null &&
            isUsableLanIpv4(routeIp) &&
            !isVirtualOwnedIpv4(routeIp)
        ) {
            return routeIp
        }

        data class Candidate(
            val address: String,
            val score: Int,
        )

        val candidates = mutableListOf<Candidate>()

        for (networkInterface in NetworkInterface.getNetworkInterfaces().toList()) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            if (isVirtualAdapter(networkInterface.name, networkInterface.displayName)) continue

            val ifaceScore = interfaceScore(networkInterface.name, networkInterface.displayName)
            val ownsRoute = routeIp != null && networkInterfaceOwnsIpv4(networkInterface, routeIp)
            val gatewayBonus = if (ownsRoute) 40 else 0
            for (address in networkInterface.inetAddresses.toList()) {
                when (address) {
                    is Inet4Address -> {
                        if (address.isLoopbackAddress) continue
                        val host = address.hostAddress ?: continue
                        if (!isUsableLanIpv4(host)) continue
                        candidates += Candidate(host, ifaceScore + ipv4Score(host) + gatewayBonus)
                    }
                    is Inet6Address -> {
                        if (address.isLoopbackAddress || address.isLinkLocalAddress) continue
                        val host = address.hostAddress?.substringBefore('%') ?: continue
                        // Prefer IPv4 heavily; IPv6 only as last resort.
                        candidates += Candidate(host, ifaceScore - 50)
                    }
                    else -> Unit
                }
            }
        }

        candidates.maxByOrNull { it.score }?.address?.let { return it }
        return "127.0.0.1"
    }

    /**
     * Local IPv4 of the interface used for a typical internet route.
     * Does not send packets; connect() only picks a source address.
     */
    internal fun routeLocalIpv4(): String? {
        return try {
            DatagramSocket().use { socket ->
                socket.connect(InetAddress.getByName("1.1.1.1"), 80)
                when (val local = socket.localAddress) {
                    is Inet4Address -> local.hostAddress
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun isUsableLanIpv4(host: String): Boolean {
        if (host.isBlank() || host == "0.0.0.0") return false
        // Link-local APIPA — never advertise these.
        if (host.startsWith("169.254.")) return false
        return true
    }

    internal fun isVirtualAdapter(name: String, displayName: String): Boolean {
        val label = "$name $displayName".lowercase()
        val blocked = listOf(
            "virtual", "vmware", "vbox", "virtualbox", "hyper-v", "vethernet",
            "host-only", "docker", "wsl", "tun", "tap", "vpn", "nordlynx",
            "wireguard", "hamachi", "zerotier", "tailscale", "radmin", "loopback",
            "bluetooth", "isatap", "teredo", "microsoft wi-fi direct",
            "pseudo-interface",
        )
        return blocked.any { label.contains(it) }
    }

    internal fun interfaceScore(name: String, displayName: String): Int {
        val label = "$name $displayName".lowercase()
        return when {
            "wi-fi" in label || "wifi" in label || "wlan" in label -> 100
            "ethernet" in label || "local area connection" in label -> 80
            else -> 40
        }
    }

    internal fun ipv4Score(host: String): Int = when {
        host.startsWith("192.168.") -> 30
        host.startsWith("10.") -> 15
        // 172.16–31 is valid LAN, but also Hyper-V/Docker default — we already
        // filter virtual adapters; keep a modest score.
        host.matches(Regex("""^172\.(1[6-9]|2\d|3[0-1])\..*""")) -> 10
        else -> 0
    }

    private fun isVirtualOwnedIpv4(host: String): Boolean {
        for (networkInterface in NetworkInterface.getNetworkInterfaces().toList()) {
            if (!networkInterface.isUp) continue
            if (networkInterfaceOwnsIpv4(networkInterface, host)) {
                return isVirtualAdapter(networkInterface.name, networkInterface.displayName)
            }
        }
        return false
    }

    private fun networkInterfaceOwnsIpv4(
        networkInterface: NetworkInterface,
        host: String,
    ): Boolean {
        return networkInterface.inetAddresses.toList().any { addr ->
            addr is Inet4Address && addr.hostAddress == host
        }
    }
}
