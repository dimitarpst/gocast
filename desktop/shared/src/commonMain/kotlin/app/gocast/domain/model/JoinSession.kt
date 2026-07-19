package app.gocast.domain.model

data class JoinSession(
    val host: String,
    val port: Int,
    val token: String,
    val shortCode: String,
    val instanceId: String,
    val displayName: String,
) {
    val joinUrl: String
        get() {
            val authority = if (host.contains(':')) "[$host]:$port" else "$host:$port"
            // Prefer shortCode over the UUID token so the QR stays low-version (large dots).
            return "https://cast.govrse.app/join?a=$authority&t=$shortCode"
        }
}
