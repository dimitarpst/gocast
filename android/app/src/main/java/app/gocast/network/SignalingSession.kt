package app.gocast.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SignalingSession(
    private val httpClient: HttpClient = GocastHttpClient.instance,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private var session: WebSocketSession? = null

    val isOpen: Boolean
        get() = session != null

    suspend fun open(host: String, port: Int, token: String): HelloResult {
        close()
        return try {
            val ws = httpClient.webSocketSession("ws://$host:$port/ws")
            session = ws
            ws.send(
                Frame.Text(
                    json.encodeToString(HelloMessage(type = "hello", token = token, role = "phone")),
                ),
            )
            val frame = ws.incoming.receive() as? Frame.Text
                ?: return HelloResult.Failed("No response from PC").also { close() }
            if (frame.readText() == "ok") {
                HelloResult.Ok
            } else {
                close()
                HelloResult.Failed("Server rejected token")
            }
        } catch (_: java.net.ConnectException) {
            close()
            HelloResult.Failed("Can't reach PC — same WiFi? Firewall?")
        } catch (_: java.net.SocketTimeoutException) {
            close()
            HelloResult.Failed("Timed out waiting for PC")
        }
    }

    /** Keeps the socket alive: WS → bridge incoming, bridge outgoing → WS. */
    suspend fun runMessageLoop() {
        val ws = session ?: return
        try {
            coroutineScope {
                val outgoingJob = launch {
                    SignalingBridge.outgoing.collect { text ->
                        ws.send(Frame.Text(text))
                    }
                }
                try {
                    for (frame in ws.incoming) {
                        if (frame is Frame.Text) {
                            SignalingBridge.receive(frame.readText())
                        }
                    }
                } finally {
                    outgoingJob.cancel()
                }
            }
        } finally {
            close()
        }
    }

    suspend fun close() {
        session?.close()
        session = null
    }
}
