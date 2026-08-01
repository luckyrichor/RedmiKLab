package com.redmiklab.model

enum class ConnectionCaptureReadiness {
    Disabled,
    VpnPermissionRequired,
    VpnConflict,
    ForwarderUnavailable,
    Ready,
}

object ConnectionCapturePolicy {
    fun readiness(
        enabledByUser: Boolean,
        vpnPermissionGranted: Boolean,
        anotherVpnIsActive: Boolean,
        transparentForwarderAvailable: Boolean,
    ): ConnectionCaptureReadiness = when {
        !enabledByUser -> ConnectionCaptureReadiness.Disabled
        anotherVpnIsActive -> ConnectionCaptureReadiness.VpnConflict
        !vpnPermissionGranted -> ConnectionCaptureReadiness.VpnPermissionRequired
        !transparentForwarderAvailable -> ConnectionCaptureReadiness.ForwarderUnavailable
        else -> ConnectionCaptureReadiness.Ready
    }
}
