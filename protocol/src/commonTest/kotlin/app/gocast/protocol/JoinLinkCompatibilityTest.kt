package app.gocast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JoinLinkCompatibilityTest {
    @Test
    fun ipv4QrKeepsExistingUrlFormat() {
        assertEquals(
            "https://cast.govrse.app/join?a=192.168.1.20:8765&t=123456",
            JoinLink.create("192.168.1.20", 8765, "123456"),
        )
        assertEquals(
            JoinParams("192.168.1.20", 8765, "123456"),
            JoinLink.parseParameters("192.168.1.20:8765", "123456"),
        )
    }

    @Test
    fun ipv6QrAndEndpointKeepBrackets() {
        assertEquals(
            "https://cast.govrse.app/join?a=[fe80::1%wlan0]:8765&t=654321",
            JoinLink.create("fe80::1%wlan0", 8765, "654321"),
        )
        assertEquals("[fe80::1%wlan0]:8765", HostEndpoint.authority("fe80::1%wlan0", 8765))
        assertEquals(
            JoinParams("fe80::1%wlan0", 8765, "654321"),
            JoinLink.parseParameters("[fe80::1%wlan0]:8765", "654321"),
        )
    }

    @Test
    fun hostNamesAndCustomPortsStillWork() {
        assertEquals("my-pc.local:9876", HostEndpoint.authority("my-pc.local", 9876))
        assertEquals(
            JoinParams("my-pc.local", 9876, "old-session-token"),
            JoinLink.parseParameters("my-pc.local:9876", "old-session-token"),
        )
    }

    @Test
    fun missingParametersStillFail() {
        assertNull(JoinLink.parseParameters(null, "123456"))
        assertNull(JoinLink.parseParameters("192.168.1.20:8765", null))
    }

    @Test
    fun malformedAuthoritiesStillFail() {
        for (authority in listOf(
            "", "pc", ":8765", "pc:", "pc:abc", "pc:2147483648",
            "::1:8765", "[]:8765", "[::1", "[::1]", "[::1]8765", "[::1]:abc",
        )) {
            assertNull(HostEndpoint.parseAuthority(authority), authority)
            assertNull(JoinLink.parseParameters(authority, "123456"), authority)
        }
    }

    @Test
    fun existingPermissiveParsingIsPreserved() {
        // This refactor must not introduce new validation or trim existing token values.
        for (port in listOf(0, -1, 65536)) {
            assertEquals("pc" to port, HostEndpoint.parseAuthority("pc:$port"))
        }
        assertEquals(JoinParams("pc", 8765, ""), JoinLink.parseParameters("pc:8765", ""))
        assertEquals(JoinParams("pc", 8765, " 123456 "), JoinLink.parseParameters("pc:8765", " 123456 "))
    }

    @Test
    fun roomIsAddedOnlyWhenPresent() {
        assertEquals(
            "https://cast.govrse.app/join?a=192.168.1.20:8765&t=ABCD&r=AbCdEfGhIjKlMnOpQrStUv",
            JoinLink.create("192.168.1.20", 8765, "ABCD", "AbCdEfGhIjKlMnOpQrStUv"),
        )
        assertEquals(
            "https://cast.govrse.app/join?a=192.168.1.20:8765&t=ABCD",
            JoinLink.create("192.168.1.20", 8765, "ABCD", null),
        )
        // Never put a malformed room into a QR.
        assertEquals(
            "https://cast.govrse.app/join?a=192.168.1.20:8765&t=ABCD",
            JoinLink.create("192.168.1.20", 8765, "ABCD", "bad room"),
        )
    }

    @Test
    fun roomIsParsedAlongsideTheExistingParameters() {
        assertEquals(
            JoinParams("192.168.1.20", 8765, "ABCD", "AbCdEfGhIjKlMnOpQrStUv"),
            JoinLink.parseParameters("192.168.1.20:8765", "ABCD", "AbCdEfGhIjKlMnOpQrStUv"),
        )
    }

    @Test
    fun linksWithoutRoomStillParse() {
        val params = JoinLink.parseParameters("192.168.1.20:8765", "ABCD")
        assertEquals(JoinParams("192.168.1.20", 8765, "ABCD"), params)
        assertNull(params?.roomId)
        assertNull(JoinLink.parseParameters("192.168.1.20:8765", "ABCD", null)?.roomId)
    }

    @Test
    fun malformedRoomFallsBackToLanOnly() {
        for (room in listOf("", "short", "has spaces in the room", "a".repeat(65))) {
            assertEquals(
                JoinParams("192.168.1.20", 8765, "ABCD", null),
                JoinLink.parseParameters("192.168.1.20:8765", "ABCD", room),
                room,
            )
        }
    }

    @Test
    fun roomDoesNotRescueABrokenLink() {
        assertNull(JoinLink.parseParameters(null, "ABCD", "AbCdEfGhIjKlMnOpQrStUv"))
        assertNull(JoinLink.parseParameters("pc", "ABCD", "AbCdEfGhIjKlMnOpQrStUv"))
    }
}
