package app.gocast.domain

data class JoinParams(
    val host: String,
    val port: Int,
    val token: String,
)

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
