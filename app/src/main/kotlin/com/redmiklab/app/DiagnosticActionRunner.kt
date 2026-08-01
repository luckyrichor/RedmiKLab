package com.redmiklab.app

interface DiagnosticActionStore {
    val activeRun: ActiveDiagnosticRun?

    fun hasSnapshot(runId: String, plannedAtEpochMs: Long, triggerSource: String): Boolean

    fun captureSnapshot(
        runId: String,
        plannedAtEpochMs: Long,
        actualAtEpochMs: Long,
        triggerSource: String,
    )

    fun completeRun(runId: String, endedAtEpochMs: Long, reason: String)
}

class DiagnosticActionRunner(
    private val store: DiagnosticActionStore,
) {
    fun run(
        action: DiagnosticAction,
        plannedAtEpochMs: Long,
        actualAtEpochMs: Long,
    ): DiagnosticActionResult {
        val run = store.activeRun ?: return DiagnosticActionResult.NoActiveRun
        if (run.status != "RUNNING") return DiagnosticActionResult.Duplicate
        return when (action) {
            DiagnosticAction.Snapshot,
            DiagnosticAction.SafeCheckpoint,
            -> captureOnce(run, action, plannedAtEpochMs, actualAtEpochMs)

            DiagnosticAction.End -> {
                store.completeRun(run.runId, actualAtEpochMs, "SCHEDULED_END")
                DiagnosticActionResult.Executed
            }

            DiagnosticAction.Start,
            DiagnosticAction.Probe,
            DiagnosticAction.Recover,
            -> DiagnosticActionResult.Executed
        }
    }

    private fun captureOnce(
        run: ActiveDiagnosticRun,
        action: DiagnosticAction,
        plannedAtEpochMs: Long,
        actualAtEpochMs: Long,
    ): DiagnosticActionResult {
        val triggerSource = when (action) {
            DiagnosticAction.SafeCheckpoint -> "SAFE_CHECKPOINT"
            else -> "PERIODIC"
        }
        if (store.hasSnapshot(run.runId, plannedAtEpochMs, triggerSource)) {
            return DiagnosticActionResult.Duplicate
        }
        store.captureSnapshot(run.runId, plannedAtEpochMs, actualAtEpochMs, triggerSource)
        return DiagnosticActionResult.Executed
    }
}
