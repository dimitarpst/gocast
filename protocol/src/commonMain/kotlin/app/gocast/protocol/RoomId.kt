package app.gocast.protocol

/** A PC's remote signaling room. Same format the Worker accepts in `/room/<id>`. */
object RoomId {
    private val format = Regex("[A-Za-z0-9_-]{16,64}")

    fun isValid(roomId: String): Boolean = format.matches(roomId)
}
