package com.redmiklab.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class DiagnosticAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceClass = when (DiagnosticServiceRouter.target(intent.action)) {
            DiagnosticServiceTarget.PROBE -> NightProbeService::class.java
            DiagnosticServiceTarget.LIFECYCLE -> NightDiagnosticService::class.java
        }
        val serviceIntent = Intent(context, serviceClass)
            .setAction(intent.action)
            .putExtras(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
        else context.startService(serviceIntent)
    }
}
