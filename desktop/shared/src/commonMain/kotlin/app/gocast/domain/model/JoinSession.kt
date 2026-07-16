package app.gocast.domain.model

data class JoinSession(
    val host: String,
    val port: Int,
    val token: String,
){
    val joinUrl
        get() = "https://cast.govrse.app/join?a=$host:$port&t=$token"
}