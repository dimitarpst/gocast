package app.gocast.server

import app.gocast.protocol.RemoteSignaling
import app.gocast.trust.DesktopIdentityStore
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlin.time.Duration.Companion.seconds

class KtorGocastServer(
    identityStore: DesktopIdentityStore = DesktopIdentityStore(),
) : GocastServer {

    companion object {
        private const val GRACE_PERIOD_MILLIS = 1000L
        private const val TIMEOUT_MILLIS = 2000L
    }

    private val phoneProtocol = PhoneProtocol(identityStore)
    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    override val isRunning: Boolean get() = engine != null

    override suspend fun start(port: Int) {
        if (isRunning) return

        // IPv4 any — phones on LAN connect over IPv4. Binding only to "::" is
        // dual-stack on some Windows setups and a silent black hole on others.
        engine = embeddedServer(Netty, host = "0.0.0.0", port = port) {
            configureRouting()
        }.start(wait = false)
    }

    override suspend fun stop() {
        engine?.stop(GRACE_PERIOD_MILLIS, TIMEOUT_MILLIS)
        engine = null
    }

    private fun Application.configureRouting() {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
        }
        routing {
            get("/health") {
                call.respondText("gocast ok")
            }
            get("/view") {
                val token = SessionGuard.expectedToken.orEmpty()
                val iceServers = RemoteSignaling.encodeIceServers(IceServerStore.current())
                call.respondText(ViewerPage.html(token, iceServers), ContentType.Text.Html)
            }
            // The viewer asks before each offer so relay credentials never go stale.
            get("/__gocast/ice-servers") {
                call.respondText(
                    RemoteSignaling.encodeIceServers(IceServerStore.current()),
                    ContentType.Application.Json,
                )
            }
            post("/__gocast/disconnect") {
                SignalingHub.disconnectPhone()
                call.respondText("ok")
            }
            post("/__gocast/show-pairing") {
                ViewerCommands.requestShowPairing()
                call.respondText("ok")
            }
            post("/__gocast/toggle-fullscreen") {
                ViewerCommands.requestToggleFullscreen()
                call.respondText("ok")
            }
            post("/__gocast/exit-fullscreen") {
                ViewerCommands.requestExitFullscreen()
                call.respondText("ok")
            }
            webSocket("/ws") {
                var role: String? = null
                val phoneLink = LanPhoneLink(this)
                try {
                    val first = incoming.receive() as? Frame.Text ?: run {
                        close()
                        return@webSocket
                    }
                    val hello = phoneProtocol.parseHello(first.readText())
                    if (hello == null || hello.type != "hello" || !SessionGuard.isAuthorized(hello.token)) {
                        close()
                        return@webSocket
                    }

                    role = hello.role
                    when (role) {
                        "phone" -> {
                            val ok = phoneProtocol.helloReply(hello) ?: run {
                                close()
                                return@webSocket
                            }
                            SignalingHub.setPhone(phoneLink)
                            send(Frame.Text(phoneProtocol.encode(ok)))
                        }
                        "viewer" -> {
                            SignalingHub.setViewer(this)
                            send(Frame.Text("ok"))
                            SignalingHub.sendToPhone("""{"type":"viewer-ready"}""")
                        }
                        else -> {
                            close()
                            return@webSocket
                        }
                    }

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        when (role) {
                            "phone" -> phoneProtocol.onPhoneMessage(text)
                            "viewer" -> SignalingHub.sendToPhone(text)
                        }
                    }
                } finally {
                    when (role) {
                        "phone" -> SignalingHub.clearPhone(phoneLink)
                        "viewer" -> SignalingHub.clearViewer(this)
                    }
                }
            }
        }
    }

    /** A phone on the LAN socket; unchanged behaviour, now behind [PhoneLink]. */
    private class LanPhoneLink(private val session: WebSocketSession) : PhoneLink {
        override suspend fun send(text: String) = session.send(Frame.Text(text))

        override suspend fun close(reason: String) =
            session.close(CloseReason(CloseReason.Codes.NORMAL, reason))
    }
}
