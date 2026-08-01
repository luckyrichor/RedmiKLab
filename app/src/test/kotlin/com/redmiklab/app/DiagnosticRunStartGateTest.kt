package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRunStartGateTest {
    @Test
    fun starts_sampling_only_after_the_run_transaction_is_persisted() {
        val gate = DiagnosticRunStartGate()

        assertFalse(gate.canSample())
        assertEquals(RunReadyAction.START_TASKS, gate.markPersisted())
        assertTrue(gate.canSample())
        assertTrue(gate.requestEnd())
    }

    @Test
    fun defers_an_early_stop_until_after_the_run_transaction_is_persisted() {
        val gate = DiagnosticRunStartGate()

        assertFalse(gate.requestEnd())
        assertEquals(RunReadyAction.FINISH, gate.markPersisted())
    }
}
