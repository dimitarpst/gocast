package app.gocast.domain

data class TrustedPc(
    val displayName: String,
    val host: String,
    val port: Int,
    val instanceId: String,
    val trustToken: String,
    /** The PC's signaling room; null for PCs paired before remote casting. */
    val roomId: String? = null,
)

data class DiscoveredHost(
    val displayName: String,
    val host: String,
    val port: Int,
    val instanceId: String,
)
