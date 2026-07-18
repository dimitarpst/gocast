package app.gocast

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.gocast.discovery.GocastAdvertiser
import app.gocast.domain.JvmSessionFactory
import app.gocast.server.KtorGocastServer
import app.gocast.server.SessionGuard
import app.gocast.trust.DesktopIdentityStore
import kotlinx.coroutines.runBlocking

fun main() {
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

    application {
        Window(
            onCloseRequest = {
                advertiser.stop()
                runBlocking { server.stop() }
                exitApplication()
            },
            title = "gocast",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            GocastRoot(session)
        }
    }
}
