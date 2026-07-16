package app.gocast.network

import kotlinx.serialization.Serializable
@Serializable
data class HelloMessage(val type: String, val token: String)
