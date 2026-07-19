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
            val ws = httpClient.webSocketSession("ws://${HostEndpoint.authority(host, port)}/ws")
            session = ws
            ws.send(
                Frame.Text(
                    json.encodeToString(HelloMessage(type = "hello", token = token, role = "phone")),
                ),
            )
            val frame = ws.incoming.receive() as? Frame.Text
                ?: return HelloResult.Failed("No response from PC").also { close() }
            val text = frame.readText()
            when {
                text == "ok" -> HelloResult.Ok()
                else -> {
                    val ok = runCatching { json.decodeFromString<HelloOkMessage>(text) }.getOrNull()
                    if (ok?.type == "ok") {
                        HelloResult.Ok(
                            trustToken = ok.trustToken,
                            instanceId = ok.instanceId,
                            displayName = ok.displayName,
                        )
                    } else {
                        close()
                        HelloResult.Failed("Server rejected token")
                    }
                }
            }
        } catch (_: java.net.ConnectException) {
            close()
            HelloResult.Failed("Can't reach PC — same WiFi? Firewall?")
        } catch (_: java.net.SocketTimeoutException) {
            close()
            HelloResult.Failed("Timed out waiting for PC")
        }
    }

    /**
     * Keeps the socket alive until it drops or the PC ends the session.
     * @return true if the PC sent session-ended (do not auto-reconnect).
     */
    suspend fun runMessageLoop(): Boolean {
        val ws = session ?: return false
        var endedByServer = false
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
                            val text = frame.readText()
                            if (isSessionEnded(text)) {
                                endedByServer = true
                                break
                            }
                            SignalingBridge.receive(text)
                        }
                    }
                } finally {
                    outgoingJob.cancel()
                }
            }
        } finally {
            close()
        }
        return endedByServer
    }

    suspend fun close() {
        session?.close()
        session = null
    }

    private fun isSessionEnded(text: String): Boolean {
        return runCatching {
            json.decodeFromString<SignalingMessage>(text).type == "session-ended"
        }.getOrDefault(false)
    }
}
