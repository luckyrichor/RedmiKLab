package com.redmiklab.diagnostics

import com.redmiklab.model.DiagnosticRuntimeMode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextActionPlannerTest {
    private val planner = NextActionPlanner()

    @Test
    fun standard_start_plans_snapshot_probe_checkpoint_and_end() {
        val start = Instant.ofEpochMilli(0)
        val end = Instant.ofEpochMilli(21_600_000)

        val actions = planner.afterStart(
            start = start,
            end = end,
            mode = DiagnosticRuntimeMode.STANDARD,
            snapshotMinutes = 5,
            throughputMinutes = 30,
        )

        assertTrue(actions.contains(PlannedDiagnosticAction(DiagnosticActionType.SNAPSHOT, 300_000)))
        assertTrue(actions.contains(PlannedDiagnosticAction(DiagnosticActionType.PROBE, 1_800_000)))
        assertTrue(actions.contains(PlannedDiagnosticAction(DiagnosticActionType.SAFE_CHECKPOINT, 21_480_000)))
        assertTrue(actions.contains(PlannedDiagnosticAction(DiagnosticActionType.END, 21_600_000)))
    }

    @Test
    fun strict_start_leaves_periodic_snapshots_to_the_vpn_coordinator() {
        val actions = planner.afterStart(
            start = Instant.ofEpochMilli(0),
            end = Instant.ofEpochMilli(21_600_000),
            mode = DiagnosticRuntimeMode.STRICT,
            snapshotMinutes = 5,
            throughputMinutes = 30,
        )

        assertEquals(false, actions.any { it.type == DiagnosticActionType.SNAPSHOT })
        assertEquals(true, actions.any { it.type == DiagnosticActionType.PROBE })
    }

    @Test
    fun late_standard_snapshot_skips_missed_slots_without_fabricating_them() {
        val next = planner.nextFutureSlot(
            previousPlannedAtEpochMs = 300_000,
            intervalMinutes = 5,
            actualAtEpochMs = 910_000,
            endAtEpochMs = 3_600_000,
        )

        assertEquals(1_200_000L, next)
    }
}
