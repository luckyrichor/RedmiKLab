package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class StartAlarmPolicyTest {
    @Test
    fun uses_exact_alarm_when_the_platform_allows_it() {
        assertEquals(StartAlarmMode.Exact, StartAlarmPolicy.select(apiLevel = 36, canScheduleExactAlarms = true))
    }

    @Test
    fun falls_back_when_exact_alarm_access_is_not_granted() {
        assertEquals(StartAlarmMode.Inexact, StartAlarmPolicy.select(apiLevel = 36, canScheduleExactAlarms = false))
    }

    @Test
    fun falls_back_when_user_disables_exact_alarm_use_inside_the_app() {
        assertEquals(
            StartAlarmMode.Inexact,
            StartAlarmPolicy.select(apiLevel = 36, canScheduleExactAlarms = true, exactAlarmEnabled = false),
        )
    }
}
