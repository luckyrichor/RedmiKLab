package com.redmiklab.app

import com.redmiklab.model.ConfigIssue
import com.redmiklab.model.DiagnosticConfig

/** Lets an invalid legacy window be repaired without allowing a valid window to regress. */
object DiagnosticTimeEditPolicy {
    fun canSave(current: DiagnosticConfig, updated: DiagnosticConfig): Boolean =
        updated.validate().isEmpty() || ConfigIssue.DiagnosticWindowTooLong in current.validate()
}
