package app.gocast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteSignalingTest {
    @Test
    fun roomUrlUsesTheSignalingDomain() {
        assertEquals(
            "wss://signal.govrse.app/room/AbCdEfGhIjKlMnOpQrStUv?role=pc",
            RemoteSignaling.roomUrl("AbCdEfGhIjKlMnOpQrStUv", RemoteSignaling.ROLE_PC),
        )
        assertEquals(
            "ws://localhost:8787/room/AbCdEfGhIjKlMnOpQrStUv?role=phone",
            RemoteSignaling.roomUrl("AbCdEfGhIjKlMnOpQrStUv", RemoteSignaling.ROLE_PHONE, "ws://localhost:8787"),
        )
    }

    @Test
    fun outgoingControlMessagesMatchTheWorker() {
        // The Worker auto-answers this exact ping string.
        assertEquals("""{"type":"ping"}""", RemoteSignaling.PING)
        assertEquals("""{"type":"get-ice-servers"}""", RemoteSignaling.GET_ICE_SERVERS)
        assertEquals(
            """{"type":"phone-accepted","phoneId":"p-1"}""",
            RemoteSignaling.phoneAccepted("p-1"),
        )
        assertEquals(
            """{"type":"phone-rejected","phoneId":"p-1"}""",
            RemoteSignaling.phoneRejected("p-1"),
        )
    }

    @Test
    fun parsesWorkerControlMessages() {
        assertEquals(
            RemoteControl.PhoneJoined("p-1"),
            RemoteSignaling.parseControl("""{"type":"phone-joined","phoneId":"p-1"}"""),
        )
        assertEquals(
            RemoteControl.PhoneLeft("p-1"),
            RemoteSignaling.parseControl("""{"type":"phone-left","phoneId":"p-1"}"""),
        )
        assertEquals(RemoteControl.Pong, RemoteSignaling.parseControl("""{"type":"pong"}"""))
        assertEquals(
            RemoteControl.IceServers(
                listOf(
                    IceServer(listOf("stun:stun.cloudflare.com:3478")),
                    IceServer(
                        listOf("turn:turn.cloudflare.com:3478?transport=udp"),
                        username = "user",
                        credential = "secret",
                    ),
                ),
            ),
            RemoteSignaling.parseControl(
                """{"type":"ice-servers","iceServers":[{"urls":["stun:stun.cloudflare.com:3478"]},""" +
                    """{"urls":["turn:turn.cloudflare.com:3478?transport=udp"],"username":"user","credential":"secret","future":1}]}""",
            ),
        )
    }

    @Test
    fun normalGocastMessagesAreNotControlMessages() {
        for (text in listOf(
            """{"type":"hello","token":"ABCD"}""",
            """{"trustToken":"t","instanceId":"i","displayName":"PC"}""",
            """{"type":"offer","sdp":"v=0"}""",
            """{"type":"viewer-ready"}""",
            """{"type":"session-ended"}""",
            "ok",
            "not json",
            """{"type":"phone-joined"}""",
            """{"type":"ice-servers","iceServers":"wrong"}""",
        )) {
            assertNull(RemoteSignaling.parseControl(text), text)
        }
    }

    @Test
    fun iceServersEncodeAsRtcIceServers() {
        assertEquals(
            """[{"urls":["stun:stun.cloudflare.com:3478"]},{"urls":["turn:t:3478"],"username":"u","credential":"c"}]""",
            RemoteSignaling.encodeIceServers(
                IceServer.STUN_ONLY + IceServer(listOf("turn:t:3478"), "u", "c"),
            ),
        )
    }

    @Test
    fun roomIdsMatchTheWorkerFormat() {
        for (valid in listOf("AbCdEfGhIjKlMnOpQrStUv", "a".repeat(16), "A-b_C".repeat(12).take(64))) {
            assertEquals(true, RoomId.isValid(valid), valid)
        }
        for (invalid in listOf("", "short", "a".repeat(65), "has space in it xx", "slash/in/the/room/id", "dots.in.the.room.id")) {
            assertEquals(false, RoomId.isValid(invalid), invalid)
        }
    }
}
