package com.redmiklab.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalSnapshotGateTest {
    @Test
    fun allows_only_one_final_snapshot_when_overdue_snapshot_and_end_arrive_together() {
        val gate = FinalSnapshotGate()

        assertTrue(gate.begin())
        assertFalse(gate.begin())
    }
}
