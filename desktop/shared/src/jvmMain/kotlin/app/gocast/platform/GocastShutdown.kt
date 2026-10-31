package app.gocast.platform

import app.gocast.discovery.GocastAdvertiser
import app.gocast.server.KtorGocastServer
import app.gocast.server.RemoteSignalingClient
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

object GocastShutdown {
    /**
     * Fire-and-forget teardown so the window can close immediately.
     */
    fun beginBackground(
        advertiser: GocastAdvertiser,
        server: KtorGocastServer,
        remote: RemoteSignalingClient? = null,
    ) {
        thread(name = "gocast-shutdown", isDaemon = true) {
            runCatching { advertiser.stop() }
            runCatching { runBlocking { remote?.stop() } }
            runCatching { runBlocking { server.stop() } }
        }
    }
}
