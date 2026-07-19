package app.gocast.network

object HostEndpoint {
    /** Formats host+port for URLs (`host:port` or `[ipv6]:port`). */
    fun authority(host: String, port: Int): String =
        if (host.contains(':')) "[$host]:$port" else "$host:$port"
}
