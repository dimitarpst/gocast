package app.gocast.network

import app.gocast.protocol.IceServer

/**
 * ICE servers the signaling Worker gave this phone: STUN always, TURN only once
 * the PC accepted it in an allowlisted room. STUN-only until the first reply and
 * on LAN-only sessions.
 */
object PhoneIceServers {
    @Volatile
    private var servers: List<IceServer> = IceServer.STUN_ONLY

    fun update(list: List<IceServer>) {
        servers = list.ifEmpty { IceServer.STUN_ONLY }
    }

    fun current(): List<IceServer> = servers
}
