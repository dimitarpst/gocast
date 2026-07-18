package app.gocast.server

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Actions requested from the viewer HTML (via local HTTP). */
object ViewerCommands {
    private val _showPairing = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showPairing: SharedFlow<Unit> = _showPairing.asSharedFlow()

    fun requestShowPairing() {
        _showPairing.tryEmit(Unit)
    }
}
