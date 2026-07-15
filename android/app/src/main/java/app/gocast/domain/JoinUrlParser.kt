package app.gocast.domain

import android.net.Uri

data class JoinParams(val host: String, val port: Int, val token: String)

object JoinUrlParser{
    fun parse(uri: Uri?): JoinParams?{
        if (uri == null) return null

        val a = uri.getQueryParameter("a") ?: return null
        val token = uri.getQueryParameter("t") ?: return null

        val hostPort = a.split(":")
        if(hostPort.size != 2) return null

        return JoinParams(hostPort[0], hostPort[1].toInt(), token)
    }
}