package app.gocast.discovery

import app.gocast.network.LocalIpResolver
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Advertises this GoCast session on the LAN via mDNS (`_gocast._tcp`).
 */
class GocastAdvertiser {
    companion object {
        const val SERVICE_TYPE = "_gocast._tcp.local."
    }

    private var jmdns: JmDNS? = null

    /**
     * @param host Prefer the same address shown in the QR. When null, resolves again.
     */
    fun start(port: Int, instanceId: String, displayName: String, host: String? = null) {
        stop()
        val lanIp = host
            ?.takeIf { it.isNotBlank() && it != "127.0.0.1" }
            ?: LocalIpResolver.resolve()
        val addr = runCatching { InetAddress.getByName(lanIp) }.getOrNull()
            ?.takeUnless { it.isLoopbackAddress }
        val dns = if (addr != null) {
            JmDNS.create(addr, displayName)
        } else {
            JmDNS.create()
        }
        val serviceName = "$displayName-${instanceId.take(8)}"
        val info = ServiceInfo.create(
            SERVICE_TYPE,
            serviceName,
            port,
            0,
            0,
            mapOf(
                "id" to instanceId,
                "name" to displayName,
            ),
        )
        dns.registerService(info)
        jmdns = dns
    }

    fun stop() {
        jmdns?.unregisterAllServices()
        jmdns?.close()
        jmdns = null
    }
}
