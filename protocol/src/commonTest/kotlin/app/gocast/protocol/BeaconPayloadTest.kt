package app.gocast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeaconPayloadTest {
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
    fun rejectsIpv6AndForeignData() {
        assertNull(BeaconPayload.encode("fe80::1", 8765, "PC"))
        assertNull(BeaconPayload.encode("192.168.1.300", 8765, "PC"))
        assertNull(BeaconPayload.encode("127.0.0.1", 8765, "PC"))
        assertNull(BeaconPayload.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
    }
}
