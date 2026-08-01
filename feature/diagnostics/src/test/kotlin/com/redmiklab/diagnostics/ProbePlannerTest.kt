package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class ProbePlannerTest {
    @Test
    fun skips_throughput_when_budget_is_exhausted_but_keeps_connectivity_probe() {
        val plan = ProbePlanner.plan(throughputAllowed = false)

        assertEquals(ProbePlan(connectivity = true, throughput = false), plan)
    }
}
