package com.redmiklab.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficReconciliationTest {
    @Test
    fun preserves_signed_difference_and_warns_that_layers_have_different_coverage() {
        val result = TrafficReconciliation.reconcile(
            systemBytes = 1_000,
            tunnelBytes = 700,
            probeBytes = 100,
            heartbeatBytes = 20,
        )

        assertEquals(820L, result.explainedBytes)
        assertEquals(180L, result.unexplainedBytes)
        assertEquals(0.18, result.differenceRatio, 0.0001)
        assertTrue("LAYERS_HAVE_DIFFERENT_COVERAGE" in result.warnings)
    }
}
