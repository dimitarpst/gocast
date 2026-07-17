package app.gocast.cast

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gocast.capture.CaptureGate
import app.gocast.domain.ConnectionState
import app.gocast.domain.JoinUrlParser
import app.gocast.network.SessionConnector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CastViewModel(application: Application) : AndroidViewModel(application) {

    private val connector = SessionConnector(viewModelScope)
    private val captureGate = CaptureGate()

    val connectionState: StateFlow<ConnectionState> = connector.state
    val events: SharedFlow<CastEvent> = captureGate.events

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                captureGate.onConnectionState(state)
            }
        }
    }

    fun onDeepLink(uri: Uri?) {
        val params = JoinUrlParser.parse(uri)
        if (params == null) {
            captureGate.reset()
            connector.stop()
            return
        }
        captureGate.reset()
        connector.start(params)
    }

    fun onCaptureConsentResult(resultCode: Int, data: Intent?) {
        captureGate.onConsentResult(getApplication(), resultCode, data)
    }

    override fun onCleared() {
        connector.stop()
        super.onCleared()
    }
}