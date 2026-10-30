package app.gocast.network

import android.content.Context
import android.content.Intent
import android.util.Log
import app.gocast.capture.CaptureService
import app.gocast.domain.ConnectionState
import app.gocast.protocol.JoinParams
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Process-scoped signaling. Survives Activity / ViewModel death so the phone↔PC
 * WebSocket stays up while [app.gocast.capture.CaptureService] is casting or paused.
 */
object CastConnection {

    // Last line of defence: a network error that slips through must end in a
    // reconnect, never in Android killing the app mid-cast (seen 30 Oct).
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Log.w("CastConnection", "Connection error", e) },
    )

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var networkWatch: NetworkWatch? = null
    private var switchJob: Job? = null

    private val connector = SessionConnector(
        scope = scope,
        // On mobile data the PC's LAN address can never answer; don't wait 16 s on it.
        session = SignalingSession(canTryLan = { networkWatch?.isOnLocalNetwork() ?: true }),
        onRemoteEnded = {
            val ctx = appContext ?: return@SessionConnector
            runCatching {
                ctx.startService(
                    Intent(ctx, CaptureService::class.java).setAction(CaptureService.ACTION_STOP),
                )
            }
        },
    )

    @Volatile
    private var lastParams: JoinParams? = null

    val state: StateFlow<ConnectionState> = connector.state
    val paired: SharedFlow<PairedSession> = connector.paired

    fun bindAppContext(context: Context) {
        appContext = context.applicationContext
        if (networkWatch == null) {
            networkWatch = NetworkWatch(
                context.applicationContext,
                onSwitched = { scope.launch { reconnectSoon(networkSwitched = true) } },
                onInternetBack = { scope.launch { reconnectSoon(networkSwitched = false) } },
            )
        }
    }

    @Volatile
    private var pendingSwitch = false

    /**
     * The phone changed network (the old socket and media path are dead), or the
     * new one just got working internet: reconnect now instead of after the ping
     * timeout and back-off. Internet coming back leaves a working link alone.
     */
    private fun reconnectSoon(networkSwitched: Boolean) {
        pendingSwitch = pendingSwitch || networkSwitched
        switchJob?.cancel()
        switchJob = scope.launch {
            // Let the new network settle (Wi-Fi off reports a few changes at once).
            delay(NETWORK_SETTLE_MS)
            val switched = pendingSwitch
            pendingSwitch = false
            val params = lastParams ?: return@launch
            when (connector.state.value) {
                is ConnectionState.Idle,
                is ConnectionState.Connecting,
                -> Unit
                is ConnectionState.Connected -> if (switched) connector.start(params, resuming = true)
                is ConnectionState.Reconnecting,
                is ConnectionState.Failed,
                -> connector.start(params, resuming = true)
            }
        }
    }

    private const val NETWORK_SETTLE_MS = 400L

    fun start(params: JoinParams) {
        lastParams = params
        connector.start(params)
    }

    /**
     * The user's Refresh: rebuild the link to the PC now. Once it is back, the
     * publisher announces itself and the PC asks for a fresh video offer.
     */
    fun refresh() {
        val params = lastParams ?: return
        connector.start(params, resuming = true)
    }

    /** Reconnect if we still have join params but the socket dropped. */
    fun ensureConnected() {
        val params = lastParams ?: return
        when (connector.state.value) {
            is ConnectionState.Connected,
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting,
            -> Unit
            else -> connector.start(params)
        }
    }

    /** A deliberate stop (Stop button, notification, Quick Settings): the PC clears its screen. */
    fun stop() {
        lastParams = null
        connector.stop(endCast = true)
    }
}
