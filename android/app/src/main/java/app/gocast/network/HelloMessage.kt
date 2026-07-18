package app.gocast.network

import kotlinx.serialization.Serializable

@Serializable
data class HelloMessage(
    val type: String,
    val token: String,
    val role: String = "phone",
)

@Serializable
data class HelloOkMessage(
    val type: String = "ok",
    val trustToken: String? = null,
    val instanceId: String? = null,
    val displayName: String? = null,
)
