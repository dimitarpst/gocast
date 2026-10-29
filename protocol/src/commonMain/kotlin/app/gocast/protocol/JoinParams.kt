package app.gocast.protocol

data class JoinParams(
    val host: String,
    val port: Int,
    val token: String,
    /** Remote signaling room; null for older QR codes and LAN-only joins. */
    val roomId: String? = null,
)
