package app.gocast.trust

import app.gocast.platform.GocastDataDirs
import app.gocast.protocol.RoomId
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DesktopIdentityStore(
    private val dir: Path = GocastDataDirs.root().toPath(),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val identityFile = dir.resolve("identity.json")
    private val trustedFile = dir.resolve("trusted-devices.json")

    fun loadOrCreateIdentity(): DesktopIdentity {
        dir.createDirectories()
        if (identityFile.exists()) {
            return runCatching {
                json.decodeFromString<DesktopIdentity>(identityFile.readText())
            }.map(::withRemoteRoom).getOrElse { createAndPersistIdentity() }
        }
        return createAndPersistIdentity()
    }

    fun updateDisplayName(displayName: String): DesktopIdentity {
        val cleaned = displayName.trim().ifBlank { "gocast PC" }
        val current = loadOrCreateIdentity()
        val updated = current.copy(displayName = cleaned.take(48))
        persist(updated)
        return updated
    }

    fun loadTrustedTokens(): Set<String> {
        if (!trustedFile.exists()) return emptySet()
        return runCatching {
            json.decodeFromString<TrustedDevicesFile>(trustedFile.readText()).tokens.toSet()
        }.getOrDefault(emptySet())
    }

    fun saveTrustedTokens(tokens: Set<String>) {
        dir.createDirectories()
        trustedFile.writeText(json.encodeToString(TrustedDevicesFile.serializer(), TrustedDevicesFile(tokens.toList())))
    }

    fun newTrustToken(): String = UUID.randomUUID().toString()

    /** Identities from before remote casting get a room once; the PC name and trust stay. */
    private fun withRemoteRoom(identity: DesktopIdentity): DesktopIdentity {
        val roomOk = identity.roomId?.let(RoomId::isValid) == true
        val keyOk = (identity.hostKey?.length ?: 0) >= HOST_KEY_MIN_CHARS
        if (roomOk && keyOk) return identity
        val updated = identity.copy(
            roomId = if (roomOk) identity.roomId else newRoomId(),
            hostKey = if (keyOk) identity.hostKey else newHostKey(),
        )
        persist(updated)
        return updated
    }

    private fun persist(identity: DesktopIdentity) {
        dir.createDirectories()
        identityFile.writeText(json.encodeToString(DesktopIdentity.serializer(), identity))
    }

    private fun createAndPersistIdentity(): DesktopIdentity {
        val hostName = runCatching { java.net.InetAddress.getLocalHost().hostName }
            .getOrDefault("gocast PC")
            .substringBefore('.')
            .ifBlank { "gocast PC" }
        val identity = DesktopIdentity(
            instanceId = UUID.randomUUID().toString(),
            displayName = hostName,
            roomId = newRoomId(),
            hostKey = newHostKey(),
        )
        persist(identity)
        return identity
    }

    private companion object {
        const val HOST_KEY_MIN_CHARS = 32
        val random = SecureRandom()

        // URL-safe so the room fits in the QR link and the Worker path unchanged.
        fun randomToken(bytes: Int): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))

        /** 22 characters: unguessable, still short enough for the QR. */
        fun newRoomId(): String = randomToken(16)

        /** Proves to the Worker that this PC owns the room; never leaves this machine except over WSS. */
        fun newHostKey(): String = randomToken(32)
    }
}

@Serializable
data class DesktopIdentity(
    val instanceId: String,
    val displayName: String,
    /** Remote signaling room and its key. Null only while reading a pre-remote identity.json. */
    val roomId: String? = null,
    val hostKey: String? = null,
)

@Serializable
private data class TrustedDevicesFile(
    val tokens: List<String> = emptyList(),
)
