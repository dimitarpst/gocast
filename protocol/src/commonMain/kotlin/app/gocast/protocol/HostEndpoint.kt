package app.gocast.protocol

object HostEndpoint {
    /** Formats host+port for URLs (`host:port` or `[ipv6]:port`). */
    fun authority(host: String, port: Int): String =
        if (host.contains(':')) "[$host]:$port" else "$host:$port"

    /** Parses `host:port` or `[ipv6]:port`, preserving existing join-link rules. */
    fun parseAuthority(authority: String): Pair<String, Int>? {
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            if (end <= 1) return null
            val host = authority.substring(1, end)
            val rest = authority.substring(end + 1)
            if (!rest.startsWith(":")) return null
            val port = rest.substring(1).toIntOrNull() ?: return null
            return host to port
        }
        val idx = authority.lastIndexOf(':')
        if (idx <= 0) return null
        val host = authority.substring(0, idx)
        if (host.contains(':')) return null
        val port = authority.substring(idx + 1).toIntOrNull() ?: return null
        return host to port
    }
}
