package app.gocast.network

import app.gocast.domain.ConnectionState
import app.gocast.protocol.JoinParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SessionConnector(
    private val scope: CoroutineScope,
    private val session: SignalingSession = SignalingSession(),
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    private val onRemoteEnded: () -> Unit = {},
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _paired = MutableSharedFlow<PairedSession>(extraBufferCapacity = 1)
    val paired: SharedFlow<PairedSession> = _paired.asSharedFlow()

    private var connectJob: Job? = null

    /** @param resuming true to show Reconnecting rather than Connecting from the first attempt. */
    fun start(params: JoinParams, resuming: Boolean = false) {
        stop(clearState = false)
        connectJob = scope.launch {
            var attempt = if (resuming) 1 else 0
            while (isActive) {
                _state.value = if (attempt == 0) {
                    ConnectionState.Connecting
                } else {
                    ConnectionState.Reconnecting
                }
                when (val result = session.open(params)) {
                    is HelloResult.Ok -> {
                        _paired.tryEmit(
                            PairedSession(
                                host = params.host,
                                port = params.port,
                                roomId = params.roomId,
                                trustToken = result.trustToken,
                                instanceId = result.instanceId,
                                displayName = result.displayName,
                            ),
                        )
                        // Connected only once the socket forwards messages: after a
                        // reconnect the publisher's "phone-media-ready" was dropped
                        // otherwise, and the PC stayed frozen until Retry (30 Oct).
                        val endedByServer = session.runMessageLoop(
                            onReady = { _state.value = ConnectionState.Connected },
                        )
                        if (!isActive) return@launch
                        if (endedByServer) {
                            _state.value = ConnectionState.Idle
                            onRemoteEnded()
                            return@launch
                        }
                        attempt++
                    }
                    is HelloResult.Failed -> {
                        runCatching { session.close() }
                        _state.value = ConnectionState.Failed(result.reason)
                        delay(reconnectPolicy.delayMsForAttempt(attempt))
                        attempt++
                    }
                }
            }
        }
    }

    fun stop(clearState: Boolean = true) {
        connectJob?.cancel()
        connectJob = null
        // Closing a socket the network already killed can throw; that must not crash the app.
        scope.launch { runCatching { session.close() } }
        if (clearState) {
            _state.value = ConnectionState.Idle
        }
    }
}

data class PairedSession(
    val host: String,
    val port: Int,
    val roomId: String?,
    val trustToken: String?,
    val instanceId: String?,
    val displayName: String?,
)
