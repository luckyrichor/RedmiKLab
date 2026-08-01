package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticServiceRouterTest {
    @Test
    fun probe_actions_are_isolated_from_lifecycle_actions() {
        assertEquals(
            DiagnosticServiceTarget.PROBE,
            DiagnosticServiceRouter.target(NightDiagnosticService.ACTION_PROBE),
        )
        assertEquals(
            DiagnosticServiceTarget.LIFECYCLE,
            DiagnosticServiceRouter.target(NightDiagnosticService.ACTION_SNAPSHOT),
        )
        assertEquals(
            DiagnosticServiceTarget.LIFECYCLE,
            DiagnosticServiceRouter.target(NightDiagnosticService.ACTION_END),
        )
    }
}
