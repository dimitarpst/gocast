package app.gocast.server

import app.gocast.protocol.HelloMessage
import app.gocast.protocol.HelloOkMessage
import app.gocast.trust.DesktopIdentityStore
import kotlinx.serialization.json.Json

/**
 * Phone-side rules shared by the LAN socket and the remote room, so the PC stays
 * the authority on codes and trust tokens wherever the phone comes from.
 */
class PhoneProtocol(private val identityStore: DesktopIdentityStore) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The reply for a valid phone hello, or null when the phone must be dropped. */
    fun helloReply(hello: HelloMessage): HelloOkMessage? {
        if (hello.type != "hello" || hello.role != "phone") return null
        if (!SessionGuard.isAuthorized(hello.token)) return null
        return HelloOkMessage(
            trustToken = resolveTrustToken(hello.token),
            instanceId = SessionGuard.instanceId,
            displayName = SessionGuard.displayName,
        )
    }

    fun parseHello(text: String): HelloMessage? =
        runCatching { json.decodeFromString<HelloMessage>(text) }.getOrNull()

    fun encode(reply: HelloOkMessage): String = json.encodeToString(HelloOkMessage.serializer(), reply)

    /** A message from the connected phone after its hello. */
    suspend fun onPhoneMessage(text: String) {
        val type = signalingType(text)
        if (type == "cast-stopped") {
            // Stop on the phone: clear the screen now. A plain disconnect keeps it (see clearPhone).
            SignalingHub.endCast()
            return
        }
        if (type == "phone-media-ready") {
            // After MediaProjection accept — not mere WS/QR connect.
            SignalingHub.markCastingActive()
            if (SignalingHub.hasViewer()) {
                SignalingHub.sendToPhone("""{"type":"viewer-ready"}""")
            }
        } else {
            if (type == "offer") {
                SignalingHub.markCastingActive()
            }
            SignalingHub.sendToViewer(text)
        }
    }

    private fun resolveTrustToken(presentedToken: String): String {
        if (!SessionGuard.isSessionCredential(presentedToken)) {
            return presentedToken.trim()
        }
        val trust = identityStore.newTrustToken()
        SessionGuard.addTrusted(trust)
        identityStore.saveTrustedTokens(SessionGuard.trustedSnapshot())
        return trust
    }

    private fun signalingType(text: String): String? = try {
        json.decodeFromString<SignalingTypeOnly>(text).type
    } catch (_: Exception) {
        null
    }
}

@kotlinx.serialization.Serializable
private data class SignalingTypeOnly(val type: String)
