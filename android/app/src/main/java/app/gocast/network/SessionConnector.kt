package app.gocast.network

import android.os.SystemClock
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
import kotlinx.coroutines.withTimeoutOrNull

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

    /** When the current link came up (elapsedRealtime), to tell a fresh link from a stale one. */
    @Volatile
    var connectedAtMs: Long = 0L
        private set

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
                            onReady = {
                                connectedAtMs = SystemClock.elapsedRealtime()
                                _state.value = ConnectionState.Connected
                            },
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

    /**
     * @param endCast the user stopped on purpose: tell the PC first, so it clears
     *   its screen now. Without it the PC keeps the last frame and waits for us.
     */
    fun stop(clearState: Boolean = true, endCast: Boolean = false) {
        val job = connectJob
        connectJob = null
        if (!endCast) job?.cancel()
        scope.launch {
            if (endCast) {
                runCatching { withTimeoutOrNull(STOP_NOTICE_MS) { session.sendNow(CAST_STOPPED) } }
                job?.cancel()
            }
            // Closing a socket the network already killed can throw; that must not crash the app.
            runCatching { session.close() }
        }
        if (clearState) {
            _state.value = ConnectionState.Idle
        }
    }

    private companion object {
        const val CAST_STOPPED = """{"type":"cast-stopped"}"""
        const val STOP_NOTICE_MS = 1_000L
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
