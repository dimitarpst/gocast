package app.gocast.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import app.gocast.cast.CastEvent
import app.gocast.domain.ConnectionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Connected → ask for capture consent (also used for resume after pause). */
class CaptureGate {

    // replay=1 so a resume tap that emits before Compose collects still fires consent.
    private val _events = MutableSharedFlow<CastEvent>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    val events: SharedFlow<CastEvent> = _events.asSharedFlow()

    private var captureRequested = false

    fun reset() {
        captureRequested = false
        _events.resetReplayCache()
    }

    suspend fun onConnectionState(state: ConnectionState) {
        if (state is ConnectionState.Connected && !captureRequested) {
            captureRequested = true
            _events.emit(CastEvent.RequestCaptureConsent)
        }
    }

    suspend fun requestConsentAgain() {
        captureRequested = true
        _events.emit(CastEvent.RequestCaptureConsent)
    }

    fun onConsentResult(context: Context, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            ProjectionPermissionHolder.set(resultCode, data)
            CaptureService.start(context)
        } else {
            reset()
        }
    }
}
