package app.gocast.network

sealed interface HelloResult {
    data class Ok(
        val trustToken: String? = null,
        val instanceId: String? = null,
        val displayName: String? = null,
    ) : HelloResult

    data class Failed(val reason: String) : HelloResult
}
