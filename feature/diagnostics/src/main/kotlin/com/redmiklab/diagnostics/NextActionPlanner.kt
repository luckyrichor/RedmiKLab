package com.redmiklab.diagnostics

import com.redmiklab.model.DiagnosticRuntimeMode
import java.time.Instant

enum class DiagnosticActionType {
    SNAPSHOT,
    PROBE,
    SAFE_CHECKPOINT,
    END,
}

data class PlannedDiagnosticAction(
    val type: DiagnosticActionType,
    val atEpochMs: Long,
)

class NextActionPlanner {
    fun afterStart(
        start: Instant,
        end: Instant,
        mode: DiagnosticRuntimeMode,
        snapshotMinutes: Int,
        throughputMinutes: Int,
    ): List<PlannedDiagnosticAction> = buildList {
        if (mode == DiagnosticRuntimeMode.STANDARD) {
            add(
                PlannedDiagnosticAction(
                    DiagnosticActionType.SNAPSHOT,
                    start.plusSeconds(snapshotMinutes * 60L).toEpochMilli(),
                ),
            )
        }
        add(
            PlannedDiagnosticAction(
                DiagnosticActionType.PROBE,
                start.plusSeconds(throughputMinutes * 60L).toEpochMilli(),
            ),
        )
        add(
            PlannedDiagnosticAction(
                DiagnosticActionType.SAFE_CHECKPOINT,
                end.minusSeconds(120).toEpochMilli(),
            ),
        )
        add(PlannedDiagnosticAction(DiagnosticActionType.END, end.toEpochMilli()))
    }.filter { it.atEpochMs > start.toEpochMilli() && it.atEpochMs <= end.toEpochMilli() }
        .sortedBy { it.atEpochMs }

    fun nextFutureSlot(
        previousPlannedAtEpochMs: Long,
        intervalMinutes: Int,
        actualAtEpochMs: Long,
        endAtEpochMs: Long,
    ): Long? {
        val intervalMs = intervalMinutes * 60_000L
        var next = previousPlannedAtEpochMs + intervalMs
        while (next <= actualAtEpochMs) next += intervalMs
        return next.takeIf { it < endAtEpochMs }
    }
}
