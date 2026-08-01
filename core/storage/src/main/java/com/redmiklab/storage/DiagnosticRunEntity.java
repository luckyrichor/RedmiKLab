package com.redmiklab.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "diagnostic_runs")
public class DiagnosticRunEntity {
    @PrimaryKey @NonNull public final String runId;
    public final long plannedStartEpochMs;
    public final long plannedEndEpochMs;
    public final long startedAtEpochMs;
    public final long endedAtEpochMs;
    @NonNull public final String status;
    @NonNull public final String runtimeMode;
    public final int snapshotMinutes;
    public final int connectivityMinutes;
    public final int throughputMinutes;
    public final boolean connectionCaptureEnabled;
    @Nullable public final String terminalReason;
    public final long lastActionAtEpochMs;
    @NonNull public final String reportBaseName;
    @NonNull public final String preferredReportFormat;
    @NonNull public final String deviceModel;

    public DiagnosticRunEntity(@NonNull String runId, long plannedStartEpochMs, long plannedEndEpochMs,
            long startedAtEpochMs, long endedAtEpochMs, @NonNull String status,
            @NonNull String runtimeMode, int snapshotMinutes, int connectivityMinutes,
            int throughputMinutes, boolean connectionCaptureEnabled, @Nullable String terminalReason,
            long lastActionAtEpochMs, @NonNull String reportBaseName,
            @NonNull String preferredReportFormat, @NonNull String deviceModel) {
        this.runId = runId;
        this.plannedStartEpochMs = plannedStartEpochMs;
        this.plannedEndEpochMs = plannedEndEpochMs;
        this.startedAtEpochMs = startedAtEpochMs;
        this.endedAtEpochMs = endedAtEpochMs;
        this.status = status;
        this.runtimeMode = runtimeMode;
        this.snapshotMinutes = snapshotMinutes;
        this.connectivityMinutes = connectivityMinutes;
        this.throughputMinutes = throughputMinutes;
        this.connectionCaptureEnabled = connectionCaptureEnabled;
        this.terminalReason = terminalReason;
        this.lastActionAtEpochMs = lastActionAtEpochMs;
        this.reportBaseName = reportBaseName;
        this.preferredReportFormat = preferredReportFormat;
        this.deviceModel = deviceModel;
    }

    @Ignore
    public DiagnosticRunEntity(@NonNull String runId, long plannedStartEpochMs, long plannedEndEpochMs,
            long startedAtEpochMs, long endedAtEpochMs, @NonNull String status,
            @NonNull String runtimeMode, int snapshotMinutes, int connectivityMinutes,
            int throughputMinutes, boolean connectionCaptureEnabled, @Nullable String terminalReason,
            long lastActionAtEpochMs) {
        this(runId, plannedStartEpochMs, plannedEndEpochMs, startedAtEpochMs, endedAtEpochMs,
                status, runtimeMode, snapshotMinutes, connectivityMinutes, throughputMinutes,
                connectionCaptureEnabled, terminalReason, lastActionAtEpochMs, "", "ZIP", "");
    }

    @Ignore
    public DiagnosticRunEntity(@NonNull String runId, long startedAtEpochMs, long endedAtEpochMs,
            @NonNull String status) {
        this(
                runId,
                startedAtEpochMs,
                endedAtEpochMs,
                startedAtEpochMs,
                "RUNNING".equals(status) ? 0 : endedAtEpochMs,
                status,
                "STANDARD",
                5,
                10,
                30,
                false,
                null,
                startedAtEpochMs,
                "",
                "ZIP",
                ""
        );
    }
}
