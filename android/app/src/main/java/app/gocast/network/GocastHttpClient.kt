package app.gocast.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object GocastHttpClient{
    val instance = HttpClient(OkHttp){
        engine {
            preconfigured = OkHttpClient.Builder()
                .pingInterval(15, TimeUnit.SECONDS)
                // Give up on a stalled address (e.g. IPv6 on some mobile networks) soon
                // enough for OkHttp to try the next one within one join attempt.
                .connectTimeout(4, TimeUnit.SECONDS)
                .build()
        }
        install(WebSockets)
    }
}