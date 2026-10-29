package app.gocast.server

import app.gocast.protocol.IceServer
import app.gocast.protocol.RemoteSignaling
import app.gocast.trust.DesktopIdentityStore
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Drives [RemoteSignalingClient] against a loopback stand-in for the Worker's room. */
class RemoteSignalingClientTest {
    private val roomId = "RoomAbCdEfGhIjKlMnOpQr"
    private val hostKey = "host-key-0123456789abcdefghijklmnopqrstuv"
    private val worker = FakeWorker()
    private val dir = Files.createTempDirectory("gocast-remote")
    private val store = DesktopIdentityStore(dir)
    private val client = RemoteSignalingClient(roomId, hostKey, store, baseUrl = "ws://127.0.0.1:${worker.port}")

    @BeforeTest
    fun setUp() {
        SessionGuard.expectedToken = "session-token"
        SessionGuard.shortCode = "ABCD"
        SessionGuard.instanceId = "pc-id"
        SessionGuard.displayName = "Test PC"
        SessionGuard.replaceTrusted(emptySet())
        IceServerStore.reset()
    }

    @AfterTest
    fun tearDown() = runBlocking {
        client.stop()
        SignalingHub.disconnectPhone()
        worker.stop()
        IceServerStore.reset()
    }

    @Test
    fun pcClaimsItsRoomAndAcceptsOnlyValidPhones() = runBlocking {
        withTimeout(30_000) {
            client.start()
            val pc = worker.nextConnection()
            assertEquals("/room/$roomId", pc.path)
            assertEquals("pc", pc.role)
            assertEquals("Bearer $hostKey", pc.authorization)
            assertEquals(RemoteSignaling.GET_ICE_SERVERS, pc.next())

            // A wrong code is rejected and never reaches the hub.
            pc.send("""{"type":"phone-joined","phoneId":"p1"}""")
            pc.send("""{"type":"hello","token":"WRONG","role":"phone"}""")
            assertEquals(RemoteSignaling.phoneRejected("p1"), pc.next())
            assertFalse(SignalingHub.phoneConnected.value)

            // The pairing code is accepted case-insensitively, like on the LAN.
            pc.send("""{"type":"phone-joined","phoneId":"p2"}""")
            pc.send("""{"type":"hello","token":"abcd","role":"phone"}""")
            assertEquals(RemoteSignaling.phoneAccepted("p2"), pc.next())
            val ok = Json.parseToJsonElement(pc.next()).jsonObject
            assertEquals("pc-id", ok["instanceId"]?.jsonPrimitive?.content)
            assertEquals("Test PC", ok["displayName"]?.jsonPrimitive?.content)
            val trust = checkNotNull(ok["trustToken"]?.jsonPrimitive?.content)
            assertTrue(SignalingHub.phoneConnected.first { it })
            assertTrue(trust in store.loadTrustedTokens())
            assertTrue(SessionGuard.isAuthorized(trust))

            // Phone messages follow the LAN rules.
            assertFalse(SignalingHub.castingActive.value)
            pc.send("""{"type":"phone-media-ready"}""")
            assertTrue(SignalingHub.castingActive.first { it })

            // PC-side Disconnect ends the session and drops just that phone.
            SignalingHub.disconnectPhone()
            assertEquals("""{"type":"session-ended"}""", pc.next())
            assertEquals(RemoteSignaling.phoneRejected("p2"), pc.next())
            pc.send("""{"type":"phone-left","phoneId":"p2"}""")
            assertFalse(SignalingHub.phoneConnected.value)
        }
    }

    @Test
    fun iceServersComeFromTheWorkerAndRefreshWhenStale() = runBlocking {
        withTimeout(30_000) {
            assertEquals(IceServer.STUN_ONLY, IceServerStore.current())
            client.start()
            val pc = worker.nextConnection()
            assertEquals(RemoteSignaling.GET_ICE_SERVERS, pc.next())
            val relayed = """[{"urls":["stun:stun.cloudflare.com:3478"]},""" +
                """{"urls":["turn:turn.cloudflare.com:3478?transport=udp"],"username":"u","credential":"c"}]"""
            pc.send("""{"type":"ice-servers","iceServers":$relayed}""")
            val expected = listOf(
                IceServer(listOf("stun:stun.cloudflare.com:3478")),
                IceServer(listOf("turn:turn.cloudflare.com:3478?transport=udp"), "u", "c"),
            )
            waitUntil { IceServerStore.current() == expected }
            assertEquals(relayed, RemoteSignaling.encodeIceServers(IceServerStore.current()))

            // Stale credentials are re-requested before the viewer uses them.
            IceServerStore.invalidate()
            val refreshed = async { IceServerStore.current() }
            assertEquals(RemoteSignaling.GET_ICE_SERVERS, pc.next())
            pc.send("""{"type":"ice-servers","iceServers":[{"urls":["stun:stun.cloudflare.com:3478"]}]}""")
            assertEquals(IceServer.STUN_ONLY, refreshed.await())
        }
    }

    @Test
    fun linkLossDropsThePhoneAndReconnects() = runBlocking {
        withTimeout(30_000) {
            client.start()
            val first = worker.nextConnection()
            assertEquals(RemoteSignaling.GET_ICE_SERVERS, first.next())
            first.send("""{"type":"phone-joined","phoneId":"p1"}""")
            first.send("""{"type":"hello","token":"session-token","role":"phone"}""")
            assertEquals(RemoteSignaling.phoneAccepted("p1"), first.next())
            first.next()
            assertTrue(SignalingHub.phoneConnected.first { it })

            first.close()
            assertFalse(SignalingHub.phoneConnected.first { !it })
            val second = worker.nextConnection()
            assertEquals("Bearer $hostKey", second.authorization)
            assertEquals(RemoteSignaling.GET_ICE_SERVERS, second.next())
            assertEquals(RemoteSignalingClient.State.Connected, client.state.first { it == RemoteSignalingClient.State.Connected })
        }
    }

    private suspend fun waitUntil(condition: suspend () -> Boolean) {
        while (!condition()) delay(20)
    }

    /** One Worker-side room socket as seen by the test. */
    private class Connection(
        val path: String,
        val role: String?,
        val authorization: String?,
        private val session: WebSocketSession,
        private val received: Channel<String>,
    ) {
        suspend fun next(): String = withTimeout(5_000) { received.receive() }
        suspend fun send(text: String) = session.send(Frame.Text(text))
        suspend fun close() = session.close()
    }

    private class FakeWorker {
        private val connections = Channel<Connection>(Channel.UNLIMITED)
        private val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            install(WebSockets)
            routing {
                webSocket("/room/{room}") {
                    val received = Channel<String>(Channel.UNLIMITED)
                    connections.send(
                        Connection(
                            path = call.request.local.uri.substringBefore('?'),
                            role = call.request.queryParameters["role"],
                            authorization = call.request.headers["Authorization"],
                            session = this,
                            received = received,
                        ),
                    )
                    for (frame in incoming) {
                        if (frame is Frame.Text) received.send(frame.readText())
                    }
                }
            }
        }.start(wait = false)

        val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }

        suspend fun nextConnection(): Connection = withTimeout(15_000) { connections.receive() }

        fun stop() = server.stop(100, 500)
    }
}
