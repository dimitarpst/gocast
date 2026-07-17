package app.gocast.server

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SignalingHub {
    private val mutex = Mutex()
    private var phone: WebSocketSession? = null
    private var viewer: WebSocketSession? = null

    suspend fun setPhone(session: WebSocketSession) = mutex.withLock {
        phone = session
    }

    suspend fun setViewer(session: WebSocketSession) = mutex.withLock {
        viewer = session
    }

    suspend fun clearPhone(session: WebSocketSession) = mutex.withLock {
        if (phone === session) phone = null
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
