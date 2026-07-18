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

    private val _events = MutableSharedFlow<CastEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<CastEvent> = _events.asSharedFlow()

    private var captureRequested = false
    private var consentDialogPending = false

    fun reset() {
        captureRequested = false
        consentDialogPending = false
    }

    suspend fun onConnectionState(state: ConnectionState) {
        if (state is ConnectionState.Connected && !captureRequested) {
            captureRequested = true
            emitConsentOnce()
        }
    }

    suspend fun requestConsentAgain() {
        captureRequested = true
        emitConsentOnce()
    }

    fun onConsentResult(context: Context, resultCode: Int, data: Intent?) {
        consentDialogPending = false
        if (resultCode == Activity.RESULT_OK && data != null) {
            ProjectionPermissionHolder.set(resultCode, data)
            CaptureService.start(context)
        } else {
            reset()
        }
    }

    private suspend fun emitConsentOnce() {
        if (consentDialogPending) return
        consentDialogPending = true
        _events.emit(CastEvent.RequestCaptureConsent)
    }
}
