package app.gocast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkClassifierTest {
    @Test
    fun relayOnEitherSideIsRelayed() {
        assertEquals(LinkType.Relayed, LinkClassifier.classify("relay", "srflx", "203.0.113.5"))
        assertEquals(LinkType.Relayed, LinkClassifier.classify("host", "relay", "192.168.1.9"))
    }

    @Test
    fun privatePeerAddressIsLan() {
        // Phone side on Wi-Fi: the PC answers from its LAN address, often as prflx.
        assertEquals(LinkType.Lan, LinkClassifier.classify("host", "prflx", "192.168.1.20"))
        assertEquals(LinkType.Lan, LinkClassifier.classify("host", "host", "10.4.0.7"))
        assertEquals(LinkType.Lan, LinkClassifier.classify("host", "host", "172.20.3.3"))
        assertEquals(LinkType.Lan, LinkClassifier.classify("host", "host", "[fe80::1%wlan0]"))
        assertEquals(LinkType.Lan, LinkClassifier.classify("host", "host", "fd12::9"))
        // Chromium hides host candidates behind mDNS names.
        assertEquals(LinkType.Lan, LinkClassifier.classify("host", "host", "4f3a-91c2.local"))
    }

    @Test
    fun publicPeerAddressIsInternet() {
        assertEquals(LinkType.Internet, LinkClassifier.classify("srflx", "srflx", "203.0.113.5"))
        assertEquals(LinkType.Internet, LinkClassifier.classify("host", "prflx", "198.51.100.77"))
        assertEquals(LinkType.Internet, LinkClassifier.classify("srflx", "prflx", "2001:db8::1"))
        assertEquals(LinkType.Internet, LinkClassifier.classify("host", "host", "172.32.0.1"))
    }

    @Test
    fun missingRemoteAddressFallsBackToCandidateTypes() {
        assertEquals(LinkType.Lan, LinkClassifier.classify("host", "host", null, "192.168.0.4"))
        assertEquals(LinkType.Internet, LinkClassifier.classify("host", "srflx", "", "192.168.0.4"))
        assertEquals(LinkType.Internet, LinkClassifier.classify("srflx", "host", null, "203.0.113.5"))
        assertNull(LinkClassifier.classify(null, null, null))
    }

    @Test
    fun localAddressRules() {
        for (local in listOf("10.0.0.1", "127.0.0.1", "169.254.10.10", "192.168.255.255", "172.16.0.1", "172.31.9.9", "::1", "FE80::abcd", "pc.local")) {
            assertTrue(LinkClassifier.isLocalAddress(local), local)
        }
        for (public in listOf("8.8.8.8", "172.15.0.1", "192.169.0.1", "2001:db8::1", "example.com", "", null, "999.1.1.1")) {
            assertFalse(LinkClassifier.isLocalAddress(public), public ?: "null")
        }
    }
}
