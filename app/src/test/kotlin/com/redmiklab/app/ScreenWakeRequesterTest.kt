package com.redmiklab.app

import android.app.AlarmManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreenWakeRequesterTest {
    @Before
    fun allowExactAlarms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun resetAlarms() {
        ShadowAlarmManager.reset()
    }

    @Test
    fun accepted_request_schedules_an_exact_wakeup_alarm_allowed_while_idle() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val result = AndroidScreenWakeRequester(context).request(generation = 8, attempt = 1)
        val alarm = requireNotNull(
            shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm,
        )

        assertTrue(result.accepted)
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.type)
        assertTrue(alarm.allowWhileIdle)
    }

    @Test
    fun alarm_uses_a_broadcast_relay_instead_of_asking_alarm_manager_to_start_the_activity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        AndroidScreenWakeRequester(context).request(generation = 8, attempt = 1)
        val alarm = requireNotNull(
            shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm,
        )
        val operation = requireNotNull(alarm.operation)

        assertTrue(shadowOf(operation).isBroadcast)
        assertEquals(
            "com.redmiklab.app.ScreenWakeAlarmReceiver",
            shadowOf(operation).savedIntent.component?.className,
        )
    }

    @Test
    fun alarm_relay_acquires_a_short_screen_wake_lock_without_starting_an_activity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        GuardianRuntimeStore(context).record(
            GuardianState.initial().copy(
                phase = GuardianPhase.RECONNECTING,
                generation = 8,
                retryAttempt = 1,
            ),
            vpnUnderlyingNetworkId = null,
            captureComplete = false,
        )
        AndroidScreenWakeRequester(context).request(generation = 8, attempt = 1)
        val alarm = requireNotNull(
            shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm,
        )
        val operation = requireNotNull(alarm.operation)

        ShadowPowerManager.clearWakeLocks()
        ScreenWakeAlarmReceiver().onReceive(context, shadowOf(operation).savedIntent)
        val wakeLock = requireNotNull(ShadowPowerManager.getLatestWakeLock())

        assertTrue(wakeLock.isHeld)
        assertEquals("RedmiKLab:screen-wake", shadowOf(wakeLock).tag)
    }

    @Test
    fun delayed_alarm_is_ignored_after_its_reconnect_attempt_is_no_longer_current() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        GuardianRuntimeStore(context).record(
            GuardianState.healthy(generation = 8, networkId = "119"),
            vpnUnderlyingNetworkId = "119",
            captureComplete = true,
        )
        AndroidScreenWakeRequester(context).request(generation = 8, attempt = 1)
        val alarm = requireNotNull(
            shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm,
        )

        ShadowPowerManager.clearWakeLocks()
        ScreenWakeAlarmReceiver().onReceive(context, shadowOf(requireNotNull(alarm.operation)).savedIntent)

        assertEquals(null, ShadowPowerManager.getLatestWakeLock())
    }

    @Test
    fun debug_request_can_verify_screen_wake_without_an_active_reconnect_attempt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        GuardianRuntimeStore(context).record(
            GuardianState.healthy(generation = 8, networkId = "119"),
            vpnUnderlyingNetworkId = "119",
            captureComplete = true,
        )
        AndroidScreenWakeRequester(context).request(
            generation = 99,
            attempt = 1,
            debugBypassCurrentAttemptValidation = true,
        )
        val alarm = requireNotNull(
            shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm,
        )

        ShadowPowerManager.clearWakeLocks()
        ScreenWakeAlarmReceiver().onReceive(context, shadowOf(requireNotNull(alarm.operation)).savedIntent)

        assertTrue(requireNotNull(ShadowPowerManager.getLatestWakeLock()).isHeld)
    }

    @Test
    fun missing_exact_alarm_permission_is_reported_instead_of_crashing() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val result = AndroidScreenWakeRequester(context).request(generation = 8, attempt = 1)

        assertFalse(result.accepted)
        assertEquals("exact_alarm_not_allowed", result.details)
    }
}
