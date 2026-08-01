package com.redmiklab.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionCaptureLifecyclePolicyTest {
    @Test
    fun clears_user_intent_only_for_an_explicit_stop() {
        assertTrue(ConnectionCaptureLifecyclePolicy.shouldClearUserIntent(explicitStopRequested = true))
        assertFalse(ConnectionCaptureLifecyclePolicy.shouldClearUserIntent(explicitStopRequested = false))
    }

    @Test
    fun resumes_after_process_or_apk_restart_when_user_intent_is_still_enabled() {
        assertTrue(
            ConnectionCaptureLifecyclePolicy.shouldResume(
                userEnabled = true,
                vpnPermissionAlreadyGranted = true,
                hasConflictingVpn = false,
            ),
        )
    }

    @Test
    fun does_not_resume_when_the_user_disabled_capture() {
        assertFalse(
            ConnectionCaptureLifecyclePolicy.shouldResume(
                userEnabled = false,
                vpnPermissionAlreadyGranted = true,
                hasConflictingVpn = false,
            ),
        )
    }

    @Test
    fun does_not_resume_without_current_vpn_permission() {
        assertFalse(
            ConnectionCaptureLifecyclePolicy.shouldResume(
                userEnabled = true,
                vpnPermissionAlreadyGranted = false,
                hasConflictingVpn = false,
            ),
        )
    }

    @Test
    fun does_not_displace_clash_or_another_apps_vpn() {
        assertFalse(
            ConnectionCaptureLifecyclePolicy.shouldResume(
                userEnabled = true,
                vpnPermissionAlreadyGranted = true,
                hasConflictingVpn = true,
            ),
        )
        assertFalse(
            ConnectionCaptureLifecyclePolicy.shouldDisableUserIntent(
                userEnabled = true,
                vpnPermissionAlreadyGranted = true,
            ),
        )
    }

    @Test
    fun clears_stale_user_intent_if_android_vpn_permission_was_revoked() {
        assertTrue(
            ConnectionCaptureLifecyclePolicy.shouldDisableUserIntent(
                userEnabled = true,
                vpnPermissionAlreadyGranted = false,
            ),
        )
    }

    @Test
    fun scheduled_guardian_keeps_service_running_after_capture_is_disabled() {
        assertTrue(
            ConnectionCaptureLifecyclePolicy.shouldKeepServiceRunning(
                captureEnabled = false,
                nightScheduleEnabled = true,
                strictRunAttached = false,
            ),
        )
    }

    @Test
    fun service_stops_only_when_capture_schedule_and_strict_run_are_all_absent() {
        assertFalse(
            ConnectionCaptureLifecyclePolicy.shouldKeepServiceRunning(
                captureEnabled = false,
                nightScheduleEnabled = false,
                strictRunAttached = false,
            ),
        )
        assertTrue(ConnectionCaptureLifecyclePolicy.shouldKeepServiceRunning(true, false, false))
        assertTrue(ConnectionCaptureLifecyclePolicy.shouldKeepServiceRunning(false, false, true))
    }
}
