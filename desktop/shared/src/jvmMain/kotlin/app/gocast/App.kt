package app.gocast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gocast.domain.model.JoinSession
import app.gocast.server.SignalingHub
import app.gocast.server.ViewerCommands
import app.gocast.ui.CastConnectingOverlay
import app.gocast.ui.PairingScreen
import app.gocast.ui.ViewerPane
import java.awt.Window
import kotlinx.coroutines.delay

/**
 * Fade-in Connecting before WebView expands; then hold after reveal while HWND paints.
 * Compose cannot cover WebView2 HWND — see [CastConnectingOverlay].
 */
private const val CastOverlayFadeInMs = 1_000L
private const val CastOverlayHoldMs = 1_000L

private val GocastBg = Color(0xFF12141A)
private val GocastText = Color(0xFFE8ECF5)

@Composable
fun App(
    session: JoinSession,
    displayName: String,
    onRename: (String) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    ownerWindow: Window,
) {
    val phoneConnected by SignalingHub.phoneConnected.collectAsState()
    val castingActive by SignalingHub.castingActive.collectAsState()
    var showPairing by remember { mutableStateOf(true) }
    // WebView2 HWND sits above Compose — keep at 1.dp while pairing only.
    var revealViewer by remember { mutableStateOf(false) }

    val connectingOverlay = remember(ownerWindow) { CastConnectingOverlay(ownerWindow) }
    DisposableEffect(connectingOverlay) {
        onDispose { connectingOverlay.dispose() }
    }

    LaunchedEffect(phoneConnected) {
        if (!phoneConnected) {
            showPairing = true
        }
    }

    LaunchedEffect(Unit) {
        ViewerCommands.showPairing.collect {
            showPairing = true
        }
    }

    // Phone WS connect is not cast-ready. On castingActive: leave pairing once,
    // ONE Connecting fade-in, then reveal under overlay, then hold + fade-out.
    // Do not key this on showPairing — that restarted show()/fade-in when pairing
    // hid (and again on any later showPairing toggle while still casting).
    LaunchedEffect(castingActive) {
        if (castingActive) {
            showPairing = false
            connectingOverlay.show()
            delay(CastOverlayFadeInMs)
            revealViewer = true
            delay(CastOverlayHoldMs)
            connectingOverlay.hide()
        } else {
            connectingOverlay.hide()
            revealViewer = false
        }
    }

    // Fullscreen is driven from the viewer HTML menu / F11 / Esc.
    LaunchedEffect(Unit) {
        ViewerCommands.toggleFullscreen.collect {
            onToggleFullscreen()
        }
    }
    LaunchedEffect(Unit) {
        ViewerCommands.exitFullscreen.collect {
            onExitFullscreen()
        }
    }

    // Pairing is never fullscreen — drop out if we navigate back to QR.
    LaunchedEffect(showPairing) {
        if (showPairing && isFullscreen) {
            onExitFullscreen()
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GocastBg),
        ) {
            // Keep WebView2 mounted across pairing↔cast. Removing it from composition
            // destroys the HWND. Stay at 1.dp while pairing; full size under overlay
            // as soon as cast starts.
            ViewerPane(
                url = "http://127.0.0.1:${session.port}/view",
                modifier = if (revealViewer) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.size(1.dp)
                },
            )
            if (showPairing) {
                PairingScreen(
                    session = session,
                    displayName = displayName,
                    onRename = onRename,
                )
                if (phoneConnected && !castingActive) {
                    Text(
                        text = "Phone connected — accept screen sharing on your phone",
                        color = GocastText,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xCC1B2030))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                } else if (castingActive) {
                    Text(
                        text = "Back to cast",
                        color = GocastText,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xCC1B2030))
                            .clickable { showPairing = false }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
