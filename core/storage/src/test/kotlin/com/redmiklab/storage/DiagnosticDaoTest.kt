package com.redmiklab.storage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class DiagnosticDaoTest {
    private lateinit var database: DiagnosticDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DiagnosticDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun reads_snapshots_in_timestamp_order_for_one_run() {
        database.diagnosticDao().insertSnapshot(SnapshotEntity("run-a", 20, 200, true, "5G", -85, 4, false, "SUCCESS"))
        database.diagnosticDao().insertSnapshot(SnapshotEntity("run-a", 10, 100, true, "4G", -100, 2, false, "USAGE_ACCESS_MISSING"))

        val snapshots = database.diagnosticDao().snapshotsFor("run-a")
        assertEquals(listOf(10L, 20L), snapshots.map { it.timestampEpochMs })
        assertEquals("4G", snapshots.first().radioTechnology)
        assertEquals("USAGE_ACCESS_MISSING", snapshots.first().trafficCollectionStatus)
    }

    @Test
    fun reads_the_most_recent_completed_run_first() {
        database.diagnosticDao().insertRun(DiagnosticRunEntity("older", 10, 20, "COMPLETED"))
        database.diagnosticDao().insertRun(DiagnosticRunEntity("newer", 30, 40, "COMPLETED"))

        assertEquals(listOf("newer", "older"), database.diagnosticDao().completedRuns().map { it.runId })
    }

    @Test
    fun reads_probe_results_in_timestamp_order_for_one_run() {
        database.diagnosticDao().insertProbe(ProbeEntity("run-a", 20, 10, 20, 3.5, 1.5, 1000, null))
        database.diagnosticDao().insertProbe(ProbeEntity("run-a", 10, null, null, null, null, 0, "TIMEOUT"))

        assertEquals(listOf(10L, 20L), database.diagnosticDao().probesFor("run-a").map { it.timestampEpochMs })
    }

    @Test
    fun finds_an_existing_probe_for_the_same_planned_slot() {
        database.diagnosticDao().insertProbe(
            ProbeEntity(
                "run-a",
                1_800_000,
                1_800_125,
                125,
                "STRICT_INTERNAL",
                10,
                20,
                3.5,
                null,
                1_000,
                null,
            ),
        )

        val stored = database.diagnosticDao().probeForSchedule("run-a", 1_800_000)

        assertNotNull(stored)
        assertEquals(125L, stored.delayMs)
        assertEquals("STRICT_INTERNAL", stored.triggerSource)
        assertEquals(null, database.diagnosticDao().probeForSchedule("run-a", 3_600_000))
    }

    @Test
    fun reads_app_traffic_for_a_run_in_window_order() {
        database.diagnosticDao().insertAppTraffic(AppTrafficEntity("run-a", 20, 30, "sync", "Sync", 600, true))
        database.diagnosticDao().insertAppTraffic(AppTrafficEntity("run-a", 10, 20, "video", "Video", 300, true))

        assertEquals(listOf(10L, 20L), database.diagnosticDao().appTrafficFor("run-a").map { it.windowStartEpochMs })
    }

    @Test
    fun reads_connection_metadata_for_a_run_in_timestamp_order() {
        database.diagnosticDao().insertConnectionFlow(ConnectionFlowEntity("run-a", 20, "TCP", "1.1.1.1", 443, 800, null, null, "BACKGROUND_SCREEN_LOCKED"))
        database.diagnosticDao().insertConnectionFlow(ConnectionFlowEntity("run-a", 10, "UDP", "8.8.8.8", 53, 120, "com.example", "Example", "FOREGROUND_ACTIVE"))

        assertEquals(listOf(10L, 20L), database.diagnosticDao().connectionFlowsFor("run-a").map { it.timestampEpochMs })
    }

    @Test
    fun finds_the_run_that_contains_a_connection_event_timestamp() {
        database.diagnosticDao().insertRun(DiagnosticRunEntity("finished", 100, 200, "COMPLETED"))
        database.diagnosticDao().insertRun(DiagnosticRunEntity("running", 300, 0, "RUNNING"))

        assertEquals("finished", database.diagnosticDao().runContaining(150).runId)
        assertEquals("running", database.diagnosticDao().runContaining(350).runId)
        assertEquals(null, database.diagnosticDao().runContaining(250))
    }

    @Test
    fun atomically_interrupts_every_stale_run_and_inserts_the_new_run() {
        database.diagnosticDao().insertRun(DiagnosticRunEntity("stale-a", 100, 500, "RUNNING"))
        database.diagnosticDao().insertRun(DiagnosticRunEntity("stale-b", 200, 0, "RUNNING"))
        database.diagnosticDao().insertRun(DiagnosticRunEntity("complete", 50, 90, "COMPLETED"))

        database.diagnosticDao().startRunReplacingActive(
            DiagnosticRunEntity("new", 300, 600, "RUNNING"),
        )

        assertEquals("INTERRUPTED", database.diagnosticDao().runById("stale-a").status)
        assertEquals(300L, database.diagnosticDao().runById("stale-a").endedAtEpochMs)
        assertEquals("INTERRUPTED", database.diagnosticDao().runById("stale-b").status)
        assertEquals("COMPLETED", database.diagnosticDao().runById("complete").status)
        assertEquals("RUNNING", database.diagnosticDao().runById("new").status)
    }

    @Test
    fun persists_the_locked_runtime_configuration_for_a_run() {
        database.diagnosticDao().insertRun(
            DiagnosticRunEntity(
                "strict-run",
                1_000,
                22_600,
                1_010,
                0,
                "RUNNING",
                "STRICT",
                5,
                10,
                30,
                true,
                null,
                1_010,
            ),
        )

        val stored = database.diagnosticDao().runById("strict-run")

        assertEquals(1_000L, stored.plannedStartEpochMs)
        assertEquals(22_600L, stored.plannedEndEpochMs)
        assertEquals("STRICT", stored.runtimeMode)
        assertEquals(5, stored.snapshotMinutes)
        assertEquals(true, stored.connectionCaptureEnabled)
    }

    @Test
    fun persists_and_updates_the_run_report_identity() {
        database.diagnosticDao().insertRun(
            DiagnosticRunEntity(
                "named-run",
                1_000,
                22_600,
                1_010,
                0,
                "RUNNING",
                "STRICT",
                5,
                10,
                30,
                true,
                null,
                1_010,
                "20260723-0700-RedmiKLab-K80-夜间测试",
                "JSON",
                "24117RK2CC",
            ),
        )

        assertEquals(
            1,
            database.diagnosticDao().updateReportIdentity(
                "named-run",
                "重新命名",
                "PDF",
            ),
        )
        val stored = database.diagnosticDao().runById("named-run")
        assertEquals("重新命名", stored.reportBaseName)
        assertEquals("PDF", stored.preferredReportFormat)
        assertEquals("24117RK2CC", stored.deviceModel)
    }

    @Test
    fun backfills_the_current_model_only_for_legacy_runs() {
        database.diagnosticDao().insertRun(DiagnosticRunEntity("legacy", 10, 20, "COMPLETED"))
        database.diagnosticDao().insertRun(
            DiagnosticRunEntity(
                "known", 10, 20, 10, 20, "COMPLETED", "STANDARD",
                5, 10, 30, false, null, 20, "known", "ZIP", "K60",
            ),
        )

        assertEquals(1, database.diagnosticDao().backfillMissingDeviceModel("K80"))
        assertEquals("K80", database.diagnosticDao().runById("legacy").deviceModel)
        assertEquals("K60", database.diagnosticDao().runById("known").deviceModel)
    }

    @Test
    fun finds_an_existing_snapshot_for_the_same_planned_slot_and_trigger() {
        database.diagnosticDao().insertSnapshot(
            SnapshotEntity(
                "run-a",
                1_000,
                1_125,
                125,
                "PERIODIC",
                200,
                true,
                "5G",
                -85,
                4,
                false,
                "SUCCESS",
            ),
        )

        assertNotNull(database.diagnosticDao().snapshotForSchedule("run-a", 1_000, "PERIODIC"))
        assertEquals(null, database.diagnosticDao().snapshotForSchedule("run-a", 1_000, "FINAL"))
    }

    @Test
    fun reads_diagnostic_events_in_timestamp_order() {
        database.diagnosticDao().insertEvent(DiagnosticEventEntity("run-a", 20, "RECOVERED", "alarm"))
        database.diagnosticDao().insertEvent(DiagnosticEventEntity("run-a", 10, "DATA_SYNC_TIMEOUT", "system"))

        assertEquals(
            listOf("DATA_SYNC_TIMEOUT", "RECOVERED"),
            database.diagnosticDao().eventsFor("run-a").map { it.eventType },
        )
    }

    @Test
    fun only_one_finalizer_can_claim_a_running_run() {
        database.diagnosticDao().insertRun(DiagnosticRunEntity("run-a", 100, 0, "RUNNING"))

        assertEquals(1, database.diagnosticDao().claimRunForFinalization("run-a", 7_000))
        assertEquals(0, database.diagnosticDao().claimRunForFinalization("run-a", 7_001))
        assertEquals("FINALIZING", database.diagnosticDao().runById("run-a").status)
        assertEquals(7_000L, database.diagnosticDao().runById("run-a").lastActionAtEpochMs)
    }

    @Test
    fun a_finalizing_run_remains_recoverable_after_process_restart() {
        database.diagnosticDao().insertRun(DiagnosticRunEntity("run-a", 100, 0, "RUNNING"))
        database.diagnosticDao().claimRunForFinalization("run-a", 7_000)

        assertEquals("run-a", database.diagnosticDao().recoverableRun().runId)
        assertEquals(1, database.diagnosticDao().completeFinalizingRun("run-a", 7_100, "SCHEDULED_END"))
        assertEquals("COMPLETED", database.diagnosticDao().runById("run-a").status)
        assertEquals(null, database.diagnosticDao().recoverableRun())
    }

    @Test
    fun persists_layered_traffic_network_lifecycle_and_capture_gaps() {
        database.diagnosticDao().insertNetworkLifecycle(
            NetworkLifecycleEntity(
                "run-a", 30, "RECONNECTING", "WAITING_FOR_NETWORK",
                null, "223", "218", "RETRIES_EXHAUSTED", 5, 9,
            ),
        )
        database.diagnosticDao().insertTrafficAccounting(
            TrafficAccountingEntity(
                "run-a", 10, 20, "com.example", "UPSTREAM_SOCKET_ACCEPTED",
                "UPSTREAM", 480, "ACCEPTED", null,
            ),
        )
        val gapId = database.diagnosticDao().insertCaptureGap(
            CaptureGapEntity("run-a", 20, null, "NO_PHYSICAL_NETWORK", 0, null),
        )
        assertEquals(1, database.diagnosticDao().finishCaptureGap(gapId, 40, 120, "network=119"))

        assertEquals("WAITING_FOR_NETWORK", database.diagnosticDao().networkLifecycleFor("run-a").single().newState)
        assertEquals(480L, database.diagnosticDao().trafficAccountingFor("run-a").single().bytes)
        assertEquals(40L, database.diagnosticDao().captureGapsFor("run-a").single().endedAtEpochMs)
    }

    @Test
    fun persists_evidence_fingerprint_and_snapshot_runtime_state() {
        database.diagnosticDao().insertAppEvidence(
            AppEvidenceEntity(
                "run-a", 10, "com.example", "Example", "MEDIA_SESSION", "{}",
                "MEDIA_CALLBACK", "session-1", "sha256", "AVAILABLE",
            ),
        )
        database.diagnosticDao().insertSnapshot(
            SnapshotEntity(
                "run-a", 10, 11, 1, "STRICT_INTERNAL", 100, true, "FiveG",
                -92, 4, false, "SUCCESS", true, true, false, true, true, true,
                "119", "223", "119", "HEALTHY", 0, true, 5L, 9L,
            ),
        )

        val evidence = database.diagnosticDao().appEvidenceFor("run-a").single()
        assertEquals("MEDIA_CALLBACK", evidence.source)
        assertEquals("sha256", evidence.contentFingerprint)
        val snapshot = database.diagnosticDao().snapshotsFor("run-a").single()
        assertEquals(true, snapshot.deepIdle)
        assertEquals("119", snapshot.physicalNetworkId)
        assertEquals("HEALTHY", snapshot.guardianState)
    }
}
