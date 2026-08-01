package com.redmiklab.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "probes")
public class ProbeEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public final String runId;
    public final long plannedAtEpochMs;
    public final long timestampEpochMs;
    public final long delayMs;
    @NonNull public final String triggerSource;
    @Nullable public final Long firstAttemptAtEpochMs;
    @Nullable public final Long lastAttemptAtEpochMs;
    public final long completedAtEpochMs;
    @Nullable public final Long dnsLatencyMs;
    @Nullable public final Long httpsLatencyMs;
    @Nullable public final Double downloadMbps;
    @Nullable public final Double uploadMbps;
    public final long consumedBytes;
    @Nullable public final String failure;

    public ProbeEntity(@NonNull String runId, long plannedAtEpochMs, long timestampEpochMs,
            long delayMs, @NonNull String triggerSource, @Nullable Long firstAttemptAtEpochMs,
            @Nullable Long lastAttemptAtEpochMs, long completedAtEpochMs, @Nullable Long dnsLatencyMs,
            @Nullable Long httpsLatencyMs, @Nullable Double downloadMbps, @Nullable Double uploadMbps,
            long consumedBytes, @Nullable String failure) {
        this.runId = runId;
        this.plannedAtEpochMs = plannedAtEpochMs;
        this.timestampEpochMs = timestampEpochMs;
        this.delayMs = delayMs;
        this.triggerSource = triggerSource;
        this.firstAttemptAtEpochMs = firstAttemptAtEpochMs;
        this.lastAttemptAtEpochMs = lastAttemptAtEpochMs;
        this.completedAtEpochMs = completedAtEpochMs;
        this.dnsLatencyMs = dnsLatencyMs;
        this.httpsLatencyMs = httpsLatencyMs;
        this.downloadMbps = downloadMbps;
        this.uploadMbps = uploadMbps;
        this.consumedBytes = consumedBytes;
        this.failure = failure;
    }

    @Ignore
    public ProbeEntity(@NonNull String runId, long plannedAtEpochMs, long timestampEpochMs,
            long delayMs, @NonNull String triggerSource, @Nullable Long dnsLatencyMs,
            @Nullable Long httpsLatencyMs, @Nullable Double downloadMbps, @Nullable Double uploadMbps,
            long consumedBytes, @Nullable String failure) {
        this(runId, plannedAtEpochMs, timestampEpochMs, delayMs, triggerSource,
                timestampEpochMs, timestampEpochMs, timestampEpochMs, dnsLatencyMs,
                httpsLatencyMs, downloadMbps, uploadMbps, consumedBytes, failure);
    }

    @Ignore
    public ProbeEntity(@NonNull String runId, long timestampEpochMs, @Nullable Long dnsLatencyMs,
            @Nullable Long httpsLatencyMs, @Nullable Double downloadMbps, @Nullable Double uploadMbps,
            long consumedBytes, @Nullable String failure) {
        this(runId, timestampEpochMs, timestampEpochMs, 0, "LEGACY",
                timestampEpochMs, timestampEpochMs, timestampEpochMs, dnsLatencyMs,
                httpsLatencyMs, downloadMbps, uploadMbps, consumedBytes, failure);
    }
}
