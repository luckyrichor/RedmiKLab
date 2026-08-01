package com.redmiklab.app

import com.redmiklab.diagnostics.NetworkSnapshotSource
import com.redmiklab.diagnostics.TrafficStatsSource
import com.redmiklab.model.AppTraffic
import com.redmiklab.model.NetworkSnapshot
import com.redmiklab.model.RadioTechnology
import com.redmiklab.model.TrafficWindow
import com.redmiklab.storage.AppTrafficEntity
import com.redmiklab.storage.SnapshotEntity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSnapshotWriterTest {
    @Test
    fun writes_planned_actual_delay_and_a_continuous_traffic_window() {
        val snapshotSource = NetworkSnapshotSource {
            NetworkSnapshot(Instant.ofEpochMilli(1_125), true, RadioTechnology.FiveG, -85, 4, false)
        }
        var requestedStart: Instant? = null
        var requestedEnd: Instant? = null
        val trafficSource = TrafficStatsSource { start, end ->
            requestedStart = start
            requestedEnd = end
            TrafficWindow(start, end, 300, listOf(AppTraffic("com.video", "Video", 300)), true)
        }
        val store = RecordingSnapshotStore(lastSnapshotAtEpochMs = 500)
        val writer = DiagnosticSnapshotWriter(snapshotSource, trafficSource, store)

        writer.write(
            runId = "run-a",
            runStartedAtEpochMs = 100,
            plannedAtEpochMs = 1_000,
            triggerSource = "PERIODIC",
        )

        assertEquals(Instant.ofEpochMilli(500), requestedStart)
        assertEquals(Instant.ofEpochMilli(1_125), requestedEnd)
        assertEquals(1_000L, store.snapshot!!.plannedAtEpochMs)
        assertEquals(1_125L, store.snapshot!!.timestampEpochMs)
        assertEquals(125L, store.snapshot!!.delayMs)
        assertEquals("PERIODIC", store.snapshot!!.triggerSource)
        assertEquals(1, store.traffic.size)
    }

    @Test
    fun records_traffic_collection_failure_without_dropping_the_signal_snapshot() {
        val snapshotSource = NetworkSnapshotSource {
            NetworkSnapshot(Instant.ofEpochMilli(1_125), true, RadioTechnology.FourG, -95, 3, false)
        }
        val trafficSource = TrafficStatsSource { _, _ -> throw SecurityException("missing usage access") }
        val store = RecordingSnapshotStore(lastSnapshotAtEpochMs = null)
        val writer = DiagnosticSnapshotWriter(snapshotSource, trafficSource, store)

        writer.write("run-a", 100, 1_000, "PERIODIC")

        assertEquals("USAGE_ACCESS_MISSING", store.snapshot!!.trafficCollectionStatus)
        assertEquals(0L, store.snapshot!!.totalMobileBytes)
    }

    @Test
    fun persists_idle_charging_network_and_guardian_runtime_state() {
        val snapshotSource = NetworkSnapshotSource {
            NetworkSnapshot(Instant.ofEpochMilli(1_125), true, RadioTechnology.FiveG, -85, 4, false)
        }
        val trafficSource = TrafficStatsSource { start, end -> TrafficWindow(start, end, 0, emptyList(), true) }
        val runtimeState = DeviceRuntimeState(
            screenInteractive = false,
            deviceLocked = true,
            charging = true,
            lightIdle = true,
            deepIdle = true,
            batteryExempt = true,
            physicalNetworkId = "119",
            vpnNetworkId = "120",
            vpnUnderlyingNetworkId = "119",
            guardianState = "WAITING_FOR_NETWORK",
            guardianRetryAttempt = 5,
            captureComplete = false,
            lastNetworkLostAtEpochMs = 900,
            lastNetworkRecoveredAtEpochMs = 800,
        )
        val store = RecordingSnapshotStore(null)

        DiagnosticSnapshotWriter(snapshotSource, trafficSource, store) { runtimeState }
            .write("run-a", 100, 1_000, "STRICT_INTERNAL")

        val saved = store.snapshot!!
        assertFalse(saved.screenInteractive)
        assertTrue(saved.deviceLocked)
        assertTrue(saved.charging)
        assertTrue(saved.deepIdle)
        assertEquals("119", saved.physicalNetworkId)
        assertEquals("WAITING_FOR_NETWORK", saved.guardianState)
        assertEquals(5, saved.guardianRetryAttempt)
        assertFalse(saved.captureComplete)
    }
}

private class RecordingSnapshotStore(
    private val lastSnapshotAtEpochMs: Long?,
) : DiagnosticSnapshotStore {
    var snapshot: SnapshotEntity? = null
    val traffic = mutableListOf<AppTrafficEntity>()

    override fun lastSnapshotAt(runId: String): Long? = lastSnapshotAtEpochMs
    override fun insertSnapshot(snapshot: SnapshotEntity) { this.snapshot = snapshot }
    override fun insertAppTraffic(traffic: AppTrafficEntity) { this.traffic += traffic }
}
