package app.gocast.network

import app.gocast.protocol.LinkType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The link the running cast uses (LAN, internet or relay), for the UI and logs. */
object CastLinkMonitor {
    private val _linkType = MutableStateFlow<LinkType?>(null)
    val linkType: StateFlow<LinkType?> = _linkType.asStateFlow()

    /** The PC can't play the chosen codec, so the cast uses another one. */
    data class CodecFallback(val wanted: String, val used: String)

    private val _codecFallback = MutableStateFlow<CodecFallback?>(null)
    val codecFallback: StateFlow<CodecFallback?> = _codecFallback.asStateFlow()

    /** @return true when the value changed. */
    fun update(type: LinkType): Boolean {
        val changed = _linkType.value != type
        _linkType.value = type
        return changed
    }

    /** @return true when the value changed. */
    fun updateCodec(fallback: CodecFallback?): Boolean {
        val changed = _codecFallback.value != fallback
        _codecFallback.value = fallback
        return changed
    }

    /** "H265" → "H.265"; other names as they are. */
    fun codecLabel(name: String): String = name.replace(Regex("^H26(\\d)$"), "H.26$1")

    fun clear() {
        _linkType.value = null
        _codecFallback.value = null
    }
}
