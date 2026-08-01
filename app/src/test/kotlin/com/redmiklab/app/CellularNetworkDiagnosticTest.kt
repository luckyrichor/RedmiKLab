package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CellularNetworkDiagnosticTest {
    @Test
    fun renders_system_candidates_selection_and_outcome_as_stable_json() {
        val diagnostic = CellularNetworkDiagnostic(
            outcome = CellularResolutionOutcome.REQUEST_TIMEOUT,
            selectedNetworkId = null,
            initialState = CellularSystemNetworkState(
                activeNetworkId = "vpn-7",
                activeNetworkMetered = true,
                restrictBackgroundStatus = 3,
                networks = listOf(
                    CellularNetworkCandidateSnapshot(
                        networkId = "cell-4",
                        isActive = false,
                        transports = listOf("CELLULAR"),
                        hasInternet = true,
                        isValidated = false,
                        isNotSuspended = true,
                        isNotRestricted = false,
                        isMetered = true,
                        isRecordedUnderlying = true,
                        score = 2,
                        rejectionReason = null,
                        interfaceName = "rmnet_data0",
                        dnsServers = listOf("240e::1"),
                    ),
                    CellularNetworkCandidateSnapshot(
                        networkId = "vpn-7",
                        isActive = true,
                        transports = listOf("VPN"),
                        hasInternet = true,
                        isValidated = true,
                        isNotSuspended = true,
                        isNotRestricted = true,
                        isMetered = true,
                        isRecordedUnderlying = false,
                        score = null,
                        rejectionReason = "VPN_TRANSPORT",
                        interfaceName = "tun0",
                        dnsServers = emptyList(),
                    ),
                ),
            ),
            finalState = CellularSystemNetworkState("vpn-7", true, 3, emptyList(), "cell-4"),
            resolutionWaitMs = 10_000,
            errorType = "TimeoutException",
        )

        val json = diagnostic.toJson()

        assertTrue(json.contains("\"outcome\":\"REQUEST_TIMEOUT\""))
        assertTrue(json.contains("\"activeNetworkId\":\"vpn-7\""))
        assertTrue(json.contains("\"networkId\":\"cell-4\""))
        assertTrue(json.contains("\"isValidated\":false"))
        assertTrue(json.contains("\"isNotRestricted\":false"))
        assertTrue(json.contains("\"recordedUnderlyingNetworkId\":\"cell-4\""))
        assertTrue(json.contains("\"isRecordedUnderlying\":true"))
        assertTrue(json.contains("\"rejectionReason\":\"VPN_TRANSPORT\""))
        assertTrue(json.contains("\"selectedNetworkId\":null"))
        assertTrue(json.contains("\"resolutionWaitMs\":10000"))
        assertEquals(1, "\"errorType\":\"TimeoutException\"".toRegex().findAll(json).count())
    }
}
