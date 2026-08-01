package com.redmiklab.app

import android.app.AlarmManager
import com.redmiklab.model.DiagnosticConfig
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class NightScheduleUiPolicyTest {
    @Test
    fun next_start_uses_tomorrow_when_todays_start_has_passed() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDateTime.of(2026, 8, 1, 23, 0)
        val config = DiagnosticConfig.default().copy(start = LocalTime.of(1, 0))

        val result = NightScheduleUiPolicy.nextStart(config, now, zone)

        assertEquals(
            LocalDateTime.of(2026, 8, 2, 1, 0).atZone(zone).toInstant().toEpochMilli(),
            result,
        )
    }

    @Test
    fun status_distinguishes_no_plan_from_an_exact_saved_next_start() {
        val zone = ZoneId.of("Asia/Shanghai")
        val next = LocalDateTime.of(2026, 8, 2, 1, 0).atZone(zone).toInstant().toEpochMilli()

        assertEquals("夜间计划：尚未安排", NightScheduleUiPolicy.status(false, null, zone))
        assertEquals(
            "夜间计划：已安排 · 下次开始 2026-08-02 01:00",
            NightScheduleUiPolicy.status(true, next, zone),
        )
    }

    @Test
    fun scheduler_persists_the_same_trigger_time_submitted_to_alarm_manager_and_cancel_clears_it() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("diagnostic_config", 0).edit().clear().commit()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = DiagnosticScheduler(context)

        scheduler.schedule(DiagnosticConfig.default())

        val alarm = shadowOf(context.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm()!!
        val scheduledPreferences = DiagnosticPreferences(context)
        assertTrue(scheduledPreferences.nightScheduleEnabled())
        assertEquals(alarm.triggerAtMs, scheduledPreferences.nightScheduleNextStartEpochMs())

        scheduler.cancel()

        val cancelledPreferences = DiagnosticPreferences(context)
        assertFalse(cancelledPreferences.nightScheduleEnabled())
        assertNull(cancelledPreferences.nightScheduleNextStartEpochMs())
    }
}
