package app.gocast.server

import app.gocast.protocol.RemoteControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this PC's room may use the Cloudflare relay, as last reported by the
 * Worker. Null while the remote link is down.
 */
object RelayAccess {
    private val _status = MutableStateFlow<RemoteControl.RelayStatus?>(null)
    val status: StateFlow<RemoteControl.RelayStatus?> = _status.asStateFlow()

    /** Set by the remote client while connected; null means nobody to ask. */
    @Volatile
    var unlock: (suspend (String) -> RemoteControl.RelayStatus?)? = null

    fun update(status: RemoteControl.RelayStatus) {
        _status.value = status
    }

    fun clear() {
        _status.value = null
    }
}
