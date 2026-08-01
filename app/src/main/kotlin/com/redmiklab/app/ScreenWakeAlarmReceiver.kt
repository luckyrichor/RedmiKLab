package com.redmiklab.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/** Uses a short wake lock to light the lock screen without a background Activity launch. */
class ScreenWakeAlarmReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent?) {
        val generation = intent?.getLongExtra(AndroidScreenWakeRequester.EXTRA_GENERATION, 0L) ?: 0L
        val attempt = intent?.getIntExtra(AndroidScreenWakeRequester.EXTRA_ATTEMPT, 1) ?: 1
        val debugBypass = intent?.getBooleanExtra(
            AndroidScreenWakeRequester.EXTRA_DEBUG_BYPASS_CURRENT_ATTEMPT,
            false,
        ) == true
        val current = GuardianRuntimeStore(context).read()
        if (!debugBypass && !current.isCurrentReconnectAttempt(generation, attempt)) {
            Log.i(
                TAG,
                "stale_screen_wake_ignored;generation=$generation;attempt=$attempt;" +
                    "currentGeneration=${current.generation};currentAttempt=${current.retryAttempt};" +
                    "phase=${current.phase}",
            )
            return
        }
        val powerManager = context.getSystemService(PowerManager::class.java)
        if (powerManager == null) {
            Log.e(TAG, "power_manager_unavailable;generation=$generation;attempt=$attempt")
            return
        }
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            WAKE_LOCK_TAG,
        )
        wakeLock.setReferenceCounted(false)
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        Log.i(TAG, "screen_wake_lock_acquired;generation=$generation;attempt=$attempt;timeoutMs=$WAKE_LOCK_TIMEOUT_MS")
    }

    private companion object {
        const val TAG = "RedmiKLabScreenWake"
        const val WAKE_LOCK_TAG = "RedmiKLab:screen-wake"
        const val WAKE_LOCK_TIMEOUT_MS = 10_000L
    }

    private fun GuardianRuntimeSnapshot.isCurrentReconnectAttempt(generation: Long, attempt: Int): Boolean =
        phase == GuardianPhase.RECONNECTING.name &&
            this.generation == generation &&
            retryAttempt == attempt
}
