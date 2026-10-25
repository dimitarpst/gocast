package app.gocast.protocol

object JoinLink {
    const val AUTHORITY_PARAMETER = "a"
    const val TOKEN_PARAMETER = "t"

    fun create(host: String, port: Int, shortCode: String): String =
        "https://cast.govrse.app/join?$AUTHORITY_PARAMETER=${HostEndpoint.authority(host, port)}&$TOKEN_PARAMETER=$shortCode"

    // URI query decoding stays with each platform so existing link handling is unchanged.
    fun parseParameters(authority: String?, token: String?): JoinParams? {
        if (authority == null || token == null) return null
        val (host, port) = HostEndpoint.parseAuthority(authority) ?: return null
        return JoinParams(host, port, token)
    }
}
