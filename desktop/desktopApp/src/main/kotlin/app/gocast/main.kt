package app.gocast

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.gocast.domain.JvmSessionFactory
import app.gocast.server.KtorGocastServer
import app.gocast.server.SessionGuard
import kotlinx.coroutines.runBlocking

fun main() {
    val server = KtorGocastServer()
    val session = runBlocking {
        val created = JvmSessionFactory().create(8765)
        SessionGuard.expectedToken = created.token
        server.start(8765)
        created
    }

    application {
        Window(
            onCloseRequest = {
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
