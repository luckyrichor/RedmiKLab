package com.redmiklab.app

object ConnectionCaptureLifecyclePolicy {
    fun shouldClearUserIntent(explicitStopRequested: Boolean): Boolean = explicitStopRequested

    fun shouldResume(
        userEnabled: Boolean,
        vpnPermissionAlreadyGranted: Boolean,
        hasConflictingVpn: Boolean,
    ): Boolean = userEnabled && vpnPermissionAlreadyGranted && !hasConflictingVpn

    fun shouldDisableUserIntent(userEnabled: Boolean, vpnPermissionAlreadyGranted: Boolean): Boolean =
        userEnabled && !vpnPermissionAlreadyGranted

    fun shouldKeepServiceRunning(
        captureEnabled: Boolean,
        nightScheduleEnabled: Boolean,
        strictRunAttached: Boolean,
    ): Boolean = captureEnabled || nightScheduleEnabled || strictRunAttached
}
