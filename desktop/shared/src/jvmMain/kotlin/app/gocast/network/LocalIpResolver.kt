package app.gocast.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

object LocalIpResolver {
    /**
     * Picks a LAN address for QR / mDNS.
     *
     * Laptops often have Hyper-V / Docker / VPN adapters whose IPv4 sorts
     * *before* Wi-Fi. Taking the first address puts the wrong IP in the QR and
     * advertises mDNS on a network the phone cannot reach.
     */
    fun resolve(): String {
        data class Candidate(
            val address: String,
            val score: Int,
        )

        val candidates = mutableListOf<Candidate>()

        for (networkInterface in NetworkInterface.getNetworkInterfaces().toList()) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            if (isVirtualAdapter(networkInterface.name, networkInterface.displayName)) continue

            val ifaceScore = interfaceScore(networkInterface.name, networkInterface.displayName)
            for (address in networkInterface.inetAddresses.toList()) {
                when (address) {
                    is Inet4Address -> {
                        if (address.isLoopbackAddress) continue
                        val host = address.hostAddress ?: continue
                        candidates += Candidate(host, ifaceScore + ipv4Score(host))
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

    private fun isVirtualAdapter(name: String, displayName: String): Boolean {
        val label = "$name $displayName".lowercase()
        val blocked = listOf(
            "virtual", "vmware", "vbox", "virtualbox", "hyper-v", "vethernet",
            "docker", "wsl", "tun", "tap", "vpn", "nordlynx", "wireguard",
            "hamachi", "zerotier", "tailscale", "radmin", "loopback",
            "bluetooth", "isatap", "teredo", "microsoft wi-fi direct",
        )
        return blocked.any { label.contains(it) }
    }

    private fun interfaceScore(name: String, displayName: String): Int {
        val label = "$name $displayName".lowercase()
        return when {
            "wi-fi" in label || "wifi" in label || "wlan" in label -> 100
            "ethernet" in label || "local area connection" in label -> 80
            else -> 40
        }
    }

    private fun ipv4Score(host: String): Int = when {
        host.startsWith("192.168.") -> 30
        host.startsWith("10.") -> 15
        // 172.16–31 is valid LAN, but also Hyper-V/Docker default — we already
        // filter virtual adapters; keep a modest score.
        host.matches(Regex("""^172\.(1[6-9]|2\d|3[0-1])\..*""")) -> 10
        else -> 0
    }
}
