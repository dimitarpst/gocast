package app.gocast.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits a new LAN IPv4 whenever [LocalIpResolver] disagrees with the last value.
 * Used so QR / mDNS track Wi-Fi after VirtualBox/VPN adapters appear, or after
 * the laptop joins Wi-Fi post-launch.
 */
object LanHostMonitor {
    fun watch(
        initialHost: String,
        intervalMs: Long = 2_000L,
    ): Flow<String> = flow {
        var last = initialHost
        while (true) {
            delay(intervalMs)
            val next = LocalIpResolver.resolve()
            if (next != last && next != "127.0.0.1") {
                last = next
                emit(next)
            } else if (last == "127.0.0.1" && next != "127.0.0.1") {
                last = next
                emit(next)
            }
        }
    }
}
