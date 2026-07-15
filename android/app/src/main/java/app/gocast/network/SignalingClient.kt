package app.gocast.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HelloMessage(val type: String, val token: String)

class SignalingClient{
    private val client = HttpClient(OkHttp)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sendHello(host: String, port: Int, token: String): Boolean{
        var success = false;
        client.webSocket("ws://$host:$port/ws"){
            send(Frame.Text(json.encodeToString(HelloMessage("hello", token))))
            val frame = incoming.receive() as? Frame.Text ?: return@webSocket
            success = frame.readText() == "ok"
        }
        return success
    }
}