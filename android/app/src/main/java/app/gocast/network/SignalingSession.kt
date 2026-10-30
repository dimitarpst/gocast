package app.gocast.network

import android.util.Log
import app.gocast.protocol.HelloMessage
import app.gocast.protocol.HelloOkMessage
import app.gocast.protocol.HostEndpoint
import app.gocast.protocol.IceServer
import app.gocast.protocol.JoinParams
import app.gocast.protocol.RemoteControl
import app.gocast.protocol.RemoteSignaling
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SignalingSession(
    private val httpClient: HttpClient = GocastHttpClient.instance,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val remoteBaseUrl: String = RemoteSignaling.BASE_URL,
    /** False on mobile data: the LAN fallback after the relay cannot answer there. */
    private val canTryLan: () -> Boolean = { true },
) {
    enum class Transport { Remote, Lan }

    private var session: DefaultClientWebSocketSession? = null

    val isOpen: Boolean
        get() = session != null

    /** Which path the open socket uses; null while closed. */
    @Volatile
    var transport: Transport? = null
        private set

    /**
     * The PC's room on the signaling Worker first (works from any network, no
     * inbound port), then the LAN socket on 8765 as before.
     */
    suspend fun open(params: JoinParams): HelloResult {
        close()
        // Credentials belong to one session; a LAN-only join must not reuse them.
        PhoneIceServers.update(IceServer.STUN_ONLY)
        val roomId = params.roomId
        var triedRemote = false
        if (roomId != null) {
            triedRemote = true
            when (val remote = openRemote(roomId, params.token)) {
                is RemoteAttempt.Ok -> return remote.result
                RemoteAttempt.Rejected -> return HelloResult.Failed(WRONG_CODE)
                RemoteAttempt.Unavailable -> {
                    if (!canTryLan()) {
                        Log.i(TAG, "Remote signaling unavailable; not on Wi-Fi, so no LAN fallback")
                        return HelloResult.Failed(PC_UNREACHABLE)
                    }
                    Log.i(TAG, "Remote signaling unavailable, trying Wi-Fi")
                }
            }
        }
        return openLan(params.host, params.port, params.token, triedRemote)
    }

    private sealed interface RemoteAttempt {
        data class Ok(val result: HelloResult.Ok) : RemoteAttempt
        /** The PC said no (wrong code or revoked trust); the LAN would say the same. */
        data object Rejected : RemoteAttempt
        /** Worker unreachable or PC not connected to its room. */
        data object Unavailable : RemoteAttempt
    }

    private suspend fun openRemote(roomId: String, token: String): RemoteAttempt {
        val url = RemoteSignaling.roomUrl(roomId, RemoteSignaling.ROLE_PHONE, remoteBaseUrl)
        val ws = try {
            // HTTP 503 (PC offline) surfaces as a handshake exception here.
            withTimeoutOrNull(8_000) { httpClient.webSocketSession(url) }
        } catch (e: Exception) {
            Log.i(TAG, "Remote signaling: ${e.message ?: e.javaClass.simpleName}")
            null
        } ?: return RemoteAttempt.Unavailable
        session = ws
        transport = Transport.Remote
        return try {
            ws.send(Frame.Text(helloJson(token)))
            val text = withTimeoutOrNull(8_000) { receiveText(ws) }
            if (text == null) {
                val reason = withTimeoutOrNull(500) { ws.closeReason.await() }
                close()
                if (reason?.code == RemoteSignaling.CLOSE_REJECTED) RemoteAttempt.Rejected else RemoteAttempt.Unavailable
            } else {
                val ok = parseOk(text)
                if (ok == null) {
                    close()
                    RemoteAttempt.Rejected
                } else {
                    // Reply handled in the message loop; STUN-only until then.
                    ws.send(Frame.Text(RemoteSignaling.GET_ICE_SERVERS))
                    Log.i(TAG, "Connected through the signaling room")
                    RemoteAttempt.Ok(ok)
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "Remote signaling failed: ${e.message ?: e.javaClass.simpleName}")
            close()
            RemoteAttempt.Unavailable
        }
    }

    private suspend fun openLan(host: String, port: Int, token: String, triedRemote: Boolean): HelloResult {
        return try {
            val ws = withTimeoutOrNull(8_000) {
                httpClient.webSocketSession("ws://${HostEndpoint.authority(host, port)}/ws")
            } ?: return HelloResult.Failed(LanHealthProbe.explainFailure(host, port, null, triedRemote)).also {
                close()
            }
            session = ws
            transport = Transport.Lan
            ws.send(Frame.Text(helloJson(token)))
            val text = withTimeoutOrNull(8_000) { receiveText(ws) }
                ?: return HelloResult.Failed("Timed out waiting for PC").also { close() }
            val ok = parseOk(text)
            if (ok == null) {
                close()
                HelloResult.Failed(WRONG_CODE)
            } else {
                Log.i(TAG, "Connected over Wi-Fi")
                ok
            }
        } catch (e: Exception) {
            close()
            HelloResult.Failed(LanHealthProbe.explainFailure(host, port, e, triedRemote))
        }
    }

    private fun helloJson(token: String): String =
        json.encodeToString(HelloMessage(type = "hello", token = token, role = "phone"))

    /** Null when the socket closes first. */
    private suspend fun receiveText(ws: DefaultClientWebSocketSession): String? =
        runCatching { (ws.incoming.receive() as? Frame.Text)?.readText() }.getOrNull()

    private fun parseOk(text: String): HelloResult.Ok? {
        if (text == "ok") return HelloResult.Ok()
        val ok = runCatching { json.decodeFromString<HelloOkMessage>(text) }.getOrNull()
        if (ok?.type != "ok") return null
        return HelloResult.Ok(
            trustToken = ok.trustToken,
            instanceId = ok.instanceId,
            displayName = ok.displayName,
        )
    }

    /**
     * Keeps the socket alive until it drops or the PC ends the session.
     * @return true if the PC sent session-ended (do not auto-reconnect).
     */
    suspend fun runMessageLoop(): Boolean {
        val ws = session ?: return false
        val remote = transport == Transport.Remote
        var endedByServer = false
        try {
            coroutineScope {
                val outgoingJob = launch {
                    SignalingBridge.outgoing.collect { text ->
                        ws.send(Frame.Text(text))
                    }
                }
                // The Worker answers pings without waking the room; credentials expire after 4 h.
                val keepAlive = if (remote) {
                    launch {
                        while (true) {
                            delay(PING_INTERVAL_MS)
                            ws.send(Frame.Text(RemoteSignaling.PING))
                        }
                    }
                } else {
                    null
                }
                val iceRefresh = if (remote) {
                    launch {
                        while (true) {
                            delay(ICE_REFRESH_MS)
                            ws.send(Frame.Text(RemoteSignaling.GET_ICE_SERVERS))
                        }
                    }
                } else {
                    null
                }
                try {
                    for (frame in ws.incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            if (isSessionEnded(text)) {
                                endedByServer = true
                                break
                            }
                            if (remote && handleControl(text)) continue
                            SignalingBridge.receive(text)
                        }
                    }
                } finally {
                    outgoingJob.cancel()
                    keepAlive?.cancel()
                    iceRefresh?.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Switching Wi-Fi ↔ mobile data kills the socket mid-read ("Software caused
            // connection abort"). That is a drop to reconnect from, not a crash.
            Log.i(TAG, "Signaling socket dropped: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { close() }
        }
        return endedByServer
    }

    /** Worker control messages never reach the publisher. */
    private fun handleControl(text: String): Boolean {
        when (val control = RemoteSignaling.parseControl(text)) {
            is RemoteControl.IceServers -> {
                PhoneIceServers.update(control.servers)
                val relay = control.servers.any { server -> server.urls.any { it.startsWith("turn") } }
                Log.i(TAG, "ICE servers from room: ${if (relay) "STUN+TURN" else "STUN only"}")
            }
            // Relay status goes to the PC only; ignore it if it ever arrives here.
            is RemoteControl.PhoneJoined, is RemoteControl.PhoneLeft, is RemoteControl.RelayStatus, RemoteControl.Pong -> Unit
            null -> return false
        }
        return true
    }

    suspend fun close() {
        session?.close()
        session = null
        transport = null
    }

    private fun isSessionEnded(text: String): Boolean {
        return runCatching {
            json.decodeFromString<SignalingMessage>(text).type == "session-ended"
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "SignalingSession"
        const val WRONG_CODE = "Wrong code or QR — open gocast on the PC and try again."
        const val PC_UNREACHABLE = "Can't reach your PC. Is GoCast open on it?"
        const val PING_INTERVAL_MS = 30_000L
        const val ICE_REFRESH_MS = 3 * 60 * 60 * 1000L
    }
}
