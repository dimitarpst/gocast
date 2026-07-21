package app.gocast.network

import java.net.DatagramSocket
import java.net.Inet4Address
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
     * Strategy (phone joins on Wi-Fi):
     1. Prefer a real Wi-Fi IPv4 whenever one exists.
     2. Else prefer the route-local IPv4 if it sits on a non-virtual NIC.
     3. Else score remaining interfaces (Ethernet > other), skip virtual.
     */
    fun resolve(): String {
        val candidates = collectIpv4Candidates()
        if (candidates.isEmpty()) return "127.0.0.1"

        candidates.filter { it.kind == IfaceKind.Wifi }
            .maxByOrNull { it.score }
            ?.let { return it.address }

        val routeIp = routeLocalIpv4()
        if (routeIp != null) {
            candidates.find { it.address == routeIp }?.let { return it.address }
        }

        return candidates.maxByOrNull { it.score }!!.address
    }

    private data class Candidate(
        val address: String,
        val score: Int,
        val kind: IfaceKind,
    )

    private enum class IfaceKind { Wifi, Ethernet, Other }

    private fun collectIpv4Candidates(): List<Candidate> {
        val out = mutableListOf<Candidate>()
        for (networkInterface in NetworkInterface.getNetworkInterfaces().toList()) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            if (isVirtualAdapter(networkInterface.name, networkInterface.displayName)) continue

            val kind = ifaceKind(networkInterface.name, networkInterface.displayName)
            val ifaceScore = when (kind) {
                IfaceKind.Wifi -> 100
                IfaceKind.Ethernet -> 80
                IfaceKind.Other -> 40
            }
            for (address in networkInterface.inetAddresses.toList()) {
                if (address !is Inet4Address || address.isLoopbackAddress) continue
                val host = address.hostAddress ?: continue
                if (!isUsableLanIpv4(host)) continue
                out += Candidate(host, ifaceScore + ipv4Score(host), kind)
            }
        }
        return out
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
            // Prefer whole tokens / product names over bare "virtual" so we don't
            // over-match odd OEM labels.
            "vmware", "vbox", "virtualbox", "hyper-v", "vethernet",
            "host-only", "docker", "wsl", "nordlynx",
            "wireguard", "hamachi", "zerotier", "tailscale", "radmin", "loopback",
            "bluetooth", "isatap", "teredo", "microsoft wi-fi direct",
            "pseudo-interface", "openvpn", "surfshark",
            // VPN / tunnel adapters (substring match on common markers).
            " vpn", "vpn ", "tun0", "tun1", "tap0", "tap-windows", "wintun",
        )
        if (blocked.any { label.contains(it) }) return true
        // Catch remaining *virtual* adapters (VirtualBox Host-Only, etc.) without
        // treating every "virtual" substring in unrelated names as poison.
        if ("virtualbox" in label || "host-only" in label) return true
        if ("virtual" in label && ("adapter" in label || "ethernet" in label || "switch" in label)) {
            return true
        }
        return false
    }

    internal fun interfaceScore(name: String, displayName: String): Int {
        return when (ifaceKind(name, displayName)) {
            IfaceKind.Wifi -> 100
            IfaceKind.Ethernet -> 80
            IfaceKind.Other -> 40
        }
    }

    private fun ifaceKind(name: String, displayName: String): IfaceKind {
        val label = "$name $displayName".lowercase()
        return when {
            "wi-fi" in label || "wifi" in label || "wlan" in label -> IfaceKind.Wifi
            "ethernet" in label || "local area connection" in label -> IfaceKind.Ethernet
            else -> IfaceKind.Other
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
}
