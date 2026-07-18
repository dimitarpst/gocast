package app.gocast.server

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SignalingHub {
    private val mutex = Mutex()
    private var phone: WebSocketSession? = null
    private var viewer: WebSocketSession? = null

    private val _phoneConnected = MutableStateFlow(false)
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()

    suspend fun setPhone(session: WebSocketSession) = mutex.withLock {
        phone = session
        _phoneConnected.value = true
    }

    suspend fun setViewer(session: WebSocketSession) = mutex.withLock {
        viewer = session
    }

    suspend fun clearPhone(session: WebSocketSession) = mutex.withLock {
        if (phone === session) {
            phone = null
            _phoneConnected.value = false
        }
    }

    /** Kick the phone and mark disconnected (PC-side Disconnect). */
    suspend fun disconnectPhone() {
        val session = mutex.withLock {
            val current = phone
            phone = null
            _phoneConnected.value = false
            current
        } ?: return
        runCatching {
            session.send(Frame.Text("""{"type":"session-ended"}"""))
        }
        runCatching {
            session.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnected from PC"))
        }
    }

    suspend fun clearViewer(session: WebSocketSession) = mutex.withLock {
        if (viewer === session) viewer = null
    }

    suspend fun hasViewer(): Boolean = mutex.withLock { viewer != null }

    suspend fun sendToPhone(text: String) {
        val target = mutex.withLock { phone }
        target?.send(Frame.Text(text))
    }

    suspend fun sendToViewer(text: String) {
        val target = mutex.withLock { viewer }
        target?.send(Frame.Text(text))
    }
}
