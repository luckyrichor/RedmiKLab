package com.redmiklab.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DiagnosticStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = DiagnosticScheduler(context)
        val config = DiagnosticPreferences(context).load()
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            context.startForegroundService(
                Intent(context, NightDiagnosticService::class.java)
                    .setAction(NightDiagnosticService.ACTION_RECOVER),
            )
            if (config.canStartDiagnosis() && DiagnosticPreferences(context).nightScheduleEnabled()) {
                scheduler.schedule(config)
            }
            return
        }
        if (!config.canStartDiagnosis()) return
        context.startForegroundService(
            Intent(context, NightDiagnosticService::class.java)
                .setAction(NightDiagnosticService.ACTION_START)
                .putExtra(
                    NightDiagnosticService.EXTRA_PLANNED_AT,
                    intent.getLongExtra(NightDiagnosticService.EXTRA_PLANNED_AT, 0L),
                ),
        )
        // AlarmManager alarms are one-shot; arm the next night immediately after this one fires.
        scheduler.schedule(config)
    }
}
