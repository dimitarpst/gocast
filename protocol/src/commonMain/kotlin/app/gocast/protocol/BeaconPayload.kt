package app.gocast.protocol

/** What a phone learns from the PC's Bluetooth LE beacon. */
data class BeaconInfo(
    /** Blank from the short room beacon, which has no space for it. */
    val displayName: String,
    val host: String,
    /** 0 from the short room beacon, which has no space for it. */
    val port: Int,
    /** The PC's relay room; null from the name beacon (version 1). */
    val roomId: String? = null,
)

/**
 * Manufacturer data inside the PC's Bluetooth LE beacons. All start with
 * `G` `C` and a version byte. The pairing code is never broadcast. A legacy
 * advertisement leaves 24 bytes for this.
 *
 * - Version 1 (legacy, every phone): IPv4 (4 bytes), port (2 bytes,
 *   big-endian), PC name cut to [MAX_NAME_BYTES].
 * - Version 2 (Bluetooth 5 extended advertisement): room id length (1 byte),
 *   room id (ASCII), IPv4, port, PC name up to [MAX_ROOM_NAME_BYTES].
 * - Version 3 (legacy, every phone): the 22-character room id as its 16 raw
 *   bytes, then IPv4. The PC alternates it with version 1 so a phone or PC
 *   without Bluetooth 5 extended advertising still learns the room and can
 *   join through the relay (31 Oct: such a pair only got version 1, so it
 *   only tried Wi-Fi). Phones match versions 1 and 3 by IPv4.
 */
object BeaconPayload {
    /** Bluetooth SIG "reserved for testing" id. Needs a real id before release. */
    const val COMPANY_ID = 0xFFFF
    const val VERSION_LAN: Byte = 1
    const val VERSION_ROOM: Byte = 2
    const val VERSION_SHORT_ROOM: Byte = 3
    private const val SHORT_ROOM_CHARS = 22
    private const val SHORT_ROOM_BYTES = 16
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

    /**
     * The short room beacon. Returns null unless [roomId] is the usual 22-character
     * id (16 random bytes) and [host] is a usable IPv4 address.
     */
    fun encodeShortRoom(host: String, roomId: String): ByteArray? {
        val room = shortRoomBytes(roomId) ?: return null
        val ip = encodeAddress(host, port = 1)?.copyOf(4) ?: return null
        return MAGIC + byteArrayOf(VERSION_SHORT_ROOM) + room + ip
    }

    fun decode(bytes: ByteArray): BeaconInfo? {
        if (bytes.size < 3 || bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1]) return null
        return when (bytes[2]) {
            VERSION_LAN -> decodeRest(bytes, offset = 3, roomId = null)
            VERSION_SHORT_ROOM -> {
                if (bytes.size < 3 + SHORT_ROOM_BYTES + 4) return null
                val roomId = Base64Url.encode(bytes.copyOfRange(3, 3 + SHORT_ROOM_BYTES))
                val ipStart = 3 + SHORT_ROOM_BYTES
                val host = (ipStart until ipStart + 4).joinToString(".") { (bytes[it].toInt() and 0xFF).toString() }
                BeaconInfo(displayName = "", host = host, port = 0, roomId = roomId)
            }
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

    /** The room's 16 bytes, only when they turn back into exactly the same id. */
    private fun shortRoomBytes(roomId: String): ByteArray? {
        if (roomId.length != SHORT_ROOM_CHARS || !RoomId.isValid(roomId)) return null
        val bytes = Base64Url.decode(roomId) ?: return null
        if (bytes.size != SHORT_ROOM_BYTES || Base64Url.encode(bytes) != roomId) return null
        return bytes
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

/** URL-safe Base64 without padding, the alphabet room ids use (`DesktopIdentityStore.randomToken`). */
internal object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(bytes: ByteArray): String = buildString {
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            val chunk = (b0 shl 16) or (b1 shl 8) or b2
            append(ALPHABET[(chunk shr 18) and 63])
            append(ALPHABET[(chunk shr 12) and 63])
            if (i + 1 < bytes.size) append(ALPHABET[(chunk shr 6) and 63])
            if (i + 2 < bytes.size) append(ALPHABET[chunk and 63])
            i += 3
        }
    }

    fun decode(text: String): ByteArray? {
        if (text.length % 4 == 1) return null
        val out = ArrayList<Byte>(text.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (ch in text) {
            val value = ALPHABET.indexOf(ch)
            if (value < 0) return null
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
