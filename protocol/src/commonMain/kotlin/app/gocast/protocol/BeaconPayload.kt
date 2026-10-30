package app.gocast.protocol

/** What a phone learns from the PC's Bluetooth LE beacon. */
data class BeaconInfo(
    val displayName: String,
    val host: String,
    val port: Int,
    /** The PC's relay room; null from the short beacon, which has no space for it. */
    val roomId: String? = null,
)

/**
 * Manufacturer data inside the PC's Bluetooth LE beacons. Both start with
 * `G` `C` and a version byte. The pairing code is never broadcast.
 *
 * - Version 1 (legacy advertisement, every phone): IPv4 (4 bytes), port
 *   (2 bytes, big-endian), PC name. Only 24 bytes fit, so the name is cut
 *   to [MAX_NAME_BYTES].
 * - Version 2 (Bluetooth 5 extended advertisement): room id length (1 byte),
 *   room id (ASCII), IPv4, port, PC name up to [MAX_ROOM_NAME_BYTES]. A
 *   phone that hears this joins through the relay, from any network.
 */
object BeaconPayload {
    /** Bluetooth SIG "reserved for testing" id. Needs a real id before release. */
    const val COMPANY_ID = 0xFFFF
    const val VERSION_LAN: Byte = 1
    const val VERSION_ROOM: Byte = 2
    const val MAX_NAME_BYTES = 15
    const val MAX_ROOM_NAME_BYTES = 48
    val MAGIC = byteArrayOf('G'.code.toByte(), 'C'.code.toByte())
    private const val ADDRESS_BYTES = 6

    /**
     * The short beacon. Returns null when [host] is not a usable IPv4 address
     * (IPv6 does not fit; loopback means the PC has no network yet).
     */
    fun encode(host: String, port: Int, displayName: String): ByteArray? {
        val address = encodeAddress(host, port) ?: return null
        return MAGIC + byteArrayOf(VERSION_LAN) + address +
            truncateUtf8(displayName.trim(), MAX_NAME_BYTES)
    }

    /** The extended beacon. Returns null without a valid [roomId] or usable address. */
    fun encodeWithRoom(host: String, port: Int, displayName: String, roomId: String): ByteArray? {
        if (!RoomId.isValid(roomId)) return null
        val address = encodeAddress(host, port) ?: return null
        val room = roomId.encodeToByteArray()
        return MAGIC + byteArrayOf(VERSION_ROOM, room.size.toByte()) + room + address +
            truncateUtf8(displayName.trim(), MAX_ROOM_NAME_BYTES)
    }

    fun decode(bytes: ByteArray): BeaconInfo? {
        if (bytes.size < 3 || bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1]) return null
        return when (bytes[2]) {
            VERSION_LAN -> decodeRest(bytes, offset = 3, roomId = null)
            VERSION_ROOM -> {
                if (bytes.size < 4) return null
                val length = bytes[3].toInt() and 0xFF
                val end = 4 + length
                if (bytes.size < end) return null
                val roomId = bytes.copyOfRange(4, end).decodeToString()
                if (!RoomId.isValid(roomId)) return null
                decodeRest(bytes, offset = end, roomId = roomId)
            }
            else -> null
        }
    }

    private fun decodeRest(bytes: ByteArray, offset: Int, roomId: String?): BeaconInfo? {
        if (bytes.size < offset + ADDRESS_BYTES) return null
        val host = (offset until offset + 4).joinToString(".") { (bytes[it].toInt() and 0xFF).toString() }
        val port = ((bytes[offset + 4].toInt() and 0xFF) shl 8) or (bytes[offset + 5].toInt() and 0xFF)
        if (port == 0) return null
        val name = bytes.copyOfRange(offset + ADDRESS_BYTES, bytes.size).decodeToString()
            .ifBlank { "GoCast PC" }
        return BeaconInfo(displayName = name, host = host, port = port, roomId = roomId)
    }

    private fun encodeAddress(host: String, port: Int): ByteArray? {
        val ip = parseIpv4(host) ?: return null
        if (ip[0].toInt() == 127 || ip[0].toInt() == 0) return null
        if (port !in 1..65535) return null
        return ip + byteArrayOf((port shr 8).toByte(), port.toByte())
    }

    private fun parseIpv4(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
        return ByteArray(4) { octets[it].toByte() }
    }

    /** Cuts at a character boundary so the name never ends in half a letter. */
    private fun truncateUtf8(text: String, maxBytes: Int): ByteArray {
        var out = ByteArray(0)
        var i = 0
        while (i < text.length) {
            val step = if (text[i].isHighSurrogate() && i + 1 < text.length) 2 else 1
            val next = out + text.substring(i, i + step).encodeToByteArray()
            if (next.size > maxBytes) break
            out = next
            i += step
        }
        return out
    }
}
