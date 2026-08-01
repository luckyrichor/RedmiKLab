package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class SnapshotScheduleTest {
    private val schedule = SnapshotSchedule(intervalSeconds = 5 * 60)
    private val end = Instant.parse("2026-07-14T10:25:00Z")

    @Test
    fun does_not_schedule_a_periodic_snapshot_after_the_end() {
        val next = schedule.nextPeriodicAfter(Instant.parse("2026-07-14T10:24:00Z"))

        assertEquals(Instant.parse("2026-07-14T10:29:00Z"), next)
        assertFalse(schedule.shouldSchedulePeriodic(next, end))
    }

    @Test
    fun schedules_a_final_snapshot_at_the_end_even_after_a_recent_periodic_snapshot() {
        assertEquals(end, schedule.finalSnapshotAt(end))
    }
}
