package com.redmiklab.app

enum class RunReadyAction {
    START_TASKS,
    FINISH,
}

/** Serializes early stop requests with asynchronous persistence of a new run. */
class DiagnosticRunStartGate {
    private var persisted = false
    private var endRequested = false

    fun canSample(): Boolean = persisted

    fun requestEnd(): Boolean {
        if (persisted) return true
        endRequested = true
        return false
    }

    fun markPersisted(): RunReadyAction {
        persisted = true
        return if (endRequested) RunReadyAction.FINISH else RunReadyAction.START_TASKS
    }
}
