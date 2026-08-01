package com.redmiklab.app

object FixedRateDeadlinePlanner {
    fun nextFutureSlot(
        startedAtEpochMs: Long,
        nowEpochMs: Long,
        intervalMs: Long,
    ): Long {
        require(intervalMs > 0)
        if (nowEpochMs < startedAtEpochMs) return startedAtEpochMs + intervalMs
        val elapsed = nowEpochMs - startedAtEpochMs
        return startedAtEpochMs + ((elapsed / intervalMs) + 1) * intervalMs
    }
}
