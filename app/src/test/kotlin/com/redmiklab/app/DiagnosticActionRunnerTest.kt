package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticActionRunnerTest {
    @Test
    fun repeated_snapshot_action_does_not_duplicate_the_planned_snapshot() {
        val store = FakeDiagnosticActionStore(activeRun = ActiveDiagnosticRun("run-a", "RUNNING"))
        val runner = DiagnosticActionRunner(store)

        assertEquals(
            DiagnosticActionResult.Executed,
            runner.run(DiagnosticAction.Snapshot, plannedAtEpochMs = 1_000, actualAtEpochMs = 1_125),
        )
        assertEquals(
            DiagnosticActionResult.Duplicate,
            runner.run(DiagnosticAction.Snapshot, plannedAtEpochMs = 1_000, actualAtEpochMs = 1_130),
        )
        assertEquals(listOf(1_000L), store.snapshotPlans)
    }

    @Test
    fun end_action_completes_the_persisted_run_without_service_memory() {
        val store = FakeDiagnosticActionStore(activeRun = ActiveDiagnosticRun("run-a", "RUNNING"))
        val runner = DiagnosticActionRunner(store)

        val result = runner.run(DiagnosticAction.End, plannedAtEpochMs = 6_000, actualAtEpochMs = 6_050)

        assertEquals(DiagnosticActionResult.Executed, result)
        assertEquals("COMPLETED", store.activeRun?.status)
        assertEquals(6_050L, store.completedAt)
    }

    @Test
    fun action_without_an_active_run_is_ignored() {
        val runner = DiagnosticActionRunner(FakeDiagnosticActionStore(activeRun = null))

        assertEquals(
            DiagnosticActionResult.NoActiveRun,
            runner.run(DiagnosticAction.Snapshot, plannedAtEpochMs = 1_000, actualAtEpochMs = 1_100),
        )
    }
}

private class FakeDiagnosticActionStore(
    override var activeRun: ActiveDiagnosticRun?,
) : DiagnosticActionStore {
    val snapshotPlans = mutableListOf<Long>()
    var completedAt: Long? = null

    override fun hasSnapshot(runId: String, plannedAtEpochMs: Long, triggerSource: String): Boolean =
        plannedAtEpochMs in snapshotPlans

    override fun captureSnapshot(
        runId: String,
        plannedAtEpochMs: Long,
        actualAtEpochMs: Long,
        triggerSource: String,
    ) {
        snapshotPlans += plannedAtEpochMs
    }

    override fun completeRun(runId: String, endedAtEpochMs: Long, reason: String) {
        completedAt = endedAtEpochMs
        activeRun = activeRun?.copy(status = "COMPLETED")
    }
}
