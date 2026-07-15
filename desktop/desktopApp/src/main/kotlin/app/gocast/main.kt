package app.gocast

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.window.application
import app.gocast.server.KtorGocastServer
import kotlinx.coroutines.runBlocking
import app.gocast.domain.JvmSessionFactory
import app.gocast.server.SessionGuard


@OptIn(ExperimentalComposeUiApi::class)
fun main() = runBlocking {
    val server = KtorGocastServer()

    try{
        val session = JvmSessionFactory().create(8765)
        SessionGuard.expectedToken = session.token
        server.start(8765)
        application{
            SwingWindow(
                onCloseRequest = ::exitApplication,
                title = "GoCast",
                init = {}
            ){
                App(session)
            }
        }
    } finally{
        server.stop()
    }

}