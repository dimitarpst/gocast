package app.gocast.server

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The connected phone: a LAN socket on 8765 or a phone reached through the signaling Worker. */
interface PhoneLink {
    suspend fun send(text: String)
    suspend fun close(reason: String)
}

object SignalingHub {
    private val mutex = Mutex()
    private var phone: PhoneLink? = null
    private var viewer: WebSocketSession? = null

    private val _phoneConnected = MutableStateFlow(false)
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()

    /**
     * True once cast media is starting (phone-media-ready and/or WebRTC offer).
     * Phone WS connect alone is not enough — MediaProjection may still be pending.
     */
    private val _castingActive = MutableStateFlow(false)
    val castingActive: StateFlow<Boolean> = _castingActive.asStateFlow()

    suspend fun setPhone(link: PhoneLink) = mutex.withLock {
        phone = link
        _phoneConnected.value = true
    }

    suspend fun setViewer(session: WebSocketSession) = mutex.withLock {
        viewer = session
    }

    /**
     * The phone's link closed. Mid-cast that is a connection loss (Wi-Fi ↔ mobile
     * data, relay hiccup): keep the cast screen and its last frame up while the
     * phone reconnects. Only [endCast] or [disconnectPhone] clear it.
     */
    suspend fun clearPhone(link: PhoneLink) = mutex.withLock {
        if (phone === link) {
            phone = null
            if (!_castingActive.value) _phoneConnected.value = false
        }
    }

    /** The phone said it stopped on purpose ("cast-stopped"): clear the screen now. */
    suspend fun endCast() = mutex.withLock {
        _phoneConnected.value = false
        _castingActive.value = false
    }

    /** Kick the phone and mark disconnected (PC-side Disconnect). */
    suspend fun disconnectPhone() {
        val link = mutex.withLock {
            val current = phone
            phone = null
            _phoneConnected.value = false
            _castingActive.value = false
            current
        } ?: return
        runCatching {
            link.send("""{"type":"session-ended"}""")
        }
        runCatching {
            link.close("Disconnected from PC")
        }
    }

    suspend fun clearViewer(session: WebSocketSession) = mutex.withLock {
        if (viewer === session) viewer = null
    }

    suspend fun hasViewer(): Boolean = mutex.withLock { viewer != null }

    fun markCastingActive() {
        _castingActive.value = true
    }

    suspend fun sendToPhone(text: String) {
        val target = mutex.withLock { phone }
        target?.send(text)
    }

    suspend fun sendToViewer(text: String) {
        val target = mutex.withLock { viewer }
        target?.send(Frame.Text(text))
    }
}
