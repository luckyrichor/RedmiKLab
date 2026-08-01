package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpProbeRunnerTest {
    @Test
    fun reports_budget_exhausted_without_opening_a_connection() {
        val result = HttpProbeRunner(FailingConnectionFactory).run(ProbeRequest("https://example.invalid", 10), BudgetTracker(5))

        assertEquals(ProbeOutcome.BudgetExhausted, result.outcome)
    }
}
