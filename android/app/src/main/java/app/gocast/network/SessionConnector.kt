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

    fun start(params: JoinParams) {
        stop(clearState = false)
        connectJob = scope.launch {
            var attempt = 0
            while (isActive) {
                _state.value = if (attempt == 0) {
                    ConnectionState.Connecting
                } else {
                    ConnectionState.Reconnecting
                }
                when (val result = session.open(params)) {
                    is HelloResult.Ok -> {
                        _state.value = ConnectionState.Connected
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
                        val endedByServer = session.runMessageLoop()
                        if (!isActive) return@launch
                        if (endedByServer) {
                            _state.value = ConnectionState.Idle
                            onRemoteEnded()
                            return@launch
                        }
                        attempt++
                    }
                    is HelloResult.Failed -> {
                        session.close()
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
        scope.launch { session.close() }
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
