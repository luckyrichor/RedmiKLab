package com.redmiklab.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

class DiagnosticAlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)

    fun scheduleSnapshot(runId: String, at: Instant) =
        schedule(NightDiagnosticService.ACTION_SNAPSHOT, SNAPSHOT_REQUEST, runId, at)

    fun scheduleProbe(runId: String, at: Instant) =
        schedule(NightDiagnosticService.ACTION_PROBE, PROBE_REQUEST, runId, at)

    fun scheduleSafeCheckpoint(runId: String, at: Instant) =
        schedule(NightDiagnosticService.ACTION_SAFE_CHECKPOINT, SAFE_REQUEST, runId, at)

    fun scheduleEnd(runId: String, at: Instant) =
        schedule(NightDiagnosticService.ACTION_END, END_REQUEST, runId, at)

    fun scheduleRecovery(runId: String, at: Instant) =
        schedule(NightDiagnosticService.ACTION_RECOVER, RECOVERY_REQUEST, runId, at)

    fun cancelAll() {
        listOf(
            NightDiagnosticService.ACTION_SNAPSHOT to SNAPSHOT_REQUEST,
            NightDiagnosticService.ACTION_PROBE to PROBE_REQUEST,
            NightDiagnosticService.ACTION_SAFE_CHECKPOINT to SAFE_REQUEST,
            NightDiagnosticService.ACTION_END to END_REQUEST,
            NightDiagnosticService.ACTION_RECOVER to RECOVERY_REQUEST,
        ).forEach { (action, request) ->
            pendingIntent(action, request, "", 0).also { alarms.cancel(it); it.cancel() }
        }
    }

    private fun schedule(action: String, requestCode: Int, runId: String, at: Instant) {
        val pending = pendingIntent(action, requestCode, runId, at.toEpochMilli())
        val exactAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (DiagnosticPreferences(appContext).exactAlarmEnabled() && exactAccess) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        }
    }

    private fun pendingIntent(
        action: String,
        requestCode: Int,
        runId: String,
        plannedAtEpochMs: Long,
    ): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        requestCode,
        Intent(appContext, DiagnosticAlarmReceiver::class.java)
            .setAction(action)
            .putExtra(NightDiagnosticService.EXTRA_RUN_ID, runId)
            .putExtra(NightDiagnosticService.EXTRA_PLANNED_AT, plannedAtEpochMs),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val SNAPSHOT_REQUEST = 2001
        const val END_REQUEST = 2002
        const val PROBE_REQUEST = 2003
        const val SAFE_REQUEST = 2004
        const val RECOVERY_REQUEST = 2005
    }
}
