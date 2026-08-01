package com.redmiklab.app

import com.redmiklab.model.DiagnosticConfig
import com.redmiklab.model.DiagnosticRuntimeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticRunStartPolicyTest {
    private val strictConfig = DiagnosticConfig.default().copy(
        runtimeMode = DiagnosticRuntimeMode.STRICT,
    )

    @Test
    fun strict_run_starts_with_a_tunnel_gap_when_capture_is_enabled_but_temporarily_not_running() {
        val decision = DiagnosticRunStartPolicy().decide(
            config = strictConfig,
            connectionCaptureEnabled = true,
            connectionCaptureRunning = false,
        )

        assertEquals(DiagnosticRunStartDecision.START_WITH_TUNNEL_GAP, decision)
    }

    @Test
    fun strict_run_is_rejected_when_connection_capture_was_never_enabled() {
        val decision = DiagnosticRunStartPolicy().decide(
            config = strictConfig,
            connectionCaptureEnabled = false,
            connectionCaptureRunning = false,
        )

        assertEquals(DiagnosticRunStartDecision.REJECT, decision)
    }

    @Test
    fun strict_run_starts_ready_when_capture_tunnel_is_running() {
        val decision = DiagnosticRunStartPolicy().decide(
            config = strictConfig,
            connectionCaptureEnabled = true,
            connectionCaptureRunning = true,
        )

        assertEquals(DiagnosticRunStartDecision.START_READY, decision)
    }
}
