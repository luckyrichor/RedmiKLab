package com.redmiklab.app

import android.content.Context
import android.os.PowerManager
import com.redmiklab.diagnostics.WakeLockHandle

class AndroidWakeLockHandle(context: Context) : WakeLockHandle {
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RedmiKLab:NightDiagnostic").apply {
            setReferenceCounted(false)
        }

    override fun acquire() {
        if (!wakeLock.isHeld) wakeLock.acquire(MAX_STRICT_WINDOW_WAKE_MILLIS)
    }

    override fun release() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    private companion object {
        const val MAX_STRICT_WINDOW_WAKE_MILLIS = 25L * 60 * 60 * 1_000
    }
}
