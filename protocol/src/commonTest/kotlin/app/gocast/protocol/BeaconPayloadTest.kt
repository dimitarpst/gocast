package app.gocast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeaconPayloadTest {
    private val room = "AbCdEfGhIjKlMnOpQrStUv"

    @Test
    fun roundTripsAddressPortAndName() {
        val bytes = BeaconPayload.encode("192.168.1.20", 8765, "Mitko's PC")!!
        assertEquals(BeaconInfo("Mitko's PC", "192.168.1.20", 8765), BeaconPayload.decode(bytes))
    }

    @Test
    fun longNamesFitALegacyAdvertisement() {
        val bytes = BeaconPayload.encode("10.0.0.5", 8765, "Ж".repeat(20))!!
        assertTrue(bytes.size <= 24)
        // Two-byte letters: 15 bytes leave room for 7 whole letters.
        assertEquals("Ж".repeat(7), BeaconPayload.decode(bytes)!!.displayName)
    }

    @Test
    fun roomBeaconCarriesTheRoomAndALongerName() {
        val bytes = BeaconPayload.encodeWithRoom("172.16.113.244", 8765, "Mitko's gaming PC", room)!!
        assertEquals(
            BeaconInfo("Mitko's gaming PC", "172.16.113.244", 8765, roomId = room),
            BeaconPayload.decode(bytes),
        )
        // Well inside one extended advertisement.
        assertTrue(bytes.size <= 3 + 1 + 64 + 6 + BeaconPayload.MAX_ROOM_NAME_BYTES)
    }

    @Test
    fun rejectsIpv6InvalidRoomsAndForeignData() {
        assertNull(BeaconPayload.encode("fe80::1", 8765, "PC"))
        assertNull(BeaconPayload.encode("192.168.1.300", 8765, "PC"))
        assertNull(BeaconPayload.encode("127.0.0.1", 8765, "PC"))
        assertNull(BeaconPayload.encodeWithRoom("10.0.0.5", 8765, "PC", "short"))
        assertNull(BeaconPayload.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
        // A room beacon cut short must not decode into a wrong address.
        val cut = BeaconPayload.encodeWithRoom("10.0.0.5", 8765, "PC", room)!!.copyOf(20)
        assertNull(BeaconPayload.decode(cut))
    }
}
