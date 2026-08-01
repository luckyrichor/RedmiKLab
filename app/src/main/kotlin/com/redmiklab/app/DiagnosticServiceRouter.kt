package com.redmiklab.app

enum class DiagnosticServiceTarget { LIFECYCLE, PROBE }

object DiagnosticServiceRouter {
    fun target(action: String?): DiagnosticServiceTarget =
        if (action == NightDiagnosticService.ACTION_PROBE) DiagnosticServiceTarget.PROBE
        else DiagnosticServiceTarget.LIFECYCLE
}
