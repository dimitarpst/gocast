package app.gocast.domain

import android.net.Uri

object JoinUrlParser {
    fun parse(uri: Uri?): JoinParams? {
        if (uri == null) return null

        val a = uri.getQueryParameter("a") ?: return null
        val token = uri.getQueryParameter("t") ?: return null
        val (host, port) = parseHostPort(a) ?: return null
        return JoinParams(host, port, token)
    }

    /** Parses `host:port` or `[ipv6]:port`. */
    fun parseHostPort(authority: String): Pair<String, Int>? {
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
