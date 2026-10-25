package app.gocast.protocol

import kotlinx.serialization.Serializable

@Serializable
data class HelloMessage(
    val type: String,
    val token: String,
    val role: String = "phone",
)

// Older desktops may omit trust details; keep the phone's existing optional fields.
// The desktop still supplies all three fields when sending a successful handshake.
@Serializable
data class HelloOkMessage(
    val type: String = "ok",
    val trustToken: String? = null,
    val instanceId: String? = null,
    val displayName: String? = null,
)
