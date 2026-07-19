package app.gocast.cast

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gocast.capture.CaptureGate
import app.gocast.capture.CaptureService
import app.gocast.data.TrustedPcStore
import app.gocast.domain.ConnectionState
import app.gocast.domain.DiscoveredHost
import app.gocast.domain.JoinParams
import app.gocast.domain.JoinUrlParser
import app.gocast.domain.TrustedPc
import app.gocast.network.CastConnection
import app.gocast.network.GocastDiscovery
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CastViewModel(application: Application) : AndroidViewModel(application) {

    private val captureGate = CaptureGate()
    private val trustedStore = TrustedPcStore(application)
    private val discovery = GocastDiscovery(application)

    val connectionState: StateFlow<ConnectionState> = CastConnection.state
    val events: SharedFlow<CastEvent> = captureGate.events

    private val _trustedPc = MutableStateFlow(trustedStore.load())
    val trustedPc: StateFlow<TrustedPc?> = _trustedPc.asStateFlow()

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredHosts: StateFlow<List<DiscoveredHost>> = _discoveredHosts.asStateFlow()

    private val _joinBusy = MutableStateFlow(false)
    val joinBusy: StateFlow<Boolean> = _joinBusy.asStateFlow()

    private var discoveryJob: Job? = null
    private var castTrustedJob: Job? = null

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                captureGate.onConnectionState(state)
            }
        }
        viewModelScope.launch {
            CastConnection.paired.collect { paired ->
                val trust = paired.trustToken ?: return@collect
                val instanceId = paired.instanceId ?: return@collect
                val name = paired.displayName ?: "GoCast PC"
                val pc = TrustedPc(
                    displayName = name,
                    host = paired.host,
                    port = paired.port,
                    instanceId = instanceId,
                    trustToken = trust,
                )
                trustedStore.save(pc)
                _trustedPc.value = pc
            }
        }
    }

    fun onDeepLink(uri: Uri?) {
        val params = JoinUrlParser.parse(uri) ?: return
        startJoin(params)
    }

    fun startJoin(params: JoinParams) {
        if (!canStartJoin()) return
        captureGate.reset()
        CastConnection.bindAppContext(getApplication())
        CastConnection.start(params)
    }

    /** One-tap: try last host, refresh IP via discovery if needed. */
    fun castToTrustedPc() {
        if (_joinBusy.value || castTrustedJob?.isActive == true) return
        if (!canStartJoin()) return
        if (_trustedPc.value == null) return
        _joinBusy.value = true
        castTrustedJob = viewModelScope.launch {
            try {
                val resolved = TrustedCastResolver.resolveTrustedJoin(getApplication())
                    ?: return@launch
                val (pc, params) = resolved
                _trustedPc.value = pc
                startJoin(params)
            } finally {
                _joinBusy.value = false
            }
        }
    }

    private fun canStartJoin(): Boolean {
        return when (connectionState.value) {
            is ConnectionState.Idle,
            is ConnectionState.Failed,
            -> true
            is ConnectionState.Connecting,
            is ConnectionState.Connected,
            is ConnectionState.Reconnecting,
            -> false
        }
    }

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = viewModelScope.launch {
            discovery.discover().collect { hosts ->
                _discoveredHosts.value = hosts
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _discoveredHosts.value = emptyList()
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

    /** Same as notification Stop — end capture and drop the PC link. */
    fun stopCast() {
        castTrustedJob?.cancel()
        castTrustedJob = null
        _joinBusy.value = false
        val context = getApplication<Application>()
        runCatching {
            context.startService(
                Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_STOP),
            )
        }
        CastConnection.stop()
        captureGate.reset()
    }

    override fun onCleared() {
        stopDiscovery()
    }
}
