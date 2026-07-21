package app.gocast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.gocast.ui.PairingScreen
import app.gocast.ui.ViewerPane

@Composable
fun App(
    session: JoinSession,
    displayName: String,
    onRename: (String) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
) {
    val phoneConnected by SignalingHub.phoneConnected.collectAsState()
    var showPairing by remember { mutableStateOf(true) }

    LaunchedEffect(phoneConnected) {
        showPairing = !phoneConnected
    }

    LaunchedEffect(Unit) {
        ViewerCommands.showPairing.collect {
            showPairing = true
        }
    }

    // CEF steals focus — fullscreen is driven from the viewer HTML menu / F11 / Esc.
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
                .background(Color(0xFF12141A)),
        ) {
            if (showPairing) {
                PairingScreen(
                    session = session,
                    displayName = displayName,
                    onRename = onRename,
                )
                if (phoneConnected) {
                    Text(
                        text = "Back to cast",
                        color = Color(0xFFE8ECF5),
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
            } else {
                ViewerPane(
                    url = "http://127.0.0.1:${session.port}/view",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
