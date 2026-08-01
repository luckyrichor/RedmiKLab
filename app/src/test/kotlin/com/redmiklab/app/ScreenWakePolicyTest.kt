package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenWakePolicyTest {
    @Test
    fun retry_policy_wakes_only_attempts_one_and_four() {
        assertEquals(listOf(true, false, false, true, false), (1..5).map(ReconnectAttemptPolicy::shouldWakeScreen))
        assertEquals(10_000L, ReconnectAttemptPolicy.SCREEN_SETTLE_DELAY_MS)
    }
}
