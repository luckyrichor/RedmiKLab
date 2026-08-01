package com.redmiklab.app

import com.redmiklab.diagnostics.KeepAwakeLease
import com.redmiklab.diagnostics.WakeLockHandle

interface StrictSamplingTicker {
    fun schedule(delayMs: Long, task: () -> Unit)
    fun cancel()
}

interface StrictSnapshotSink {
    fun capture(runId: String, plannedAtEpochMs: Long)
    fun probe(runId: String, plannedAtEpochMs: Long)
    fun safeCheckpoint(runId: String, plannedAtEpochMs: Long)
    fun finish(runId: String, plannedAtEpochMs: Long)
}

class StrictSamplingCoordinator(
    wakeLock: WakeLockHandle,
    private val ticker: StrictSamplingTicker,
    private val sink: StrictSnapshotSink,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val keepAwake = KeepAwakeLease(wakeLock)
    private var activeRunId: String? = null

    fun start(
        runId: String,
        startedAtEpochMs: Long,
        nowEpochMs: Long,
        intervalMs: Long,
        probeIntervalMs: Long,
        endAtEpochMs: Long,
    ) {
        require(intervalMs > 0)
        require(probeIntervalMs > 0)
        ticker.cancel()
        activeRunId = runId
        keepAwake.acquire()
        scheduleNextSnapshot(runId, startedAtEpochMs, nowEpochMs, intervalMs, endAtEpochMs)
        scheduleNextProbe(runId, startedAtEpochMs, nowEpochMs, probeIntervalMs, endAtEpochMs)
        val safeCheckpointAt = endAtEpochMs - SAFE_CHECKPOINT_LEAD_MS
        if (safeCheckpointAt > startedAtEpochMs) {
            ticker.schedule((safeCheckpointAt - nowEpochMs).coerceAtLeast(0)) {
                if (activeRunId == runId) sink.safeCheckpoint(runId, safeCheckpointAt)
            }
        }
        ticker.schedule((endAtEpochMs - nowEpochMs).coerceAtLeast(0)) {
            if (activeRunId != runId) return@schedule
            sink.finish(runId, endAtEpochMs)
            ticker.schedule(FINALIZATION_GRACE_MS) {
                stop(runId)
            }
        }
    }

    private fun scheduleNextProbe(
        runId: String,
        startedAtEpochMs: Long,
        nowEpochMs: Long,
        intervalMs: Long,
        endAtEpochMs: Long,
    ) {
        val plannedAt = FixedRateDeadlinePlanner.nextFutureSlot(startedAtEpochMs, nowEpochMs, intervalMs)
        if (plannedAt >= endAtEpochMs) return
        ticker.schedule((plannedAt - nowEpochMs).coerceAtLeast(0)) {
            if (activeRunId != runId) return@schedule
            sink.probe(runId, plannedAt)
            scheduleNextProbe(runId, startedAtEpochMs, this.nowEpochMs(), intervalMs, endAtEpochMs)
        }
    }

    private fun scheduleNextSnapshot(
        runId: String,
        startedAtEpochMs: Long,
        nowEpochMs: Long,
        intervalMs: Long,
        endAtEpochMs: Long,
    ) {
        val plannedAt = FixedRateDeadlinePlanner.nextFutureSlot(startedAtEpochMs, nowEpochMs, intervalMs)
        if (plannedAt >= endAtEpochMs) return
        ticker.schedule((plannedAt - nowEpochMs).coerceAtLeast(0)) {
            if (activeRunId != runId) return@schedule
            sink.capture(runId, plannedAt)
            scheduleNextSnapshot(runId, startedAtEpochMs, this.nowEpochMs(), intervalMs, endAtEpochMs)
        }
    }

    fun stop(runId: String) {
        if (activeRunId != runId) return
        activeRunId = null
        ticker.cancel()
        keepAwake.release()
    }

    private companion object {
        const val SAFE_CHECKPOINT_LEAD_MS = 120_000L
        const val FINALIZATION_GRACE_MS = 120_000L
    }
}
