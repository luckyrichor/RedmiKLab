package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionCaptureTunnelProfileTest {
    @Test
    fun routes_both_ipv4_and_ipv6_through_the_transparent_forwarder() {
        assertEquals(
            listOf("10.77.0.2" to 32, "fd42:7265:646d:6900::2" to 128),
            ConnectionCaptureTunnelProfile.addresses,
        )
        assertEquals(listOf("0.0.0.0" to 0, "::" to 0), ConnectionCaptureTunnelProfile.routes)
    }

    @Test
    fun preserves_carrier_ipv6_dns_and_only_falls_back_when_none_exist() {
        assertEquals(
            listOf("218.2.2.2", "240e:5a::6666"),
            ConnectionCaptureTunnelProfile.dnsServers(listOf("218.2.2.2", "240e:5a::6666", "218.2.2.2")),
        )
        assertEquals(listOf("223.5.5.5", "2400:3200::1"), ConnectionCaptureTunnelProfile.dnsServers(emptyList()))
    }
}
