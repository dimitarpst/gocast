package app.gocast.domain.model

import app.gocast.protocol.JoinLink

data class JoinSession(
    val host: String,
    val port: Int,
    val token: String,
    val shortCode: String,
    val instanceId: String,
    val displayName: String,
) {
    val joinUrl: String
        // Prefer shortCode over the UUID token so the QR stays low-version (large dots).
        get() = JoinLink.create(host, port, shortCode)
}
