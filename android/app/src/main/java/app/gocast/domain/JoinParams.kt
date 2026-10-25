package app.gocast.domain

data class TrustedPc(
    val displayName: String,
    val host: String,
    val port: Int,
    val instanceId: String,
    val trustToken: String,
)

data class DiscoveredHost(
    val displayName: String,
    val host: String,
    val port: Int,
    val instanceId: String,
)
