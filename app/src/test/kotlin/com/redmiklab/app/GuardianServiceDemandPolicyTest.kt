package com.redmiklab.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianServiceDemandPolicyTest {
    @Test
    fun disabling_capture_keeps_guardian_when_a_night_schedule_exists() {
        val scheduled = GuardianServiceDemandPolicy.apply(
            GuardianServiceDemand(),
            GuardianServiceCommand.START_PLAN_GUARD,
        )
        val captureStarted = GuardianServiceDemandPolicy.apply(scheduled, GuardianServiceCommand.START_CAPTURE)
        val captureStopped = GuardianServiceDemandPolicy.apply(captureStarted, GuardianServiceCommand.STOP_CAPTURE)

        assertTrue(captureStopped.keepServiceRunning)
        assertTrue(captureStopped.guardianRequired)
        assertFalse(captureStopped.tunnelRequired)
    }

    @Test
    fun cancelling_the_only_demand_stops_guardian_service() {
        val scheduled = GuardianServiceDemandPolicy.apply(
            GuardianServiceDemand(),
            GuardianServiceCommand.START_PLAN_GUARD,
        )

        val cancelled = GuardianServiceDemandPolicy.apply(scheduled, GuardianServiceCommand.CANCEL_PLAN_GUARD)

        assertFalse(cancelled.keepServiceRunning)
        assertFalse(cancelled.guardianRequired)
    }

    @Test
    fun strict_run_keeps_service_and_requires_tunnel_until_detached() {
        val attached = GuardianServiceDemandPolicy.apply(
            GuardianServiceDemand(),
            GuardianServiceCommand.ATTACH_STRICT,
        )

        assertTrue(attached.keepServiceRunning)
        assertTrue(attached.guardianRequired)
        assertTrue(attached.tunnelRequired)
        assertFalse(
            GuardianServiceDemandPolicy.apply(attached, GuardianServiceCommand.DETACH_STRICT)
                .keepServiceRunning,
        )
    }
}
