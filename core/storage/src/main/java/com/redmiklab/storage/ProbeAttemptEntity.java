package com.redmiklab.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "probe_attempts")
public class ProbeAttemptEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public final String runId;
    public final long timestampEpochMs;
    @NonNull public final String endpoint;
    public final int attemptNumber;
    @Nullable public final Long dnsLatencyMs;
    @Nullable public final Long httpsLatencyMs;
    public final long consumedBytes;
    @Nullable public final String failure;

    public ProbeAttemptEntity(@NonNull String runId, long timestampEpochMs, @NonNull String endpoint, int attemptNumber,
            @Nullable Long dnsLatencyMs, @Nullable Long httpsLatencyMs, long consumedBytes, @Nullable String failure) {
        this.runId = runId;
        this.timestampEpochMs = timestampEpochMs;
        this.endpoint = endpoint;
        this.attemptNumber = attemptNumber;
        this.dnsLatencyMs = dnsLatencyMs;
        this.httpsLatencyMs = httpsLatencyMs;
        this.consumedBytes = consumedBytes;
        this.failure = failure;
    }
}
