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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    private var discoveryJob: Job? = null

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
        captureGate.reset()
        CastConnection.bindAppContext(getApplication())
        CastConnection.start(params)
    }

    /** One-tap: try last host, refresh IP via discovery if needed. */
    fun castToTrustedPc() {
        val pc = _trustedPc.value ?: return
        viewModelScope.launch {
            val refreshed = withTimeoutOrNull(2_500) {
                discovery.discover().first { hosts ->
                    hosts.any { it.instanceId == pc.instanceId }
                }.first { it.instanceId == pc.instanceId }
            }
            val host = refreshed?.host ?: pc.host
            val port = refreshed?.port ?: pc.port
            if (refreshed != null && (refreshed.host != pc.host || refreshed.port != pc.port)) {
                trustedStore.save(pc.copy(host = host, port = port))
                _trustedPc.value = pc.copy(host = host, port = port)
            }
            startJoin(JoinParams(host = host, port = port, token = pc.trustToken))
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
