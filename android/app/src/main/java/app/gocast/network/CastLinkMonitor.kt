package app.gocast.network

import app.gocast.protocol.LinkType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The link the running cast uses (LAN, internet or relay), for the UI and logs. */
object CastLinkMonitor {
    private val _linkType = MutableStateFlow<LinkType?>(null)
    val linkType: StateFlow<LinkType?> = _linkType.asStateFlow()

    /** @return true when the value changed. */
    fun update(type: LinkType): Boolean {
        val changed = _linkType.value != type
        _linkType.value = type
        return changed
    }

    fun clear() {
        _linkType.value = null
    }
}
