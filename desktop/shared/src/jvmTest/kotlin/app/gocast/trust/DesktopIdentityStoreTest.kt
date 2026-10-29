package app.gocast.trust

import app.gocast.protocol.RoomId
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopIdentityStoreTest {
    private val dir = Files.createTempDirectory("gocast-identity")

    @Test
    fun existingIdentityGainsARoomWithoutLosingNameOrTrust() {
        // Written by a build from before remote casting.
        dir.resolve("identity.json").writeText("""{"instanceId":"old-pc","displayName":"Old PC"}""")
        dir.resolve("trusted-devices.json").writeText("""{"tokens":["trusted-phone"]}""")
        val store = DesktopIdentityStore(dir)

        val identity = store.loadOrCreateIdentity()
        assertEquals("old-pc", identity.instanceId)
        assertEquals("Old PC", identity.displayName)
        val roomId = checkNotNull(identity.roomId)
        val hostKey = checkNotNull(identity.hostKey)
        assertTrue(RoomId.isValid(roomId), roomId)
        assertTrue(hostKey.length >= 32)
        assertNotEquals(roomId, hostKey)
        assertEquals(setOf("trusted-phone"), store.loadTrustedTokens())

        // Persisted once, then stable across restarts and renames.
        assertTrue(dir.resolve("identity.json").readText().contains(roomId))
        assertEquals(identity, DesktopIdentityStore(dir).loadOrCreateIdentity())
        val renamed = store.updateDisplayName("New PC")
        assertEquals(identity.copy(displayName = "New PC"), renamed)
        assertEquals(renamed, store.loadOrCreateIdentity())
    }

    @Test
    fun newIdentityHasARoomAndIsUrlSafe() {
        val identity = DesktopIdentityStore(dir).loadOrCreateIdentity()
        val roomId = checkNotNull(identity.roomId)
        assertTrue(RoomId.isValid(roomId), roomId)
        assertTrue(roomId.none { it == '+' || it == '/' || it == '=' })
        assertTrue(checkNotNull(identity.hostKey).all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun malformedRoomIsReplacedButTheKeyIsKept() {
        val key = "k".repeat(43)
        dir.resolve("identity.json").writeText(
            """{"instanceId":"pc","displayName":"PC","roomId":"bad room","hostKey":"$key"}""",
        )
        val identity = DesktopIdentityStore(dir).loadOrCreateIdentity()
        assertTrue(RoomId.isValid(checkNotNull(identity.roomId)))
        assertEquals(key, identity.hostKey)
    }
}
