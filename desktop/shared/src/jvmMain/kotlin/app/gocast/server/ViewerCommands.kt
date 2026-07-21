package app.gocast.server

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Actions requested from the viewer HTML (via local HTTP). */
object ViewerCommands {
    private val _showPairing = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showPairing: SharedFlow<Unit> = _showPairing.asSharedFlow()

    private val _toggleFullscreen = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val toggleFullscreen: SharedFlow<Unit> = _toggleFullscreen.asSharedFlow()

    private val _exitFullscreen = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitFullscreen: SharedFlow<Unit> = _exitFullscreen.asSharedFlow()

    fun requestShowPairing() {
        _showPairing.tryEmit(Unit)
    }

    fun requestToggleFullscreen() {
        _toggleFullscreen.tryEmit(Unit)
    }

    fun requestExitFullscreen() {
        _exitFullscreen.tryEmit(Unit)
    }
}
