package com.redmiklab.storage;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import java.util.List;

@Dao
public interface DiagnosticDao {
    @Insert
    void insertSnapshot(SnapshotEntity snapshot);

    @Insert
    void insertRun(DiagnosticRunEntity run);

    @Update
    void updateRun(DiagnosticRunEntity run);

    @Insert
    void insertProbe(ProbeEntity probe);

    @Insert
    void insertProbeAttempt(ProbeAttemptEntity attempt);

    @Insert
    void insertAppTraffic(AppTrafficEntity traffic);

    @Insert
    void insertConnectionFlow(ConnectionFlowEntity flow);

    @Insert
    void insertEvent(DiagnosticEventEntity event);

    @Insert
    void insertAppEvidence(AppEvidenceEntity evidence);

    @Insert
    void insertNetworkLifecycle(NetworkLifecycleEntity lifecycle);

    @Insert
    void insertTrafficAccounting(TrafficAccountingEntity traffic);

    @Insert
    long insertCaptureGap(CaptureGapEntity gap);

    @Query("SELECT * FROM snapshots WHERE runId = :runId ORDER BY timestampEpochMs ASC")
    List<SnapshotEntity> snapshotsFor(String runId);

    @Query("SELECT MAX(timestampEpochMs) FROM snapshots WHERE runId = :runId")
    Long lastSnapshotAt(String runId);

    @Query("SELECT * FROM diagnostic_runs WHERE status = 'COMPLETED' ORDER BY endedAtEpochMs DESC")
    List<DiagnosticRunEntity> completedRuns();

    @Query("SELECT * FROM diagnostic_runs WHERE status = 'RUNNING' ORDER BY startedAtEpochMs DESC LIMIT 1")
    DiagnosticRunEntity activeRun();

    @Query("SELECT * FROM diagnostic_runs WHERE status IN ('RUNNING', 'FINALIZING') ORDER BY startedAtEpochMs DESC LIMIT 1")
    DiagnosticRunEntity recoverableRun();

    @Query("UPDATE diagnostic_runs SET endedAtEpochMs = :endedAtEpochMs, status = 'INTERRUPTED' WHERE status = 'RUNNING'")
    int interruptActiveRuns(long endedAtEpochMs);

    @Transaction
    default void startRunReplacingActive(DiagnosticRunEntity run) {
        interruptActiveRuns(run.startedAtEpochMs);
        insertRun(run);
    }

    @Query("SELECT * FROM diagnostic_runs WHERE status IN ('COMPLETED', 'INTERRUPTED') ORDER BY endedAtEpochMs DESC")
    List<DiagnosticRunEntity> terminalRuns();

    @Query("SELECT * FROM probes WHERE runId = :runId ORDER BY timestampEpochMs ASC")
    List<ProbeEntity> probesFor(String runId);

    @Query("SELECT * FROM probes WHERE runId = :runId AND plannedAtEpochMs = :plannedAtEpochMs LIMIT 1")
    ProbeEntity probeForSchedule(String runId, long plannedAtEpochMs);

    @Query("SELECT * FROM probe_attempts WHERE runId = :runId ORDER BY timestampEpochMs ASC, id ASC")
    List<ProbeAttemptEntity> probeAttemptsFor(String runId);

    @Query("SELECT * FROM app_traffic WHERE runId = :runId ORDER BY windowStartEpochMs ASC")
    List<AppTrafficEntity> appTrafficFor(String runId);

    @Query("SELECT * FROM connection_flows WHERE runId = :runId ORDER BY timestampEpochMs ASC, id ASC")
    List<ConnectionFlowEntity> connectionFlowsFor(String runId);

    @Query("SELECT * FROM diagnostic_events WHERE runId = :runId ORDER BY timestampEpochMs ASC, id ASC")
    List<DiagnosticEventEntity> eventsFor(String runId);

    @Query("SELECT * FROM app_evidence WHERE runId = :runId ORDER BY timestampEpochMs ASC, id ASC")
    List<AppEvidenceEntity> appEvidenceFor(String runId);

    @Query("SELECT * FROM network_lifecycle WHERE runId = :runId ORDER BY timestampEpochMs ASC, id ASC")
    List<NetworkLifecycleEntity> networkLifecycleFor(String runId);

    @Query("SELECT * FROM traffic_accounting WHERE runId = :runId ORDER BY windowStartEpochMs ASC, id ASC")
    List<TrafficAccountingEntity> trafficAccountingFor(String runId);

    @Query("SELECT * FROM capture_gaps WHERE runId = :runId ORDER BY startedAtEpochMs ASC, id ASC")
    List<CaptureGapEntity> captureGapsFor(String runId);

    @Query("UPDATE capture_gaps SET endedAtEpochMs = :endedAtEpochMs, systemMobileBytes = :systemMobileBytes, details = :details WHERE id = :id")
    int finishCaptureGap(long id, long endedAtEpochMs, long systemMobileBytes, String details);

    @Query("DELETE FROM app_evidence WHERE id = :id")
    void deleteAppEvidence(long id);

    @Query("SELECT * FROM snapshots WHERE runId = :runId AND plannedAtEpochMs = :plannedAtEpochMs AND triggerSource = :triggerSource LIMIT 1")
    SnapshotEntity snapshotForSchedule(String runId, long plannedAtEpochMs, String triggerSource);

    @Query("SELECT * FROM diagnostic_runs WHERE runId = :runId LIMIT 1")
    DiagnosticRunEntity runById(String runId);

    @Query("UPDATE diagnostic_runs SET reportBaseName = :reportBaseName, preferredReportFormat = :preferredReportFormat WHERE runId = :runId")
    int updateReportIdentity(String runId, String reportBaseName, String preferredReportFormat);

    @Query("UPDATE diagnostic_runs SET deviceModel = :deviceModel WHERE deviceModel = ''")
    int backfillMissingDeviceModel(String deviceModel);

    @Query("UPDATE diagnostic_runs SET status = :status, endedAtEpochMs = :endedAtEpochMs, terminalReason = :reason, lastActionAtEpochMs = :endedAtEpochMs WHERE runId = :runId AND status = 'RUNNING'")
    int finishRun(String runId, long endedAtEpochMs, String status, String reason);

    @Query("UPDATE diagnostic_runs SET status = 'FINALIZING', lastActionAtEpochMs = :claimedAtEpochMs WHERE runId = :runId AND status = 'RUNNING'")
    int claimRunForFinalization(String runId, long claimedAtEpochMs);

    @Query("UPDATE diagnostic_runs SET status = 'COMPLETED', endedAtEpochMs = :endedAtEpochMs, terminalReason = :reason, lastActionAtEpochMs = :endedAtEpochMs WHERE runId = :runId AND status = 'FINALIZING'")
    int completeFinalizingRun(String runId, long endedAtEpochMs, String reason);

    @Query("UPDATE diagnostic_runs SET lastActionAtEpochMs = :timestampEpochMs WHERE runId = :runId AND status = 'RUNNING'")
    int touchRun(String runId, long timestampEpochMs);

    @Query("SELECT COALESCE(SUM(consumedBytes), 0) FROM probes WHERE runId = :runId")
    long consumedProbeBytes(String runId);

    @Query("SELECT * FROM diagnostic_runs WHERE startedAtEpochMs <= :timestampEpochMs AND ((status = 'RUNNING' AND endedAtEpochMs = 0) OR endedAtEpochMs >= :timestampEpochMs) ORDER BY startedAtEpochMs DESC LIMIT 1")
    DiagnosticRunEntity runContaining(long timestampEpochMs);
}
