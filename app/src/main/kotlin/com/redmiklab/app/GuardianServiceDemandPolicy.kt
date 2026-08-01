package com.redmiklab.app

enum class GuardianServiceCommand {
    START_CAPTURE,
    STOP_CAPTURE,
    START_PLAN_GUARD,
    CANCEL_PLAN_GUARD,
    ATTACH_STRICT,
    DETACH_STRICT,
}

data class GuardianServiceDemand(
    val captureEnabled: Boolean = false,
    val nightScheduleEnabled: Boolean = false,
    val strictRunAttached: Boolean = false,
) {
    val guardianRequired: Boolean
        get() = captureEnabled || nightScheduleEnabled || strictRunAttached
    val tunnelRequired: Boolean
        get() = captureEnabled || strictRunAttached
    val keepServiceRunning: Boolean
        get() = ConnectionCaptureLifecyclePolicy.shouldKeepServiceRunning(
            captureEnabled,
            nightScheduleEnabled,
            strictRunAttached,
        )
}

object GuardianServiceDemandPolicy {
    fun apply(current: GuardianServiceDemand, command: GuardianServiceCommand): GuardianServiceDemand =
        when (command) {
            GuardianServiceCommand.START_CAPTURE -> current.copy(captureEnabled = true)
            GuardianServiceCommand.STOP_CAPTURE -> current.copy(captureEnabled = false)
            GuardianServiceCommand.START_PLAN_GUARD -> current.copy(nightScheduleEnabled = true)
            GuardianServiceCommand.CANCEL_PLAN_GUARD -> current.copy(nightScheduleEnabled = false)
            GuardianServiceCommand.ATTACH_STRICT -> current.copy(strictRunAttached = true)
            GuardianServiceCommand.DETACH_STRICT -> current.copy(strictRunAttached = false)
        }
}
