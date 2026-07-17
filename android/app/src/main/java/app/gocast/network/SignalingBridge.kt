package app.gocast.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Bridges CaptureService WebRTC ↔ SignalingSession WebSocket. */
object SignalingBridge {
    private val _outgoing = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val outgoing: SharedFlow<String> = _outgoing.asSharedFlow()

    // No replay: a resumed publisher must not see stale viewer-ready/answer/ice
    // from the previous session (it would offer against dead state).
    private val _incoming = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    fun send(text: String) {
        _outgoing.tryEmit(text)
    }

    fun receive(text: String) {
        _incoming.tryEmit(text)
    }
}
