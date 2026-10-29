package app.gocast.server

import app.gocast.protocol.IceServer

/**
 * ICE servers for this PC's viewer: the Worker's list while the remote link is up
 * (STUN, plus TURN for an allowlisted room), otherwise Cloudflare STUN only.
 */
object IceServerStore {
    /** Worker credentials last 4 h; ask again well before that. */
    private const val REFRESH_AFTER_MILLIS = 3 * 60 * 60 * 1000L

    @Volatile
    private var servers: List<IceServer> = IceServer.STUN_ONLY

    @Volatile
    private var updatedAt = 0L

    /** Set by the remote client while connected; null means nobody to ask. */
    @Volatile
    var refresh: (suspend () -> List<IceServer>?)? = null

    fun update(list: List<IceServer>) {
        servers = list.ifEmpty { IceServer.STUN_ONLY }
        updatedAt = System.currentTimeMillis()
    }

    /** Fresh enough to open a connection with; falls back to the last list, then STUN. */
    suspend fun current(): List<IceServer> {
        val stale = System.currentTimeMillis() - updatedAt > REFRESH_AFTER_MILLIS
        if (stale) {
            refresh?.invoke()?.let(::update)
        }
        return servers
    }

    /** Test seams. */
    internal fun reset() {
        servers = IceServer.STUN_ONLY
        updatedAt = 0L
        refresh = null
    }

    internal fun invalidate() {
        updatedAt = 0L
    }
}
