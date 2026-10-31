package app.gocast.protocol

/** How cast media really travels, read from the selected ICE candidate pair. */
enum class LinkType(val label: String) {
    Lan("Direct (LAN)"),
    Internet("Direct (internet)"),
    Relayed("Relayed"),
}

object LinkClassifier {
    /**
     * @param localType ICE candidate type of the local side: host, srflx, prflx or relay.
     * @param remoteType the same for the remote side.
     * @param remoteAddress the remote candidate's address (IP or mDNS name), if reported.
     * @param localAddress the local candidate's address, used only when the remote one is missing.
     */
    fun classify(
        localType: String?,
        remoteType: String?,
        remoteAddress: String?,
        localAddress: String? = null,
    ): LinkType? {
        if (localType == null && remoteType == null) return null
        if (localType == "relay" || remoteType == "relay") return LinkType.Relayed
        // A private peer address means the packets never left the local network.
        if (!remoteAddress.isNullOrBlank()) {
            return if (isLocalAddress(remoteAddress)) LinkType.Lan else LinkType.Internet
        }
        return if (remoteType == "host" && isLocalAddress(localAddress)) LinkType.Lan else LinkType.Internet
    }

    /** Private, link-local and loopback IPs, plus mDNS names (only host candidates use them). */
    fun isLocalAddress(address: String?): Boolean {
        val value = address?.trim()?.trimStart('[')?.trimEnd(']')?.lowercase() ?: return false
        if (value.isEmpty()) return false
        if (value.endsWith(".local")) return true
        val v4 = value.split('.')
        if (v4.size == 4 && v4.all { it.toIntOrNull() in 0..255 }) {
            val a = v4[0].toInt()
            val b = v4[1].toInt()
            return a == 10 || a == 127 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254)
        }
        if (!value.contains(':')) return false
        val v6 = value.substringBefore('%')
        return v6 == "::1" ||
            v6.startsWith("fc") || v6.startsWith("fd") ||
            v6.startsWith("fe8") || v6.startsWith("fe9") || v6.startsWith("fea") || v6.startsWith("feb")
    }
}
