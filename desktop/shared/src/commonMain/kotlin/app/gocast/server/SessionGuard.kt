package app.gocast.server

object SessionGuard {
    @Volatile
    var expectedToken: String? = null

    @Volatile
    var shortCode: String? = null

    @Volatile
    var instanceId: String = ""

    @Volatile
    var displayName: String = "GoCast"

    private val trustedTokens = mutableSetOf<String>()
    private val lock = Any()

    fun replaceTrusted(tokens: Set<String>) {
        synchronized(lock) {
            trustedTokens.clear()
            trustedTokens.addAll(tokens)
        }
    }

    fun addTrusted(token: String) {
        synchronized(lock) {
            trustedTokens.add(token)
        }
    }

    fun trustedSnapshot(): Set<String> = synchronized(lock) { trustedTokens.toSet() }

    fun isAuthorized(token: String): Boolean {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return false
        val expected = expectedToken
        val code = shortCode
        if (expected != null && trimmed == expected) return true
        if (code != null && trimmed.equals(code, ignoreCase = true)) return true
        synchronized(lock) {
            return trustedTokens.contains(trimmed)
        }
    }

    fun isSessionCredential(token: String): Boolean {
        val trimmed = token.trim()
        val expected = expectedToken
        val code = shortCode
        if (expected != null && trimmed == expected) return true
        if (code != null && trimmed.equals(code, ignoreCase = true)) return true
        return false
    }
}
