package app.gocast

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.gocast.discovery.GocastAdvertiser
import app.gocast.domain.JvmSessionFactory
import app.gocast.platform.WindowsLanAccess
import app.gocast.server.KtorGocastServer
import app.gocast.server.SessionGuard
import app.gocast.trust.DesktopIdentityStore
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

fun main() {
    // Packaged Windows builds: open LAN before we bind so phones can connect.
    // Gradle run skips this; the OS Allow dialog still appears on first listen
    // if netsh could not add a rule without elevation.
    WindowsLanAccess.ensureInboundAccess()

    val identityStore = DesktopIdentityStore()
    val server = KtorGocastServer(identityStore)
    val advertiser = GocastAdvertiser()
    val session = runBlocking {
        val created = JvmSessionFactory(identityStore).create(8765)
        SessionGuard.expectedToken = created.token
        SessionGuard.shortCode = created.shortCode
        SessionGuard.instanceId = created.instanceId
        SessionGuard.displayName = created.displayName
        SessionGuard.replaceTrusted(identityStore.loadTrustedTokens())
        server.start(8765)
        advertiser.start(8765, created.instanceId, created.displayName)
        created
    }

    val appIcon = BitmapPainter(useResource("icon.png", ::loadImageBitmap))
    val awtIcons = loadAwtIconSizes()

    application {
        Window(
            onCloseRequest = {
                advertiser.stop()
                runBlocking { server.stop() }
                exitApplication()
            },
            title = "gocast",
            icon = appIcon,
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            // Title-bar needs dedicated 16/32px bitmaps — a single 256px downscale looks scuffed.
            LaunchedEffect(Unit) {
                if (awtIcons.isNotEmpty()) {
                    window.iconImages = awtIcons
                }
            }
            GocastRoot(
                session = session,
                identityStore = identityStore,
                advertiser = advertiser,
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
