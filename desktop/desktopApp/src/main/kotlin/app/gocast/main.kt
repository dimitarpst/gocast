package app.gocast

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.gocast.discovery.GocastAdvertiser
import app.gocast.domain.JvmSessionFactory
import app.gocast.platform.GocastShutdown
import app.gocast.platform.WebView2Support
import app.gocast.platform.WindowsLanAccess
import app.gocast.server.KtorGocastServer
import app.gocast.server.RemoteSignalingClient
import app.gocast.server.SessionGuard
import app.gocast.trust.DesktopIdentityStore
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

private val MinWindowSize = DpSize(640.dp, 400.dp)
private val DefaultWindowSize = DpSize(1280.dp, 800.dp)
private val DevBuildLabel = System.getProperty("gocast.devBuild")?.takeIf { it.isNotBlank() }

fun main() {
    // Packaged Windows: detect whether inbound LAN is open (no UAC yet).
    WindowsLanAccess.refreshStatus()

    WebView2Support.prepareProcessEnvironment()

    val identityStore = DesktopIdentityStore()
    val server = KtorGocastServer(identityStore)
    val advertiser = GocastAdvertiser()
    val identity = identityStore.loadOrCreateIdentity()
    // Owner adds this id to the Worker's ALLOWED_ROOMS to permit relay (see cloud/signal/README.md).
    println("Remote room id: ${identity.roomId}")
    val remote = RemoteSignalingClient(
        roomId = checkNotNull(identity.roomId),
        hostKey = checkNotNull(identity.hostKey),
        identityStore = identityStore,
    )
    val session = runBlocking {
        val created = JvmSessionFactory(identityStore).create(8765)
        SessionGuard.expectedToken = created.token
        SessionGuard.shortCode = created.shortCode
        SessionGuard.instanceId = created.instanceId
        SessionGuard.displayName = created.displayName
        SessionGuard.replaceTrusted(identityStore.loadTrustedTokens())
        server.start(8765)
        advertiser.start(8765, created.instanceId, created.displayName, created.host, created.roomId)
        remote.start()
        created
    }

    val appIcon = BitmapPainter(useResource("icon.png", ::loadImageBitmap))
    val awtIcons = loadAwtIconSizes()

    application {
        val windowState = rememberWindowState(size = DefaultWindowSize)
        var isFullscreen by remember { mutableStateOf(false) }

        Window(
            onCloseRequest = {
                GocastShutdown.beginBackground(advertiser, server, remote)
                exitApplication()
            },
            title = DevBuildLabel?.let { "gocast Dev · $it" } ?: "gocast",
            icon = appIcon,
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.key == Key.Escape &&
                    isFullscreen
                ) {
                    isFullscreen = false
                    true
                } else {
                    false
                }
            },
        ) {
            LaunchedEffect(Unit) {
                if (awtIcons.isNotEmpty()) {
                    window.iconImages = awtIcons
                }
                // Match viewer / scaffold so AWT clear isn't white under WebView2.
                window.background = java.awt.Color(0x12, 0x14, 0x1A)
                // AWT minimum — Compose has no first-class minSize on Window yet.
                window.minimumSize = Dimension(
                    MinWindowSize.width.value.toInt(),
                    MinWindowSize.height.value.toInt(),
                )
            }
            LaunchedEffect(isFullscreen) {
                windowState.placement = if (isFullscreen) {
                    WindowPlacement.Fullscreen
                } else {
                    WindowPlacement.Floating
                }
            }
            GocastRoot(
                session = session,
                identityStore = identityStore,
                advertiser = advertiser,
                isFullscreen = isFullscreen,
                onToggleFullscreen = { isFullscreen = !isFullscreen },
                onExitFullscreen = { isFullscreen = false },
                ownerWindow = window,
            )
        }
    }
}

private fun loadAwtIconSizes(): List<BufferedImage> {
    val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
    return sizes.mapNotNull { size ->
        val stream = object {}.javaClass.getResourceAsStream("/icon_$size.png")
            ?: return@mapNotNull null
        stream.use { ImageIO.read(it) }
    }
}
