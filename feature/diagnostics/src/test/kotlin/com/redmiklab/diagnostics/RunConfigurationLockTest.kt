package com.redmiklab.diagnostics

import com.redmiklab.model.DiagnosticConfig
import com.redmiklab.model.DiagnosticRuntimeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RunConfigurationLockTest {
    @Test
    fun locks_the_mode_intervals_and_capture_state_for_the_run() {
        val config = DiagnosticConfig.default().copy(
            runtimeMode = DiagnosticRuntimeMode.STRICT,
            snapshotMinutes = 7,
            connectivityMinutes = 11,
            throughputMinutes = 31,
        )

        val locked = RunConfigurationLock.lock(config, connectionCaptureEnabled = true)

        assertEquals(DiagnosticRuntimeMode.STRICT, locked.mode)
        assertEquals(7, locked.snapshotMinutes)
        assertEquals(11, locked.connectivityMinutes)
        assertEquals(31, locked.throughputMinutes)
        assertEquals(true, locked.connectionCaptureEnabled)
    }
}
