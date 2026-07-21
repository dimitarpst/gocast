package app.gocast.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class LocalIpResolverTest {

    @Test
    fun rejectsApipaAndEmpty() {
        assertFalse(LocalIpResolver.isUsableLanIpv4("169.254.5.29"))
        assertFalse(LocalIpResolver.isUsableLanIpv4("0.0.0.0"))
        assertFalse(LocalIpResolver.isUsableLanIpv4(""))
        assertTrue(LocalIpResolver.isUsableLanIpv4("192.168.100.113"))
    }

    @Test
    fun filtersVirtualBoxAndHyperVNames() {
        assertTrue(
            LocalIpResolver.isVirtualAdapter(
                "eth19",
                "VirtualBox Host-Only Ethernet Adapter",
            ),
        )
        assertTrue(
            LocalIpResolver.isVirtualAdapter("vEthernet (Default Switch)", "Hyper-V"),
        )
        assertFalse(
            LocalIpResolver.isVirtualAdapter("wlan0", "Intel(R) Wi-Fi 6 AX200 160MHz"),
        )
    }

    @Test
    fun scoresWifiAboveGenericEthernet() {
        val wifi = LocalIpResolver.interfaceScore("wlan0", "Intel Wi-Fi")
        val ethernet = LocalIpResolver.interfaceScore("eth0", "Realtek PCIe GbE")
        assertTrue(wifi > ethernet)
        assertEquals(30, LocalIpResolver.ipv4Score("192.168.100.113"))
    }
}
