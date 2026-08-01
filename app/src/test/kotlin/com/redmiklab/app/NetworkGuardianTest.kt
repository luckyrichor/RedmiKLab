package com.redmiklab.app

import com.redmiklab.diagnostics.WakeLockHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkGuardianTest {
    @Test
    fun strict_sampling_survives_a_tunnel_gap_and_guardian_rebuild() {
        var now = 0L
        val ticker = GapIntegrationTicker { now }
        val snapshots = mutableListOf<Long>()
        val strict = StrictSamplingCoordinator(
            wakeLock = object : WakeLockHandle {
                override fun acquire() = Unit
                override fun release() = Unit
            },
            ticker = ticker,
            sink = object : StrictSnapshotSink {
                override fun capture(runId: String, plannedAtEpochMs: Long) {
                    snapshots += plannedAtEpochMs
                }
                override fun probe(runId: String, plannedAtEpochMs: Long) = Unit
                override fun safeCheckpoint(runId: String, plannedAtEpochMs: Long) = Unit
                override fun finish(runId: String, plannedAtEpochMs: Long) = Unit
            },
            nowEpochMs = { now },
        )
        val effects = FakeGuardianEffects()
        val guardian = NetworkGuardian(effects)
        strict.start("run", 0, 0, 300_000, 1_800_000, 900_000)
        guardian.start()
        guardian.networkAvailable(guardian.state.generation, "115", "network-115")

        guardian.networkLost("115")
        now = 300_000
        ticker.runDue()
        guardian.networkAvailable(guardian.state.generation, "119", "network-119")

        assertEquals(listOf(300_000L), snapshots)
        assertEquals(listOf("network-115", "network-119"), effects.rebuiltNetworks)
        assertTrue(effects.monitoring)
    }

    @Test
    fun lost_network_pauses_only_tunnel_and_keeps_monitoring() {
        val effects = FakeGuardianEffects()
        val guardian = NetworkGuardian(effects)
        guardian.start()
        val generation = guardian.state.generation
        guardian.networkAvailable(generation, "115", "network-115")

        guardian.networkLost("115")

        assertEquals(listOf("NETWORK_LOST:115"), effects.pauseReasons)
        assertTrue(effects.monitoring)
        assertFalse(effects.serviceStopRequested)
        assertEquals(GuardianPhase.RECONNECTING, guardian.state.phase)
    }

    @Test
    fun new_network_rebuilds_tunnel_and_closes_open_gap() {
        val effects = FakeGuardianEffects()
        val guardian = NetworkGuardian(effects)
        guardian.start()
        val initialGeneration = guardian.state.generation
        guardian.networkAvailable(initialGeneration, "115", "network-115")
        guardian.networkLost("115")
        val recoveryGeneration = guardian.state.generation

        guardian.networkAvailable(recoveryGeneration, "119", "network-119")

        assertEquals(listOf("network-115", "network-119"), effects.rebuiltNetworks)
        assertEquals(listOf("115", "119"), effects.closedGapNetworks)
        assertEquals(GuardianPhase.HEALTHY, guardian.state.phase)
    }

    @Test
    fun retry_failure_schedules_only_the_bounded_backoff_sequence() {
        val effects = FakeGuardianEffects()
        val guardian = NetworkGuardian(effects)
        guardian.start()
        val generation = guardian.state.generation

        repeat(5) { guardian.reconnectFailed(generation) }

        assertEquals(listOf(30_000L, 30_000L, 60_000L, 120_000L, 300_000L), effects.retryDelays)
        assertEquals(GuardianPhase.WAITING_FOR_NETWORK, guardian.state.phase)
        assertTrue(effects.monitoring)
    }
}

private class GapIntegrationTicker(private val now: () -> Long) : StrictSamplingTicker {
    private data class Entry(val at: Long, val task: () -> Unit)
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMs: Long, task: () -> Unit) {
        entries += Entry(now() + delayMs, task)
    }

    override fun cancel() {
        entries.clear()
    }

    fun runDue() {
        entries.filter { it.at <= now() }.sortedBy { it.at }.toList().forEach {
            entries.remove(it)
            it.task()
        }
    }
}

private class FakeGuardianEffects : NetworkGuardianEffects<String> {
    var monitoring = false
    var serviceStopRequested = false
    val pauseReasons = mutableListOf<String>()
    val rebuiltNetworks = mutableListOf<String>()
    val closedGapNetworks = mutableListOf<String>()
    val retryDelays = mutableListOf<Long>()
    val transitions = mutableListOf<Pair<GuardianState, GuardianState>>()

    override fun startMonitoring(generation: Long) { monitoring = true }
    override fun stopMonitoring() { monitoring = false }
    override fun pauseTunnel(reason: String) { pauseReasons += reason }
    override fun rebuildTunnel(network: String) { rebuiltNetworks += network }
    override fun openGap(reason: String) = Unit
    override fun closeGap(networkId: String) { closedGapNetworks += networkId }
    override fun scheduleRetry(delayMs: Long, generation: Long, attempt: Int) { retryDelays += delayMs }
    override fun recordTransition(previous: GuardianState, current: GuardianState) {
        transitions += previous to current
    }
}
