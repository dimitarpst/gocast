package app.gocast.server

import app.gocast.domain.model.HelloMessage
import app.gocast.domain.model.HelloOkMessage
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
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class KtorGocastServer(
    private val identityStore: DesktopIdentityStore = DesktopIdentityStore(),
) : GocastServer {

    companion object {
        private const val GRACE_PERIOD_MILLIS = 1000L
        private const val TIMEOUT_MILLIS = 2000L
        private val json = Json { ignoreUnknownKeys = true }
    }

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
                call.respondText(ViewerPage.html(token), ContentType.Text.Html)
            }
            post("/__gocast/disconnect") {
                SignalingHub.disconnectPhone()
                call.respondText("ok")
            }
            post("/__gocast/show-pairing") {
                ViewerCommands.requestShowPairing()
                call.respondText("ok")
            }
            webSocket("/ws") {
                var role: String? = null
                try {
                    val first = incoming.receive() as? Frame.Text ?: run {
                        close()
                        return@webSocket
                    }
                    val hello = json.decodeFromString<HelloMessage>(first.readText())
                    if (hello.type != "hello" || !SessionGuard.isAuthorized(hello.token)) {
                        close()
                        return@webSocket
                    }

                    role = hello.role
                    when (role) {
                        "phone" -> {
                            SignalingHub.setPhone(this)
                            val trustToken = resolveTrustToken(hello.token)
                            val ok = HelloOkMessage(
                                trustToken = trustToken,
                                instanceId = SessionGuard.instanceId,
                                displayName = SessionGuard.displayName,
                            )
                            send(Frame.Text(json.encodeToString(HelloOkMessage.serializer(), ok)))
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
                            "phone" -> {
                                val type = signalingType(text)
                                if (type == "phone-media-ready") {
                                    if (SignalingHub.hasViewer()) {
                                        SignalingHub.sendToPhone("""{"type":"viewer-ready"}""")
                                    }
                                } else {
                                    SignalingHub.sendToViewer(text)
                                }
                            }
                            "viewer" -> SignalingHub.sendToPhone(text)
                        }
                    }
                } finally {
                    when (role) {
                        "phone" -> SignalingHub.clearPhone(this)
                        "viewer" -> SignalingHub.clearViewer(this)
                    }
                }
            }
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
