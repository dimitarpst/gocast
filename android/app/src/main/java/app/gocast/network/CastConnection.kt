package app.gocast.network

import android.content.Context
import android.content.Intent
import app.gocast.capture.CaptureService
import app.gocast.domain.ConnectionState
import app.gocast.protocol.JoinParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-scoped signaling. Survives Activity / ViewModel death so the phone↔PC
 * WebSocket stays up while [app.gocast.capture.CaptureService] is casting or paused.
 */
object CastConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var appContext: Context? = null

    private val connector = SessionConnector(
        scope = scope,
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
    }

    fun start(params: JoinParams) {
        lastParams = params
        connector.start(params)
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

    fun stop() {
        lastParams = null
        connector.stop()
    }
}
