package app.gocast.network

import app.gocast.domain.ConnectionState
import app.gocast.domain.JoinParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SessionConnector(
    private val scope: CoroutineScope,
    private val session: SignalingSession = SignalingSession(),
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var connectJob: Job? = null

    fun start(params: JoinParams) {
        stop()
        connectJob = scope.launch {
            var attempt = 0
            while (isActive) {
                _state.value = if (attempt == 0) {
                    ConnectionState.Connecting
                } else {
                    ConnectionState.Reconnecting
                }
                when (val result = session.open(params.host, params.port, params.token)) {
                    is HelloResult.Ok -> {
                        _state.value = ConnectionState.Connected
                        session.runMessageLoop()
                        if (!isActive) return@launch
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

    fun stop() {
        connectJob?.cancel()
        connectJob = null
        scope.launch { session.close() }
        _state.value = ConnectionState.Idle
    }
}
