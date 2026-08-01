package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class RunStateTest {
    @Test
    fun keeps_snapshot_collection_after_throughput_budget_is_exhausted() {
        val action = RunState.Running(budgetExhausted = true).next(RunEvent.ThroughputTick)

        assertEquals(RunAction.SkipThroughputKeepSnapshot, action)
    }
}
