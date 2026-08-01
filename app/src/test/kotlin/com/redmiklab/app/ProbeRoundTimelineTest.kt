package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProbeRoundTimelineTest {
    @Test
    fun delay_is_dispatch_delay_not_last_attempt_delay() {
        val timeline = ProbeRoundTimeline(plannedAtEpochMs = 1_000, dispatchedAtEpochMs = 1_125)

        timeline.recordAttempt(1_150)
        timeline.recordAttempt(191_000)
        val timing = timeline.complete(191_100)

        assertEquals(125L, timing.delayMs)
        assertEquals(1_150L, timing.firstAttemptAtEpochMs)
        assertEquals(191_000L, timing.lastAttemptAtEpochMs)
        assertEquals(191_100L, timing.completedAtEpochMs)
    }

    @Test
    fun a_budget_exhausted_round_can_complete_without_an_attempt() {
        val timing = ProbeRoundTimeline(1_000, 1_100).complete(1_120)

        assertEquals(100L, timing.delayMs)
        assertNull(timing.firstAttemptAtEpochMs)
        assertNull(timing.lastAttemptAtEpochMs)
    }
}
