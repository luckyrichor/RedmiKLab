package com.redmiklab.diagnostics

interface WakeLockHandle {
    fun acquire()
    fun release()
}

/** Ensures a diagnostic run owns at most one CPU wake lock. */
class KeepAwakeLease(private val wakeLock: WakeLockHandle) {
    private var acquired = false

    fun acquire() {
        if (!acquired) {
            wakeLock.acquire()
            acquired = true
        }
    }

    fun release() {
        if (acquired) {
            wakeLock.release()
            acquired = false
        }
    }
}
