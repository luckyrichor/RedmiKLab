package com.redmiklab.app

data class ProbeRoundTiming(
    val plannedAtEpochMs: Long,
    val dispatchedAtEpochMs: Long,
    val delayMs: Long,
    val firstAttemptAtEpochMs: Long?,
    val lastAttemptAtEpochMs: Long?,
    val completedAtEpochMs: Long,
)

class ProbeRoundTimeline(
    private val plannedAtEpochMs: Long,
    private val dispatchedAtEpochMs: Long,
) {
    private var firstAttemptAtEpochMs: Long? = null
    private var lastAttemptAtEpochMs: Long? = null

    fun recordAttempt(timestampEpochMs: Long) {
        if (firstAttemptAtEpochMs == null) firstAttemptAtEpochMs = timestampEpochMs
        lastAttemptAtEpochMs = timestampEpochMs
    }

    fun complete(completedAtEpochMs: Long) = ProbeRoundTiming(
        plannedAtEpochMs = plannedAtEpochMs,
        dispatchedAtEpochMs = dispatchedAtEpochMs,
        delayMs = (dispatchedAtEpochMs - plannedAtEpochMs).coerceAtLeast(0),
        firstAttemptAtEpochMs = firstAttemptAtEpochMs,
        lastAttemptAtEpochMs = lastAttemptAtEpochMs,
        completedAtEpochMs = completedAtEpochMs,
    )
}
