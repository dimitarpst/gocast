package app.gocast.platform

import app.gocast.discovery.GocastAdvertiser
import app.gocast.server.KtorGocastServer
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

object GocastShutdown {
    /**
     * Fire-and-forget teardown so the window can close immediately.
     * Waiting on [KCEF.disposeBlocking] on the UI path freezes the X button.
     */
    fun beginBackground(
        advertiser: GocastAdvertiser,
        server: KtorGocastServer,
    ) {
        thread(name = "gocast-shutdown", isDaemon = true) {
            runCatching { advertiser.stop() }
            runCatching { runBlocking { server.stop() } }
            runCatching { KCEF.disposeBlocking() }
        }
    }
}
