package app.gocast.domain.model

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
    val trustToken: String,
    val instanceId: String,
    val displayName: String,
)
