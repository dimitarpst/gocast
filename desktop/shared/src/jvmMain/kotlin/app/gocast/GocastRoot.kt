package app.gocast

import app.gocast.ui.theme.GocastTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.gocast.discovery.GocastAdvertiser
import app.gocast.domain.model.JoinSession
import app.gocast.network.LanHostMonitor
import app.gocast.platform.WindowsLanAccess
import app.gocast.server.SessionGuard
import app.gocast.trust.DesktopIdentityStore
import app.gocast.ui.LanSetupScreen
import java.awt.Window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GocastRoot(
    session: JoinSession,
    identityStore: DesktopIdentityStore,
    advertiser: GocastAdvertiser,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    ownerWindow: Window,
) {
    var displayName by remember { mutableStateOf(session.displayName) }
    var liveSession by remember { mutableStateOf(session) }
    // Packaged Windows: always show LAN setup once after install (and again if
    // the firewall rule is missing).
    var lanReady by remember {
        mutableStateOf(!WindowsLanAccess.shouldShowLanSetup())
    }

    // Keep QR + mDNS on the real Wi-Fi address if VirtualBox/VPN wins at boot
    // or the laptop joins Wi-Fi after launch. Debounced inside LanHostMonitor.
    LaunchedEffect(session.instanceId, session.port) {
        LanHostMonitor.watch(liveSession.host).collect { host ->
            liveSession = liveSession.copy(host = host)
            withContext(Dispatchers.IO) {
                advertiser.start(
                    port = liveSession.port,
                    instanceId = liveSession.instanceId,
                    displayName = displayName,
                    host = host,
                    roomId = liveSession.roomId,
                )
            }
        }
    }

    GocastTheme {
        if (!lanReady) {
            LanSetupScreen(onReady = { lanReady = true })
        } else {
            App(
                session = liveSession,
                displayName = displayName,
                onRename = { next ->
                    val updated = identityStore.updateDisplayName(next)
                    displayName = updated.displayName
                    SessionGuard.displayName = updated.displayName
                    advertiser.start(
                        port = liveSession.port,
                        instanceId = liveSession.instanceId,
                        displayName = updated.displayName,
                        host = liveSession.host,
                        roomId = liveSession.roomId,
                    )
                },
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                onExitFullscreen = onExitFullscreen,
                ownerWindow = ownerWindow,
            )
        }
    }
}
