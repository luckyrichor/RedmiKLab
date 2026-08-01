package com.redmiklab.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** ADB-only debug hook for verifying the same alarm-and-activity path used by reconnect attempts. */
class ScreenWakeDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val result = AndroidScreenWakeRequester(context).request(
            generation = intent?.getLongExtra(EXTRA_GENERATION, 0L) ?: 0L,
            attempt = intent?.getIntExtra(EXTRA_ATTEMPT, 1) ?: 1,
            debugBypassCurrentAttemptValidation = true,
        )
        Log.i(TAG, "accepted=${result.accepted};${result.details}")
    }

    companion object {
        const val ACTION = "com.redmiklab.app.debug.SCREEN_WAKE"
        const val EXTRA_GENERATION = "generation"
        const val EXTRA_ATTEMPT = "attempt"
        private const val TAG = "RedmiKLabScreenWake"
    }
}
