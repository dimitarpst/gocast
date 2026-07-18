package app.gocast.domain

import android.net.Uri

object JoinUrlParser {
    fun parse(uri: Uri?): JoinParams? {
        if (uri == null) return null

        val a = uri.getQueryParameter("a") ?: return null
        val token = uri.getQueryParameter("t") ?: return null

        val hostPort = a.split(":")
        if (hostPort.size != 2) return null

        val port = hostPort[1].toIntOrNull() ?: return null
        return JoinParams(hostPort[0], port, token)
    }
}
