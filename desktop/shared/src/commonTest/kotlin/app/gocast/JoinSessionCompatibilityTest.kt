package app.gocast

import app.gocast.domain.model.JoinSession
import kotlin.test.Test
import kotlin.test.assertEquals

class JoinSessionCompatibilityTest {
    @Test
    fun qrStillUsesShortCodeInsteadOfSessionToken() {
        val session = session("192.168.1.20")
        assertEquals(
            "https://cast.govrse.app/join?a=192.168.1.20:8765&t=123456",
            session.joinUrl,
        )
    }

    @Test
    fun qrStillBracketsIpv6Addresses() {
        assertEquals(
            "https://cast.govrse.app/join?a=[fe80::1]:8765&t=123456",
            session("fe80::1").joinUrl,
        )
    }

    @Test
    fun qrCarriesTheRemoteRoomWhenThePcHasOne() {
        assertEquals(
            "https://cast.govrse.app/join?a=192.168.1.20:8765&t=123456&r=AbCdEfGhIjKlMnOpQrStUv",
            session("192.168.1.20").copy(roomId = "AbCdEfGhIjKlMnOpQrStUv").joinUrl,
        )
    }

    private fun session(host: String) = JoinSession(
        host = host,
        port = 8765,
        token = "session-uuid-must-not-appear-in-qr",
        shortCode = "123456",
        instanceId = "pc-id",
        displayName = "My PC",
    )
}
