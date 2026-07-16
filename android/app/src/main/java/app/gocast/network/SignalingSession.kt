package app.gocast.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class SignalingSession(
    private val httpClient: HttpClient = GocastHttpClient.instance,
    private val json: Json = Json { ignoreUnknownKeys = true },
){
    private var session: WebSocketSession? = null

    val isOpen: Boolean
        get() = session != null

    suspend fun open(host: String, port: Int, token: String): HelloResult {
        close()
        return try{
            val ws = httpClient.webSocketSession("ws://$host:$port/ws")
            session = ws
            ws.send(Frame.Text(json.encodeToString(HelloMessage("hello", token))))
            val frame = ws.incoming.receive() as? Frame.Text
                ?: return HelloResult.Failed("No response from PC").also { close() }
            if (frame.readText() == "ok"){
                HelloResult.Ok
            } else{
                close()
                HelloResult.Failed("Server rejected token")
            }
        } catch (e: java.net.ConnectException){
            close()
            HelloResult.Failed("Can't reach PC — same WiFi? Firewall?")
        } catch(e: java.net.SocketTimeoutException){
            close()
            HelloResult.Failed("Timed out waiting for PC")
        }
    }
    suspend fun close(){
        session?.close()
        session = null
    }
}