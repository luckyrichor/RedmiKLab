package com.redmiklab.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.redmiklab.diagnostics.StartAlarmMode
import com.redmiklab.diagnostics.StartAlarmPolicy
import android.content.Intent
import com.redmiklab.model.DiagnosticConfig

class DiagnosticScheduler(private val context: Context) {
    fun schedule(config: DiagnosticConfig) {
        require(config.canStartDiagnosis()) { "Diagnostic configuration is not eligible to start" }
        cancelAlarm()
        val alarms = context.getSystemService(AlarmManager::class.java)
        val triggerAt = NightScheduleUiPolicy.nextStart(config)
        val exactAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        when (StartAlarmPolicy.select(Build.VERSION.SDK_INT, exactAccess, DiagnosticPreferences(context).exactAlarmEnabled())) {
            StartAlarmMode.Exact -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(triggerAt))
            StartAlarmMode.Inexact -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(triggerAt))
        }
        DiagnosticPreferences(context).saveNightSchedule(true, triggerAt)
        context.startForegroundService(
            Intent(context, ConnectionCaptureVpnService::class.java)
                .setAction(ConnectionCaptureVpnService.ACTION_START_PLAN_GUARD),
        )
    }

    fun cancel() {
        cancelAlarm()
        DiagnosticPreferences(context).saveNightSchedule(false, null)
        context.startService(
            Intent(context, ConnectionCaptureVpnService::class.java)
                .setAction(ConnectionCaptureVpnService.ACTION_CANCEL_PLAN_GUARD),
        )
    }

    private fun cancelAlarm() {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(0)
        alarms.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(plannedAtEpochMs: Long) = PendingIntent.getBroadcast(
        context,
        1001,
        Intent(context, DiagnosticStartReceiver::class.java)
            .putExtra(NightDiagnosticService.EXTRA_PLANNED_AT, plannedAtEpochMs),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
