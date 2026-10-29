package app.gocast.protocol

object JoinLink {
    const val AUTHORITY_PARAMETER = "a"
    const val TOKEN_PARAMETER = "t"
    const val ROOM_PARAMETER = "r"

    fun create(host: String, port: Int, shortCode: String, roomId: String? = null): String {
        val link = "https://cast.govrse.app/join?$AUTHORITY_PARAMETER=${HostEndpoint.authority(host, port)}&$TOKEN_PARAMETER=$shortCode"
        // Room ids are URL-safe, so they need no encoding.
        return if (roomId != null && RoomId.isValid(roomId)) "$link&$ROOM_PARAMETER=$roomId" else link
    }

    // URI query decoding stays with each platform so existing link handling is unchanged.
    // A missing or malformed room only disables remote signaling; the LAN join still works.
    fun parseParameters(authority: String?, token: String?, room: String? = null): JoinParams? {
        if (authority == null || token == null) return null
        val (host, port) = HostEndpoint.parseAuthority(authority) ?: return null
        return JoinParams(host, port, token, room?.takeIf(RoomId::isValid))
    }
}
