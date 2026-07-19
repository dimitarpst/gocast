package app.gocast.cast

import android.content.Context
import app.gocast.data.TrustedPcStore
import app.gocast.domain.JoinParams
import app.gocast.domain.TrustedPc
import app.gocast.network.GocastDiscovery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Same target selection as in-app “tap to cast”: prefer a fresh mDNS address for
 * the last trusted PC, otherwise use the last known host/port.
 */
object TrustedCastResolver {

    suspend fun resolveTrustedJoin(
        context: Context,
        timeoutMs: Long = 2_500,
    ): Pair<TrustedPc, JoinParams>? {
        val store = TrustedPcStore(context)
        val pc = store.load() ?: return null
        val refreshed = withTimeoutOrNull(timeoutMs) {
            GocastDiscovery(context).discover().first { hosts ->
                hosts.any { it.instanceId == pc.instanceId }
            }.first { it.instanceId == pc.instanceId }
        }

        val host = refreshed?.host ?: pc.host
        val port = refreshed?.port ?: pc.port
        val updated = if (refreshed != null && (refreshed.host != pc.host || refreshed.port != pc.port)) {
            pc.copy(host = host, port = port).also { store.save(it) }
        } else {
            pc
        }
        return updated to JoinParams(
            host = host,
            port = port,
            token = pc.trustToken,
        )
    }
}
