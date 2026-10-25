package app.gocast.domain

import android.net.Uri
import app.gocast.protocol.HostEndpoint
import app.gocast.protocol.JoinLink
import app.gocast.protocol.JoinParams

object JoinUrlParser {
    fun parse(uri: Uri?): JoinParams? {
        if (uri == null) return null

        val a = uri.getQueryParameter(JoinLink.AUTHORITY_PARAMETER) ?: return null
        val token = uri.getQueryParameter(JoinLink.TOKEN_PARAMETER) ?: return null
        return JoinLink.parseParameters(a, token)
    }

    /** Parses `host:port` or `[ipv6]:port`. */
    fun parseHostPort(authority: String): Pair<String, Int>? =
        HostEndpoint.parseAuthority(authority)
}
