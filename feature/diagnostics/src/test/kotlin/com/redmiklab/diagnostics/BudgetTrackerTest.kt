package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BudgetTrackerTest {
    @Test
    fun refuses_probe_that_would_exceed_nightly_budget() {
        val budget = BudgetTracker(limitBytes = 100, usedBytes = 98)

        assertFalse(budget.reserve(5))
        assertEquals(98, budget.usedBytes)
    }
}
