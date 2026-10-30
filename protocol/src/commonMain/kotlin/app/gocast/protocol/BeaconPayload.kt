package app.gocast.protocol

/** What a phone learns from the PC's Bluetooth LE beacon. */
data class BeaconInfo(
    val displayName: String,
    val host: String,
    val port: Int,
)

/**
 * Manufacturer data inside the PC's Bluetooth LE beacon:
 * `G` `C` version, IPv4 (4 bytes), port (2 bytes, big-endian), then the PC
 * name in UTF-8. A legacy advertisement leaves 24 bytes for this, so the name
 * is cut to [MAX_NAME_BYTES]. The pairing code is never broadcast.
 */
object BeaconPayload {
    /** Bluetooth SIG "reserved for testing" id. Needs a real id before release. */
    const val COMPANY_ID = 0xFFFF
    const val VERSION: Byte = 1
    const val MAX_NAME_BYTES = 15
    val MAGIC = byteArrayOf('G'.code.toByte(), 'C'.code.toByte())
    private const val HEADER_BYTES = 9

    /**
     * Returns null when [host] is not a usable IPv4 address (IPv6 does not fit;
     * loopback means the PC has no network yet).
     */
    fun encode(host: String, port: Int, displayName: String): ByteArray? {
        val ip = parseIpv4(host) ?: return null
        if (ip[0].toInt() == 127 || ip[0].toInt() == 0) return null
        if (port !in 1..65535) return null
        val name = truncateUtf8(displayName.trim(), MAX_NAME_BYTES)
        return MAGIC + byteArrayOf(VERSION) + ip +
            byteArrayOf((port shr 8).toByte(), port.toByte()) + name
    }

    fun decode(bytes: ByteArray): BeaconInfo? {
        if (bytes.size < HEADER_BYTES) return null
        if (bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1] || bytes[2] != VERSION) return null
        val host = (3..6).joinToString(".") { (bytes[it].toInt() and 0xFF).toString() }
        val port = ((bytes[7].toInt() and 0xFF) shl 8) or (bytes[8].toInt() and 0xFF)
        if (port == 0) return null
        val name = bytes.copyOfRange(HEADER_BYTES, bytes.size).decodeToString().ifBlank { "GoCast PC" }
        return BeaconInfo(displayName = name, host = host, port = port)
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
