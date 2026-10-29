package app.gocast.cast

import android.content.Context
import app.gocast.data.TrustedPcStore
import app.gocast.protocol.JoinParams
import app.gocast.domain.TrustedPc
import app.gocast.network.GocastDiscovery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Same target selection as in-app “tap to cast”. A PC with a signaling room is
 * joined through the room (any network), with the saved LAN address as fallback.
 * A PC without one is joined only when it is visible on Wi-Fi via mDNS.
 */
object TrustedCastResolver {

    /** With a room, mDNS only refreshes the LAN address when the PC happens to be nearby. */
    private const val LAN_REFRESH_WITH_ROOM_MS = 800L

    /**
     * Resolve the last trusted PC. Without a room, never fall back to a stale IP —
     * that hangs / fails when the desktop app is off.
     */
    suspend fun resolveTrustedJoin(
        context: Context,
        timeoutMs: Long = 2_500,
    ): Pair<TrustedPc, JoinParams>? {
        val store = TrustedPcStore(context)
        val pc = store.load() ?: return null
        val hasRoom = pc.roomId != null
        val refreshed = withTimeoutOrNull(if (hasRoom) LAN_REFRESH_WITH_ROOM_MS else timeoutMs) {
            GocastDiscovery(context).discover().first { hosts ->
                hosts.any { it.instanceId == pc.instanceId }
            }.first { it.instanceId == pc.instanceId }
        }
        if (refreshed == null && !hasRoom) return null

        val updated = if (refreshed != null && (
                refreshed.host != pc.host || refreshed.port != pc.port ||
                    refreshed.displayName != pc.displayName
                )
        ) {
            pc.copy(
                displayName = refreshed.displayName.ifBlank { pc.displayName },
                host = refreshed.host,
                port = refreshed.port,
            ).also { store.save(it) }
        } else {
            pc
        }
        return updated to JoinParams(
            host = updated.host,
            port = updated.port,
            token = pc.trustToken,
            roomId = pc.roomId,
        )
    }
}
