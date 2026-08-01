package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellularNetworkCandidatePolicyTest {
    @Test
    fun rejects_vpn_even_when_it_inherits_cellular_and_internet() {
        val decision = evaluate(facts(isVpn = true))

        assertNull(decision.score)
        assertEquals(CellularCandidateRejection.VPN_TRANSPORT, decision.rejection)
    }

    @Test
    fun rejects_each_carrier_specialized_capability() {
        assertEquals(CellularCandidateRejection.IMS_CAPABILITY, evaluate(facts(isIms = true)).rejection)
        assertEquals(CellularCandidateRejection.EIMS_CAPABILITY, evaluate(facts(isEims = true)).rejection)
        assertEquals(CellularCandidateRejection.MMS_CAPABILITY, evaluate(facts(isMms = true)).rejection)
    }

    @Test
    fun accepts_unvalidated_physical_internet_network_but_rejects_suspended_networks() {
        val unvalidated = evaluate(facts(isValidated = false))

        assertNotNull(unvalidated.score)
        assertNull(unvalidated.rejection)
        assertEquals(
            CellularCandidateRejection.SUSPENDED,
            evaluate(facts(isNotSuspended = false)).rejection,
        )
    }

    @Test
    fun accepts_a_validated_unsuspended_physical_internet_network() {
        val decision = evaluate(facts())

        assertNotNull(decision.score)
        assertNull(decision.rejection)
    }

    @Test
    fun accepts_no_internet_only_for_recorded_underlying_behind_validated_vpn() {
        val accepted = evaluate(
            facts(hasInternet = false, isRecordedUnderlying = true),
            activeDefaultIsValidatedVpn = true,
        )
        val unrecorded = evaluate(
            facts(hasInternet = false, isRecordedUnderlying = false),
            activeDefaultIsValidatedVpn = true,
        )
        val noVpn = evaluate(
            facts(hasInternet = false, isRecordedUnderlying = true),
            activeDefaultIsValidatedVpn = false,
        )

        assertNotNull(accepted.score)
        assertNull(accepted.rejection)
        assertEquals(CellularCandidateRejection.INTERNET_MISSING_UNTRUSTED, unrecorded.rejection)
        assertEquals(CellularCandidateRejection.INTERNET_MISSING_UNTRUSTED, noVpn.rejection)
    }

    @Test
    fun normal_internet_candidate_outranks_compatibility_fallback() {
        val normal = evaluate(facts()).score
        val fallback = evaluate(
            facts(hasInternet = false, isRecordedUnderlying = true),
            activeDefaultIsValidatedVpn = true,
        ).score

        assertTrue(normal!! > fallback!!)
    }

    @Test
    fun recorded_underlying_receives_a_same_tier_preference() {
        val recorded = evaluate(facts(isRecordedUnderlying = true)).score
        val ordinary = evaluate(facts(isRecordedUnderlying = false)).score

        assertTrue(recorded!! > ordinary!!)
    }

    @Test
    fun validated_network_outranks_otherwise_equivalent_unvalidated_network() {
        val validated = evaluate(facts(isValidated = true)).score
        val unvalidated = evaluate(facts(isValidated = false)).score

        assertTrue(validated!! > unvalidated!!)
    }

    private fun evaluate(
        facts: CellularNetworkCandidateFacts,
        activeDefaultIsValidatedVpn: Boolean = false,
    ) = CellularNetworkCandidatePolicy.evaluate(facts, activeDefaultIsValidatedVpn)

    private fun facts(
        isCellular: Boolean = true,
        isVpn: Boolean = false,
        isIms: Boolean = false,
        isEims: Boolean = false,
        isMms: Boolean = false,
        hasInternet: Boolean = true,
        isValidated: Boolean = true,
        isNotSuspended: Boolean = true,
        isRecordedUnderlying: Boolean = false,
    ) = CellularNetworkCandidateFacts(
        isCellular = isCellular,
        isVpn = isVpn,
        isIms = isIms,
        isEims = isEims,
        isMms = isMms,
        hasInternet = hasInternet,
        isValidated = isValidated,
        isNotSuspended = isNotSuspended,
        isRecordedUnderlying = isRecordedUnderlying,
    )
}
