package app.gocast.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits a new LAN IPv4 whenever [LocalIpResolver] disagrees with the last value.
 * Used so QR / mDNS track Wi-Fi after VirtualBox/VPN adapters appear, or after
 * the laptop joins Wi-Fi post-launch.
 *
 * Changes must stay stable for [stablePolls] consecutive checks before emit —
 * flapping (VPN route vs Wi-Fi) was restarting JmDNS every few seconds and
 * killing Android NSD discovery.
 */
object LanHostMonitor {
    fun watch(
        initialHost: String,
        intervalMs: Long = 2_000L,
        stablePolls: Int = 2,
    ): Flow<String> = flow {
        var last = initialHost
        var pending: String? = null
        var pendingCount = 0
        while (true) {
            delay(intervalMs)
            val next = LocalIpResolver.resolve()
            if (next == last || next == "127.0.0.1") {
                pending = null
                pendingCount = 0
                continue
            }
            if (next == pending) {
                pendingCount++
                if (pendingCount >= stablePolls) {
                    last = next
                    pending = null
                    pendingCount = 0
                    emit(next)
                }
            } else {
                pending = next
                pendingCount = 1
            }
        }
    }
}
