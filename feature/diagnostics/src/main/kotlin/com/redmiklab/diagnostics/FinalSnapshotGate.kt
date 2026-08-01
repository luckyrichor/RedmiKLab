package com.redmiklab.diagnostics

class FinalSnapshotGate {
    private var started = false

    @Synchronized
    fun begin(): Boolean = !started.also { if (!started) started = true }

    @Synchronized
    fun isStarted(): Boolean = started
}
