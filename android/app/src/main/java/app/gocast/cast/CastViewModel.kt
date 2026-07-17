package app.gocast.cast

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gocast.capture.CaptureGate
import app.gocast.domain.ConnectionState
import app.gocast.domain.JoinUrlParser
import app.gocast.network.CastConnection
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CastViewModel(application: Application) : AndroidViewModel(application) {

    private val captureGate = CaptureGate()

    val connectionState: StateFlow<ConnectionState> = CastConnection.state
    val events: SharedFlow<CastEvent> = captureGate.events

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                captureGate.onConnectionState(state)
            }
        }
    }

    fun onDeepLink(uri: Uri?) {
        val params = JoinUrlParser.parse(uri) ?: return
        // Null / missing URI must NOT stop the socket — resume notification opens
        // MainActivity without a join URL and used to kill Peer here.
        captureGate.reset()
        CastConnection.start(params)
    }

    fun onResumeCastRequested() {
        CastConnection.ensureConnected()
        viewModelScope.launch {
            captureGate.requestConsentAgain()
        }
    }

    fun onCaptureConsentResult(resultCode: Int, data: Intent?) {
        captureGate.onConsentResult(getApplication(), resultCode, data)
    }

    // Do not stop CastConnection in onCleared — CaptureService owns lifetime
    // while casting/paused so the PC link survives Activity death.
}
