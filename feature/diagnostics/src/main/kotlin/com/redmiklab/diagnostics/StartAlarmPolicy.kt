package com.redmiklab.diagnostics

enum class StartAlarmMode { Exact, Inexact }

object StartAlarmPolicy {
    fun select(
        apiLevel: Int,
        canScheduleExactAlarms: Boolean,
        exactAlarmEnabled: Boolean = true,
    ): StartAlarmMode =
        if (exactAlarmEnabled && (apiLevel < 31 || canScheduleExactAlarms)) StartAlarmMode.Exact else StartAlarmMode.Inexact
}
