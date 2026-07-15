package app.gocast.server

import app.gocast.domain.model.HelloMessage
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json

class KtorGocastServer : GocastServer {

    companion object{
        private const val GRACE_PERIOD_MILLIS = 1000L
        private const val TIMEOUT_MILLIS = 2000L
    }

    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    override val isRunning: Boolean get() = engine != null

    override suspend fun start(port: Int) {
        if(isRunning) return

        engine = embeddedServer(Netty, port = port) {
            configureRouting()
        }.start(wait = false)
    }

    override suspend fun stop() {
        engine?.stop(GRACE_PERIOD_MILLIS, TIMEOUT_MILLIS)
        engine = null
    }

    private fun Application.configureRouting() {
        install(WebSockets)
        routing {
            get("/health") {
                call.respondText("gocast ok")
            }
            webSocket("/ws"){
                for(frame in incoming){
                    if(frame !is Frame.Text) continue

                    val msg = Json.decodeFromString<HelloMessage>(frame.readText())

                    if(msg.type == "hello" && msg.token == SessionGuard.expectedToken){
                        send(Frame.Text("ok"))
                    }else{
                        close()
                    }
                }
            }
        }
    }
}