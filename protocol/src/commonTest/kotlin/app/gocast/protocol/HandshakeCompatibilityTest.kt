package app.gocast.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HandshakeCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun phoneHandshakeKeepsExistingWireFormat() {
        assertEquals(
            """{"type":"hello","token":"123456"}""",
            json.encodeToString(HelloMessage(type = "hello", token = "123456", role = "phone")),
        )
    }

    @Test
    fun viewerHandshakeKeepsExplicitRole() {
        val wire = """{"type":"hello","token":"session-token","role":"viewer"}"""
        val hello = HelloMessage(type = "hello", token = "session-token", role = "viewer")
        assertEquals(wire, json.encodeToString(hello))
        assertEquals(hello, json.decodeFromString<HelloMessage>(wire))
    }

    @Test
    fun missingRoleStillMeansPhone() {
        assertEquals(
            "phone",
            json.decodeFromString<HelloMessage>("""{"type":"hello","token":"123456"}""").role,
        )
    }

    @Test
    fun handshakeStillRequiresTypeAndToken() {
        for (wire in listOf("""{"type":"hello"}""", """{"token":"123456"}""")) {
            assertFailsWith<SerializationException> { json.decodeFromString<HelloMessage>(wire) }
        }
    }

    @Test
    fun desktopReplyKeepsExistingWireFormat() {
        val reply = HelloOkMessage(trustToken = "trusted", instanceId = "pc-id", displayName = "My PC")
        val wire = """{"trustToken":"trusted","instanceId":"pc-id","displayName":"My PC"}"""
        assertEquals(wire, json.encodeToString(HelloOkMessage.serializer(), reply))
        assertEquals(reply, json.decodeFromString<HelloOkMessage>(wire))
    }

    @Test
    fun emptyTrustDetailsAreStillIncludedInDesktopReply() {
        assertEquals(
            """{"trustToken":"","instanceId":"","displayName":""}""",
            json.encodeToString(HelloOkMessage(trustToken = "", instanceId = "", displayName = "")),
        )
    }

    @Test
    fun phoneStillAcceptsRepliesWithoutTrustDetails() {
        val reply = json.decodeFromString<HelloOkMessage>("""{"type":"ok"}""")
        assertEquals("ok", reply.type)
        assertNull(reply.trustToken)
        assertNull(reply.instanceId)
        assertNull(reply.displayName)
        assertEquals(reply, json.decodeFromString<HelloOkMessage>("{}"))
    }

    @Test
    fun phoneStillAcceptsExplicitNullsAndUnknownFields() {
        val wire = """{"type":"ok","trustToken":null,"instanceId":null,"displayName":null,"futureField":true}"""
        assertEquals(HelloOkMessage(), json.decodeFromString<HelloOkMessage>(wire))
    }
}
