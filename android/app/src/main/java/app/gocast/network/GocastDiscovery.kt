package app.gocast.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import app.gocast.domain.DiscoveredHost
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class GocastDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun discover(): Flow<List<DiscoveredHost>> = callbackFlow {
        val hosts = ConcurrentHashMap<String, DiscoveredHost>()
        val pending = ArrayDeque<NsdServiceInfo>()
        val resolving = AtomicBoolean(false)

        val multicastLock = runCatching {
            val wifi = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifi.createMulticastLock("gocast-nsd").also {
                it.setReferenceCounted(true)
                it.acquire()
            }
        }.getOrNull()

        fun emitHosts() {
            trySend(hosts.values.sortedBy { it.displayName.lowercase() })
        }

        fun resolveNext() {
            if (!resolving.compareAndSet(false, true)) return
            val next = synchronized(pending) { pending.pollFirst() }
            if (next == null) {
                resolving.set(false)
                return
            }
            @Suppress("DEPRECATION")
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolving.set(false)
                    resolveNext()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val hostAddress = serviceInfo.host?.hostAddress
                    if (hostAddress != null) {
                        val attrs = serviceInfo.attributes
                        val instanceId = attrs.txt("id") ?: serviceInfo.serviceName
                        val displayName = attrs.txt("name") ?: serviceInfo.serviceName
                        hosts[instanceId] = DiscoveredHost(
                            displayName = displayName,
                            host = hostAddress,
                            port = serviceInfo.port,
                            instanceId = instanceId,
                        )
                        emitHosts()
                    }
                    resolving.set(false)
                    resolveNext()
                }
            })
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("Discovery failed: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("gocast", ignoreCase = true)) return
                synchronized(pending) { pending.addLast(serviceInfo) }
                resolveNext()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val instanceId = serviceInfo.attributes.txt("id") ?: serviceInfo.serviceName
                hosts.remove(instanceId)
                emitHosts()
            }
        }

        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        emitHosts()

        awaitClose {
            runCatching { nsd.stopServiceDiscovery(discoveryListener) }
            runCatching { multicastLock?.release() }
        }
    }

    companion object {
        const val SERVICE_TYPE = "_gocast._tcp."

        private fun Map<String, ByteArray>?.txt(key: String): String? {
            val bytes = this?.get(key) ?: return null
            return bytes.toString(Charsets.UTF_8)
        }
    }
}
