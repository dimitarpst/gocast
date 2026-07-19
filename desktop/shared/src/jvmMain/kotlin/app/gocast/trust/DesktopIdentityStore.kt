package app.gocast.trust

import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DesktopIdentityStore(
    private val dir: Path = Path.of(System.getProperty("user.home"), ".gocast"),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val identityFile = dir.resolve("identity.json")
    private val trustedFile = dir.resolve("trusted-devices.json")

    fun loadOrCreateIdentity(): DesktopIdentity {
        dir.createDirectories()
        if (identityFile.exists()) {
            return runCatching {
                json.decodeFromString<DesktopIdentity>(identityFile.readText())
            }.getOrElse { createAndPersistIdentity() }
        }
        return createAndPersistIdentity()
    }

    fun updateDisplayName(displayName: String): DesktopIdentity {
        val cleaned = displayName.trim().ifBlank { "GoCast PC" }
        val current = loadOrCreateIdentity()
        val updated = current.copy(displayName = cleaned.take(48))
        dir.createDirectories()
        identityFile.writeText(json.encodeToString(DesktopIdentity.serializer(), updated))
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

    private fun createAndPersistIdentity(): DesktopIdentity {
        val hostName = runCatching { java.net.InetAddress.getLocalHost().hostName }
            .getOrDefault("GoCast PC")
            .substringBefore('.')
            .ifBlank { "GoCast PC" }
        val identity = DesktopIdentity(
            instanceId = UUID.randomUUID().toString(),
            displayName = hostName,
        )
        identityFile.writeText(json.encodeToString(DesktopIdentity.serializer(), identity))
        return identity
    }
}

@Serializable
data class DesktopIdentity(
    val instanceId: String,
    val displayName: String,
)

@Serializable
private data class TrustedDevicesFile(
    val tokens: List<String> = emptyList(),
)
