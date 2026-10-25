package app.gocast.protocol

data class JoinParams(
    val host: String,
    val port: Int,
    val token: String,
)
