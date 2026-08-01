package com.redmiklab.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionCapturePolicyTest {
    @Test
    fun keeps_capture_off_until_user_enables_it() {
        assertEquals(ConnectionCaptureReadiness.Disabled, ConnectionCapturePolicy.readiness(false, true, false, true))
    }

    @Test
    fun blocks_capture_when_another_vpn_owns_the_system_slot() {
        assertEquals(ConnectionCaptureReadiness.VpnConflict, ConnectionCapturePolicy.readiness(true, true, true, true))
    }

    @Test
    fun never_allows_capture_without_a_transparent_forwarder() {
        assertEquals(ConnectionCaptureReadiness.ForwarderUnavailable, ConnectionCapturePolicy.readiness(true, true, false, false))
    }
}
