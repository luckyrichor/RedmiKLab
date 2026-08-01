package com.redmiklab.app

import com.redmiklab.diagnostics.CellularCandidateRejection
import com.redmiklab.diagnostics.CellularNetworkCandidateFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalCellularNetworkSelectorTest {
    @Test
    fun physical_network_wins_regardless_of_vpn_list_order() {
        listOf(
            listOf(candidate("vpn", isVpn = true), candidate("physical")),
            listOf(candidate("physical"), candidate("vpn", isVpn = true)),
        ).forEach { candidates ->
            val result = PhysicalCellularNetworkSelector.select(candidates, activeDefaultIsValidatedVpn = true)

            assertEquals("physical", result.selected?.value)
            assertEquals(CellularCandidateRejection.VPN_TRANSPORT, result.decisions.getValue("vpn").rejection)
        }
    }

    @Test
    fun recorded_no_internet_physical_network_wins_over_ims() {
        val result = PhysicalCellularNetworkSelector.select(
            listOf(
                candidate("ims", isIms = true, hasInternet = false),
                candidate("physical", hasInternet = false, isRecordedUnderlying = true),
            ),
            activeDefaultIsValidatedVpn = true,
        )

        assertEquals("physical", result.selected?.value)
        assertEquals(CellularCandidateRejection.IMS_CAPABILITY, result.decisions.getValue("ims").rejection)
    }

    @Test
    fun unrecorded_no_internet_network_is_not_selected() {
        val result = PhysicalCellularNetworkSelector.select(
            listOf(candidate("unknown", hasInternet = false)),
            activeDefaultIsValidatedVpn = true,
        )

        assertNull(result.selected)
        assertEquals(
            CellularCandidateRejection.INTERNET_MISSING_UNTRUSTED,
            result.decisions.getValue("unknown").rejection,
        )
    }

    private fun candidate(
        id: String,
        isVpn: Boolean = false,
        isIms: Boolean = false,
        hasInternet: Boolean = true,
        isRecordedUnderlying: Boolean = false,
    ) = PhysicalCellularNetworkCandidate(
        networkId = id,
        value = id,
        facts = CellularNetworkCandidateFacts(
            isCellular = true,
            isVpn = isVpn,
            isIms = isIms,
            isEims = false,
            isMms = false,
            hasInternet = hasInternet,
            isValidated = true,
            isNotSuspended = true,
            isRecordedUnderlying = isRecordedUnderlying,
        ),
    )
}
