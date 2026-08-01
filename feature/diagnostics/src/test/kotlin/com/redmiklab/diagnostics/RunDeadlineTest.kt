package com.redmiklab.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RunDeadlineTest {
    private val deadline = RunDeadline(Instant.parse("2026-07-14T10:25:00Z"))

    @Test
    fun permits_sampling_before_the_configured_end() {
        assertTrue(deadline.allows(Instant.parse("2026-07-14T10:24:59Z")))
    }

    @Test
    fun rejects_a_delayed_snapshot_at_or_after_the_configured_end() {
        assertFalse(deadline.allows(Instant.parse("2026-07-14T10:25:00Z")))
        assertFalse(deadline.allows(Instant.parse("2026-07-14T10:28:07Z")))
    }
}
