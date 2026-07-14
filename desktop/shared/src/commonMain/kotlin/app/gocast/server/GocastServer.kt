package app.gocast.server

interface GocastServer {
    suspend fun start(port: Int = 8765)
    suspend fun stop()
    val isRunning: Boolean
}