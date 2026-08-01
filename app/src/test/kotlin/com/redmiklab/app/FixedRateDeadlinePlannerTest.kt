package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedRateDeadlinePlannerTest {
    @Test
    fun task_runtime_does_not_shift_the_next_deadline() {
        assertEquals(
            600_000L,
            FixedRateDeadlinePlanner.nextFutureSlot(
                startedAtEpochMs = 0,
                nowEpochMs = 302_000,
                intervalMs = 300_000,
            ),
        )
    }

    @Test
    fun delayed_execution_skips_missed_slots_instead_of_fabricating_them() {
        assertEquals(
            900_000L,
            FixedRateDeadlinePlanner.nextFutureSlot(
                startedAtEpochMs = 0,
                nowEpochMs = 610_000,
                intervalMs = 300_000,
            ),
        )
    }
}
