package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepAwakeLeaseTest {
    @Test
    fun acquires_once_for_an_active_run_and_releases_at_finish() {
        val handle = RecordingWakeLockHandle()
        val lease = KeepAwakeLease(handle)

        lease.acquire()
        lease.acquire()
        lease.release()

        assertEquals(listOf("acquire", "release"), handle.events)
    }

    private class RecordingWakeLockHandle : WakeLockHandle {
        val events = mutableListOf<String>()
        override fun acquire() { events += "acquire" }
        override fun release() { events += "release" }
    }
}
