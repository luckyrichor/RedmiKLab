package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TrafficWindowPlannerTest {
    @Test
    fun creates_contiguous_windows_from_run_start_through_final_snapshot() {
        val planner = TrafficWindowPlanner(Instant.parse("2026-07-14T10:14:40Z"))

        assertEquals(
            TrafficWindow(Instant.parse("2026-07-14T10:14:40Z"), Instant.parse("2026-07-14T10:19:00Z")),
            planner.nextEndingAt(Instant.parse("2026-07-14T10:19:00Z")),
        )
        assertEquals(
            TrafficWindow(Instant.parse("2026-07-14T10:19:00Z"), Instant.parse("2026-07-14T10:24:00Z")),
            planner.nextEndingAt(Instant.parse("2026-07-14T10:24:00Z")),
        )
        assertEquals(
            TrafficWindow(Instant.parse("2026-07-14T10:24:00Z"), Instant.parse("2026-07-14T10:25:00Z")),
            planner.nextEndingAt(Instant.parse("2026-07-14T10:25:00Z")),
        )
    }
}
