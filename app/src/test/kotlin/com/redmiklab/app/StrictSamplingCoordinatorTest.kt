package com.redmiklab.app

import com.redmiklab.diagnostics.WakeLockHandle
import org.junit.Assert.assertEquals
import org.junit.Test

class StrictSamplingCoordinatorTest {
    @Test
    fun acquires_the_wake_lock_and_emits_the_next_planned_slot() {
        val wakeLock = RecordingStrictWakeLock()
        val clock = ManualStrictClock(0)
        val ticker = ManualStrictTicker(clock)
        val sink = RecordingStrictSnapshotSink()
        val coordinator = StrictSamplingCoordinator(wakeLock, ticker, sink, clock::now)

        coordinator.start(
            runId = "run-a",
            startedAtEpochMs = 0,
            nowEpochMs = 0,
            intervalMs = 300_000,
            probeIntervalMs = 1_800_000,
            endAtEpochMs = 1_800_000,
        )
        ticker.advanceTo(300_000)

        assertEquals(listOf("acquire"), wakeLock.events)
        assertEquals(listOf(300_000L), sink.plannedTimes)
    }

    @Test
    fun restart_skips_slots_that_are_already_in_the_past() {
        val clock = ManualStrictClock(610_000)
        val ticker = ManualStrictTicker(clock)
        val sink = RecordingStrictSnapshotSink()
        val coordinator = StrictSamplingCoordinator(RecordingStrictWakeLock(), ticker, sink, clock::now)

        coordinator.start(
            runId = "run-a",
            startedAtEpochMs = 0,
            nowEpochMs = 610_000,
            intervalMs = 300_000,
            probeIntervalMs = 1_800_000,
            endAtEpochMs = 1_800_000,
        )
        ticker.advanceTo(900_000)

        assertEquals(listOf(900_000L), sink.plannedTimes)
    }

    @Test
    fun stop_releases_the_wake_lock_and_cancels_the_ticker() {
        val wakeLock = RecordingStrictWakeLock()
        val clock = ManualStrictClock(0)
        val ticker = ManualStrictTicker(clock)
        val coordinator = StrictSamplingCoordinator(wakeLock, ticker, RecordingStrictSnapshotSink(), clock::now)

        coordinator.start("run-a", 0, 0, 300_000, 1_800_000, 1_800_000)
        coordinator.stop("run-a")

        assertEquals(listOf("acquire", "release"), wakeLock.events)
        assertEquals(true, ticker.cancelled)
    }

    @Test
    fun reaching_the_end_stops_the_ticker_and_releases_the_wake_lock() {
        val wakeLock = RecordingStrictWakeLock()
        val clock = ManualStrictClock(0)
        val ticker = ManualStrictTicker(clock)
        val sink = RecordingStrictSnapshotSink()
        val coordinator = StrictSamplingCoordinator(wakeLock, ticker, sink, clock::now)

        coordinator.start("run-a", 0, 0, 300_000, 1_800_000, 600_000)
        ticker.advanceTo(600_000)

        assertEquals(listOf(300_000L), sink.plannedTimes)
        assertEquals(listOf(480_000L), sink.safeCheckpoints)
        assertEquals(listOf(600_000L), sink.finishes)
        ticker.advanceTo(720_000)
        assertEquals(listOf("acquire", "release"), wakeLock.events)
        assertEquals(true, ticker.cancelled)
    }

    @Test
    fun task_runtime_does_not_accumulate_into_the_next_snapshot_deadline() {
        val clock = ManualStrictClock(0)
        val ticker = ManualStrictTicker(clock)
        val sink = RecordingStrictSnapshotSink { clock.time += 2_000 }
        val coordinator = StrictSamplingCoordinator(RecordingStrictWakeLock(), ticker, sink, clock::now)

        coordinator.start("run-a", 0, 0, 300_000, 1_800_000, 1_800_000)
        ticker.advanceTo(300_000)

        assertEquals(600_000L, ticker.nextScheduledAt())
    }

    @Test
    fun emits_strict_probes_on_absolute_throughput_slots() {
        val clock = ManualStrictClock(0)
        val ticker = ManualStrictTicker(clock)
        val sink = RecordingStrictSnapshotSink()
        val coordinator = StrictSamplingCoordinator(RecordingStrictWakeLock(), ticker, sink, clock::now)

        coordinator.start(
            runId = "run-a",
            startedAtEpochMs = 0,
            nowEpochMs = 0,
            intervalMs = 300_000,
            probeIntervalMs = 1_800_000,
            endAtEpochMs = 7_200_000,
        )
        ticker.advanceTo(3_600_000)

        assertEquals(listOf(1_800_000L, 3_600_000L), sink.probeTimes)
    }

    @Test
    fun recovery_skips_probe_slots_that_are_already_in_the_past() {
        val clock = ManualStrictClock(2_000_000)
        val ticker = ManualStrictTicker(clock)
        val sink = RecordingStrictSnapshotSink()
        val coordinator = StrictSamplingCoordinator(RecordingStrictWakeLock(), ticker, sink, clock::now)

        coordinator.start("run-a", 0, 2_000_000, 300_000, 1_800_000, 7_200_000)
        ticker.advanceTo(3_600_000)

        assertEquals(listOf(3_600_000L), sink.probeTimes)
    }
}

private class RecordingStrictWakeLock : WakeLockHandle {
    val events = mutableListOf<String>()
    override fun acquire() { events += "acquire" }
    override fun release() { events += "release" }
}

private class ManualStrictClock(var time: Long) {
    fun now(): Long = time
}

private class ManualStrictTicker(private val clock: ManualStrictClock) : StrictSamplingTicker {
    private data class Entry(val at: Long, val task: () -> Unit)
    private val entries = mutableListOf<Entry>()
    var cancelled = false

    override fun schedule(delayMs: Long, task: () -> Unit) {
        entries += Entry(clock.time + delayMs, task)
    }

    override fun cancel() {
        cancelled = true
        entries.clear()
    }

    fun advanceTo(target: Long) {
        while (true) {
            val next = entries.minByOrNull { it.at }?.takeIf { it.at <= target } ?: break
            entries.remove(next)
            clock.time = next.at
            next.task()
        }
        clock.time = target
    }

    fun nextScheduledAt(): Long? = entries.minOfOrNull { it.at }
}

private class RecordingStrictSnapshotSink(
    private val afterCapture: () -> Unit = {},
) : StrictSnapshotSink {
    val plannedTimes = mutableListOf<Long>()
    val probeTimes = mutableListOf<Long>()
    val safeCheckpoints = mutableListOf<Long>()
    val finishes = mutableListOf<Long>()

    override fun capture(runId: String, plannedAtEpochMs: Long) {
        plannedTimes += plannedAtEpochMs
        afterCapture()
    }

    override fun probe(runId: String, plannedAtEpochMs: Long) {
        probeTimes += plannedAtEpochMs
    }

    override fun safeCheckpoint(runId: String, plannedAtEpochMs: Long) {
        safeCheckpoints += plannedAtEpochMs
    }

    override fun finish(runId: String, plannedAtEpochMs: Long) {
        finishes += plannedAtEpochMs
    }
}
