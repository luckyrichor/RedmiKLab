package com.redmiklab.app

sealed interface DiagnosticAction {
    data object Start : DiagnosticAction
    data object Snapshot : DiagnosticAction
    data object Probe : DiagnosticAction
    data object SafeCheckpoint : DiagnosticAction
    data object End : DiagnosticAction
    data object Recover : DiagnosticAction
}

enum class DiagnosticActionResult {
    Executed,
    Duplicate,
    NoActiveRun,
}

data class ActiveDiagnosticRun(
    val runId: String,
    val status: String,
)
