package app.gocast.server

import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

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
        routing {
            get("/health") {
                call.respondText("gocast ok")
            }
        }
    }
}