package app.gocast.network

import java.net.NetworkInterface

object LocalIpResolver {
    fun resolve(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()

        for(networkInterface in interfaces) {
            if(!networkInterface.isUp || networkInterface.isLoopback) continue

            val address = networkInterface.inetAddresses.toList()
                .firstOrNull {it.hostAddress?.contains(':') == false}
                ?: continue

            return address.hostAddress
        }

        return "127.0.0.1"
    }
}