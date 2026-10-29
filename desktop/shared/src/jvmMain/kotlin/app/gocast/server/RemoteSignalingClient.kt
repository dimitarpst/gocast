package app.gocast.server

import app.gocast.protocol.IceServer
import app.gocast.protocol.RemoteControl
import app.gocast.protocol.RemoteSignaling
import app.gocast.trust.DesktopIdentityStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Outbound WSS link from this PC to its room on the signaling Worker. Phones that
 * arrive through it are handed to [SignalingHub] exactly like LAN phones, so the
 * viewer and the pairing screen see no difference. No inbound port is needed.
 */
class RemoteSignalingClient(
    private val roomId: String,
    private val hostKey: String,
    identityStore: DesktopIdentityStore,
    private val baseUrl: String = signalingBaseUrl(),
    private val httpClient: HttpClient = HttpClient(CIO) { install(WebSockets) },
) {
    enum class State { Offline, Connecting, Connected }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val protocol = PhoneProtocol(identityStore)
    private var job: Job? = null

    private val _state = MutableStateFlow(State.Offline)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var link: DefaultClientWebSocketSession? = null

    /** The phone the PC accepted and the newest one still waiting for its hello check. */
    @Volatile
    private var acceptedPhone: RemotePhoneLink? = null

    @Volatile
    private var pendingPhoneId: String? = null

    @Volatile
    private var iceReply: CompletableDeferred<List<IceServer>>? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runForever() }
    }

    suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        runCatching { httpClient.close() }
        _state.value = State.Offline
    }

    private suspend fun runForever() {
        var failures = 0
        while (scope.isActive) {
            _state.value = State.Connecting
            try {
                httpClient.webSocket(
                    urlString = RemoteSignaling.roomUrl(roomId, RemoteSignaling.ROLE_PC, baseUrl),
                    request = { header(HttpHeaders.Authorization, "Bearer $hostKey") },
                ) {
                    failures = 0
                    connected(this)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Remote signaling: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                disconnected()
            }
            if (!scope.isActive) break
            delay(RECONNECT_DELAYS_MS[minOf(failures, RECONNECT_DELAYS_MS.lastIndex)])
            failures++
        }
    }

    private suspend fun connected(session: DefaultClientWebSocketSession) {
        link = session
        _state.value = State.Connected
        IceServerStore.refresh = ::requestIceServers
        println("Remote signaling connected: room $roomId")
        // The Worker answers pings without waking the room; this only keeps the socket open.
        val pinger = session.launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                session.send(Frame.Text(RemoteSignaling.PING))
            }
        }
        try {
            session.send(Frame.Text(RemoteSignaling.GET_ICE_SERVERS))
            for (frame in session.incoming) {
                if (frame is Frame.Text) handle(session, frame.readText())
            }
        } finally {
            pinger.cancel()
        }
    }

    private suspend fun disconnected() {
        link = null
        if (IceServerStore.refresh == ::requestIceServers) IceServerStore.refresh = null
        iceReply?.cancel()
        iceReply = null
        pendingPhoneId = null
        acceptedPhone?.let { SignalingHub.clearPhone(it) }
        acceptedPhone = null
        if (_state.value == State.Connected) println("Remote signaling disconnected")
        _state.value = State.Offline
    }

    private suspend fun handle(session: DefaultClientWebSocketSession, text: String) {
        when (val control = RemoteSignaling.parseControl(text)) {
            is RemoteControl.PhoneJoined -> pendingPhoneId = control.phoneId
            is RemoteControl.PhoneLeft -> {
                if (pendingPhoneId == control.phoneId) pendingPhoneId = null
                val phone = acceptedPhone
                if (phone?.phoneId == control.phoneId) {
                    acceptedPhone = null
                    SignalingHub.clearPhone(phone)
                }
            }
            is RemoteControl.IceServers -> {
                IceServerStore.update(control.servers)
                iceReply?.complete(control.servers)
            }
            RemoteControl.Pong -> Unit
            // The Worker only forwards hello from a waiting phone and everything from the accepted one.
            null -> phoneMessage(session, text)
        }
    }

    private suspend fun phoneMessage(session: DefaultClientWebSocketSession, text: String) {
        val hello = protocol.parseHello(text)
        if (hello == null) {
            if (acceptedPhone != null) protocol.onPhoneMessage(text)
            return
        }
        val phoneId = pendingPhoneId ?: return
        val reply = protocol.helloReply(hello)
        if (reply == null) {
            session.send(Frame.Text(RemoteSignaling.phoneRejected(phoneId)))
            return
        }
        // Like the LAN path, a valid new phone replaces the current one.
        pendingPhoneId = null
        val phone = RemotePhoneLink(phoneId, session)
        acceptedPhone = phone
        SignalingHub.setPhone(phone)
        session.send(Frame.Text(RemoteSignaling.phoneAccepted(phoneId)))
        session.send(Frame.Text(protocol.encode(reply)))
    }

    private suspend fun requestIceServers(): List<IceServer>? {
        val session = link ?: return null
        val reply = CompletableDeferred<List<IceServer>>()
        iceReply = reply
        return runCatching {
            session.send(Frame.Text(RemoteSignaling.GET_ICE_SERVERS))
            withTimeoutOrNull(ICE_REPLY_TIMEOUT_MS) { reply.await() }
        }.getOrNull().also { if (iceReply === reply) iceReply = null }
    }

    /** One phone behind the Worker link. Closing it drops only that phone, not the link. */
    private class RemotePhoneLink(val phoneId: String, private val session: WebSocketSession) : PhoneLink {
        override suspend fun send(text: String) = session.send(Frame.Text(text))

        override suspend fun close(reason: String) {
            session.send(Frame.Text(RemoteSignaling.phoneRejected(phoneId)))
        }
    }

    companion object {
        private const val PING_INTERVAL_MS = 30_000L
        private const val ICE_REPLY_TIMEOUT_MS = 5_000L
        private val RECONNECT_DELAYS_MS = longArrayOf(2_000L, 5_000L, 10_000L, 30_000L, 60_000L)

        /** `RemoteSignaling.BASE_URL` unless a developer points a run at `wrangler dev`. */
        fun signalingBaseUrl(): String =
            System.getProperty("gocast.signalUrl")?.takeIf { it.isNotBlank() } ?: RemoteSignaling.BASE_URL
    }
}
