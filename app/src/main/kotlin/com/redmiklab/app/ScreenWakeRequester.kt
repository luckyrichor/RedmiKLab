package com.redmiklab.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

class AndroidScreenWakeRequester(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun request(
        generation: Long,
        attempt: Int,
        debugBypassCurrentAttemptValidation: Boolean = false,
    ): ScreenWakeRequestResult {
        if (alarmManager == null) return ScreenWakeRequestResult(false, "alarm_manager_unavailable")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return ScreenWakeRequestResult(false, "exact_alarm_not_allowed")
        }
        val triggerAt = SystemClock.elapsedRealtime() + DISPATCH_DELAY_MS
        val intent = Intent(appContext, ScreenWakeAlarmReceiver::class.java)
            .putExtra(EXTRA_GENERATION, generation)
            .putExtra(EXTRA_ATTEMPT, attempt)
            .putExtra(EXTRA_DEBUG_BYPASS_CURRENT_ATTEMPT, debugBypassCurrentAttemptValidation)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode(generation, attempt),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent,
            )
            ScreenWakeRequestResult(true, "exact_alarm_at_elapsed=$triggerAt")
        }.getOrElse { error ->
            ScreenWakeRequestResult(false, "${error.javaClass.simpleName}:${error.message.orEmpty()}")
        }
    }

    private fun requestCode(generation: Long, attempt: Int): Int =
        (generation xor (generation ushr 32)).toInt() * 31 + attempt

    companion object {
        const val EXTRA_GENERATION = "screen_wake_generation"
        const val EXTRA_ATTEMPT = "screen_wake_attempt"
        const val EXTRA_DEBUG_BYPASS_CURRENT_ATTEMPT = "screen_wake_debug_bypass_current_attempt"
        private const val DISPATCH_DELAY_MS = 250L
    }
}
