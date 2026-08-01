package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeRoundPreconditionTest {
    @Test
    fun missing_physical_cellular_network_short_circuits_all_endpoint_attempts() {
        val decision = ProbeRoundPrecondition.evaluate(hasPhysicalCellularNetwork = false)

        assertFalse(decision.shouldAttemptEndpoints)
        assertEquals("NO_PHYSICAL_CELLULAR_NETWORK", decision.failure)
    }

    @Test
    fun available_physical_cellular_network_allows_failover_runner() {
        val decision = ProbeRoundPrecondition.evaluate(hasPhysicalCellularNetwork = true)

        assertTrue(decision.shouldAttemptEndpoints)
        assertEquals(null, decision.failure)
    }
}
