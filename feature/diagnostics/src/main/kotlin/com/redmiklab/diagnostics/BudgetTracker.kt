package com.redmiklab.diagnostics

class BudgetTracker(
    private val limitBytes: Long,
    usedBytes: Long = 0,
) {
    var usedBytes: Long = usedBytes
        private set

    fun reserve(bytes: Long): Boolean {
        if (bytes <= 0 || usedBytes + bytes > limitBytes) return false
        usedBytes += bytes
        return true
    }
}
