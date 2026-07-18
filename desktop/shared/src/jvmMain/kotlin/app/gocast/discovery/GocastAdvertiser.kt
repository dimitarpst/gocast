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

    fun start(port: Int, instanceId: String, displayName: String) {
        stop()
        val lanIp = LocalIpResolver.resolve()
        val addr = runCatching { InetAddress.getByName(lanIp) }.getOrNull()
            ?: runCatching { InetAddress.getLocalHost() }.getOrNull()
        val dns = if (addr != null && !addr.isLoopbackAddress) {
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
